---
phase: 05-profession-resolution-trade-picker
plan: 01
subsystem: gameplay-mechanics
tags: [neoforge, minecraft-1.21.1, block-entity, poi-registry, gametest]

# Dependency graph
requires:
  - phase: 03-menu-screen-harness
    provides: BindingAltarMenu, ProfessionResolver.fromPoi, the do-nothing Binding Altar screen harness
  - phase: 02-economy-items-soul-altar
    provides: SoulAltarBlockEntity's original Soul Block socket + persistence idiom
provides:
  - "SoulAltarBlockEntity dual-socket contract: heldJobItem slot, employeeBound occupancy flag, transient candidateOffers/defaultName session fields"
  - "ProfessionResolver.fromItem(ItemStack) — POI-registry-driven profession resolution from a BlockItem stack"
  - "SoulAltarBlock item-socket interaction surface (occupied-gate first, then Soul Block socket, then job-item socket, then invalid-item rejection)"
affects: [05-02, 05-03, 05-04, 05-05, employee-binding-flow, trade-picker]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dual block-entity item sockets sharing one persistence idiom (save/load mirrored between heldSoulBlock and heldJobItem)"
    - "Occupancy-flag-first interaction gating: employeeBound checked before any other branch in both useItemOn and useWithoutItem"
    - "Transient (non-persisted) session fields on a BlockEntity for once-per-session state (candidateOffers/defaultName) regenerated on reload"

key-files:
  created: []
  modified:
    - src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java
    - src/main/java/com/cxmxrgo/secondshift/trade/ProfessionResolver.java
    - src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java
    - src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java
    - src/main/java/com/cxmxrgo/secondshift/gametest/BindingAltarGameTests.java
    - src/main/resources/assets/secondshift/lang/en_us.json
    - src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java

key-decisions:
  - "SoundEvents.ITEM_FRAME_ADD_ITEM is a plain SoundEvent (not a Holder) in this codebase's mapped API — used directly, no .get()/.value() call, unlike SoundEvents.SOUL_ESCAPE which is a Holder"
  - "Occupied-altar GameTest sets up the bound state directly on the BE (setHeldJobItem/setHeldSoulBlock/setEmployeeBound) instead of driving two real socket interactions, because completing both sockets via useItemOn triggers a real sp.openMenu(...) packet the GameTest mock player cannot receive, crashing the test"

patterns-established:
  - "New message.* lang keys added to ModRegistrySelfCheck.EXTRA_LANG_KEYS in the same commit that introduces them (WR-04 guardrail-sync)"

requirements-completed: [ALTAR-02, ALTAR-05, PICK-01, PICK-08]

# Metrics
duration: 35min
completed: 2026-09-05
---

# Phase 5 Plan 1: Profession Resolution & Item-Socket Rework Summary

**Retired the "job-site block on top of the altar" mechanic (G-2) in favor of a second block-entity item socket, with `ProfessionResolver.fromItem` resolving the target profession from the socketed item via the POI registry, and a persisted `employeeBound` occupancy flag gating all interaction paths.**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-09-05T00:40:00Z (approx.)
- **Completed:** 2026-09-05T01:19:00Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments
- `SoulAltarBlockEntity` now carries the full dual-socket + occupancy + session-data contract every later plan in this phase builds against: `heldJobItem` slot, `employeeBound` flag (persisted, backward-compatible with pre-Phase-5 saves), and transient `candidateOffers`/`defaultName` fields for the once-per-session trade-candidate roll.
- `ProfessionResolver.fromItem(ItemStack)` replaces `fromAbove` — resolves a `VillagerProfession` from a held `BlockItem` via the live POI registry, with zero hardcoded block list (proven against a real cartography table and a non-POI stone block).
- `SoulAltarBlock.useItemOn`/`useWithoutItem` fully rewritten around the item-socket design: the occupied-altar check runs first in both methods (never mutates a socket on a bound altar), Soul Block and job-item sockets fill independently, an unmapped job item is rejected with a themed message, and the Binding Altar screen opens the instant both sockets are filled regardless of fill order.
- `BindingAltarMenu.stillValid`'s reopen-gate check now reads `be.isJobItemEmpty()` off the block entity instead of the deleted `fromAbove` block-above check.
- `BindingAltarGameTests` fully reworked: every test now sockets a real job item via `helper.useBlock` instead of placing a block above the altar; two new `ProfessionResolver.fromItem` tests replace the deleted `fromAbove` tests; added an occupied-altar refusal test and a job-item + occupancy persistence round-trip test. 22/22 GameTests green.

## Task Commits

Each task was committed atomically:

1. **Task 1: SoulAltarBlockEntity — job-item socket, occupancy flag, session-data contract** - `6d5b14c` (feat)
2. **Task 2: ProfessionResolver.fromItem + full SoulAltarBlock interaction rewrite** - `2d4de58` (feat)
3. **Task 3: BindingAltarMenu.stillValid rewiring + GameTest suite for the socket-based flow** - `865d815` (test)

**Plan metadata:** (this commit)

## Files Created/Modified
- `src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java` - dual-socket BE: heldJobItem, employeeBound, transient candidateOffers/defaultName, DATA_VERSION bumped to 2
- `src/main/java/com/cxmxrgo/secondshift/trade/ProfessionResolver.java` - `fromItem(ItemStack)` replaces `fromAbove`
- `src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java` - useItemOn/useWithoutItem rewritten around the dual-item-socket design
- `src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java` - stillValid's job_gone check reads the BE's job-item slot
- `src/main/java/com/cxmxrgo/secondshift/gametest/BindingAltarGameTests.java` - socket-based test rework, 2 new tests, 1 replaced pair
- `src/main/resources/assets/secondshift/lang/en_us.json` - added `message.secondshift.altar.occupied`; reworded `no_job_block` to item-socket phrasing
- `src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java` - added `message.secondshift.altar.occupied` to `EXTRA_LANG_KEYS`

## Decisions Made
- `SoundEvents.ITEM_FRAME_ADD_ITEM` is a plain `SoundEvent` (not `Holder<SoundEvent>`) in this mapped API surface — used directly without `.get()`/`.value()`, unlike `SoundEvents.SOUL_ESCAPE` which is a `Holder`. Discovered via a compile error; no ambiguity once corrected.
- The occupied-altar GameTest sets up the bound-altar state directly via BE setters rather than driving two real socket interactions, because completing both sockets through `useItemOn` triggers a genuine `sp.openMenu(...)` packet that the GameTest mock player cannot receive (`Payload neoforge:advanced_open_screen may not be sent to the client!`), crashing the test. Direct BE setup exercises the exact same `useWithoutItem` occupied-gate code path without that side effect.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `SoundEvents.ITEM_FRAME_ADD_ITEM.get()` does not compile — it is already a `SoundEvent`, not a `Holder`**
- **Found during:** Task 2 (SoulAltarBlock interaction rewrite)
- **Issue:** The plan's action text called for `SoundEvents.ITEM_FRAME_ADD_ITEM.get()`, following the `SOUL_ESCAPE.value()` pattern, but this constant's mapped type in the project's Minecraft 1.21.1 API surface is a plain `SoundEvent` with no `.get()` method.
- **Fix:** Call `level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F)` directly.
- **Files modified:** src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java
- **Verification:** `./gradlew compileJava` succeeds.
- **Committed in:** 2d4de58 (Task 2 commit)

**2. [Rule 3 - Blocking] `ProfessionResolver.fromAbove` deletion (Task 2) broke `BindingAltarMenu.stillValid` and `BindingAltarGameTests` before Task 3 ran**
- **Found during:** Task 2 (compileJava gate)
- **Issue:** `BindingAltarMenu.stillValid`'s `job_gone` check and two `BindingAltarGameTests` methods called the now-deleted `ProfessionResolver.fromAbove`, which is a hard compile error blocking Task 2's own `./gradlew compileJava` acceptance criterion, even though the plan assigns the menu fix to Task 3.
- **Fix:** Applied the `BindingAltarMenu.stillValid` fix (`be.isJobItemEmpty()` off the BE) during Task 2 to restore compilation, then completed the full GameTest rework as originally scoped to Task 3. Commits still landed against the plan's intended task boundaries — Task 2's commit only touched `ProfessionResolver`/`SoulAltarBlock`/lang; the `BindingAltarMenu` fix was staged and committed together with Task 3's GameTest changes.
- **Files modified:** src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java
- **Verification:** `./gradlew compileJava` succeeds after Task 2's source edits; full suite green after Task 3.
- **Committed in:** 865d815 (Task 3 commit)

**3. [Rule 1 - Bug] `binding_altar_occupied_altar_refuses_to_open` crashed the GameTest server via a real `openMenu` packet to a mock player**
- **Found during:** Task 3 (`./gradlew runGameTestServer`)
- **Issue:** The plan's test design drives two real `helper.useBlock` interactions to fill both sockets, which (per the new `useItemOn` logic) triggers `sp.openMenu(...)` the moment the second socket fills — before `setEmployeeBound(true)` is ever called. NeoForge cannot deliver that menu-open payload to a GameTest mock player, and the whole test batch failed with "Payload neoforge:advanced_open_screen may not be sent to the client!".
- **Fix:** Rewrote the test to set up the already-bound altar state directly on the BE (`setHeldJobItem`/`setHeldSoulBlock`/`setEmployeeBound(true)`) instead of driving the fill-both-sockets interaction sequence, then exercises only the empty-hand `useWithoutItem` occupied-gate path via one `helper.useBlock` call.
- **Files modified:** src/main/java/com/cxmxrgo/secondshift/gametest/BindingAltarGameTests.java
- **Verification:** `./gradlew runGameTestServer` — 22/22 tests green.
- **Committed in:** 865d815 (Task 3 commit)

---

**Total deviations:** 3 auto-fixed (1 bug, 1 blocking/compile-order, 1 bug)
**Impact on plan:** All three were necessary to keep each task's own compile/test gate green; no scope creep beyond the plan's stated files_modified list.

## Issues Encountered
None beyond the deviations documented above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- The full dual-socket + occupancy + transient session-field contract on `SoulAltarBlockEntity` is now in place for 05-02 through 05-05 (candidate offer rolling, naming, trade selection, employee binding) to build against directly.
- `ProfessionResolver.fromItem` is the canonical profession-resolution entry point going forward; no remaining production or test caller references the retired `fromAbove`.
- Minor stale copy: the "Soul Mason" advancement description (`en_us.json`) still says "set a job-site block on top" — cosmetic, out of this plan's `files_modified` scope; worth a one-line fix whenever advancement copy is next touched (not blocking).
- `./gradlew runServer` (client-class-leak gate) passed — no client-only classes touched by this plan.

---
*Phase: 05-profession-resolution-trade-picker*
*Completed: 2026-09-05*
