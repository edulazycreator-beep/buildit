# Roadmap

Status legend: ✅ done & verified · 🟡 done, verification pending · ⬜ not started

| Phase | Scope | Status |
|---|---|---|
| 0 | Environment inspection, architecture, spec, data contracts, risks, testing strategy | ✅ |
| 1 | Android foundation, module structure, navigation, dashboard, project manager | 🟡 builds in CI (tests green, debug APK produced); manual device pass pending |
| 2 | Project model expansion (edit commands, undo/redo, variables, resources), sample projects, file management | 🟡 edit commands, undo/redo, screens, snapshot throttling done; resources, variables, sample projects pending |
| 3 | Visual Designer, component hierarchy, properties inspector, XML generator | 🟡 built and green in CI; manual device pass pending |
| 4 | Blockly integration, block registry, workspace ↔ model sync | ⬜ |
| 5 | Java (then Kotlin) generation, Gradle & Manifest generation, CodeMirror editor | ⬜ |
| 6 | AI provider layer (Anthropic, OpenAI, Gemini, compatible), secure keys, AI Assistant | ⬜ |
| 7 | AI Builder producing real, validated, undoable block edits | ⬜ |
| 8 | AI Visual Generator, AI Debugger (log analysis → fix proposal → rebuild → verify) | ⬜ |
| 9 | **On-device** build (user choice, Sketchware Pro style): aapt2 + ECJ/Javac + D8 + apksigner bundled; APK signing, logs, cancellation, history | ⬜ |
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

## Phase 3 delivered (designer)

- Real-View device preview (generated app and preview use the same widgets)
- Palette of 24 widgets/layouts; tap to add, hold-and-drag to place; drag existing views to move
- Selection, select-parent, move up/down, duplicate, delete, rename view id
- Property inspector driven by `PropertyCatalog` (per type and per parent layout), validated editors (size, dp, sp, color picker, choices, gravity flags, text, numbers)
- Hierarchy view, generated-XML view with copy, screen add/rename/delete/set-main
- Undo/redo for every change, autosave with throttled history snapshots

## Next

1. Phase 4 — Logic editor (Blockly, events per component) and Phase 5 — Java/Manifest/Gradle generation
2. Phase 9 (moved earlier at user request after 4–5) — on-device compile to APK
3. Resource manager (images, colors, strings) so ImageView `src` can pick real drawables

## Phase 2 plan (original)

1. `ProjectEdit` sealed commands + pure reducer + `EditTransaction`; undo/redo stack.
2. Expand `ComponentProperty` into a typed property catalogue per `ComponentType` (shared with the designer and XML generator).
3. Screen management: add/rename/delete screens with reference checks.
4. Resource manager: import images/fonts into the project folder with type/size validation.
5. Custom source files: create/edit/delete under `src/`, ownership rules.
6. Sample projects (`samples/`) + golden tests for decode/validate.
7. Room database for indexes that are not part of the portable project (build history, AI conversations) — schema only, populated in later phases.
