---
phase: 01-skeleton-feedback-loop
verified: 2026-09-04T05:00:00Z
status: human_needed
score: 8/9 must-haves verified (1 partially — deploy done, in-instance launch pending human)
overrides_applied: 0
re_verification:
  previous_status: none
  note: initial verification
human_verification:
  - test: "Launch the CurseForge \"test\" instance from the CurseForge app; wait for the main menu; open Mods."
    expected: "\"Second Shift\" is listed alongside owo-lib, accessories, and wildcard; the game reaches the main menu with no crash / no error screen; C:/Users/user/curseforge/minecraft/Instances/test/logs/latest.log contains \"[SecondShift] common setup\" and no \"Unbound registry entries\"."
    why_human: "Requires the CurseForge desktop app launcher (real mod-loader environment with the 3 companion mods) which cannot be driven programmatically. runClient is a clean room (D-11) and does not exercise instance compatibility."
  - test: "With Minecraft still running (holding a Windows lock on mods/*.jar), run ./gradlew deployToTest once."
    expected: "The task fails with a GradleException containing \"close Minecraft (it locks mods/*.jar) and re-run\"; build/libs/secondshift-0.1.0.jar is unchanged (md5 still f969d8c9c8592f8ee2d68e801b4198a8)."
    why_human: "A live Windows file lock held by a running game cannot be simulated in an automated check (D-04 file-lock branch, BUILD-05). Code path + message wording were reviewed; runtime behaviour needs a human."
---

# Phase 1: Skeleton & Feedback Loop — Verification Report

**Phase Goal:** A pinned NeoForge 1.21.1 skeleton that builds, launches on both sides, deploys itself into the test instance, and aborts loudly on any unbound registry entry.
**Verified:** 2026-09-04T05:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

> **Mode note:** ROADMAP marks this phase `mode: mvp`, but the phase goal is an infrastructure statement, not an "As a … I want … so that …" user story. Verification proceeded goal-backward against the ROADMAP Success Criteria (the explicit contract) rather than refusing on the user-story guard. No User Flow Coverage table is produced because there is no user story.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `./gradlew build` produces a loadable `build/libs/secondshift-0.1.0.jar` | ✓ VERIFIED | Ran `./gradlew build` → EXIT 0; `build/libs/secondshift-0.1.0.jar` present (8320 B, md5 `f969d8c9…`). Jar contains 4 `.class` files, `META-INF/neoforge.mods.toml` fully expanded (`versionRange="[21.1,)"`, `[1.21.1]`), `pack.mcmeta`. Jar loads in both runClient and runServer (truths 2/3). |
| 2 | `./gradlew runClient` reaches the main menu with Second Shift loaded | ✓ VERIFIED | `run-until.sh runClient` matched `Sound engine started` (HARNESS_EXIT 0). `run/logs/latest.log`: FML `Mod List:` shows `Second Shift 0.1.0 (secondshift)`; `[SecondShift] loading 0.1.0 on NeoForge`; `[SecondShift] common setup - 1 item(s) registered: [secondshift:debug_marker]`; `[SecondShift] client setup ok`. `debug.log` shows both `Subscribing @EventBusSubscriber class Lcom/cxmxrgo/secondshift/ModRegistrySelfCheck;` and `…/client/ClientModBusEvents;` to the mod bus. 0 `Unbound registry entries`. |
| 3 | `./gradlew runServer` reaches "Done" with the mod loaded and no client-class leak | ✓ VERIFIED | `run-until.sh runServer` matched `Done (2.765s)! For help, type "help"`. `latest.log`: `Second Shift 0.1.0 (secondshift)`, `[SecondShift] loading`, `[SecondShift] common setup - 1 item(s)…`. Client-class leak grep (`ClientModBusEvents|NoClassDefFoundError|net/minecraft/client`) → 0 matches. |
| 4 | A correct build passes the startup self-check silently | ✓ VERIFIED | Both clean runClient and runServer logs contain 0 `Unbound registry entries` and no crash report from `ModRegistrySelfCheck`. |
| 5 | Detaching a `DeferredRegister` aborts startup on both sides with a named list of unbound IDs | ✓ VERIFIED | Commented out `ModItems.ITEMS.register(modBus);`, ran abort tests, then restored (working tree clean, `git status --porcelain` empty). **runClient:** `crash-2026-09-04_04.46.24-fml.txt` — `ModLoadingCrashException: Mod loading has failed`, stack at `ModRegistrySelfCheck.onLoadComplete(ModRegistrySelfCheck.java:53)`, `IllegalStateException: Unbound registry entries: [secondshift:debug_marker]`. **runServer:** `[main/FATAL] ModLoader: Failed to wait for future Complete loading of 3 mods, 1 errors found` → `crash-2026-09-04_04.46.51-fml.txt`. Rebuild after restore → EXIT 0, jar md5 unchanged. *Deviation (documented & valid): proven on runClient+runServer, not runData — `runData` on this toolchain exits after GatherDataEvent without firing FMLLoadCompleteEvent; runServer is a strictly stronger surface.* |
| 6 | `pack.mcmeta` is `pack_format` 48 and datapack folders use singular 1.21 names | ✓ VERIFIED | `src/main/resources/pack.mcmeta` → `"pack_format": 48` (integer). Folders `data/secondshift/{recipe,loot_table,advancement}/` exist (singular), present on disk and inside the built jar. |
| 7 | `./gradlew deployToTest` replaces the instance jar (glob-delete `secondshift-*.jar`, copy fresh), fails loud on missing dir / lock leaving `build/libs` intact, and is absent from the build graph | ✓ VERIFIED | Against a temp dir seeded with `secondshift-0.0.9.jar` + `second-shift-0.1.0.jar`: deploy left `secondshift-0.1.0.jar` (fresh) + `second-shift-0.1.0.jar` (kept), `0.0.9` deleted. `./gradlew build --dry-run` → 0 `:deployToTest` occurrences. Missing path → `FAILURE … deployToTest: test instance mods dir not found: … Set test_instance_mods_dir in gradle.properties…`, non-zero exit, `build/libs/secondshift-0.1.0.jar` intact. Config-cache-safe (providers captured at config time, no `project` in `doLast`). *Lock branch: human (see below).* |
| 8 | The legacy dashed `second-shift-0.1.0.jar` is gone from the instance and `deployToTest` does not sweep the dashed pattern | ✓ VERIFIED | Instance `mods/` listing: `accessories-…jar`, `owo-lib-…jar`, `secondshift-0.1.0.jar`, `wildcard-0.32.1.jar` — no `second-shift-0.1.0.jar`. Delete filter is `name ==~ /\Qsecondshift\E-.*\.jar/` (build.gradle:131-133) — does not match the dashed name (verified in truth 7 temp-dir test: dashed jar kept). |
| 9 | The built jar loads in the CurseForge "test" instance alongside owo-lib / accessories / wildcard with no crash, "Second Shift" in the Mods list | ⚠️ PARTIAL — HUMAN NEEDED | Automated portion done: `secondshift-0.1.0.jar` in the instance `mods/` is byte-identical to `build/libs/` (md5 `f969d8c9c8592f8ee2d68e801b4198a8` on both). The actual in-app launch of the CurseForge instance is explicitly deferred to end-of-phase UAT (`human_verify_mode: end-of-phase`, 01-02-PLAN Task 2 `<human-check>`). Not launched by the verifier per instructions. |

**Score:** 8/9 truths verified; truth 9 partially verified (artifact deployed, launch pending human).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `build.gradle` | MDG 2.0.146 pinned to NeoForge 21.1.248 / Java 21; runClient/runServer/runData; REGISTRIES marker; generateModMetadata; deployToTest | ✓ VERIFIED | `net.neoforged.moddev` `2.0.146`; `neoForge.version = project.neo_version`; toolchain 21; `forge.logging.markers`=`REGISTRIES`, `logLevel = DEBUG`; `generateModMetadata` ProcessResources + `sourceSets.main.resources.srcDir` + `neoForge.ideSyncTask`; no `maven-publish`, no `gameTestServer`. `deployToTest` present, `dependsOn tasks.named('build')`, not in lifecycle. |
| `gradle.properties` | version pins incl. neo_version vs neo_version_range split; mod_version 0.1.0; test_instance_mods_dir | ✓ VERIFIED | `neo_version=21.1.248`, `neo_version_range=[21.1,)` with the split-rationale comment; `mod_version=0.1.0`; `mod_id=secondshift`; `test_instance_mods_dir=C:/Users/user/curseforge/minecraft/Instances/test/mods`. (`loader_version_range=[1,)` — MDK ships `[4,)`; review IN-01, deliberate loosening, info-only.) |
| `src/main/templates/META-INF/neoforge.mods.toml` | `${...}` placeholders; no [[mixins]] / [[accessTransformers]] | ✓ VERIFIED | Under `src/main/templates/` (not resources); `modId="${mod_id}"`, two `[[dependencies.${mod_id}]]` blocks `side="BOTH"`; no mixins/AT blocks. Expands correctly in the jar. |
| `src/main/java/…/SecondShift.java` | `@Mod("secondshift")`; all DeferredRegisters attached in one visible block | ✓ VERIFIED | `@Mod(SecondShift.MODID)`; constructor `(IEventBus modBus, ModContainer container)`; one commented block with `ModItems.ITEMS.register(modBus);`; `modBus.addListener(this::commonSetup)`; `commonSetup` logs sorted registered IDs via `event.enqueueWork`. |
| `src/main/java/…/registry/ModItems.java` | real `DeferredRegister<Item>` with one throwaway debug entry | ✓ VERIFIED | `DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID)`; `DEBUG_MARKER = ITEMS.registerSimpleItem("debug_marker", new Item.Properties())`; D-07 comment; private ctor. |
| `src/main/java/…/ModRegistrySelfCheck.java` | FMLLoadCompleteEvent isBound() self-check over a hand-maintained list | ✓ VERIFIED | `@EventBusSubscriber(modid=…, bus=Bus.MOD)`; `@SubscribeEvent static void onLoadComplete(FMLLoadCompleteEvent)`; `Stream.of(ModItems.ITEMS).flatMap(getEntries).filter(!isBound).map(getId).sorted()` → `throw new IllegalStateException("Unbound registry entries: " + unbound)`. Throws directly (not via enqueueWork) — documented deviation, proven to produce a fatal abort (truth 5). |
| `src/main/java/…/client/ClientModBusEvents.java` | client-only package + Dist.CLIENT isolation | ✓ VERIFIED | `@EventBusSubscriber(modid=…, bus=Bus.MOD, value=Dist.CLIENT)`; `@SubscribeEvent static onClientSetup(FMLClientSetupEvent)` logs `[SecondShift] client setup ok`. Confirmed absent from the dedicated server (truth 3 leak grep = 0). |
| `src/main/resources/pack.mcmeta` | pack_format 48 | ✓ VERIFIED | `"pack_format": 48`. |
| `scripts/run-until.sh` | launch harness: run-until-marker-or-timeout, kill JVM tree, auto-discover log path, ready/abort modes | ✓ VERIFIED | Positional args as specified; `discover_log` globs `run/logs/latest.log runs/*/logs/latest.log`; `kill_tree` = `./gradlew --stop` + `taskkill //F //T //PID`; ready vs abort semantics implemented. Exercised 5× this verification with 0 orphan JVMs. (Review WR-01/WR-02/IN-04/IN-06: robustness warnings, non-blocking.) |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.2.1 + SHA-256 | ✓ VERIFIED | `distributionUrl=…gradle-9.2.1-bin.zip`; `distributionSha256Sum=72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f`; `validateDistributionUrl=true`. |
| `run/mods/.gitkeep` | dev-parity mods dir (D-12) | ✓ VERIFIED | Present. SUMMARY records D-12 resolved YES (FML `FMLPaths.MODSDIR` scans `run/mods/`, proven with a wildcard jar drop). Informational per D-12. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SecondShift.java` | `ModItems.ITEMS` | `.register(modBus)` in `@Mod` ctor | ✓ WIRED | Line 34, active. Runtime-proven: `common setup - 1 item(s) registered: [secondshift:debug_marker]` in both client/server logs. |
| `ModRegistrySelfCheck.java` | `ModItems.ITEMS` | `Stream.of(ModItems.ITEMS).flatMap(getEntries).filter(!isBound)` | ✓ WIRED | Lines 43-51. Runtime-proven: detaching the register produced `Unbound registry entries: [secondshift:debug_marker]` (truth 5). |
| `build.gradle` | `neoforge.mods.toml` template | `generateModMetadata` ProcessResources `expand` | ✓ WIRED | Jar's `META-INF/neoforge.mods.toml` is fully expanded (no `${...}` left). |
| `build.gradle deployToTest` | `gradle.properties test_instance_mods_dir` | `providers.gradleProperty(...)` captured at config time | ✓ WIRED | `deployDestProvider` (build.gradle:101), used in `doLast`; missing-key path produces the actionable error (truth 7). |
| `build.gradle deployToTest` | `tasks.named('build')` | `dependsOn` | ✓ WIRED | build.gradle:108. Yet absent from the `build` graph (`build --dry-run` → 0). |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Build produces jar | `./gradlew build` | EXIT 0, jar present, md5 `f969d8c9…` | ✓ PASS |
| Client boots to menu | `run-until.sh runClient 'Sound engine started…' 900` | HARNESS_EXIT 0, marker matched | ✓ PASS |
| Server reaches Done | `run-until.sh runServer 'Done \(…\)! For help, type' 900` | matched `Done (2.765s)!` | ✓ PASS |
| Self-check abort (client) | detach register + `run-until.sh runClient 'Unbound registry entries' 900 '' abort` | fatal crash report, `Unbound registry entries: [secondshift:debug_marker]` | ✓ PASS |
| Self-check abort (server) | detach register + `run-until.sh runServer 'Unbound registry entries' 900 '' abort` | `[main/FATAL] ModLoader … 1 errors found`, crash report | ✓ PASS |
| deployToTest glob-delete matrix | `deployToTest -Ptest_instance_mods_dir=<tmp>` with seeded jars | fresh jar in, `0.0.9` deleted, dashed kept | ✓ PASS |
| deployToTest missing path | `deployToTest -Ptest_instance_mods_dir=/no/such/dir` | non-zero exit, actionable msg, `build/libs` jar intact | ✓ PASS |
| deployToTest absent from build graph | `./gradlew build --dry-run \| grep -c :deployToTest` | 0 | ✓ PASS |
| Working tree restored after abort tests | `git status --porcelain` | empty | ✓ PASS |

### Probe Execution

No conventional `scripts/*/tests/probe-*.sh` probes exist for this phase. The plan's verification is `<automated>` gradle/grep assertions and the `run-until.sh` harness, all re-executed above. Not applicable.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| BUILD-01 | 01-01 | Compiles against NeoForge 21.1.248 / MC 1.21.1 (Java 21) via wrapper; `./gradlew build` produces a loadable jar | ✓ SATISFIED | Truth 1; jar loads in runClient/runServer. |
| BUILD-02 | 01-01 | `runClient` reaches main menu with mod loaded; `runServer` starts clean | ✓ SATISFIED | Truths 2, 3. |
| BUILD-03 | 01-01 | Startup self-check aborts loading with a named list of any unbound DeferredRegister entry | ✓ SATISFIED | Truth 5 — verified on both surfaces this run. |
| BUILD-04 | 01-02 | Built jar loads in the CurseForge "test" instance alongside owo-lib / accessories / wildcard without crashing | ? NEEDS HUMAN | Truth 9 — jar deployed md5-identical; in-app launch is end-of-phase UAT. |
| BUILD-05 | 01-02 | Each build deployed into the test instance's `mods/` (replacing the previous) so the user can test in-game | ✓ SATISFIED (1 human sub-check) | Truths 7, 8 — deployToTest replace/fail-loud/not-in-lifecycle verified; instance holds exactly one `secondshift-0.1.0.jar`, no dashed jar. D-04 file-lock branch → human. |
| POL-09 | 01-01 | `pack.mcmeta` uses `pack_format` 48; datapack files use singular 1.21 folder names | ✓ SATISFIED | Truth 6. |

No orphaned requirements — REQUIREMENTS.md maps exactly BUILD-01..05 + POL-09 to Phase 1, all claimed across the two plans' `requirements:` frontmatter.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | No `TODO`/`FIXME`/`XXX`/`TBD`/`HACK`/`PLACEHOLDER` in `src/`, `*.gradle`, `*.sh`, `*.toml`, `*.mcmeta` | ℹ️ Info | Clean. |
| `data/secondshift/*/.gitkeep` | — | `.gitkeep` files packaged into the jar | ℹ️ Info | Intentional (plan asked for the folders to establish the singular-name convention); FML ignores them. |
| `build.gradle` | 40, 45 | `neoforge.enabledGameTestNamespaces` system property added to client/server runs | ℹ️ Info | Minor deviation from STACK.md (which removed gameTest). No `gameTestServer` run task added; property alone is inert without gametests. Not a gap. |
| `ModRegistrySelfCheck.java` / `SecondShift.java` | — | Register list maintained in two places (ctor + `Stream.of`) — omission from `Stream.of` fails silently | ⚠️ Warning (review WR-03) | Maintainability hazard for Phase 2+. Not a Phase 1 goal failure (only one register today, both sites correct). Phase 2 plan already flags the D-10 requirement. |
| `scripts/run-until.sh` | 34-60, 41 | Stale-log-path mtime race; `./gradlew --stop` kills all daemons; unvalidated args | ⚠️ Warning (review WR-01/WR-02, IN-04/IN-06) | Harness robustness. Worked correctly across 5 launches this verification. Non-blocking. |

### Human Verification Required

#### 1. CurseForge "test" instance in-app launch (BUILD-04 / Success Criterion 1)

**Test:** Launch the CurseForge "test" instance from the CurseForge desktop app. Wait for the main menu. Open the Mods list.
**Expected:** "Second Shift" appears alongside owo-lib, accessories, and wildcard. Game reaches the main menu — no crash, no error screen. `C:/Users/user/curseforge/minecraft/Instances/test/logs/latest.log` contains `[SecondShift] common setup` and does NOT contain `Unbound registry entries`.
**Why human:** Requires the CurseForge app launcher — the real multi-mod loader environment. `runClient` is a deliberate clean room (D-11) and does not load the 3 companion mods. The deployed jar is byte-identical to the verified build (md5 `f969d8c9c8592f8ee2d68e801b4198a8`).

#### 2. deployToTest file-lock branch (BUILD-05 / D-04)

**Test:** With Minecraft still running (instance open, holding a Windows lock on `mods/*.jar`), run `./gradlew deployToTest` once.
**Expected:** Task fails with a `GradleException` containing "close Minecraft (it locks mods/*.jar) and re-run". `build/libs/secondshift-0.1.0.jar` is unchanged.
**Why human:** A live OS file lock held by a running game cannot be simulated automatically. The try/catch and message wording were code-reviewed; the missing-path fail-loud branch was verified automatically.

### Gaps Summary

No blocking gaps. All four ROADMAP Success Criteria are met in the codebase except the human-only portions explicitly planned as end-of-phase UAT (`human_verify_mode: end-of-phase`):

- The pinned toolchain builds (`secondshift-0.1.0.jar`, MDG 2.0.146 / NeoForge 21.1.248 / Gradle 9.2.1 with SHA-256).
- `runClient` reaches the main menu and `runServer` reaches "Done", mod loaded on both, no client-class leak — **re-verified live this run**.
- Detaching `ModItems.ITEMS.register(modBus)` hard-aborts **both** `runClient` and `runServer` with `Unbound registry entries: [secondshift:debug_marker]` and a fatal FML crash report — **re-verified live this run**, file restored, working tree clean.
- `deployToTest` replaces the instance jar, fails loud on a missing path, stays out of the `build` graph, and never touches `build/libs`. The instance holds exactly one `secondshift-0.1.0.jar` (md5-identical) with no legacy dashed jar.
- `pack.mcmeta` is `pack_format` 48; `data/secondshift/{recipe,loot_table,advancement}/` are singular.

The two documented plan deviations both hold and are correct: (1) the self-check throws directly from the `FMLLoadCompleteEvent` handler (proven to produce a hard abort, not a soft "broken mod" state); (2) the abort test is proven on `runClient` + `runServer` instead of `runData` (which does not fire the event on this toolchain — runServer is a strictly stronger surface).

Status is `human_needed` solely because 2 in-app checks remain (instance launch + file-lock branch), both pre-planned as end-of-phase UAT.

---

_Verified: 2026-09-04T05:00:00Z_
_Verifier: Claude (gsd-verifier)_
