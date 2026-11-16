# Build It — Technical Specification

Scope: the pre-implementation deliverables required by the master prompt (§21): environment inspection, data contracts, engineering risks and testing strategy. Architecture is in [`ARCHITECTURE.md`](../ARCHITECTURE.md); phases are in [`ROADMAP.md`](../ROADMAP.md).

## 1. Environment inspection (2026-11-16)

| Item | Found | Consequence |
|---|---|---|
| JDK | OpenJDK 21 | OK (project targets JVM 17 bytecode) |
| Gradle | 8.14.3 at `/opt/gradle` (wrapper pinned to 8.14.3) | OK |
| Android SDK | **not installed**; `dl.google.com` blocked | `:app` cannot be compiled in this sandbox |
| Maven Central / Google Maven / Gradle Plugin Portal | **blocked by network policy** | Gradle cannot resolve AGP, Compose, Hilt |
| Kotlin compiler, kotlinx.serialization-json, JUnit 4 | available as jars inside the Gradle distribution | Core modules **are** compiled and tested here via `tools/run-core-tests.sh` |

**Verification status therefore differs by module**: `:core:model` and `:core:storage` are compiled and their 32 tests pass in this environment. `:app` is written against pinned, mutually compatible versions (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Hilt 2.52, Navigation 2.8.5) but has **not been compiled yet** — the first `./gradlew build` on a machine with the Android SDK is the Phase 1 exit check.

## 2. Product targets

* App: `minSdk 26`, `targetSdk 35`, Kotlin, Compose, Material 3, light/dark.
* Generated user apps: `minSdk 21…36`, Java first (Kotlin generation later in Phase 5), AndroidX.
* Devices: phones and tablets; all core flows usable one-handed at 360 dp width.

## 3. Data contracts

### 3.1 Project document (`project.json`, schema version 1)

```jsonc
{
  "schemaVersion": 1,                  // bump + Migration on any shape change
  "id": "prj_xxxxxxxxxxxxxxxx",
  "revision": 3,                       // store-managed, optimistic locking
  "createdAt": 1731715200000, "updatedAt": 1731715300000,   // epoch ms
  "config": {
    "appName": "My App", "packageName": "com.my.myapp",
    "versionCode": 1, "versionName": "1.0",
    "minSdk": 24, "targetSdk": 35,
    "mainScreenId": "scr_…", "language": "JAVA"           // JAVA | KOTLIN
  },
  "screens": [{
    "id": "scr_…", "name": "MainActivity", "layoutName": "main",
    "root": {
      "id": "cmp_…", "type": "LINEAR_LAYOUT", "viewId": "linear1",
      "properties": { "layout_height": "match_parent", "layout_width": "match_parent", "orientation": "vertical" },
      "children": [ /* ComponentSchema */ ]
    },
    "events": [{
      "id": "evt_…", "type": "onClick", "target": "cmp_…",   // target null for screen events
      "body": [{
        "id": "blk_…", "type": "component_set_text",
        "fields": { "COMPONENT": "textview1" },
        "inputs": { "VALUE": { /* BlockSchema */ } },
        "next": null
      }]
    }],
    "variables": [{ "name": "count", "type": "INTEGER", "initialValue": "0" }]
  }],
  "resources":   [{ "id": "res_…", "type": "COLOR", "name": "primary", "value": "#FF1A5CC4" }],
  "sourceFiles": [{ "id": "src_…", "path": "src/java/com/my/myapp/Util.java", "language": "JAVA" }]
}
```

Rules: object keys inside `properties`, `fields`, `inputs` are emitted sorted; unknown enum values and missing required fields are format errors; a document with `schemaVersion` greater than the app's is refused.

### 3.2 Export archive (`.buildit`, format 1)

Zip containing `buildit-export.json` (`{"format":1,"schemaVersion":N,"exportedAt":ms}`), `project.json`, and user files (`src/**`, `res/**`, …). Excludes `history/` and `state.json`. Limits on import: 5 000 entries, 64 MiB per entry, 256 MiB total; any absolute, `..`, backslash, drive-letter or duplicate path rejects the whole archive.

### 3.3 Contracts defined in later phases (shape fixed now, details in-phase)

| Contract | Phase | Shape |
|---|---|---|
| `ProjectEdit` / `EditTransaction` | 2 | sealed command types + reducer; transaction = ordered edits + description + origin (`USER`/`AI`) |
| Block definition | 4 | `{type, category, fields[], inputs[{name, kind: VALUE\|STATEMENT, check}], output, previousStatement, nextStatement, generator}` |
| WebView message | 4 | `{type, version, requestId?, payload}`; types: `workspaceChanged`, `selectBlock`, `loadWorkspace`, `highlightBlock`, `error` |
| AI request/response | 6 | `AiRequest{provider, model, messages, context: ContextScope, tools?}` → `Flow<AiEvent{Delta \| ToolCall \| Usage \| Done \| Error}>` |
| AI Builder proposal | 7 | JSON schema → `EditTransaction`; must pass `ProjectValidator` + block registry before preview |
| Build job | 9 | `BuildRequest{sourcesZipSha256, variant: DEBUG_APK\|RELEASE_APK\|AAB, signing?}` → events `{Queued, Started, Log(line), Finished(artifactUrl, sha256) \| Failed(log) \| Cancelled}` |

## 4. Engineering risks

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| 1 | **Naming/trademark**: the product was renamed from "Sketchware AI" to **Build It** (package `com.buildit`). Sketchware is used only as a UX reference; no Sketchware code is used. | — | Resolved. |
| 2 | On-device APK compilation is hard (aapt2, d8, signing on ARM, storage). | No offline builds. | Remote worker first (as specified); `BuildService` interface keeps on-device builds pluggable. |
| 3 | Remote builds execute user-supplied code (Gradle, Java). | Worker compromise, abuse, cost. | Ephemeral containers, no network except a pinned Maven mirror, CPU/memory/time limits, no secrets in the worker, per-user quotas, artifact hashing. |
| 4 | Uploading source to a server. | Privacy expectations. | Explicit consent at first build, TLS, delete sources after build, documented retention. |
| 5 | AI produces invalid or unsafe changes. | Broken projects, user distrust. | Structured output only; schema + validator + block-registry checks; repair loop with the error list; preview + confirm; single-step undo. |
| 6 | Blockly in WebView on low-end phones. | Jank, memory. | Bundle only needed Blockly modules, lazy-load per screen, keep workspaces per event small, measure on a low-end reference device. |
| 7 | Model ↔ Blockly ↔ generated code drifting apart. | Data loss. | Model is the only source of truth; Blockly and code are projections; round-trip tests (model → workspace JSON → model). |
| 8 | Overwriting user code during regeneration. | Data loss. | Generated/user file separation, content-hash headers, refuse-and-ask on mismatch. |
| 9 | API key leakage. | Financial loss for users. | Keystore-backed encryption, never in project files/logs/exports/APKs, redaction in error reports, backup exclusion. |
| 10 | Play policy on installing APKs (`REQUEST_INSTALL_PACKAGES`) and executing downloaded code. | Distribution limits. | Install via the system package installer with a declared, justified use; review policy before Phase 9 release. |
| 11 | Schema evolution across 10 phases. | Old projects unreadable. | Versioned schema + JSON-tree migrations + golden sample projects per version in tests. |
| 12 | Scope size. | Endless half-finished features. | Strict phase exits (tests green, features verified, docs updated) before starting the next phase. |

## 5. Testing strategy

| Layer | Tooling | Runs where | Covers |
|---|---|---|---|
| Core unit tests | JUnit 4 on JVM | `./gradlew :core:model:test :core:storage:test`, or `tools/run-core-tests.sh` without network | schema rules, validation, codec round-trip & determinism, migrations, store CRUD, conflicts, history, recovery, import/export security |
| App unit tests | JUnit 4 + coroutines-test | `./gradlew :app:testDebugUnitTest` | ViewModel/form logic (e.g. `ConfigDraft`) |
| Golden tests (Phase 3+) | JUnit + checked-in expected files | JVM | XML / Java / Gradle / Manifest generators byte-for-byte |
| Sample projects (Phase 2+) | `samples/*.buildit` | JVM + worker | regression: decode, migrate, validate, generate, (Phase 9) build |
| Compose UI tests | `ui-test-junit4` | emulator / device | create → open → rename → export → import flows |
| WebView bridge tests (Phase 4) | instrumented | device | message validation, workspace round-trip |
| AI tests (Phase 6+) | recorded provider responses (no live calls in CI) | JVM | parsing, schema validation, repair loop, proposal → transaction |
| Build tests (Phase 9) | worker integration | CI with SDK | generated sample projects compile; APK signature and SHA-256 verified |

Phase exit rule: every new subsystem lands with tests; a phase is complete only when all tests pass on a machine with the Android SDK and the phase's features are exercised manually on a device.
