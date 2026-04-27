# Stair Progress

JavaFX desktop app for building daily habits. Each habit tracks 7-day periods — complete all 7 days to earn a badge. Backed by MySQL.

## Run locally (dev mode)

```bash
mvn javafx:run
```

## MySQL setup

The app connects to MySQL on startup and creates the database and tables automatically.

| Setting  | Default           |
|----------|-------------------|
| Host     | `localhost`       |
| Port     | `3306`            |
| Database | `progress_tracker`|
| Username | `root`            |
| Password | `logic`           |

Override with environment variables or Java system properties:

| Env var       | System property |
|---------------|-----------------|
| `DB_HOST`     | `db.host`       |
| `DB_PORT`     | `db.port`       |
| `DB_NAME`     | `db.name`       |
| `DB_USER`     | `db.user`       |
| `DB_PASSWORD` | `db.password`   |

## Build a Windows .exe

Requires Java 17+ (includes `jpackage`) and Maven. No extra tools needed.

```bash
# 1. Build the fat JAR
mvn clean package

# 2. Package as a self-contained exe
jpackage --input target --main-jar stair-progress-app-1.0.0.jar --name "StairProgress" --app-version 1.0.0 --type app-image --dest dist
```

The exe is at `dist\StairProgress\StairProgress.exe`.
Keep the entire `StairProgress\` folder together — the exe needs the `app\` and `runtime\` sub-folders beside it.

See `BUILD.md` for full details and rebuild steps after code changes.
