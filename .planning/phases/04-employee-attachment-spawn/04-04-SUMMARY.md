---
phase: 04-employee-attachment-spawn
plan: 04
subsystem: verification
tags: [manual-verification, gametest-limits, entity-attachment-sync, neoforge-1.21.1]

# Dependency graph
requires:
  - phase: 04-01
    provides: "EmployeeData attachment + ModAttachments registration + ClientEmployeeSyncDebug diagnostic"
  - phase: 04-02
    provides: "EmployeeManager.bind — the core spawn method"
  - phase: 04-03
    provides: "BindEmployeePayload/ServerPayloadHandler — the network bind trigger"
provides:
  - "Empirical confirmation: NeoForge 21.1.248 entity data attachment sync (AttachmentType.Builder#sync) works correctly for minecraft:villager holders — the LIGHT spike is resolved YES"
  - "Empirical confirmation: EmployeeData survives a save/quit/relaunch round trip and a 300-block chunk-unload round trip"
  - "Empirical confirmation: a vanilla Name Tag renames a bound employee exactly like a wild villager (D-02)"
  - "Empirical confirmation: a separate wild villager in the same world is completely unaffected (EMP-09)"
affects: [phase-05-trade-selection]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Manual verification checkpoint for behaviors structurally outside GameTest's reach (real save/quit/relaunch, real chunk unload/reload, a real distinct client process observing sync) — GameTest proves server-side correctness, manual verification proves the real multiplayer/client-sync path"

key-files:
  created: []
  modified: []

key-decisions:
  - "Two real bugs were found DURING this checkpoint (not pre-existing plan gaps) and fixed via a quick task before the checkpoint could be approved: CR-01 (spawn position overlapping the job-site block) and the sync-diagnostic timing bug (EntityJoinLevelEvent fires before the bundled attachment-sync sub-packet is processed, per NeoForge's ClientboundBundlePacket ordering — confirmed against actual 21.1.248 source, not assumption). Both fixes are tracked in .planning/quick/260905-0yg-fix-cr-01-spawn-position-overlap-and-cli/, not in this phase's plans, per GSD convention (quick tasks stay separate from phase plans)."

patterns-established:
  - "Entity attachment sync observation pattern: never check hasData() synchronously inside EntityJoinLevelEvent for an entity attachment — NeoForge bundles the spawn packet and the attachment sync packet together (spawn first), so the join event always fires before sync data is applied. Instead, record the candidate entity id on join and poll hasData()/getData() on a bounded ClientTickEvent.Post handler (~20 tick timeout). This pattern is now proven correct in ClientEmployeeSyncDebug and should be reused by any future client-side attachment observer."

requirements-completed: [EMP-01, EMP-02, EMP-08, EMP-09]

# Metrics
duration: ~1 session (across two test passes, one quick-task fix cycle in between)
completed: 2026-09-05
---

# Phase 4: Employee Attachment & Spawn Summary

**All 4 roadmap success criteria confirmed true in a real running client — a bind spawns a persistent, named, correctly-positioned villager whose EmployeeData attachment reliably persists and syncs to the client, with zero effect on wild villagers.**

## Performance

- **Duration:** ~1 session, across two manual test passes with a quick-task fix cycle in between
- **Completed:** 2026-09-05
- **Tasks:** 1 checkpoint task (human-verify), re-run once after fixes
- **Files modified:** 0 in this plan (verification only — the 2 bugs found were fixed via a separate quick task)

## Accomplishments

- All 7 manual verification steps pass: bind/spawn correctness, server-side persistence across chunk-unload, server-side persistence across save/quit/relaunch, **client-side attachment sync (the LIGHT spike)**, Name Tag rename behavior (D-02), and wild-villager non-interference (EMP-09).
- The LIGHT spike is empirically and definitively resolved: `AttachmentType.Builder#sync(STREAM_CODEC)` **does** deliver `EmployeeData` to the client for a `minecraft:villager` holder in NeoForge 21.1.248. Confirmed via the corrected diagnostic's log output: `entity=Villager['juninho'/195...] data=EmployeeData[version=1, name=Wendell Timesheet, profession=minecraft:librarian, tier=1, offers=[...]]` — real populated data, not `EmployeeData.EMPTY`.
- Two real bugs were caught and fixed mid-checkpoint rather than shipping silently broken: employees no longer spawn embedded in the job-site block (CR-01), and the sync diagnostic no longer structurally guarantees a false negative (sync-timing fix).

## Task Commits

This plan had no code-modifying tasks of its own — its single task was the human-verify checkpoint. The two bugs discovered during verification were fixed under a separate quick task:

1. **Quick task 260905-0yg** (`.planning/quick/260905-0yg-fix-cr-01-spawn-position-overlap-and-cli/`):
   - `42c5f46`: fix(quick-0yg): spawn bound villager clear of job-site block (CR-01)
   - `c7aec01`: fix(quick-0yg): poll for EmployeeData sync instead of checking synchronously
   - `407baae`: docs(quick-260905-0yg): plan + summary for CR-01 and sync-timing fixes

## Files Created/Modified

None in this plan directly. See quick task 260905-0yg for:
- `src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java` — spawn position fix
- `src/main/java/com/cxmxrgo/secondshift/gametest/EmployeeGameTests.java` — new regression test (`bind_villager_does_not_overlap_job_site_block`)
- `src/main/java/com/cxmxrgo/secondshift/client/ClientEmployeeSyncDebug.java` — tick-polling sync check

## Decisions Made

- CR-01's fix (`altarPos.above()` → `altarPos.above(2)`) is an interim measure — once Phase 5's locked G-2 redesign (job-site block as a socketed, non-collidable rendered item rather than a real placed block) lands, the spawn cell above the altar will be permanently free again, and Phase 5 planning should re-evaluate whether `above(2)` is still the right spawn offset or whether reverting to `above()` makes more sense once the collision hazard is gone.
- The sync-diagnostic (`ClientEmployeeSyncDebug`) remains permanently active in shipped code rather than being removed now that its research purpose is served (per `REVIEW-phases-1-4.md` WR-04) — the polling pattern it now demonstrates is valuable as a reference implementation for future client-side attachment observers, and its log output is low-frequency (once per bind, plus timeouts for nearby non-employee villagers). Revisit removal/gating behind a debug flag in a future cleanup pass if log noise becomes a problem.

## Deviations from Plan

**1 auto-fixed issue during the quick-task fix cycle (not this plan directly, but load-bearing for this checkpoint's pass):**

**1. [Rule 3 - Blocking] Incorrect NeoForge import path in the planned fix**
- **Found during:** Quick task 260905-0yg, Task 2 (sync-timing fix)
- **Issue:** The plan's interface notes specified `net.neoforged.neoforge.event.tick.ClientTickEvent.Post`, which does not exist in NeoForge 21.1.248.
- **Fix:** Verified via jar inspection that `ClientTickEvent` actually lives in `net.neoforged.neoforge.client.event`; corrected the import.
- **Verification:** `./gradlew compileJava` clean; `./gradlew runGameTestServer` 20/20 green.
- **Committed in:** `c7aec01`

**Total deviations:** 1 auto-fixed (Rule 3, import path correction). **Impact on plan:** None on scope — a necessary correctness fix, not scope creep.

## Issues Encountered

Two real bugs were found during the FIRST manual test pass (not issues with the verification process itself, but genuine implementation bugs the checkpoint was specifically designed to catch):

1. **CR-01** (found via a static code review run in parallel while waiting for the user to test): `EmployeeManager.bind()` spawned the villager at the exact `BlockPos` the bind precondition requires a job-site block to occupy, so every real bind spawned the employee embedded in that block. GameTest didn't catch it because the existing test never placed a real block at that position. User confirmed the fix resolved the visible clipping on re-test.
2. **Sync-diagnostic timing bug** (found by exhaustively grepping ~25 archived `run/logs/*.log.gz` sessions for the expected diagnostic line and finding zero occurrences despite `/data get entity` confirming server-side persistence worked correctly): `ClientEmployeeSyncDebug` checked `hasData()` synchronously inside `EntityJoinLevelEvent`, but NeoForge's `ClientboundBundlePacket` ordering guarantees the join event fires before the bundled attachment-sync sub-packet is processed — traced directly against the actual NeoForge 21.1.248 source (not assumed). Fixed with a bounded tick-poll pattern; confirmed working on re-test with real populated data in the log.

Both fixes are documented in full in `.planning/research/entity-attachment-sync-timing-investigation.md` and `.planning/REVIEW-phases-1-4.md` (CR-01).

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

Phase 4 is fully verified and ready to close. Phase 5 (Trade Selection / ALTAR-02 profession resolution) can proceed, with two carried-forward notes already flagged in STATE.md:
- G-2 (locked): the job-site-block-on-top mechanic will be reworked into an item-socket + hovering-render mechanic, which also resolves CR-01's root cause (no more real block occupying the spawn cell).
- CR-01's `above(2)` spawn offset is interim and should be re-evaluated once G-2 lands.

No blockers for Phase 5.

---
*Phase: 04-employee-attachment-spawn*
*Completed: 2026-09-05*
