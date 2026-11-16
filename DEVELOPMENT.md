# Development

## Requirements

* JDK 17+ (21 tested)
* Android SDK with platform 35 and build-tools 35 (set `sdk.dir` in `local.properties` or `ANDROID_HOME`)
* Network access to Google Maven, Maven Central and the Gradle Plugin Portal

## Common commands

```bash
./gradlew :core:model:test :core:storage:test   # core unit tests (pure JVM)
./gradlew :app:testDebugUnitTest                # app unit tests
./gradlew :app:assembleDebug                    # build the debug APK
./gradlew :app:installDebug                     # install on a connected device
./gradlew build                                 # everything incl. lint
```

Offline / sandboxed environments (no Maven access): `tools/run-core-tests.sh [GRADLE_HOME]` compiles and tests the core modules with the Kotlin compiler, kotlinx.serialization and JUnit jars bundled inside a Gradle distribution.

## Repository layout

```
app/                       Android app (Compose, Hilt, Navigation)
  src/main/kotlin/com/sketchwareai/app/
    data/                  repositories (projects, settings), error → message mapping
    di/                    Hilt modules
    ui/                    theme, navigation, dashboard, projects, create, workspace, settings, common
core/model/                project schemas, ids, validation, codec, migrations, templates (JVM)
core/storage/              file-based ProjectStore with history, recovery, import/export (JVM)
docs/SPEC.md               environment, data contracts, risks, testing strategy
tools/run-core-tests.sh    offline core test runner
ARCHITECTURE.md  ROADMAP.md  DEVELOPMENT.md
```

## Conventions

* Anything that does not need Android goes in a `:core:*` JVM module and gets JVM tests.
* The model is immutable; persistence goes only through `ProjectStore` (never write `project.json` directly).
* Changing a schema class ⇒ bump `CURRENT_SCHEMA_VERSION`, add a `Migration`, add a sample project of the old version to tests.
* User-facing errors go through `Throwable.toUserMessage()`; never show paths or stack traces.
* Destructive actions (delete, restore, recover) always require a confirmation dialog.
* No secrets in code, project files, logs or exports.

## Feature status

### Completed
* Project model v1 with stable ids for screens, components, events, blocks, resources, source files
* Field + project validation (package/SDK/version rules, unique ids and view ids, container rules, safe paths)
* Deterministic JSON codec with forward-version protection and migration pipeline
* Templates: Empty Activity, Hello World, Text and Button
* Project store: create, load, save with optimistic locking, delete, duplicate (incl. files), recent ordering, version history (bounded snapshots) with restore, corruption detection and recovery, hardened `.swai` import/export
* App: Dashboard, Project Manager (search, sort, rename, duplicate, export, import, delete, recover), Create Project (live validation, auto package from app name), Workspace shell (overview with view tree, project settings editor, history restore), Settings (theme, dynamic color, package prefix)

### Not yet implemented (by design — later phases)
Visual Designer, Logic editor (Blockly), Components/Resources editors, code generation, Code editor, AI Assistant / Builder / Visual Generator / Debugger, Build Manager, undo/redo for edits.

### Known gaps in Phase 1
* `:app` is compiled and tested in GitHub Actions (`.github/workflows/android.yml`); the debug APK is a downloadable workflow artifact.
* No Compose UI tests yet.
* Launcher icon is a placeholder vector.
