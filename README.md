# Sketchware AI

A native Android app builder in the spirit of Sketchware Pro — design screens visually, build logic with blocks — with AI built into every step.

**Status:** Phase 1 (foundation). Project management works end-to-end on top of a versioned, validated project model. Designer, blocks, code generation, AI and builds follow in Phases 3–9. See [ROADMAP.md](ROADMAP.md).

* [ARCHITECTURE.md](ARCHITECTURE.md) — modules, project model, persistence, planned subsystem designs
* [docs/SPEC.md](docs/SPEC.md) — environment, data contracts, risks, testing strategy
* [DEVELOPMENT.md](DEVELOPMENT.md) — build commands, conventions, feature status

```bash
./gradlew :core:model:test :core:storage:test :app:assembleDebug
```
