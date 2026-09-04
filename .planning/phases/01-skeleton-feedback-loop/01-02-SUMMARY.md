---
phase: 01-skeleton-feedback-loop
plan: 02
subsystem: infra
tags: [gradle, deploy, curseforge, neoforge, configuration-cache]

requires:
  - phase: 01-skeleton-feedback-loop
    provides: "build.gradle / gradle.properties and the secondshift-0.1.0.jar build (plan 01-01)"
provides:
  - "./gradlew deployToTest — on-demand deploy of the built jar into the CurseForge test instance mods/ (glob-replace, fail-loud, config-cache-safe)"
  - "committed test_instance_mods_dir key in gradle.properties (D-06)"
  - "run/mods/ dev-parity mods directory (D-12: confirmed FML-scanned)"
  - "the build <-> in-game testing seam for every subsequent phase"
affects: [02-economy-items, 03-menu-screen-harness, every phase that ships a jar for in-game testing]

tech-stack:
  added: []
  patterns:
    - "deployToTest: dependsOn build, absent from the build graph, nothing dependsOn it — Claude's routine ./gradlew build never touches the live instance"
    - "Config-cache-safe custom task: capture path provider / built-jar provider / mod_id at configuration time; doLast uses java.nio.file.Files + plain File, never project"
    - "A failed deploy throws GradleException with an actionable message and never moves/deletes build/libs/<jar> — no silent stale-jar test"

key-files:
  created:
    - .planning/phases/01-skeleton-feedback-loop/01-USER-SETUP.md
  modified:
    - build.gradle
    - gradle.properties
    - .gitignore
    - run/mods/.gitkeep

key-decisions:
  - "deployToTest implemented as tasks.register('deployToTest') { doLast { … } } with configuration-time-captured providers (not a Copy task) — simplest config-cache-safe shape"
  - "glob-delete filter is the regex /\\Qsecondshift\\E-.*\\.jar/ on the destination dir only — matches secondshift-*.jar, never bare *.jar, never the dashed second-shift-*.jar (D-05), never recursive"
  - ".gitignore run/ -> run/* so run/mods/.gitkeep can be re-included (git cannot re-include a path under a fully-excluded parent)"
  - "D-12 resolved YES: FML scans run/mods/ (FMLPaths MODSDIR); later phases may drop dev-parity mod jars there"

patterns-established:
  - "Every future phase deploys for in-game testing with ./gradlew deployToTest; plain build stays invisible to the instance"

requirements-completed: [BUILD-04, BUILD-05]

duration: ~8 min
completed: 2026-09-04
---

# Phase 1 Plan 02: On-Demand deployToTest Loop Summary

**A config-cache-safe `./gradlew deployToTest` Gradle task that glob-replaces `secondshift-*.jar` in the CurseForge "test" instance `mods/` folder on demand, fails loudly on a missing path or file lock, and never disturbs `build/libs/` — plus the committed `test_instance_mods_dir` key and a confirmed dev-parity `run/mods/` directory.**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-09-04T03:24Z
- **Completed:** 2026-09-04T03:32Z
- **Tasks:** 2
- **Files modified:** 4 (+ 1 created)

## Accomplishments

- `deployToTest` task (D-01..D-04): `dependsOn build`, **not** in the `build`/`assemble`/`check` graph, nothing `dependsOn` it. `./gradlew build --dry-run` does not list `:deployToTest`.
- Config-cache-safe: destination path (`providers.gradleProperty('test_instance_mods_dir')`), built jar (`tasks.named('jar', Jar).flatMap { it.archiveFile }`), and `mod_id` are captured at configuration time; the `doLast` action uses `java.nio.file.Files.copy` + plain `File` and never touches `project`. Verified: second run "Reusing configuration cache / entry reused".
- Fail-loud behaviours: missing/unset path → `GradleException("deployToTest: test instance mods dir not found: … Set test_instance_mods_dir in gradle.properties or create the folder, then re-run.")` (non-zero exit, `build/libs/secondshift-0.1.0.jar` untouched); `IOException` during delete/copy → `GradleException("deployToTest: could not write to <destDir> - close Minecraft (it locks mods/*.jar) and re-run. build/libs/secondshift-0.1.0.jar is unchanged.")`.
- Glob-delete matrix: deploy into an empty dir → exactly one `secondshift-0.1.0.jar`; with `secondshift-0.0.9.jar` + `second-shift-0.1.0.jar` present → `0.0.9` deleted, dashed jar **kept**, fresh jar copied.
- `test_instance_mods_dir=C:/Users/user/curseforge/minecraft/Instances/test/mods` committed in `gradle.properties` (D-06).
- **D-05:** the legacy dashed `second-shift-0.1.0.jar` was already gone from the instance `mods/` — no-op. `deployToTest` does not sweep the dashed pattern.
- **BUILD-04:** real `./gradlew deployToTest` placed `secondshift-0.1.0.jar` (md5 `f969d8c9…`, byte-identical to `build/libs/`) into the instance `mods/` alongside `owo-lib` / `accessories` / `wildcard`; exactly one `secondshift-*.jar`, no dashed jar.

## D-12 — does FML scan `run/mods/`? **YES.**

- `FMLPaths.MODSDIR` resolves to `C:\Users\user\Documents\PROJECTS\necromancy-mod\run\mods` for every dev run task.
- `net.neoforged.fml.loading.moddiscovery.locators.ModsFolderLocator` is registered (priority 0) and scans `MODSDIR`.
- **Definitive test:** dropped `wildcard-0.32.1.jar` into `run/mods/` and launched `runClient` — FML logged `Considering mod file candidate …\run\mods\wildcard-0.32.1.jar` / `Found valid mod file wildcard-0.32.1.jar`, listed **Wildcard 0.32.1 (wildcard)** in the Mod List, and then correctly FATAL'd on its missing `accessories` dependency. The jar was removed afterward; `run/mods/` holds only `.gitkeep`.
- **Implication for later phases:** dev-parity mods (owo-lib / accessories / wildcard) can be dropped into `run/mods/` if a phase ever needs `runClient` to mirror the instance — but the full dependency set must be copied together. Nothing in v1 currently needs this; `runClient` stays a clean room (D-11).

## Task Commits

1. **Task 1: deployToTest task + test_instance_mods_dir** — `5020232` (feat)
2. **Task 2: legacy cleanup + run/mods + integration deploy** — `092d7c7` (chore)

## Decisions Made

- `deployToTest` as `tasks.register { doLast { … } }` with captured providers, not a `Copy` task (simplest config-cache-safe form; the plan left the style to Claude's discretion).
- Delete filter is `name ==~ /\Qsecondshift\E-.*\.jar/` on the destination directory only.
- `.gitignore`: changed `run/` → `run/*` and added `!run/mods/` + `run/mods/*` + `!run/mods/.gitkeep`, because git cannot re-include a path whose parent directory is fully excluded. `run/logs/`, `run/mods/*.jar`, etc. stay ignored.

## Deviations from Plan

None — plan executed as written. (The `deployToTest` implementation style, task group/description, and the success-message print were explicitly left to Claude's discretion per 01-CONTEXT.md "Claude's Discretion"; choices are recorded above.)

## Issues Encountered

- Initial `git add run/mods/.gitkeep` was blocked by the blanket `run/` ignore rule — resolved by the `run/*` + re-include pattern above (git's "cannot re-include under an excluded parent" rule).
- `-Ptest_instance_mods_dir="/no/such/dir"` gets MSYS path-mangled to `C:\Program Files\Git\no\such\dir` in the fail-path test — irrelevant, the directory still does not exist and the actionable "not found" error fires correctly.

## User Setup Required

**External service configured.** See [01-USER-SETUP.md](./01-USER-SETUP.md):
- The CurseForge `test` instance exists (`neoforge-21.1.248` / MC `1.21.1`) and must be **closed** during `deployToTest`.
- No env vars, no account creation.
- Two in-game checks are deferred to end-of-phase UAT (`human_verify_mode: end-of-phase`): (1) the instance launches to the menu with Second Shift listed alongside owo-lib/accessories/wildcard, no crash; (2) the D-04 file-lock branch fails with the "close Minecraft" message.

## Next Phase Readiness

- Phase 1 code work is complete: `secondshift-0.1.0.jar` builds, launches clean on both sides, self-aborts on an unbound register, and deploys into the test instance on demand.
- Ready for phase verification. The two end-of-phase human checks above are the only outstanding items.
- Phase 2 (`02-economy-items`) can start from here: it removes the D-07 `debug_marker`, adds real `ModItems` / `ModBlocks` / `ModBlockEntities`, and must add each new register class to `ModRegistrySelfCheck`'s `Stream.of(...)` list (D-10) and the `SecondShift` constructor.

---
*Phase: 01-skeleton-feedback-loop*
*Completed: 2026-09-04*

## Self-Check: PASSED

`deployToTest` present in `build.gradle` and absent from `build --dry-run`; `test_instance_mods_dir` in `gradle.properties`; `run/mods/.gitkeep` tracked; both task commits (`5020232`, `092d7c7`) in git; the instance `mods/` holds exactly one `secondshift-0.1.0.jar` (md5-identical to the build) and no dashed jar.
