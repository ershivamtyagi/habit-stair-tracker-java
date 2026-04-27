# Habit Stair Tracker Java

A stair-based habit tracker built with JavaFX and MySQL, with daily progress, weekly milestones, notes, and profile badges.

## Run locally

```bash
mvn javafx:run
```

## MySQL setup

The app connects to MySQL on startup and creates the database and tables automatically.

| Setting  | Default            |
|----------|--------------------|
| Host     | `localhost`        |
| Port     | `3306`             |
| Database | `progress_tracker` |
| Username | `root`             |
| Password | `logic`            |

Override with environment variables or Java system properties:

| Env var       | System property |
|---------------|-----------------|
| `DB_HOST`     | `db.host`       |
| `DB_PORT`     | `db.port`       |
| `DB_NAME`     | `db.name`       |
| `DB_USER`     | `db.user`       |
| `DB_PASSWORD` | `db.password`   |

## Build a Windows exe

Requires Java 17+ and Maven.

```bash
mvn clean package
jpackage --input target --main-jar stair-progress-app-1.0.0.jar --name "StairProgress" --app-version 1.0.0 --type app-image --dest dist
```

The executable is generated at `dist\StairProgress\StairProgress.exe`.

See `BUILD.md` for full build details.
