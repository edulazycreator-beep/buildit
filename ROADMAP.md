# Roadmap

Status legend: ✅ done & verified · 🟡 done, verification pending · ⬜ not started

| Phase | Scope | Status |
|---|---|---|
| 0 | Environment inspection, architecture, spec, data contracts, risks, testing strategy | ✅ |
| 1 | Android foundation, module structure, navigation, dashboard, project manager | 🟡 builds in CI (tests green, debug APK produced); manual device pass pending |
| 2 | Project model expansion (edit commands, undo/redo, variables, resources), sample projects, file management | ⬜ (schema v1, codec, migrations and storage already landed in Phase 1) |
| 3 | Visual Designer, component hierarchy, properties inspector, XML generator | ⬜ |
| 4 | Blockly integration, block registry, workspace ↔ model sync | ⬜ |
| 5 | Java (then Kotlin) generation, Gradle & Manifest generation, CodeMirror editor | ⬜ |
| 6 | AI provider layer (Anthropic, OpenAI, Gemini, compatible), secure keys, AI Assistant | ⬜ |
| 7 | AI Builder producing real, validated, undoable block edits | ⬜ |
| 8 | AI Visual Generator, AI Debugger (log analysis → fix proposal → rebuild → verify) | ⬜ |
| 9 | Remote build service (APK / AAB, signing, logs, cancellation, history) | ⬜ |
| 10 | Testing, performance, security audit, release | ⬜ |

## Phase 1 exit checklist

- [x] Gradle multi-module setup with version catalog and wrapper
- [x] `:core:model` — schemas, stable ids, validation, deterministic codec, migration framework, templates
- [x] `:core:storage` — atomic saves, optimistic locking, history, recovery, duplicate, safe import/export
- [x] Core tests green (32)
- [x] `:app` — Hilt, Compose M3 theme (light/dark/dynamic), type-safe navigation
- [x] Dashboard, Project Manager, Create Project, Workspace shell (overview/settings/history), Settings
- [x] CI (GitHub Actions): core + app unit tests and `assembleDebug` pass
- [ ] Manual device pass: create → open → edit settings → restore history → rename → duplicate → export → import → delete
- [ ] Compose UI test for the create/open flow

## Phase 2 plan (next)

1. `ProjectEdit` sealed commands + pure reducer + `EditTransaction`; undo/redo stack.
2. Expand `ComponentProperty` into a typed property catalogue per `ComponentType` (shared with the designer and XML generator).
3. Screen management: add/rename/delete screens with reference checks.
4. Resource manager: import images/fonts into the project folder with type/size validation.
5. Custom source files: create/edit/delete under `src/`, ownership rules.
6. Sample projects (`samples/`) + golden tests for decode/validate.
7. Room database for indexes that are not part of the portable project (build history, AI conversations) — schema only, populated in later phases.
