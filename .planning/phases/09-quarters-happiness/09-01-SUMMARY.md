---
phase: 09-quarters-happiness
plan: 01
subsystem: gameplay
tags: [neoforge-events, villager, attachments, flood-fill, gametest]

requires:
  - phase: 05-profession-resolution-trade-picker
    provides: bind-time trade infrastructure
  - phase: 08-mod-owned-restock
    provides: ModConfig, EmployeeEvents periodic restock check
provides:
  - QuartersChecker (HAPP-01) — bounded flood-fill enclosed-room + door detection
  - FoodChecker (HAPP-02) — nearby-container food scan, consuming and read-only variants
  - Happiness enum + a 0-100 meter attachment (HAPP-03)
  - Price modulation via MerchantOffer#setSpecialPriceDiff (HAPP-04)
  - Restock pause while Unhappy (STOCK-04)
  - EmployeeManager.quit — sustained-Unhappy revert, not kill (HAPP-06)
  - A chat/action-bar happiness+cause readout on altar interaction (HAPP-07)
affects: [10-polish-config-invalid-states]

tech-stack:
  added: []
  patterns:
    - "A bounded flood fill (visited-cell cap doubling as the 'did this leak' signal) as the
       pragmatic, documented-limitation answer to 'detect an enclosed room' when no generic
       vanilla primitive exists for it."
    - "Reuse a vanilla entity's own public data (Villager.FOOD_POINTS) and its own existing price-
       modulation mechanism (MerchantOffer#setSpecialPriceDiff, vanilla's hero-of-the-village
       discount) instead of inventing new equivalents."
    - "A 'revert' (attachment removal only) is different from a 'kill' (EmployeeFiring's smite) —
       both are legitimate employee-exit paths with different vocabulary and different code."

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/employee/QuartersChecker.java
    - src/main/java/com/cxmxrgo/secondshift/employee/FoodChecker.java
    - src/main/java/com/cxmxrgo/secondshift/employee/Happiness.java
    - src/main/java/com/cxmxrgo/secondshift/gametest/HappinessGameTests.java
  modified:
    - src/main/java/com/cxmxrgo/secondshift/registry/ModAttachments.java
    - src/main/java/com/cxmxrgo/secondshift/config/ModConfig.java
    - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java
    - src/main/java/com/cxmxrgo/secondshift/event/EmployeeEvents.java
    - src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java
    - src/main/resources/assets/secondshift/lang/en_us.json

key-decisions:
  - "Quarters detection is a bounded 6-connected flood fill (400-cell cap) from the employee's own
     tether-home spawn point, not real structure recognition. A door is treated as a wall-boundary
     element (its collision shape stops the fill) whose adjacency to an interior cell is checked
     separately. Accepted limitation: cannot distinguish a real room from a large enclosed
     non-room space under the cap. See 09-CONTEXT.md D-01."
  - "Food uses vanilla's own PUBLIC Villager.FOOD_POINTS map via a Container-interface scan (any
     real storage block, not just Chest) within 6 blocks of the altar. A read-only
     hasFoodAvailable() exists separately from the consuming tryConsumeFood() specifically so
     HAPP-07's status readout never side-effects just from a player checking. See 09-CONTEXT.md
     D-02."
  - "The happiness meter is a persisted 0-100 Integer attachment (not an instantaneous derivation)
     so it moves gradually, matching the ROADMAP's own 'moves it to Happy... moves it toward
     Unhappy' language. See 09-CONTEXT.md D-03."
  - "Price and restock modulation reuse vanilla's OWN existing mechanisms —
     MerchantOffer#setSpecialPriceDiff (vanilla's hero-of-the-village discount) for price, and a
     direct skip on the Phase 8 restock check for STOCK-04 — rather than inventing new equivalents.
     See 09-CONTEXT.md D-04/D-05."
  - "HAPP-06's quit REVERTS, it does not kill: removing every Second Shift attachment
     (EMPLOYEE/RESTOCK_TIMER/HAPPINESS/UNHAPPY_STREAK_TICKS) is sufficient, since every Phase
     6/7/8/9 handler is already gated on hasData(EMPLOYEE). The villager keeps its
     trades/profession/name as ordinary vanilla leftover state. See 09-CONTEXT.md D-06."
  - "HAPP-07's 'altar GUI' requirement is satisfied via a chat/action-bar message on the existing
     altar interaction, not a new screen — consistent with Phase 5's own GUI-03 precedent (no
     altar surface yet re-displays bound-employee status). See 09-CONTEXT.md D-07."

patterns-established: []

requirements-completed: [HAPP-01, HAPP-02, HAPP-03, HAPP-04, HAPP-05, HAPP-06, HAPP-07, STOCK-04]

duration: ~1.5h (autonomous overnight session, continuing directly from Phase 8)
completed: 2026-09-08
---

# Phase 9: Quarters & Happiness Summary

**Employees need a real enclosed 3×3+ room with a door and a nearby stocked chest, tied to their
altar; a 0-100 happiness meter derived from those two conditions moves gradually between Unhappy/
OK/Happy, modulating emerald prices (via vanilla's own hero-of-the-village mechanism) and pausing
restock while Unhappy; and an employee left Unhappy for a sustained, configurable period quits —
reverting to an ordinary villager and freeing its altar, rather than dying.**

## Performance

- **Duration:** ~1.5 hours of direct implementation — the ROADMAP's own flagged "largest net-new
  chunk with the least research coverage," resolved with a documented design spike
  (`09-CONTEXT.md`) before any code was written, same discipline as every prior phase this
  session.
- **Started:** 2026-09-08, continuing the `/gsd-autonomous` overnight session immediately after
  Phase 8's commit/push.
- **Completed:** 2026-09-08T06:00:00Z.
- **Tasks:** implemented directly (no formal multi-plan PLAN.md — the design spike doubled as the
  planning document).
- **Files modified:** 6 modified, 4 created.

## Accomplishments

- HAPP-01: a real, working (if deliberately bounded) enclosed-room + door detector, built from
  first principles since vanilla has no generic primitive for it.
- HAPP-02: food availability reuses vanilla's own `Villager.FOOD_POINTS`, with a genuine
  consume-on-check ("draw from") semantic and a separate non-mutating read for status checks.
- HAPP-03/04/05: a real, gradually-moving happiness meter that visibly changes emerald prices
  (vanilla's own price-modulation field) and pauses restock while Unhappy.
- HAPP-06: a full, tested quit-and-revert sequence that correctly does NOT kill the villager.
- HAPP-07: happiness tier + specific cause surfaced via the altar interaction.
- 11 new GameTests, all passing alongside the full pre-existing 72 (83/83 total).
- Verified crash-free with a real `runClient` boot: `happinessQuitThresholdTicks` generates
  correctly in `secondshift-common.toml` alongside Phase 8's `restockIntervalTicks`.

## Files Created/Modified

- `employee/QuartersChecker.java` (new) — the bounded flood-fill room+door detector.
- `employee/FoodChecker.java` (new) — nearby-container food scan (`tryConsumeFood`/
  `hasFoodAvailable`).
- `employee/Happiness.java` (new) — the 3-band enum + price-adjustment mapping.
- `gametest/HappinessGameTests.java` (new) — 11 tests across quarters, food, band mapping, meter
  movement, price adjustment, restock pause, and the full quit sequence.
- `registry/ModAttachments.java` — `HAPPINESS` and `UNHAPPY_STREAK_TICKS` (both unsynced
  `AttachmentType<Integer>`).
- `config/ModConfig.java` — `happinessQuitThresholdTicks`.
- `employee/EmployeeManager.java` — `quit(level, employee, data)`.
- `event/EmployeeEvents.java` — the 400-tick happiness recompute (quarters+food → meter → price →
  streak → maybe-quit), and the restock check now reads happiness to decide pause.
- `content/block/SoulAltarBlock.java` — the "occupied, not promotable" branch now shows happiness
  tier + cause instead of a bare message.
- `lang/en_us.json` — happiness/quit-related keys.

## Decisions Made

See `key-decisions` in frontmatter above — full rationale for all of them is in `09-CONTEXT.md`
D-01 through D-07, written as this phase's design spike before implementation began.

## Deviations from Plan

None. This phase had no formal multi-plan PLAN.md structure (a written design-spike CONTEXT.md
served that role directly, matching the ROADMAP's own note that this phase specifically needed
one) and no deviations from that design once implementation started.

## Issues Encountered

**Coordinate-space bug caught by the test suite itself (not shipped):** the first draft of
`HappinessGameTests`'s room-building helper mixed GameTest-relative (x, z) coordinates with an
absolute Y coordinate, producing a room that wasn't actually where `QuartersChecker` was told to
look. Three tests failed on the first `runGameTestServer` run with a clear, specific failure list;
root-caused immediately (relative vs. absolute coordinate space, not a `QuartersChecker` logic
bug) and fixed by passing the relative interior Y consistently. Re-run confirmed 83/83 green. No
production code was ever wrong — this was purely a test-authoring bug, caught before any build was
deployed.

**No concurrent-agent dispatch this phase**, continuing Phase 6's lesson: implemented directly by
the orchestrating session throughout.

## User Setup Required

None automatically — but the user can now tune `happinessQuitThresholdTicks` in
`run/config/secondshift-common.toml` alongside Phase 8's `restockIntervalTicks`.

## Next Phase Readiness

- Phase 10 (Polish, Config & Invalid States) depends on Phases 2-9, all now complete — this is the
  final phase of the milestone.
- **For the user:** the quarters flood-fill's real-room feel, the happiness meter's pacing, and
  the sustained-quit timing have only been verified by GameTest + a clean client boot — see
  `09-VERIFICATION.md` Human Verification items for what to check when back.

---
*Phase: 09-quarters-happiness*
*Completed: 2026-09-08*
