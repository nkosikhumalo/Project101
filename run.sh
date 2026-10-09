#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_SDK="${ANDROID_HOME:-/home/nkosi/Android/Sdk}"
JAVA_HOME="${SMART_PANTRY_JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
ADB="$ANDROID_SDK/platform-tools/adb"
EMULATOR="$ANDROID_SDK/emulator/emulator"
AVD_NAME="${ANDROID_AVD_NAME:-SmartPantry_API_35}"
APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$ANDROID_SDK/platform-tools:$ANDROID_SDK/emulator:$PATH"
export ANDROID_HOME="$ANDROID_SDK"
export ANDROID_SDK_ROOT="$ANDROID_SDK"
# The emulator bundles XCB but not Qt's Wayland plugin.
export QT_QPA_PLATFORM="${QT_QPA_PLATFORM:-xcb}"

API_PID=""
EMULATOR_PID=""
API_LOG=""
EMULATOR_LOG=""

cleanup() {
    if [[ -n "$EMULATOR_PID" ]]; then
        kill "$EMULATOR_PID" 2>/dev/null || true
        wait "$EMULATOR_PID" 2>/dev/null || true
    fi
    if [[ -n "$API_PID" ]]; then
        kill "$API_PID" 2>/dev/null || true
        wait "$API_PID" 2>/dev/null || true
    fi
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

cd "$PROJECT_DIR"

if ! command -v pg_isready >/dev/null 2>&1 || ! pg_isready -q; then
    echo "PostgreSQL is not running. Start PostgreSQL and run ./run.sh again." >&2
    exit 1
fi

DB_NAME="${SMART_PANTRY_DB:-smartpantry}"
echo "Preparing the database and loading the starter recipes..."
psql -d "$DB_NAME" -v ON_ERROR_STOP=1 \
    -f "$PROJECT_DIR/backend/schema.sql" \
    -f "$PROJECT_DIR/backend/seed.sql"

echo "Building the Android app and Java API..."
./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1024m assembleDebug
./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1024m \
    -p backend installDist

if ! curl --fail --silent --max-time 2 http://127.0.0.1:8080/api/recipes >/dev/null; then
    API_LOG="$(mktemp /tmp/smart-pantry-api.XXXXXX.log)"
    DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://127.0.0.1:5432/smartpantry}" \
        DB_USER="${DB_USER:-pantry_app}" \
        DB_PASSWORD="${DB_PASSWORD:-pantry_local_dev}" \
        "$PROJECT_DIR/backend/build/install/smart-pantry-api/bin/smart-pantry-api" \
        >"$API_LOG" 2>&1 &
    API_PID=$!

    echo "Starting the Java API..."
    for attempt in $(seq 1 60); do
        if curl --fail --silent --max-time 2 \
            http://127.0.0.1:8080/api/recipes >/dev/null; then
            break
        fi
        if ! kill -0 "$API_PID" 2>/dev/null; then
            cat "$API_LOG" >&2
            exit 1
        fi
        sleep 1
    done

    if ! curl --fail --silent --max-time 2 \
        http://127.0.0.1:8080/api/recipes >/dev/null; then
        echo "The API did not start. Log: $API_LOG" >&2
        exit 1
    fi
else
    echo "Using the Java API already running on port 8080."
fi

DEVICE_SERIAL="$("$ADB" devices | awk '$2 == "device" { print $1; exit }')"
if [[ -z "$DEVICE_SERIAL" ]]; then
    EMULATOR_LOG="$(mktemp /tmp/smart-pantry-emulator.XXXXXX.log)"
    echo "Starting Android emulator $AVD_NAME..."
    "$EMULATOR" -avd "$AVD_NAME" -memory 1536 -cores 2 -gpu auto \
        -no-snapshot -no-boot-anim >"$EMULATOR_LOG" 2>&1 &
    EMULATOR_PID=$!
fi

echo "Waiting for Android to finish booting..."
for attempt in $(seq 1 150); do
    if [[ -z "$DEVICE_SERIAL" ]]; then
        DEVICE_SERIAL="$("$ADB" devices | awk '$2 == "device" { print $1; exit }')"
    fi
    if [[ -n "$DEVICE_SERIAL" ]] && \
        [[ "$("$ADB" -s "$DEVICE_SERIAL" shell getprop sys.boot_completed 2>/dev/null \
            | tr -d '\r')" == "1" ]]; then
        break
    fi
    if [[ -n "$EMULATOR_PID" ]] && ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
        cat "$EMULATOR_LOG" >&2
        exit 1
    fi
    sleep 2
done

if [[ -z "$DEVICE_SERIAL" ]] || \
    [[ "$("$ADB" -s "$DEVICE_SERIAL" shell getprop sys.boot_completed 2>/dev/null \
        | tr -d '\r')" != "1" ]]; then
    echo "Android did not finish booting."
    if [[ -n "$EMULATOR_LOG" ]]; then
        echo "Emulator log: $EMULATOR_LOG" >&2
        cat "$EMULATOR_LOG" >&2
    fi
    exit 1
fi
if [[ -z "$EMULATOR_PID" ]]; then
    echo "Using Android device $DEVICE_SERIAL."
fi

echo "Installing and opening Smart Pantry..."
"$ADB" -s "$DEVICE_SERIAL" install -r "$APK"
"$ADB" -s "$DEVICE_SERIAL" shell am start \
    -n za.co.smartpantry/.PantryActivity

echo "Smart Pantry is running. Press Ctrl+C to stop services started by this script."
while true; do
    sleep 3600
done
