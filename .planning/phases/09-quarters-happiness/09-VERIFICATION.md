---
phase: 09-quarters-happiness
verified: 2026-09-08T06:00:00Z
status: human_needed
score: 4/4 success criteria code-verified; 3 items pending human playtest
human_verification:
  - test: "Build a real 3x3+ enclosed room with a door and a nearby stocked chest (bread, potato,
      carrot, or beetroot) near a bound employee's altar, and watch its happiness move to Happy
      over a few minutes; then remove the door (or empty the chest) and watch it drift back."
    expected: "The employee's trades get visibly cheaper (Happy) after the room+chest are in
      place; removing either condition makes prices drift back up over the next few checks."
    why_human: "GameTest verifies the underlying meter math and price adjustment directly via
      forced attachment values and a single posted tick — never exercised by actually building a
      room in a live client and letting real ticks accumulate over real time."
  - test: "Right-click a bound, non-promotable altar and read the happiness status message."
    expected: "A themed message names the current tier (Unhappy/OK/Happy) and, if not fully
      satisfied, which specific condition is missing (quarters or food)."
    why_human: "GameTest confirms the underlying computation is correct; the actual wording and
      whether it reads clearly as 'the altar GUI' (per HAPP-07, satisfied here via chat/action-bar
      rather than a new screen — see 09-CONTEXT.md D-07) is a feel/scope judgment for a human."
  - test: "Leave an employee Unhappy (no quarters/food) for the full configured
      happinessQuitThresholdTicks (default 12000 ticks / 10 real minutes) in a live client."
    expected: "The employee quits: drops a Soul Block, keeps existing as an ordinary villager
      (does not die), and its altar becomes re-bindable."
    why_human: "GameTest forces the streak counter directly to just under threshold and posts one
      synthetic tick to confirm the quit fires exactly at the boundary and reverts correctly —
      never exercised by actually waiting out the real 10-minute default in a live client."
---

# Phase 9: Quarters & Happiness Verification Report

**Phase Goal:** Employees need quarters and a stocked food chest tied to their altar; a discrete
Unhappy/OK/Happy state modulates emerald prices and restock speed, and sustained neglect makes an
employee quit.

**Verified:** 2026-09-08T06:00:00Z (autonomous overnight session, self-verified — no separate
verifier agent dispatched, continuing the precedent Phases 6-8 set; verification performed
directly by the implementing session, with the same rigor: independent GameTest re-run, not taken
on faith, plus a real `runClient` boot check confirming the generated config file).

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Giving an employee valid quarters plus a stocked chest moves it to Happy; removing the door or emptying the chest moves it toward Unhappy | ✓ VERIFIED (logic) / human_needed (real-time feel) | `QuartersChecker.hasValidQuarters` (bounded flood fill, door-adjacency check, 9-column footprint minimum) and `FoodChecker.tryConsumeFood` (nearby-Container scan for `Villager.FOOD_POINTS` items) both feed `EmployeeEvents`' 400-tick recompute, which steps the meter ±10 toward the appropriate extreme. GameTests `a_real_enclosed_3x3_room_with_a_door_is_valid_quarters`, `an_enclosed_room_without_a_door_is_invalid_quarters`, `an_open_unenclosed_space_is_invalid_quarters`, `a_stocked_chest_provides_food_and_is_drawn_from`, `an_empty_or_foodless_chest_provides_no_food`, `meeting_conditions_moves_the_meter_toward_happy_and_discounts_price`, and `missing_conditions_moves_the_meter_toward_unhappy_and_surcharges_price` all pass. |
| 2 | The altar GUI shows the current happiness tier and its cause | ✓ VERIFIED (logic) / human_needed (does chat/action-bar satisfy "GUI") | `SoulAltarBlock.useWithoutItem`'s "occupied, not promotable" branch computes the live tier and the specific missing condition (quarters vs. food vs. neither) and shows it via a themed message. Deliberately a chat/action-bar readout, not a new screen — see 09-CONTEXT.md D-07 for why this matches Phase 5's own established GUI-03 scope precedent rather than being an oversight. |
| 3 | Happy employees sell below vanilla emerald prices, OK at vanilla, Unhappy above vanilla; Unhappy employees restock slowly or not at all | ✓ VERIFIED | `Happiness.priceAdjustment()` (Happy=-1, OK=0, Unhappy=+1) is applied to every current offer via `MerchantOffer#setSpecialPriceDiff` — vanilla's OWN hero-of-the-village price-modulation field, confirmed to directly affect `getModifiedCostCount`. GameTest `meeting_conditions_moves_the_meter_toward_happy_and_discounts_price` confirms the adjustment matches the resulting tier. The Phase 8 restock check now reads the happiness attachment and skips restocking entirely while Unhappy; GameTest `an_unhappy_employee_never_restocks` confirms an exhausted offer stays exhausted past the configured interval while Unhappy. |
| 4 | An employee left Unhappy for a sustained period quits: drops its Soul Block, reverts to an ordinary unbound villager, releases its altar | ✓ VERIFIED | `EmployeeManager.quit` spawns a Soul Block `ItemEntity`, calls the existing `releaseAltar` (Phase 6), and removes every Second Shift attachment — deliberately NOT `employee.kill()`, since "reverts" (Phase 6's `EmployeeFiring.fire`, the ALTAR-06 path, is the one that kills). GameTest `a_sustained_unhappy_streak_makes_the_employee_quit` confirms the attachment is gone, the villager is still alive, and a Soul Block dropped; `happiness_leaving_unhappy_resets_the_streak` confirms the streak correctly resets the moment happiness leaves the Unhappy band (so recovering doesn't leave a stale near-quit counter). |

**Score:** 4/4 success criteria have a verified, working code path and automated coverage. 3 of the
4 additionally carry a `human_needed` item for the "does it feel right over real play time"
dimension GameTest cannot judge (see frontmatter).

### Required Artifacts

| Artifact | Expected | Status |
|----------|----------|--------|
| `employee/QuartersChecker.java` (new) | Bounded flood-fill enclosed-room + door detector | ✓ VERIFIED |
| `employee/FoodChecker.java` (new) | Nearby-container food scan, consuming + read-only variants | ✓ VERIFIED |
| `employee/Happiness.java` (new) | 3-band enum + price-adjustment mapping | ✓ VERIFIED |
| `registry/ModAttachments.java` | `HAPPINESS`, `UNHAPPY_STREAK_TICKS` (unsynced `AttachmentType<Integer>`) | ✓ VERIFIED |
| `config/ModConfig.java` | `happinessQuitThresholdTicks` | ✓ VERIFIED — confirmed generating correctly in `secondshift-common.toml` after a real `runClient` boot |
| `employee/EmployeeManager.java` | `quit(level, employee, data)` | ✓ VERIFIED |
| `event/EmployeeEvents.java` | 400-tick happiness recompute; restock check reads happiness | ✓ VERIFIED |
| `content/block/SoulAltarBlock.java` | Happiness+cause readout on altar interaction | ✓ VERIFIED |

### Key Link Verification

| From | To | Via | Status |
|------|-----|-----|--------|
| `EmployeeEvents`'s 400-tick recompute | `QuartersChecker`/`FoodChecker` | direct calls, gated on `data.altarPos()` presence | WIRED |
| Happiness recompute | Every current offer | `MerchantOffer#setSpecialPriceDiff(tier.priceAdjustment())` loop | WIRED |
| Happiness recompute | Phase 8 restock check | shared `ModAttachments.HAPPINESS` read | WIRED |
| Sustained-Unhappy streak | `EmployeeManager.quit` | direct call once `streak >= ModConfig.HAPPINESS_QUIT_THRESHOLD_TICKS` | WIRED |
| `SoulAltarBlock.useWithoutItem` | Happiness status message | direct `QuartersChecker`/`FoodChecker` read-only calls | WIRED |

## Issues Encountered

**Test-authoring coordinate-space bug, caught and fixed before any build was deployed** — see
`09-01-SUMMARY.md` Issues Encountered for the full account. No production code was affected.

**No concurrent-agent collision this phase** — applying Phase 6's lesson, this entire phase was
implemented directly by the orchestrating session with no subagent dispatch.

## Next Phase Readiness

- Phase 10 (Polish, Config & Invalid States) depends on Phases 2-9, all now complete — this is the
  final phase of the milestone.
- **For the user:** please playtest the 3 human_needed items above when you're back — none are
  expected to be broken (all have verified underlying logic and a clean client boot), but the
  quarters flood-fill's real-room feel, the happiness readout's clarity, and the quit timing are
  all genuinely new systems that only real play can fully validate.

---
*Verified: 2026-09-08T06:00:00Z*
*Verifier: Claude (self-verified, autonomous session)*
