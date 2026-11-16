# Sketchware AI — Architecture

_Last updated: Phase 1 (foundation)._

## 1. Principles

1. **One model, many views.** `ProjectSchema` (in `:core:model`) is the single source of truth. The Visual Designer, the Logic (Blockly) editor, the code generators and the AI all read and write the same immutable model. Nothing else is authoritative — the Blockly workspace and generated Java/XML are *projections* of it.
2. **Pure core, thin Android shell.** Everything that does not need Android (model, validation, persistence format, code generation, block registry, AI response validation) is plain Kotlin/JVM. It is unit-testable anywhere and is reused unchanged by the remote build worker.
3. **Deterministic outputs.** Same model → byte-identical JSON and generated sources. This is what makes diffs, AI change previews, caching and regression tests reliable.
4. **Every change is reviewable and reversible.** Saves are revisioned and snapshotted; AI changes arrive as *proposals* that are validated, previewed and applied as one undoable transaction.
5. **Untrusted by default.** AI output, imported archives and WebView messages are all treated as untrusted input and validated before they touch the model.

## 2. Module graph

```
:app  (Android, Compose, Hilt)                         ← Phase 1
  ├── :core:storage  (JVM) file-based ProjectStore     ← Phase 1
  │     └── :core:model (JVM) schemas, ids, validation, codec, migrations   ← Phase 1
  │
  ├── :core:codegen   (JVM) XML / Java / Kotlin / Gradle / Manifest generators   ← Phase 3/5
  ├── :core:blocks    (JVM) block registry: definitions, typing rules, validation ← Phase 4
  ├── :feature:designer   Compose drag-and-drop designer                         ← Phase 3
  ├── :feature:logic      WebView + bundled Blockly + restricted JS bridge        ← Phase 4
  ├── :feature:code       WebView + bundled CodeMirror 6                          ← Phase 5
  ├── :core:ai        provider abstraction, prompts, response schemas            ← Phase 6
  └── :core:build     BuildService interface + remote client                     ← Phase 9

build-worker/ (separate deployable, JVM)  uses :core:model + :core:codegen        ← Phase 9
```

Dependency rule: `:core:*` modules never depend on Android or on `:feature:*`. Feature modules depend on core modules, never on each other.

## 3. Project model (`:core:model`)

| Schema | Purpose | Key invariants |
|---|---|---|
| `ProjectSchema` | Root: config, screens, resources, source files, revision, schemaVersion | ids globally unique; `mainScreenId` exists |
| `ProjectConfig` | name, package, version, min/target SDK, language | package ≥ 2 valid segments, no Java keywords; `21 ≤ minSdk ≤ targetSdk ≤ 36` |
| `ScreenSchema` | One Activity + one layout; root view, events, variables | class name valid; layout name valid; root is a container |
| `ComponentSchema` | A view: type, `viewId`, properties map, children | `viewId` unique per screen; only containers have children; scroll views ≤ 1 child |
| `EventSchema` | Event handler (`onClick` of `button1`), body = blocks | target component exists |
| `BlockSchema` | Block instance mirroring Blockly JSON (`fields`, `inputs`, `next`) | ids unique; types validated against the block registry (Phase 4) |
| `ResourceSchema` | strings, colors, dimens, drawables, fonts, raw | name valid; value XOR path; paths safe |
| `CodeFileSchema` | User-owned source file under `src/` | path safe and unique; **never written by generators** |

* **Stable IDs**: `<prefix>_<suffix>` (`prj_`, `scr_`, `cmp_`, `evt_`, `blk_`, `res_`, `src_`). Never change on rename. Validated by regex, which also makes them safe as file names.
* **Codec**: hand-written JSON codec over `kotlinx.serialization.json` trees. Sorted map keys → deterministic output. Rejects documents from newer schema versions instead of silently dropping data.
* **Migrations**: operate on the raw JSON tree (`Migration.fromVersion → +1`), applied in sequence before decoding, so old shapes never need Kotlin classes.
* **Validation**: `Rules` (field-level, shared with UI forms) and `ProjectValidator` (whole-project). A project with `ERROR` issues cannot be saved via `ProjectFactory` or built.

## 4. Persistence (`:core:storage`)

`FileProjectStore` — one directory per project under the app's private `filesDir/projects`:

```
<projectId>/project.json          current model (atomic replace: tmp → fsync → rename)
<projectId>/state.json            last-opened time (does not bump revision)
<projectId>/history/r<rev>-<ts>.json   snapshot taken before every save (bounded, default 30)
<projectId>/src/… res/…           user files referenced by the model
```

* **Optimistic locking**: `save()` requires `project.revision` to equal the stored revision, then writes `revision + 1`. Prevents two editors (e.g. designer and AI) from silently overwriting each other.
* **Version history / recovery**: snapshots are the history; `restoreSnapshot` snapshots the current state first (restores are undoable). A corrupt `project.json` surfaces as `StoreException.Corrupt`; `recover()` sets the damaged file aside and restores the newest readable snapshot.
* **Delete** renames the directory first, so a crash mid-delete never leaves a half-deleted project in the list.
* **Export/Import**: `.swai` = zip with `sketchware-ai-export.json` manifest, `project.json` and user files (no history). Import is hardened: path normalization and canonical-path check (zip-slip), entry count / per-entry / total size limits, duplicate-entry rejection, format-version check, full model validation, and always a fresh project id. Extraction happens in a temp dir that is removed on any failure.

Room is deliberately *not* the source of truth for projects: a project must be a portable folder for export, backup and the build worker. Room enters in later phases for indexes and caches (build history, AI conversation history, usage stats).

## 5. App layer (`:app`)

* **DI**: Hilt. `AppModule` provides `IdGenerator`, `ProjectFactory`, `ProjectStore`, `@IoDispatcher`.
* **Data**: `ProjectRepository` moves all disk I/O to `Dispatchers.IO`, exposes `StateFlow<List<ProjectSummary>?>` refreshed after every mutation, and bridges SAF `Uri`s to the store's streams. `SettingsRepository` uses Preferences DataStore.
* **UI**: Compose + Material 3, unidirectional data flow (ViewModel `StateFlow` state + `Channel` one-shot events). Type-safe Navigation routes (`@Serializable`).
* **Screens (Phase 1)**: Dashboard (recent projects, quick actions), Project Manager (search, sort, all actions), Create Project (templates, live validation, Sketchware-style auto package), Workspace shell (Overview with view tree, project Settings, History with restore), App Settings (theme, dynamic color, package prefix).

## 6. Planned subsystem designs (to be refined in their phases)

### 6.1 Edits, undo and AI changes (Phase 2)
All modifications become `ProjectEdit` commands (`AddComponent`, `SetProperty`, `ReplaceEventBody`, …) applied by a pure reducer. An `EditTransaction` groups edits; the editor keeps an undo/redo stack of transactions. AI Builder output is *exactly* an `EditTransaction` proposal → validated → diff preview → user confirms → applied → one undo step reverts it (acceptance criterion 8).

### 6.2 Blockly integration (Phase 4)
* Blockly and all JS/CSS are **bundled in assets** (no CDN) and served via `WebViewAssetLoader` on `https://appassets.androidplatform.net`.
* Bridge = `WebViewCompat.addWebMessageListener` restricted to that origin; message envelope `{type, version, payload}` validated on the Kotlin side. No `addJavascriptInterface`, no file access, no arbitrary navigation.
* Block definitions live once in `:core:blocks` (JVM) and are exported as JSON into the WebView, so the editor, the validator, the code generator and the AI share the same registry.
* **Kotlin is the authoritative code generator** (testable on the JVM and in the build worker); Blockly is the editor only. The model is authoritative: workspace changes are converted to `ProjectEdit`s; model changes (e.g. AI) re-render the workspace.

### 6.3 Code generation (Phases 3 & 5)
Generators are pure functions `ProjectSchema → Map<path, String>` with a `GENERATOR_VERSION`. Generated files carry a header with generator version and content hash. User code lives in `CodeFileSchema` files and custom-code blocks; if a generated file's on-disk hash differs from its header (hand-edited), regeneration stops and asks — never silently overwrites.

### 6.4 AI (Phases 6–8)
`AiProvider` interface (`stream(request): Flow<AiEvent>`) with Anthropic, OpenAI, Gemini and OpenAI-compatible implementations over OkHttp. Keys are encrypted with an Android Keystore-backed key, stored outside project files and excluded from backups, logs and generated APKs. Builder/Visual Generator responses are constrained to JSON schemas (tool use / structured output), parsed into `EditTransaction`s and rejected or repaired if `ProjectValidator` / the block registry report errors. AI never executes code.

### 6.5 Build (Phase 9)
`BuildService` interface: `submit(project, variant): BuildJob`, `events(job): Flow<BuildEvent>`, `cancel(job)`, `artifact(job)`. Phase 9 implements `RemoteBuildService` (upload generated sources → isolated, network-restricted, time- and memory-limited worker → logs streamed → artifact + SHA-256 returned and verified). An on-device implementation can be added later behind the same interface.
