---
phase: quick/260905-0yg
plan: 01
subsystem: employee
tags: [gametest, neoforge, attachments, entity-sync, minecraft]

# Dependency graph
requires:
  - phase: 04-employee-attachment-spawn
    provides: EmployeeManager.bind, ClientEmployeeSyncDebug diagnostic, EmployeeGameTests suite
provides:
  - CR-01 fix - bound employee villager no longer overlaps the job-site block at altarPos.above()
  - Poll-based client-side EmployeeData sync diagnostic that can actually observe success/timeout
affects: [employee-attachment-spawn, binding-flow]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Client-tick polling with a bounded (20-tick) pending map for observing async attachment sync, instead of a synchronous check inside EntityJoinLevelEvent"

key-files:
  created: []
  modified:
    - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java
    - src/main/java/com/cxmxrgo/secondshift/gametest/EmployeeGameTests.java
    - src/main/java/com/cxmxrgo/secondshift/client/ClientEmployeeSyncDebug.java

key-decisions:
  - "Spawn offset changed from altarPos.above() to altarPos.above(2) to clear the job-site block the bind precondition (ProfessionResolver.fromAbove) requires at altarPos.above()"
  - "ClientTickEvent.Post lives in net.neoforged.neoforge.client.event (not net.neoforged.neoforge.event.tick as the plan's interface notes assumed) on NeoForge 21.1.248 - verified via javap/jar inspection"
  - "Int2IntOpenHashMap chosen for the entity-id -> ticks-pending tracking map (fastutil, already a project dependency)"

patterns-established:
  - "Async attachment-sync diagnostics must poll on a tick event rather than check synchronously inside the spawn-join event, since NeoForge bundles the sync sub-packet after the spawn packet in the same ClientboundBundlePacket"

requirements-completed: [CR-01, SYNC-TIMING]

# Metrics
duration: 25min
completed: 2026-09-05
---

# Quick Task 260905-0yg: Fix CR-01 Spawn Overlap and Sync-Timing Diagnostic Summary

**Bound employee villagers now spawn one block clear of the job-site block (altarPos.above(2)), and the client-side EmployeeData sync diagnostic polls on ClientTickEvent.Post instead of checking synchronously (and always-falsely) inside EntityJoinLevelEvent.**

## Performance

- **Duration:** ~25 min
- **Tasks:** 3 completed (2 code tasks + 1 verification-only task)
- **Files modified:** 3

## Accomplishments
- Fixed CR-01: `EmployeeManager.bind()` now spawns the villager at `altarPos.above(2)`, clear of the job-site block that the bind precondition (`ProfessionResolver.fromAbove`) requires at `altarPos.above()`.
- Added a new regression GameTest (`bind_villager_does_not_overlap_job_site_block`) that places a real job-site block and asserts the bound villager's bounding box does not intersect it — the exact real-play scenario the prior suite never exercised.
- Reworked `ClientEmployeeSyncDebug` from a synchronous (structurally-always-false) `hasData()` check to a bounded, 20-tick client-tick poll that can genuinely observe sync success or report a timeout.
- All 20 GameTests (including the new one) pass under `runGameTestServer`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Fix CR-01 spawn overlap and add job-site overlap regression GameTest** - `42c5f46` (fix)
2. **Task 2: Replace synchronous hasData check with a bounded client-tick poll** - `c7aec01` (fix)
3. **Task 3: Full build and GameTest verification** - no commit (verification-only, no files modified)

## Files Created/Modified
- `src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java` - Spawn position changed to `altarPos.above(2)`
- `src/main/java/com/cxmxrgo/secondshift/gametest/EmployeeGameTests.java` - Updated Test 5's position assertion to `above(2)`; added `bind_villager_does_not_overlap_job_site_block` regression test
- `src/main/java/com/cxmxrgo/secondshift/client/ClientEmployeeSyncDebug.java` - Reworked to a `PENDING` `Int2IntOpenHashMap` (entity id -> ticks-pending) populated in `onEntityJoinLevel`, polled every tick in a new `onClientTick` handler

## Decisions Made
- **Spawn offset:** `altarPos.above(2)` chosen per the plan's exact interface spec — `BlockPos#above(int)` is a vanilla method, no new import needed.
- **Job-site block for the new test:** `Blocks.CARTOGRAPHY_TABLE.defaultBlockState()`, matching the pattern already used in `BindingAltarGameTests`. AABB-intersection assertion constructed via `new AABB(jobSitePos)` (vanilla's `AABB(BlockPos)` constructor builds the unit-cube AABB at that block position) compared against `villager.getBoundingBox()`.
- **Tracking map type:** `it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap` (fastutil, already a dependency via `EmployeeManager`'s `Int2ObjectMap` usage), mapping entity id -> ticks-pending count.
- **Timeout log message:** `"[SecondShift] client-side EmployeeData sync check TIMED OUT for entity={}"` (logged at WARN, after the pending count exceeds 20 ticks / 1 second).
- **Success log message (unchanged text):** `"[SecondShift] client-side EmployeeData sync check: entity={} data={}"` (logged at INFO, now from `onClientTick` once `hasData()` becomes true, instead of unconditionally-false from `onEntityJoinLevel`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Corrected `ClientTickEvent` package**
- **Found during:** Task 2 (client-tick poll implementation)
- **Issue:** The plan's interface notes specified `net.neoforged.neoforge.event.tick.ClientTickEvent.Post`. That package/class does not exist in NeoForge 21.1.248 — `compileJava` failed with "cannot find symbol". Inspecting the actual `neoforge-21.1.248-universal.jar` contents (`unzip -l`) showed `ClientTickEvent` and its `$Post`/`$Pre` nested classes live in `net.neoforged.neoforge.client.event`, not `net.neoforged.neoforge.event.tick` (which only contains `EntityTickEvent`/`LevelTickEvent`/`PlayerTickEvent`/`ServerTickEvent`).
- **Fix:** Changed the import to `net.neoforged.neoforge.client.event.ClientTickEvent`. No other code changes needed — `ClientTickEvent.Post` usage itself was correct.
- **Files modified:** `src/main/java/com/cxmxrgo/secondshift/client/ClientEmployeeSyncDebug.java`
- **Verification:** `./gradlew compileJava` exits 0 after the fix.
- **Committed in:** `c7aec01` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking — incorrect package name from plan interface notes)
**Impact on plan:** No scope creep; the fix is a single import correction needed for the plan's own Task 2 to compile at all.

## Issues Encountered
None beyond the deviation above.

## User Setup Required
None - no external service configuration required.

## `runGameTestServer` GREEN Tail Output

```
[Server thread/INFO] [minecraft/GameTestServer]: 20 tests are now running at position -11366878, -59, 10357400!
[Server thread/INFO] [minecraft/GameTestRunner]: Running test batch 'defaultBatch:0' (20 tests)...
[Server thread/INFO] [minecraft/GameTestServer]: [++++++++++++++++++++]
[Server thread/INFO] [minecraft/GameTestServer]: ========= 20 GAME TESTS COMPLETE IN 1.165 s ======================
[Server thread/INFO] [minecraft/GameTestServer]: All 20 required tests passed :)
[Server thread/INFO] [minecraft/GameTestServer]: ====================================================
```

All 6 `EmployeeGameTests` methods (5 existing + the new `bind_villager_does_not_overlap_job_site_block` regression test) are included in the 20/20 passing count, alongside every other existing GameTest suite in the project (`BindingAltarGameTests`, `HarvesterGameTests`, etc.).

## Next Phase Readiness
- CR-01 is closed: real binds (job-site block placed, matching actual play) no longer spawn the villager overlapping the job-site block.
- The sync-timing diagnostic can now actually report success or a genuine timeout; it is still a LIGHT spike diagnostic only (no gameplay/rendering behavior), and remains a candidate for cleanup/removal once the sync question is fully closed out in a later phase's UAT.
- No blockers for continuing Phase 04 or later phases.

---
*Phase: quick/260905-0yg*
*Completed: 2026-09-05*

## Self-Check: PASSED

- FOUND: src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java
- FOUND: src/main/java/com/cxmxrgo/secondshift/gametest/EmployeeGameTests.java
- FOUND: src/main/java/com/cxmxrgo/secondshift/client/ClientEmployeeSyncDebug.java
- FOUND commit: 42c5f46
- FOUND commit: c7aec01
- Grep verified: `altarPos.above(2)` present in EmployeeManager.java; `ClientTickEvent` present in ClientEmployeeSyncDebug.java (no synchronous `hasData` call remains in `onEntityJoinLevel`)
