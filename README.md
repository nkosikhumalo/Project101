# Smart Pantry Manager

Java Android app backed by PostgreSQL through a Java REST API. Both the Android app and its API are written in Java. Docker is not required.

## Database setup

The local PostgreSQL database is named `smartpantry`, owned by the `nkosi` role. Its schema and 18 starter recipes have already been created in this environment. A restricted `pantry_app` login is configured for the API. To create them on another machine, run from the project root:

```bash
createdb -O "$USER" smartpantry
psql -d smartpantry -v ON_ERROR_STOP=1 -f backend/schema.sql -f backend/seed.sql
psql -d postgres -c "CREATE ROLE pantry_app LOGIN PASSWORD 'pantry_local_dev';"
psql -d smartpantry -c 'GRANT CONNECT ON DATABASE smartpantry TO pantry_app; GRANT USAGE ON SCHEMA public TO pantry_app; GRANT SELECT, INSERT, UPDATE, DELETE ON pantry_items TO pantry_app; GRANT SELECT ON recipes TO pantry_app; GRANT SELECT, UPDATE ON app_settings TO pantry_app; GRANT USAGE, SELECT ON SEQUENCE pantry_items_id_seq TO pantry_app;'
```

## Start the API

In a terminal, from the project root, start the Java API:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH \
./gradlew -p backend run
```

The API listens on port `8080`. The Java service connects to `jdbc:postgresql://127.0.0.1:5432/smartpantry` with the `pantry_app` login. Override `DATABASE_URL`, `DB_USER` or `DB_PASSWORD` with environment variables if your PostgreSQL setup differs. Gradle downloads the Java PostgreSQL driver and JSON library on first run.

## Run the Android app

From the project root, start PostgreSQL and run:

```bash
./run.sh
```

The script builds the APK and API, starts the PostgreSQL-backed API and the lower-memory API 35 emulator, installs the app, and opens it. Keep the terminal open while using the app; press Ctrl+C to stop processes the script started. It reuses an already-running API or emulator when available.

The project uses Gradle 8.13, JDK 21, and Android SDK Platform 35. The app reaches the API at `10.0.2.2:8080` from the emulator.

## Features

- Pantry list with custom adapter; tap an item to edit or delete it.
- Add/edit form with required field, positive quantity and expiry date validation.
- Suggested recipe list, clear no-match state, and recipe detail with ingredients and method.
- Settings screen for expiring-soon alerts; bottom navigation between Pantry, Recipes and Settings.
- 18 recipes in PostgreSQL, seeded by `backend/seed.sql`.

Recipe suggestions use strict matching: each required ingredient must be in the pantry in sufficient quantity. Ingredient names ignore case and common English plural endings. Kilograms convert to grams and litres to millilitres. Pieces, slices, cloves and unknown units only match their own unit category; incompatible units never satisfy a requirement. Multiple matching pantry entries are added together.

## API routes

- `GET /api/pantry`, `POST /api/pantry`
- `GET /api/pantry/{id}`, `PUT /api/pantry/{id}`, `DELETE /api/pantry/{id}`
- `GET /api/recipes`, `GET /api/recipes/suggested`, `GET /api/recipes/{id}`
- `GET /api/settings`, `PUT /api/settings`
