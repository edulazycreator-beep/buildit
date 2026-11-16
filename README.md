# Build It

A native Android app builder that uses Sketchware Pro as a UX reference (no Sketchware code is used) — design screens visually, build logic with blocks — with AI built into every step.

**Status:** project management and the visual designer (adaptive for phones, landscape and tablets) work on top of a versioned, validated project model. Logic blocks, code generation, on-device compile and AI follow. See [ROADMAP.md](ROADMAP.md).

* [ARCHITECTURE.md](ARCHITECTURE.md) — modules, project model, persistence, planned subsystem designs
* [docs/SPEC.md](docs/SPEC.md) — environment, data contracts, risks, testing strategy
* [DEVELOPMENT.md](DEVELOPMENT.md) — build commands, conventions, feature status

```bash
./gradlew :core:model:test :core:storage:test :app:assembleDebug
```
