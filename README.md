# Smart Pantry Manager

Smart Pantry Manager is a Java Android app that keeps pantry items and preferences in a local SQLite database on the device. It works offline and does not need PostgreSQL, Docker, a separate API server, or an internet connection. The app includes 19 South African starter recipes.

## Run from Android Studio

Open this folder in Android Studio, let Gradle sync, choose an emulator or connected Android phone, then press **Run**. The first launch creates the local database and loads the starter recipes. Pantry items and settings remain on that device.

## Build from a terminal

If Android Studio is unstable, use its installed Android SDK and Java runtime from PowerShell in the project folder:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME = (Join-Path (Get-Location) '.gradle')
$env:ANDROID_USER_HOME = (Join-Path (Get-Location) '.android-local')
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Copy it to an Android phone and install it, or use Android Debug Bridge:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

If the phone already has a copy signed with a different debug key, Android requires uninstalling that copy before installing this APK; uninstalling also deletes that copy's app data.

## Features

- Add, edit, and delete pantry items, with quantity and optional expiry date.
- Browse the recipe collection and view ingredients and cooking steps.
- See recipes whose ingredients and quantities are covered by the pantry.
- Save the expiry-alert preference locally.
- Match equivalent metric quantities such as kilograms and grams, while keeping incompatible units separate.

## Project structure

- `app/` — Android app and its on-device SQLite database.
- `app/src/main/assets/recipes.json` — starter recipe catalogue loaded into SQLite on first launch.
- `backend/` — standalone Java/PostgreSQL API retained as a separate backend example; the Android app no longer depends on it.
