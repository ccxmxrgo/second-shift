---
phase: 05-profession-resolution-trade-picker
plan: 02
subsystem: gameplay-mechanics
tags: [neoforge, minecraft-1.21.1, villager-trades, gametest]

# Dependency graph
requires:
  - phase: 05-profession-resolution-trade-picker (Plan 05-01)
    provides: SoulAltarBlockEntity dual-socket contract, ProfessionResolver.fromItem
provides:
  - "TradePoolCache.rollTier1Candidates(ServerLevel, BlockPos, VillagerProfession) -> List<MerchantOffer>, a throwaway-villager-based, side-effect-aware tier-1 roll"
affects: [05-04, 05-05, trade-picker, menu-wiring, network-handler]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Throwaway, never-added-to-level villager as a pure-computation trading context (level() is set at construction, independent of addFreshEntity)"
    - "Skip-not-retry null handling for ItemListing#getOffer results (a null result is structural, not transient randomness)"

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/trade/TradePoolCache.java
    - src/main/java/com/cxmxrgo/secondshift/gametest/TradePoolCacheGameTests.java
  modified: []

key-decisions:
  - "List.of()/List.copyOf() immutable lists throw NullPointerException from contains(null) by JDK design (JEP 269 ImmutableCollections) — the no-null-leak GameTest asserts via Stream#anyMatch(Objects::isNull) instead of List#contains(null)"

patterns-established:
  - "TradePoolCache mirrors ProfessionResolver's static-utility shape (final class, private constructor, no mutable state) as the canonical pure-function idiom for this trade package"

requirements-completed: [PICK-02, PICK-05, PICK-08]

# Metrics
duration: 15min
completed: 2026-09-05
---

# Phase 5 Plan 2: Trade Pool Materialization Summary

**`TradePoolCache.rollTier1Candidates` rolls a profession's real vanilla tier-1 `VillagerTrades.ItemListing[]` pool into concrete `MerchantOffer`s via a throwaway, never-added villager, resolving the phase's LIGHT research spike into production code plus a 4-test GameTest suite.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-09-05T02:15:00Z (approx.)
- **Completed:** 2026-09-05T02:30:00Z (approx.)
- **Tasks:** 1
- **Files modified:** 2 (both new)

## Accomplishments
- `TradePoolCache.rollTier1Candidates` is now the single place this phase's picker (and every later plan) materializes a profession's real tier-1 trade pool: constructs a throwaway `Villager`, moves it to the altar position before rolling (sane origin for the Cartographer's treasure-map search per 05-RESEARCH.md Finding 1), sets its profession/level, rolls each tier-1 listing exactly once, skips (never retries) a null result, and never calls `addFreshEntity` — the throwaway never joins the level.
- 4 new `TradePoolCacheGameTests` prove the exact behaviors 05-RESEARCH.md Finding 1 flagged as risky: Librarian rolls include exactly one enchanted-book offer across 10 independent rolls (PICK-05, no full-enumeration bug); Cartographer rolls never throw and never leak a null into the returned list, bounded by the real vanilla pool size (PICK-08's null-risk profession); Farmer rolls produce concrete, non-empty offers (sanity check against stub/empty results); the throwaway villager never increases the level's entity count.
- All 26 GameTests in the suite (22 pre-existing + 4 new) pass under `./gradlew runGameTestServer`.

## Task Commits

Each task was committed atomically:

1. **Task 1: TradePoolCache.rollTier1Candidates + GameTest suite** - `85c8fbd` (feat)

**Plan metadata:** (this commit)

## Files Created/Modified
- `src/main/java/com/cxmxrgo/secondshift/trade/TradePoolCache.java` - pure static utility, `rollTier1Candidates(ServerLevel, BlockPos, VillagerProfession)`, no caching/memoization (session-lifecycle "roll once" concern is the caller's responsibility)
- `src/main/java/com/cxmxrgo/secondshift/gametest/TradePoolCacheGameTests.java` - 4-test GameTest suite covering the librarian enchanted-book-count, cartographer null/throw safety, farmer concrete-offer sanity, and never-adds-to-level behaviors

## Decisions Made
- `List.of()`/`List.copyOf()`'s `contains(null)` throws `NullPointerException` by JDK design (the `ImmutableCollections` implementations call `Objects.requireNonNull` internally on the search element) — discovered via a real GameTest failure during this plan's own verification run. Rewrote the no-null-leak assertion to use `offers.stream().anyMatch(Objects::isNull)` instead, which iterates elements without ever calling `contains` on the immutable list. `TradePoolCache` itself was correct throughout; the bug was entirely in the test's assertion code.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test assertion `offers.contains(null)` threw `NullPointerException`, failing `cartographer_roll_never_throws_and_never_leaks_null`**
- **Found during:** Task 1 (`./gradlew runGameTestServer` verification run)
- **Issue:** `TradePoolCache.rollTier1Candidates` returns an immutable list via `List.copyOf(...)`. The JDK's `ImmutableCollections` list implementations reject `null` arguments to `contains()` with a `NullPointerException` (per their Javadoc/JEP 269 design), so the test's own `!offers.contains(null)` assertion crashed with an NPE that looked, at first glance, like a bug in `TradePoolCache` — it was not; `TradePoolCache` never leaked a null element.
- **Fix:** Replaced the assertion with `offers.stream().anyMatch(Objects::isNull)` (asserted false), which correctly iterates elements without invoking the immutable list's `contains` null-check path.
- **Files modified:** src/main/java/com/cxmxrgo/secondshift/gametest/TradePoolCacheGameTests.java
- **Verification:** `./gradlew runGameTestServer` — 26/26 GameTests green after the fix.
- **Committed in:** 85c8fbd (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug, in test code only — no production-code changes)
**Impact on plan:** No scope creep; `TradePoolCache.java` was implemented exactly as planned and never needed correction. The single deviation was a test-authoring mistake caught by the plan's own mandatory verification step.

## Issues Encountered
None beyond the deviation documented above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `TradePoolCache.rollTier1Candidates` is ready for Plan 05-04 (menu wiring) and Plan 05-05 (network handler) to call exactly once per bind session, per the BE's `candidatesRolled()`/`setCandidateOffers` session guard from Plan 05-01.
- The class has zero mutable state and zero caching by design — later plans must own the "roll once" discipline; calling this method twice in one session would double any one-time side effects (e.g. the Cartographer's orphaned map `SavedData` allocation, per 05-RESEARCH.md Finding 1 / threat T-05-04).
- `./gradlew runServer` was not re-run this plan (no client-only classes touched); `./gradlew compileJava` and `./gradlew runGameTestServer` both pass.

---
*Phase: 05-profession-resolution-trade-picker*
*Completed: 2026-09-05*

## Self-Check: PASSED

All created files and the task commit hash verified present.
