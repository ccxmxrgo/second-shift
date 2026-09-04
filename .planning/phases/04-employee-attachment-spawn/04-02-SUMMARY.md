---
phase: 04-employee-attachment-spawn
plan: 02
subsystem: employee-spawn
tags: [villager, gametest, minecraft-1.21.1, neoforge-attachments, merchant-offers]

# Dependency graph
requires:
  - phase: 04-01
    provides: "EmployeeData record + ModAttachments.EMPLOYEE attachment holder (the write target for bind)"
provides:
  - "EmployeeManager.bind(ServerLevel, BlockPos) -> Villager — the phase's permanent core spawn method"
  - "EmployeeNames.pickRandom(RandomSource) — themed default-name pool (D-01)"
  - "EmployeeGameTests — 5-method automated proof of EMP-01/EMP-02/EMP-08/EMP-09"
affects: [04-03-network-bind-trigger]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Spawn ordering discipline: setVillagerData (profession) strictly before setOffers (setVillagerData nulls offers on profession change), setData(EMPLOYEE, ...) after offers/name, addFreshEntity always last"
    - "Static-utility class shape (private no-arg constructor, only static methods) reused for EmployeeManager/EmployeeNames, matching ProfessionResolver's established convention"
    - "Style#getColor() returns TextColor, not Integer — compare via .getValue() against ChatFormatting#getColor()'s int, never .equals() the boxed Integer directly"

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeNames.java
    - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java
    - src/main/java/com/cxmxrgo/secondshift/gametest/EmployeeGameTests.java

key-decisions:
  - "EmployeeNames pool: 16 'Second Shift' HR-themed names (Claude's discretion per D-01/A2), no persisted counter state"
  - "GameTest Test 5 (position-above-altar regression) folded into bind_spawns_villager_with_employee_data rather than a 6th standalone method, matching the plan's explicit '5 methods, 19 tests total' acceptance criterion"

patterns-established:
  - "Bind spawn ordering (profession -> xp -> offers -> name -> attachment -> addFreshEntity) is now the canonical shape every later re-bind/respawn path must match"

requirements-completed: [EMP-01, EMP-02, EMP-08, EMP-09]

# Metrics
duration: 5min
completed: 2026-09-04
---

# Phase 4 Plan 2: Employee Spawn Logic & GameTest Proof Summary

**EmployeeManager.bind spawns a minecraft:villager above the altar with a random fixed profession (FARMER/LIBRARIAN/CLERIC), villagerXp=1, green always-visible name, rolled tier-1 offers, and a populated EmployeeData attachment — proven by 5 new GameTest methods (19/19 total green).**

## Performance

- **Duration:** 5 min
- **Started:** 2026-09-04T20:28:34+01:00
- **Completed:** 2026-09-04T20:33:19+01:00
- **Tasks:** 2
- **Files modified:** 3 (all created)

## Accomplishments
- `EmployeeNames` themed default-name pool (16 entries) with a stateless `pickRandom(RandomSource)` picker (D-01/A2)
- `EmployeeManager.bind(ServerLevel, BlockPos)` — the phase's one genuinely new piece of business logic — spawns a fresh `minecraft:villager` positioned at `altarPos.above()`, with profession randomly rolled from `FARMER`/`LIBRARIAN`/`CLERIC` (D-03), `villagerXp = 1` (EMP-02, prevents `ResetProfession`), up to 2 rolled tier-1 offers, a green always-visible custom name (EMP-08), and a populated `EmployeeData` attachment (EMP-01) — following the exact non-negotiable ordering from RESEARCH.md Pattern 2 (`setVillagerData` before `setOffers`, `addFreshEntity` last)
- `EmployeeGameTests` — 5 new GameTest methods proving EMP-01 (attachment + position), EMP-02 (xp), EMP-08 (name styling/visibility), D-03 (profession pool over 20 iterations), and EMP-09 (a wild villager spawned in the same world is completely untouched by `bind` — no attachment, no name, `xp == 0`)
- Full suite verified: `./gradlew runGameTestServer` reports **19/19 required tests passed** (14 existing + 5 new); `./gradlew runServer` boots clean (`Done (0.317s)!`) with no exceptions, confirming this common-side work introduces no client-class leak

## Task Commits

Each task was committed atomically:

1. **Task 1: EmployeeNames themed default-name pool** - `5311a96` (feat)
2. **Task 2: EmployeeManager.bind + EmployeeGameTests (merged TDD task)** - `017335d` (feat)

**Plan metadata:** pending (docs: complete plan)

## Files Created/Modified
- `src/main/java/com/cxmxrgo/secondshift/employee/EmployeeNames.java` - themed name pool + random picker
- `src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java` - `bind(ServerLevel, BlockPos)` and the private `rollDefaultTier1Offers` helper
- `src/main/java/com/cxmxrgo/secondshift/gametest/EmployeeGameTests.java` - 5 GameTest methods covering EMP-01/EMP-02/EMP-08/EMP-09/D-03

## Decisions Made
- Themed name pool content is Claude's discretion per D-01/A2 — 16 "Second Shift" HR-flavored names, no persisted counter state (a themed pool needs none)
- Test 5 (position-above-altar regression, Pitfall A) folded into the `bind_spawns_villager_with_employee_data` method rather than added as a 6th standalone GameTest method, to match the plan's explicit "5 methods, 19 tests total" acceptance criterion while still covering all 6 behavior cases in the plan's `<behavior>` block

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed incorrect Style/ChatFormatting color comparison in EmployeeGameTests**
- **Found during:** Task 2 (`./gradlew runGameTestServer` first run — `bind_sets_green_always_visible_name` failed)
- **Issue:** `name.getStyle().getColor().equals(ChatFormatting.GREEN.getColor())` compares a `TextColor` object to a boxed `Integer` — always `false` regardless of the actual styled color, even though `EmployeeManager.bind` correctly applies `ChatFormatting.GREEN`
- **Fix:** Compare `style.getColor().getValue()` (int) against `ChatFormatting.GREEN.getColor()` (Integer, auto-unboxed) instead of `.equals()` across mismatched types
- **Files modified:** `src/main/java/com/cxmxrgo/secondshift/gametest/EmployeeGameTests.java`
- **Verification:** Re-ran `./gradlew runGameTestServer` — 19/19 tests passed
- **Committed in:** `017335d` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Test-only fix, no change to `EmployeeManager.bind`'s actual behavior (the name was always styled correctly; only the test's assertion was wrong). No scope creep.

## Issues Encountered
None beyond the auto-fixed test assertion above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `EmployeeManager.bind(ServerLevel, BlockPos)` is now available for Plan 04-03's network bind-trigger handler to call directly.
- All four phase requirements (EMP-01, EMP-02, EMP-08, EMP-09) have automated GameTest proof for everything a single-tick GameTest can observe.
- No blockers.

---
*Phase: 04-employee-attachment-spawn*
*Completed: 2026-09-04*

## Self-Check: PASSED

All created files and task commit hashes verified present on disk / in git history.
