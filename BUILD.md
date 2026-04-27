# How to Build the Stair Progress .exe

## What was set up (one-time changes)

Two files were changed to make the `.exe` possible:

1. **`src/main/java/com/stairprogress/Launcher.java`** — A thin wrapper class.
   JavaFX apps crash when run from a fat JAR if the main class extends `Application`.
   `Launcher.java` works around this by being a plain Java class that calls into the real app.

2. **`pom.xml`** — Added `maven-shade-plugin`.
   This plugin bundles all dependencies (JavaFX, MySQL driver, etc.) into a single fat JAR
   at `target/stair-progress-app-1.0.0.jar`. Without it, the app would need a separate
   `lib/` folder with all the JARs.

---

## Every time you change code and want a new .exe

### Step 1 — Build the fat JAR
Open a terminal in the project folder and run:
```
mvn clean package
```
This compiles your code and produces `target/stair-progress-app-1.0.0.jar`.

### Step 2 — Build the exe
```
jpackage --input target --main-jar stair-progress-app-1.0.0.jar --name "StairProgress" --app-version 1.0.0 --type app-image --dest dist
```

### Step 3 — Your exe is ready
```
dist\StairProgress\StairProgress.exe
```
Double-click it to run. You can copy the entire `StairProgress` folder anywhere you like
(Desktop, Program Files, etc.). The `.exe` must stay in the same folder as `app\` and `runtime\`.

---

## Notes

- **MySQL must be running** on any machine where you run the exe.
  The database connection is baked into the app.

- **The `runtime\` folder** inside the app is a bundled Java runtime (~100 MB).
  This is why the app is large but works on any Windows PC without installing Java.

- **To fully clean the project** (removes `target\` and all build output):
  ```
  mvn clean
  ```
