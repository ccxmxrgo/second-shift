# Phase 1: Skeleton & Feedback Loop - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-04
**Phase:** 1-skeleton-feedback-loop
**Areas discussed:** Deploy loop & versioning

---

## Gray-area selection

Presented four candidate areas; the user chose to discuss one and defer the rest to
research-recommended defaults.

| Area | Description | Discussed |
|------|-------------|-----------|
| Deploy loop & versioning | How the built jar reaches the test instance; wiring; old-jar cleanup; version scheme | ✓ |
| Self-check strictness | Hard-crash always vs. dev-only; sweep vs. list; extend to lang-key check? | Deferred → research default |
| Skeleton scope / token content | What registry entry proves the guardrail; Phase 1/2 line | Deferred → research default |
| runClient parity | Clean room vs. copy owo-lib/accessories/wildcard into run/mods/ | Deferred → research default |

---

## Deploy loop & versioning

### Q1 — How should the built jar get into the test instance's mods/ folder?

| Option | Description | Selected |
|--------|-------------|----------|
| Dedicated Gradle task, run on demand | `deployToTest` (dependsOn build), deletes old jar + copies new; Claude runs it only when an iteration is ready; plain build stays out of the live instance | ✓ |
| Wired into every build | `build` itself copies into the instance; simplest model but every compile-check touches mods/ and can hit a file lock | |
| Manual copy by Claude | No Gradle wiring; shell copy after each build; most steps, easy to forget the delete-old part | |

**User's choice:** Dedicated Gradle task, run on demand
**Notes:** Keeps Claude's routine `./gradlew build` compile-checks invisible to the running game.

### Q2 — What should the jar's version be during development?

| Option | Description | Selected |
|--------|-------------|----------|
| Static 0.1.0 until v1 ships | mod_version stays 0.1.0 through all 10 phases; glob-delete keeps one jar in mods/ | ✓ |
| Bump per phase | 0.1.0 → 0.2.0 → …; filename marks progress; manual bump each phase transition | |
| 0.1.0 + git short hash | Uniquely identifiable builds for regression bisecting; adds build-script complexity | |

**User's choice:** Static 0.1.0 until v1 ships

### Q3a — If deployToTest can't write to the instance, what should it do?

| Option | Description | Selected |
|--------|-------------|----------|
| Fail loudly with a clear message | Task errors ("close Minecraft and re-run" / "path not found"); build/libs jar still present | ✓ |
| Warn and continue | Print warning, exit 0; lower friction but risks loading an old jar | |

**User's choice:** Fail loudly with a clear message

### Q3b — How to handle the old draft's differently-named `second-shift-0.1.0.jar`?

| Option | Description | Selected |
|--------|-------------|----------|
| One-time manual delete now, then rely on glob | Delete once during Phase 1 setup; `secondshift-*.jar` glob handles it after | ✓ |
| deployToTest always sweeps both name patterns | Task deletes `secondshift-*.jar` and `second-shift-*.jar` every run; defensive | |

**User's choice:** One-time manual delete now, then rely on glob

### Q4 — Where should the test-instance path live?

| Option | Description | Selected |
|--------|-------------|----------|
| gradle.properties, committed | `test_instance_mods_dir=` key in committed gradle.properties; documents the setup | ✓ |
| gradle.properties with a gitignored local override | Default/empty committed; real path untracked; cleaner if pushed public (out of scope) | |
| Hardcoded in build.gradle | Path string in the task; fewer moving parts but mixes machine config into the build script | |

**User's choice:** gradle.properties, committed

---

## Claude's Discretion

- Exact `deployToTest` task implementation, group/description, success-path output.
- Register holder class names and the throwaway debug item name.
- `gradle.properties` key name (if a collision with the MDK template appears).
- Mechanism for the one-time dashed-jar delete (throwaway gradle invocation, shell `rm`, or a
  documented one-off inside the first `deployToTest` run).
- All toolchain wiring already fixed by STACK.md.

## Deferred Ideas

- descriptionId / lang-key resolution self-check — belongs where translated content exists (Phase 2+ / Phase 10).
- Per-phase version bumps / git-hash jar names — rejected for v1; revisit if a shelf of old jars is wanted.
- `deployToTest` sweeping the dashed jar pattern — unnecessary after the one-time delete.
- Datagen / `runData` — not wired in Phase 1 (research: hand-write JSON until ~10 registry objects).
- owo-lib / accessories / wildcard in `run/mods/` — only if a future phase needs dev-time parity.
