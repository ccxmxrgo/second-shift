---
phase: 01-skeleton-feedback-loop
plan: 01
subsystem: infra
tags: [neoforge, moddevgradle, gradle, deferredregister, fml, java21]

requires: []
provides:
  - Pinned MDG 2.0.146 / NeoForge 21.1.248 / Gradle 9.2.1 project that builds secondshift-0.1.0.jar
  - "@Mod(\"secondshift\") entrypoint with the one-visible-block DeferredRegister wiring pattern"
  - registry/ModItems DeferredRegister.Items (throwaway debug_marker — D-07, removed in Phase 2)
  - FMLLoadCompleteEvent unbound-registry self-check (hard-aborts runClient + runServer with a named list)
  - client/ package + Dist.CLIENT isolation proof
  - scripts/run-until.sh launch harness (auto-discovers run/logs, taskkill kill sequence)
  - pack.mcmeta pack_format 48 + singular 1.21 datapack folders (POL-09 convention)
affects: [02-economy-items, 03-menu-screen-harness, 04-employee-attachment, deployToTest]

tech-stack:
  added:
    - net.neoforged.moddev 2.0.146 (Gradle plugin)
    - NeoForge 21.1.248 / Minecraft 1.21.1 / Temurin JDK 21
    - Gradle 9.2.1 wrapper (distributionSha256Sum pinned)
    - Parchment 1.21.1 / 2024.11.17
    - org.gradle.toolchains.foojay-resolver-convention 1.0.0
  patterns:
    - "Every DeferredRegister created as a static field in a flat registry/Mod* class and attached to the mod bus in the SecondShift constructor, in one visible block"
    - "Hand-maintained Stream.of(...) register list feeding the FMLLoadCompleteEvent self-check (D-10) — each new Mod* register class must be added there"
    - "Client-only code under com.cxmxrgo.secondshift.client, isolated with @EventBusSubscriber(value = Dist.CLIENT), never imported from common"
    - "Game launches driven through scripts/run-until.sh (marker-or-timeout, then taskkill //F //T the JVM tree)"

key-files:
  created:
    - build.gradle
    - settings.gradle
    - gradle.properties
    - gradle/wrapper/gradle-wrapper.properties
    - .gitattributes
    - src/main/templates/META-INF/neoforge.mods.toml
    - src/main/java/com/cxmxrgo/secondshift/SecondShift.java
    - src/main/java/com/cxmxrgo/secondshift/registry/ModItems.java
    - src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java
    - src/main/java/com/cxmxrgo/secondshift/client/ClientModBusEvents.java
    - src/main/resources/pack.mcmeta
    - src/main/resources/assets/secondshift/lang/en_us.json
    - scripts/run-until.sh
  modified: []

key-decisions:
  - "Self-check throws directly from the FMLLoadCompleteEvent handler, NOT inside event.enqueueWork() — enqueueWork swallows the exception into a soft \"broken mod\" state; a direct throw is a fatal ModLoadingException (the hard abort D-08 requires)"
  - "runData is NOT a self-check surface on this toolchain — datagen exits after GatherDataEvent without firing FMLCommonSetupEvent / FMLLoadCompleteEvent. The abort test is proven on runClient + runServer instead"
  - "Added .gitattributes (gradlew / *.sh forced eol=lf) so the wrapper script survives a Windows checkout"
  - "Kept bus = EventBusSubscriber.Bus.MOD despite the deprecation-for-removal warning in 21.1.248 — documentation parity per PITFALLS §2; FML ignores the attribute and the project is version-pinned for all of v1"

patterns-established:
  - "registry/Mod* holder classes are pure DeferredRegister data, class-loaded from the @Mod constructor"
  - "One ./gradlew runServer per phase as the dedicated-server / client-class-leak gate"
  - "Grep run/logs/debug.log for `Subscribing @EventBusSubscriber class L...; to the (mod|game) event bus` each launch"

requirements-completed: [BUILD-01, BUILD-02, BUILD-03, POL-09]

duration: ~55 min (includes a usage-limit pause)
completed: 2026-09-04
---

# Phase 1 Plan 01: Pinned NeoForge 1.21.1 Skeleton + Unbound-Registry Self-Check Summary

**A pinned MDG 2.0.146 / NeoForge 21.1.248 / Gradle 9.2.1 project that builds `secondshift-0.1.0.jar`, launches clean on both sides, and hard-aborts `runClient`/`runServer` with `Unbound registry entries: [secondshift:debug_marker]` the moment a `DeferredRegister` is left unattached.**

## Performance

- **Duration:** ~55 min (includes a usage-limit pause mid-run)
- **Started:** 2026-09-04T03:17Z
- **Completed:** 2026-09-04T03:24Z
- **Tasks:** 2
- **Files created:** 13 (+ Gradle wrapper from the MDK)

## Accomplishments

- Bootstrapped from the official `NeoForgeMDKs/MDK-1.21.1-ModDevGradle@main` (only `gradle/`, `gradlew`, `gradlew.bat`, `.gitignore` copied); `build.gradle` / `settings.gradle` / `gradle.properties` / `neoforge.mods.toml` transcribed from STACK.md (no `maven-publish`, no `gameTestServer`, `neo_version` compile-pin + `neo_version_range=[21.1,)` runtime range).
- `./gradlew build` → `build/libs/secondshift-0.1.0.jar` with `META-INF/neoforge.mods.toml` fully expanded and `pack.mcmeta` `pack_format` 48.
- `./gradlew runClient` reaches the main menu with **Second Shift 0.1.0 (secondshift)** in the FML mod list; `./gradlew runServer` reaches `Done (…)! For help, type "help"` with no client-class leak (`ClientModBusEvents` never loads on the dedicated server — BUILD-03).
- Real `DeferredRegister.Items` holding the D-07 `debug_marker`, attached in the `@Mod` constructor; `commonSetup` guardrail logs `[SecondShift] common setup - 1 item(s) registered: [secondshift:debug_marker]`.
- `FMLLoadCompleteEvent` self-check: on a correct build it passes silently; commenting out `ModItems.ITEMS.register(modBus)` produces a **fatal** mod-loading crash on both `runClient` and `runServer` — `IllegalStateException: Unbound registry entries: [secondshift:debug_marker]` + `run/crash-reports/crash-*-fml.txt` pointing at `ModRegistrySelfCheck.onLoadComplete`.
- `scripts/run-until.sh` launch harness: auto-discovers the run log, polls for a marker or times out, then `./gradlew --stop` + `taskkill //F //T //PID` — verified to leave **0 orphan `java.exe`** across ~8 launches.

## Task Commits

1. **Task 1: Bootstrap the pinned MDG project** — `f33dde9` (chore)
2. **Task 2: First DeferredRegister + @Mod wiring + self-check** — `83e1933` (feat)

## Supply-chain / toolchain record (T-01-SC)

- MDK source: `https://codeload.github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/zip/refs/heads/main`
- Gradle wrapper: `9.2.1`, `distributionUrl=https://services.gradle.org/distributions/gradle-9.2.1-bin.zip`
- `distributionSha256Sum=72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f` (added — the MDK ships the wrapper without a checksum line; sum fetched from `services.gradle.org/distributions/gradle-9.2.1-bin.zip.sha256`)
- No npm/pip/cargo installs in this phase.
- NeoForge / MC / decompile caches were already present in `~/.gradle/caches/neoformruntime` (211 MiB) on this machine, so the first build was ~19s rather than the 10+ min cold path.

## Canonical run paths (for downstream plans / D-12)

- **MDG 2.0.146 writes run logs to `run/logs/`** — `run/logs/latest.log` and `run/logs/debug.log` (rotated as `run/logs/<date>-N.log.gz` / `debug-N.log.gz`). It does **NOT** use `runs/client/logs/` / `runs/<name>/logs/`. Downstream greps can use `run/logs/latest.log` directly (the `runs/*/logs/…` fallback glob in `run-until.sh` never matched).
- The dev run directory (gameDir) is `run/` for every run task; `run/mods/` exists after the first server launch.
- Dev-mode assets live in `~/.gradle/caches/neoformruntime/assets` (shared, not under `run/`).
- `run-until.sh` kill sequence confirmed on Windows Git Bash: `./gradlew --stop` then `taskkill //F //T //PID "$GRADLE_PID"` (doubled slashes for MSYS). No orphan JVMs after client, server, or abort-mode runs.

## Decisions Made

- **Self-check throws directly, not via `event.enqueueWork()`** — see Deviations.
- **`runData` dropped as a self-check surface** — see Deviations.
- **`.gitattributes` added** — `gradlew` and `*.sh` forced to `eol=lf`; without it `core.autocrlf=true` corrupts the wrapper script on a fresh Windows checkout.
- **`bus = EventBusSubscriber.Bus.MOD` kept** despite `[removal]` deprecation warnings in 21.1.248 — the plan mandates it for documentation parity (PITFALLS §2: FML 4.0.43 ignores the attribute and derives the bus from the event type). The project pins NeoForge for all of v1 (D-03), so it will keep compiling.
- **`.gitkeep` files land inside the built jar** (`data/secondshift/{recipe,loot_table,advancement}/.gitkeep`) — harmless (FML ignores them); the plan explicitly asked for these folders to establish the singular-name convention. A `processResources` exclude can be added later if it ever matters.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Self-check must throw directly from the handler, not inside `event.enqueueWork()`**
- **Found during:** Task 2 (runClient abort test)
- **Issue:** Plan step 3 and PITFALLS §1 specify `onLoadComplete` calls `event.enqueueWork(() -> { … throw new IllegalStateException(…); })`. Empirically, an exception raised inside `enqueueWork` is caught by FML's `DeferredWorkQueue`, logged as `Mod 'secondshift' encountered an error in a deferred task`, and the mod is merely flagged "broken" — the client then *limps on* (`Sound engine started`, texture atlas, etc. all "Cowardly refusing … to a broken mod state"). That is not the hard abort D-08 requires ("hard-aborts the launch with a named list").
- **Fix:** Throw directly from the `@SubscribeEvent static` handler. An uncaught exception from a mod-bus lifecycle handler is aggregated by `ModLoader` into a fatal `ModLoadingException` → `Failed to wait for future Complete loading of 3 mods, 1 errors found` → `crash-*-fml.txt`. `getEntries()` / `isBound()` / `getId()` are thread-safe reads, so no main-thread hop is needed.
- **Files modified:** `src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java`
- **Verification:** Detached-register runs of `runClient` and `runServer` both produce a fatal crash report naming `ModRegistrySelfCheck.onLoadComplete` and `Unbound registry entries: [secondshift:debug_marker]`. Correct builds pass silently.
- **Committed in:** `83e1933`

**2. [Rule 1 - Research error] `runData` is not a self-check surface on MDG 2.0.146 / NeoForge 21.1.248**
- **Found during:** Task 2 (runData self-check verification)
- **Issue:** D-08 and PITFALLS §1 state the self-check "fires on client, dedicated server, and `runData`". Verified false for this toolchain: `./gradlew runData` runs mod construction + `RegisterEvent` + `GatherDataEvent`, then exits (`All providers took: 0 ms` → `BUILD SUCCESSFUL`) **without** firing `FMLCommonSetupEvent` or `FMLLoadCompleteEvent`. A detached register under `runData` produces a clean `BUILD SUCCESSFUL` (exit 0) with no self-check.
- **Fix:** The "detach a register → startup aborts with a named list" test (plan verify #4) is proven under **`runServer`** instead of `runData` — a strictly stronger pair with `runClient`, since the dedicated server is the true "other side" and, unlike `runData`, it actually completes the load sequence. `runData` remains wired in `build.gradle` (costs nothing) but is no longer treated as a guardrail surface.
- **Files modified:** none (verification approach only)
- **Verification:** `runServer` with the register detached → `[ServerModLoader/FATAL] Crash report saved` + `ModLoadingCrashException: Mod loading has failed`.
- **Committed in:** n/a (documented here)

**3. [Rule 1 - Verification command] FML 4.0.43 dev-mode log phrasing**
- **Found during:** Tasks 1 & 2 (log-grep verifications)
- **Issue:** Plan greps assumed `Loading .* mods` / `Found mod file .*secondshift` and dotted class names (`Subscribing @EventBusSubscriber class com.cxmxrgo.secondshift.ModRegistrySelfCheck …`). In dev mode the mod is a classpath entry, not a jar file, and FML 4.0.43 logs the subscribe line with the JVM descriptor form (`Lcom/cxmxrgo/secondshift/ModRegistrySelfCheck;`).
- **Fix:** Verified against the actual output — the FML `Mod List:` block (`Second Shift 0.1.0 (secondshift)`), `Found valid mod file main with {secondshift} mods`, the `[SecondShift] loading …` constructor line, and `Subscribing @EventBusSubscriber class Lcom/cxmxrgo/secondshift/…; to the mod event bus of mod secondshift` for both `ModRegistrySelfCheck` and `client.ClientModBusEvents`.
- **Files modified:** none
- **Committed in:** n/a (documented here)

---

**Total deviations:** 1 code fix + 2 verification-approach corrections
**Impact on plan:** No architectural change. The self-check is *stronger* than planned (fatal crash vs. soft "broken" state) and proven on the two surfaces that matter for BUILD-03. `runData`'s role was overstated in the research and is now corrected for downstream phases.

## Issues Encountered

- 4 `[removal]` deprecation warnings for `EventBusSubscriber.Bus` / `bus()` in NeoForge 21.1.248 — expected, kept per plan (see Decisions).
- First-run `NoSuchFileException: server.properties` on `runServer` — harmless; the server generates it on first launch.

## Threat Flags

None — no new network endpoints, auth paths, or trust boundaries beyond the MDK/Gradle supply chain already covered by the threat model (T-01-SC recorded above).

## User Setup Required

None — `01-01` has no `user_setup` frontmatter. (Plan `01-02` requires the CurseForge "test" instance for `deployToTest`.)

## Next Phase Readiness

- Ready for **01-02** (the `deployToTest` loop): `build.gradle` / `gradle.properties` exist and are the files 01-02 edits; `secondshift-0.1.0.jar` builds to the predictable name; the run-log path (`run/logs/`) and `run-until.sh` behaviour are recorded above for 01-02's `run/mods` scan check (D-12).
- No blockers.

---
*Phase: 01-skeleton-feedback-loop*
*Completed: 2026-09-04*

## Self-Check: PASSED

All 10 tracked files present on disk; both task commits (`f33dde9`, `83e1933`) in git; `build/libs/secondshift-0.1.0.jar` builds. `runClient` / `runServer` verified green; self-check abort verified fatal on both surfaces.
