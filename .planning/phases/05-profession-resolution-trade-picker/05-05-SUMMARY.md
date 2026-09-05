---
phase: 05-profession-resolution-trade-picker
plan: 05
subsystem: networking
tags: [neoforge-networking, custompacketpayload, streamcodec, trust-boundary, villager-trades, gametest]

# Dependency graph
requires:
  - phase: 05-profession-resolution-trade-picker (Plan 05-04)
    provides: "BindingAltarMenu GUI-03 read contract (getCandidateOffers, isAutoLocked, getProfession, getTier, getDefaultName) — the ONLY source of truth this plan's server validation trusts"
  - phase: 04-employee-attachment-spawn (Plan 04-03)
    provides: "BindEmployeePayload/ServerPayloadHandler atomic access().execute trust-boundary pattern this plan extends and replaces"
provides:
  - "EmployeeManager.bind(ServerLevel, BlockPos, VillagerProfession, MerchantOffers, String) — the real, non-random, non-placeholder bind signature"
  - "SelectTradesPayload — serverbound indices+name payload, the permanent replacement for the throwaway BindEmployeePayload"
  - "ServerPayloadHandler.handleSelectTrades + public validateIndices/sanitizeName — full GUI-02 trust-boundary implementation (bounds/dedup/auto-lock re-derivation, name sanitize/cap/fallback, atomic employeeBound guard set only after a successful bind)"
affects: [05-06, employee-leveling, phase-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "employeeBound set true ONLY after EmployeeManager.bind returns successfully, inside the same atomic access().execute lambda, before sp.closeContainer() — never before, to avoid a soft-locked altar with no employee spawned"
    - "Server-side trust-boundary validation (validateIndices/sanitizeName) exposed as public static pure functions on the controller class specifically so a cross-package GameTest suite can exercise them in isolation, without a real network round trip"
    - "A minimal hand-rolled IPayloadContext stub (only player() implemented, everything else throws) is sufficient to GameTest a payload handler directly, since NeoForge's IPayloadContext is a thin interface with a single method the handler actually calls"

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/network/SelectTradesPayload.java
    - src/main/java/com/cxmxrgo/secondshift/gametest/ServerPayloadHandlerGameTests.java
  modified:
    - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java
    - src/main/java/com/cxmxrgo/secondshift/gametest/EmployeeGameTests.java
    - src/main/java/com/cxmxrgo/secondshift/network/ServerPayloadHandler.java
    - src/main/java/com/cxmxrgo/secondshift/SecondShift.java
    - src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java

key-decisions:
  - "validateIndices/sanitizeName made public (not package-private as the plan's illustrative text implied), matching Phase 4's ServerPayloadHandler cross-package-visibility precedent — needed so ServerPayloadHandlerGameTests (gametest package) can call them directly to test the trust-boundary logic in isolation from a real network round trip"
  - "sanitizeName strips only the literal section-sign character and \\p{Cc} control characters, NOT full 2-character vanilla formatting codes (e.g. \"§cBadName\" sanitizes to \"cBadName\", not \"BadName\") — this is the plan's literal regex spec (`[§\\p{Cc}]`), verified correct against a live GameTest rather than assumed"
  - "ByteBufCodecs.collection(...) requires 3 explicit type arguments (B, V, C) on NeoForge 21.1.248, not 2 as the plan's illustrative sketch showed — a straightforward compile-correctness fix, no behavioral change"

patterns-established: []

requirements-completed: [ALTAR-04, ALTAR-05, PICK-03, PICK-04, PICK-06, PICK-07, GUI-02]

# Metrics
duration: 30min
completed: 2026-09-05
---

# Phase 5 Plan 5: Second Shift Trade Confirm Trust-Boundary Summary

**`SelectTradesPayload` + a fully rewritten `ServerPayloadHandler.handleSelectTrades` replace Phase 4's throwaway zero-field bind trigger with the real trust-boundary implementation: every client-claimed trade index is bounds/duplicate/count-checked against the server's own materialized candidate list (never the client's), a pool of ≤2 candidates is auto-selected server-side regardless of client intent (PICK-04), the submitted name is stripped/capped/defaulted, and `employeeBound` is only ever set after `EmployeeManager.bind` (now given its real 5-arg signature) returns successfully — closing the double-confirm race and the soft-lock risk in one atomic lambda.**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-09-05T02:45:00Z (approx.)
- **Completed:** 2026-09-05T02:57:27Z
- **Tasks:** 3
- **Files modified:** 8 (2 created, 6 modified — 1 deleted: `BindEmployeePayload.java`)

## Accomplishments
- `EmployeeManager.bind` gained its permanent 5-arg signature (`level, altarPos, profession, chosenOffers, name`) — the internal random profession pick (`BINDABLE_PROFESSIONS`) and hardcoded default-offer roll (`rollDefaultTier1Offers`) are deleted entirely, superseded by upstream callers (`TradePoolCache` via Plan 05-04, and this plan's `ServerPayloadHandler`). Spawn ordering discipline (profession → xp → offers → name → attachment → addFreshEntity) is preserved byte-for-byte.
- `SelectTradesPayload` (indices + name record) replaces the zero-field `BindEmployeePayload`, wired via `StreamCodec.composite` with a `ByteBufCodecs.collection(...).map(...)` int-array codec and a generous 64-char wire-level name cap (defense in depth alongside the real 32-char semantic cap).
- `ServerPayloadHandler.handleSelectTrades` implements RESEARCH Finding 3's full checklist in the exact specified order: `employeeBound` occupancy guard FIRST inside the atomic lambda (T-05-11), empty-socket guard, `validateIndices` against `menu.getCandidateOffers()` (never trusting client indices — T-05-07/08/09), `sanitizeName` (T-05-10), profession re-resolution, atomic dual-socket consume, then `EmployeeManager.bind` wrapped in try/catch with `employeeBound` set `true` only on success, logged clearly on failure (T-05-13 accepted risk).
- `BindingAltarScreen`'s Confirm button sends an interim placeholder `SelectTradesPayload` (first up-to-2 candidates, empty name) — explicitly throwaway per the plan's D-01 pattern, superseded by Plan 05-06's real name-field/candidate-row UI. The payload and handler themselves are permanent.
- 8 new `ServerPayloadHandlerGameTests` (7 validation-helper unit-style tests + 1 end-to-end double-confirm race test) plus 2 new/updated `EmployeeGameTests` prove every threat-register mitigation is real, not just documented. 38/38 GameTests green. `./gradlew compileJava` and `./gradlew runServer` (client-class-leak gate) both clean.

## Task Commits

Each task was committed atomically:

1. **Task 1: EmployeeManager's real bind signature** - `960263a` (feat)
2. **Task 2: SelectTradesPayload + ServerPayloadHandler trust-boundary rewrite + SecondShift/BindingAltarScreen wiring** - `1c5239c` (feat)
3. **Task 3: GameTest coverage for the full GUI-02 trust boundary** - `f380a2d` (test)

**Plan metadata:** (this commit)

## Files Created/Modified
- `src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java` - real 5-arg `bind` signature; deleted `BINDABLE_PROFESSIONS`/`rollDefaultTier1Offers`
- `src/main/java/com/cxmxrgo/secondshift/gametest/EmployeeGameTests.java` - updated all 5 pre-existing tests to the new signature; deleted the fixed-3-profession test; added exact-offers/exact-name/exact-profession coverage
- `src/main/java/com/cxmxrgo/secondshift/network/SelectTradesPayload.java` - new serverbound `(int[] indices, String name)` record
- `src/main/java/com/cxmxrgo/secondshift/network/ServerPayloadHandler.java` - `handleSelectTrades` full trust-boundary rewrite; public `validateIndices`/`sanitizeName` helpers
- `src/main/java/com/cxmxrgo/secondshift/network/BindEmployeePayload.java` - deleted
- `src/main/java/com/cxmxrgo/secondshift/SecondShift.java` - payload registration updated to `SelectTradesPayload`/`handleSelectTrades`
- `src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java` - Confirm button sends interim placeholder `SelectTradesPayload`
- `src/main/java/com/cxmxrgo/secondshift/gametest/ServerPayloadHandlerGameTests.java` - new: 7 validation-helper tests + 1 end-to-end double-confirm test

## Decisions Made
- Made `validateIndices`/`sanitizeName` `public` rather than package-private, since the plan's own file layout puts the exercising GameTest class in a different package (`gametest`, not `network`) — same cross-package-visibility necessity Phase 4 already established for `ServerPayloadHandler` itself.
- Verified `sanitizeName`'s regex (`[§\p{Cc}]`) behavior empirically via GameTest rather than assuming full vanilla-formatting-code stripping: it strips only the bare `§` character and control characters, leaving any following color-code letter (`§c` → `c`) — this matches the plan's literal spec, not a "smarter" 2-char code stripper, so no deviation was needed once the test's own (incorrect) expectation was corrected.
- Fixed `ByteBufCodecs.collection(...)`'s actual 3-type-argument signature on this NeoForge version (plan's sketch showed 2) — compile-correctness only, no behavioral change.

## Deviations from Plan

None architecturally. Two implementation-detail corrections surfaced during execution, both Rule 3 (blocking-issue) fixes:

**1. [Rule 3 - Blocking] `ByteBufCodecs.collection` requires 3 explicit type arguments**
- **Found during:** Task 2 (SelectTradesPayload compile)
- **Issue:** The plan's illustrative `ByteBufCodecs.<RegistryFriendlyByteBuf, Integer>collection(...)` sketch has 2 type args; the real method signature on NeoForge 21.1.248 requires `<B, V, C extends Collection<V>>`.
- **Fix:** Added the third `List<Integer>` type argument.
- **Files modified:** `src/main/java/com/cxmxrgo/secondshift/network/SelectTradesPayload.java`
- **Verification:** `./gradlew compileJava` clean.
- **Committed in:** `1c5239c` (Task 2 commit)

**2. [Rule 3 - Blocking] Made `validateIndices`/`sanitizeName` public instead of package-private**
- **Found during:** Task 3 (ServerPayloadHandlerGameTests)
- **Issue:** The plan directs "package-private static helper methods... testable in isolation by Task 3's GameTest" but Task 3's own file list places the test class in `com.cxmxrgo.secondshift.gametest`, a different package from `com.cxmxrgo.secondshift.network` — package-private visibility would make the methods uncallable from the test.
- **Fix:** Made both helper methods `public static`, mirroring the exact precedent Phase 4 already set for `ServerPayloadHandler`'s own class/method visibility (04-03 SUMMARY.md Decision).
- **Files modified:** `src/main/java/com/cxmxrgo/secondshift/network/ServerPayloadHandler.java`
- **Verification:** `./gradlew compileJava` + `./gradlew runGameTestServer` (38/38 green).
- **Committed in:** `1c5239c` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 3 — blocking compile/testability issues)
**Impact on plan:** Both fixes were necessary for the plan's own stated tasks to compile and be testable as specified. No scope creep, no weakening of the threat-model mitigations.

## Issues Encountered
- Two of the newly-written `ServerPayloadHandlerGameTests` initially asserted the wrong expected output for `sanitizeName` (assumed full vanilla-formatting-code stripping, e.g. `"§cBadName"` → `"BadName"`). Running `./gradlew runGameTestServer` caught both failures immediately; corrected the test expectations to match the plan's literal regex spec (`§cBadName` → `cBadName`) rather than weakening the implementation. Re-ran to green (38/38).

## User Setup Required
None - no external service configuration required. `./gradlew runServer` confirms no client-class leak; manual `runClient` verification of the full confirm → spawn loop with the interim placeholder selection is optional and not required to unblock Plan 05-06 (which replaces the placeholder anyway).

## Next Phase Readiness
- The full GUI-02 security-critical seam (SelectTradesPayload → ServerPayloadHandler → EmployeeManager.bind) is now real, permanent infrastructure — not throwaway. Plan 05-06 only needs to replace `BindingAltarScreen`'s interim placeholder-selection button logic (first-2-candidates, empty name) with the real name-field + click-to-toggle candidate-row UI; it sends the exact same `SelectTradesPayload` this plan already validates server-side.
- All 5 threat-register mitigations this plan owns (T-05-07 through T-05-11) are proven under GameTest, not just implemented: out-of-range rejection, duplicate rejection, over-cap rejection, auto-lock re-derivation (ignoring client selection entirely when candidateCount ≤ 2), and the double-confirm race closed by the atomic `employeeBound` guard.
- No blockers. `./gradlew compileJava`, `./gradlew runGameTestServer` (38/38), and `./gradlew runServer` (client-class-leak gate) all pass.

---
*Phase: 05-profession-resolution-trade-picker*
*Completed: 2026-09-05*

## Self-Check: PASSED

All modified/created files and all three task commit hashes verified present on disk / in git history.
