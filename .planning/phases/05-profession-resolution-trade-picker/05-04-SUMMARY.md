---
phase: 05-profession-resolution-trade-picker
plan: 04
subsystem: gameplay-mechanics
tags: [neoforge, minecraft-1.21.1, menu, villager-trades, gametest]

# Dependency graph
requires:
  - phase: 05-profession-resolution-trade-picker (Plan 05-01)
    provides: SoulAltarBlockEntity dual-socket contract (bothSocketsFilled/isEmployeeBound/candidatesRolled/candidateOffers/defaultName), ProfessionResolver.fromItem
  - phase: 05-profession-resolution-trade-picker (Plan 05-02)
    provides: TradePoolCache.rollTier1Candidates(ServerLevel, BlockPos, VillagerProfession)
provides:
  - "BindingAltarMenu one-time server-side roll-once wiring: TradePoolCache + EmployeeNames materialized onto the BE exactly once per bind session, gated on be.candidatesRolled()"
  - "BindingAltarMenu GUI-03 read contract: getCandidateOffers(), isAutoLocked(), getProfession(), getTier(), getDefaultName()"
affects: [05-05, 05-06, network-handler, real-screen]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Menu-ctor roll-once gate: server-only branch (!isClientSide) checks bothSocketsFilled && !isEmployeeBound && !candidatesRolled before calling TradePoolCache/EmployeeNames, so repeated menu-open never re-rolls"
    - "GUI-03 accessors re-resolve the BE fresh via ContainerLevelAccess#evaluate on every call (mirrors AltarSoulContainer's no-caching idiom) rather than caching the BE reference on the menu"

key-files:
  created: []
  modified:
    - src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java
    - src/main/java/com/cxmxrgo/secondshift/gametest/BindingAltarGameTests.java

key-decisions:
  - "GameTest setup for the fully-socketed-altar tests sets heldJobItem/heldSoulBlock directly on the BE (not via two real helper.useBlock interactions), continuing the same 05-01 pattern that avoids the real openMenu packet a GameTest mock player cannot receive"
  - "MerchantOffer has no equals() override, so the roll-once content-comparison test compares ItemStack.matches(...) on result/costA/costB plus maxUses/xp per offer rather than relying on List#equals"
  - "Test suite uses Blocks.LECTERN (Librarian) instead of the pre-existing helper's Cartography Table (Cartographer) so the auto-lock/roll-once tests exercise a real >2-sized tier-1 pool"

patterns-established: []

requirements-completed: [PICK-02, PICK-04, PICK-07, PICK-08, GUI-03]

# Metrics
duration: 20min
completed: 2026-09-05
---

# Phase 5 Plan 4: One-Time Trade Pool + Default-Name Wiring Summary

**`BindingAltarMenu`'s core constructor now rolls the real profession's tier-1 trade pool (via `TradePoolCache`) and a themed default name (via `EmployeeNames`) onto the block entity exactly once per bind session, and exposes the full GUI-03 read contract (`getCandidateOffers`, `isAutoLocked`, `getProfession`, `getTier`, `getDefaultName`) the real screen and network handler will consume.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-09-05T02:35:00Z (approx.)
- **Completed:** 2026-09-05T02:55:00Z (approx.)
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- `BindingAltarMenu`'s core constructor gains the seam between "the pool exists" (05-02) and "the pool is trustworthy server-side state" (05-05/05-06): a server-only branch (`!isClientSide && bothSocketsFilled() && !isEmployeeBound() && !candidatesRolled()`) resolves the profession from the socketed job item, calls `TradePoolCache.rollTier1Candidates` and `EmployeeNames.pickRandom` exactly once, and stores both onto the BE — reopening the menu (menu re-constructed) never re-rolls, per RESEARCH.md Finding 1's "generate once, store, reuse" policy and threat T-05-06's mitigation.
- The full GUI-03 read contract is now live on `BindingAltarMenu`: `getCandidateOffers()` (re-resolves the BE fresh per call, `List.of()` if unresolvable/not yet rolled), `isAutoLocked()` (true when the pool has 2 or fewer candidates), `getProfession()` (via `ProfessionResolver.fromItem` off the BE's held job item), `getTier()` (always `1` this phase — PROG-04's tier advancement is Phase 7), and `getDefaultName()` (`""` if unresolvable/not yet rolled).
- 4 new `BindingAltarGameTests` prove all 4 plan-specified behaviors: menu construction against a fully-socketed altar rolls a non-empty candidate list + non-blank default name; a second menu construction against the same BE does not change the stored candidate list contents; `isAutoLocked()` correctly reflects a hand-constructed 2-element vs. 3-element candidate list; `getProfession()`/`getTier()` resolve correctly off a socketed Lectern (Librarian). 30/30 GameTests green (26 pre-existing + 4 new).

## Task Commits

Each task was committed atomically:

1. **Task 1: One-time candidate + default-name materialization in BindingAltarMenu + GUI-03 accessors** - `e9bd9ed` (feat)
2. **Task 2: GameTest coverage for roll-once + auto-lock + accessor correctness** - `a6f5d1b` (test)

**Plan metadata:** (this commit)

## Files Created/Modified
- `src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java` - roll-once wiring in the core ctor; new `getCandidateOffers()`, `isAutoLocked()`, `getProfession()`, `getTier()`, `getDefaultName()`, and a private `resolveBlockEntity()` helper
- `src/main/java/com/cxmxrgo/secondshift/gametest/BindingAltarGameTests.java` - 4 new tests plus a `setupFullySocketedAltar` helper and an `offersEqual` content-comparison helper

## Decisions Made
- GameTest setup for the new tests sets `heldJobItem`/`heldSoulBlock` directly on the BE via setters rather than driving two real `helper.useBlock` interactions, consistent with the 05-01 precedent: completing both sockets through the real interaction path triggers `sp.openMenu(...)`, which the GameTest mock player cannot receive and crashes the test batch.
- `MerchantOffer` has no `equals()` override, so the roll-once "contents unchanged" assertion (Test 2) compares each offer's result/costA/costB via `ItemStack.matches(...)` plus `maxUses`/`xp`, rather than relying on list equality.
- Chose `Blocks.LECTERN` (Librarian) for the new tests instead of the existing helper's Cartography Table (Cartographer), since Librarian's real tier-1 pool has more than 2 listings — needed to exercise the "not auto-locked" branch meaningfully alongside the hand-constructed 2/3-element lists used for the isAutoLocked unit-style assertions.

## Deviations from Plan

None - plan executed exactly as written. The plan's suggested `MerchantOffer`/`ItemCost` constructor signatures were verified against the real 1.21.1 mapped jar (`build/moddev/artifacts/neoforge-21.1.248-merged.jar`) via `javap` before writing the tests, confirming `new MerchantOffer(ItemCost, ItemStack, int, int, float)` and `new ItemCost(ItemLike)` are the correct overloads — no deviation needed.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `BindingAltarMenu` now exposes the complete GUI-03 read contract (`getCandidateOffers`, `getDefaultName`, `isAutoLocked`, `getProfession`, `getTier`) that Plan 05-06's real screen renders from and Plan 05-05's network trust-boundary handler validates client selections against.
- The roll-once guard (`!be.candidatesRolled()`) is proven under GameTest to survive menu reconstruction without re-rolling, closing the risk flagged in RESEARCH.md Finding 1 (orphaned `SavedData` from a repeated Cartographer treasure-map roll) for the menu-open code path specifically.
- `./gradlew compileJava` and `./gradlew runGameTestServer` both pass (30/30 GameTests green). `./gradlew runServer` was not re-run this plan — no client-only classes touched.

---
*Phase: 05-profession-resolution-trade-picker*
*Completed: 2026-09-05*

## Self-Check: PASSED

All modified files and both task commit hashes verified present.
