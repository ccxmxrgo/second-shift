---
phase: 07-progression-promotion-ritual
verified: 2026-09-08T05:35:00Z
status: human_needed
score: 4/4 success criteria code-verified; 2 items pending human playtest
human_verification:
  - test: "Trade with a fresh employee until it earns enough XP to reach Apprentice (10 XP — a
      couple of trades), and watch/listen for the promotion signal."
    expected: "HAPPY_VILLAGER particles appear above the employee and an action-bar + chat message
      names the employee and its new tier, the moment the level-up actually happens (within ~2
      seconds, per the periodic check's cadence)."
    why_human: "GameTest verifies the underlying revert-and-signal logic by directly setting
      VillagerData's level and posting a synthetic EntityTickEvent.Post — never exercised via a
      real trade-XP grind against a live client. The signal's timing/visibility 'feel' needs a
      human's judgment call."
  - test: "Right-click the bound, now-promotable altar empty-handed, pick trades in the
      Promotion Ritual screen (toggle rows, watch the Confirm button enable at the right count),
      and confirm."
    expected: "The ritual screen opens (enchanting-table texture, 'Promotion Ritual' title),
      clicking a row highlights it with a checkmark, the Confirm button's label updates
      ('Confirm (1/2)' etc.) and enables only once exactly pickCount rows are selected, and
      confirming closes the screen and leaves the employee tradeable with its new trade(s) added."
    why_human: "GameTest constructs PromotionRitualMenu directly and calls clickMenuButton with a
      pre-packed confirm id — never exercised through a real mouse click on the toggle-select
      list widget or the Confirm button in a live client. This is the phase's one genuinely new
      player-facing screen."
---

# Phase 7: Progression & Promotion Ritual Verification Report

**Phase Goal:** Employees level through normal vanilla trading XP, and the player hand-picks each
new tier's trades at the altar — never seeing a trade they did not choose.

**Verified:** 2026-09-08T05:35:00Z (autonomous overnight session, self-verified — no separate
verifier agent dispatched, continuing the precedent Phase 6 set given the earlier concurrent-agent
collision lesson; verification performed directly by the implementing session, with the same
rigor: independent GameTest re-run, not taken on faith, plus a real `runClient` boot check).

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Trading with an employee to the Apprentice threshold (and each later threshold) produces an unmissable "ready for promotion" signal | ✓ VERIFIED (logic) / human_needed (feel) | `EmployeeEvents.onEntityTick`'s existing 40-tick check now also compares `villager.getVillagerData().getLevel()` against `EmployeeData.tier()`; the moment it's newly greater, `signalPromotable` fires `HAPPY_VILLAGER` particles + an action-bar/chat message to every player within 32 blocks, deduped per-tier via an in-memory map so it doesn't spam every 2 seconds. GameTest `vanilla_auto_appended_offers_are_reverted_on_the_next_check` exercises the same code path (via a directly-posted `EntityTickEvent.Post`, matching Phase 6's established technique for periodic-check tests) and passes. |
| 2 | At no point before the ritual does the employee's trade list gain a trade the player did not choose; vanilla's auto-appended tier trades are reverted | ✓ VERIFIED | Same periodic check unconditionally calls `villager.setOffers(data.offers())` whenever a level-up is observed — idempotent, safe every 2 seconds until the ritual resolves it. GameTest confirms a synthetic vanilla-appended trade (a Compass) is stripped back to the original 1-offer set within one check, while `an_employee_still_at_its_installed_tier_is_never_touched` confirms an employee that hasn't leveled is never touched. |
| 3 | Right-clicking the altar with a promotable employee nearby opens the picker for the new tier, showing existing trades locked and the new tier's pool as "pick 2" | ✓ VERIFIED (logic) / human_needed (screen feel) | `SoulAltarBlock.useWithoutItem`'s occupied branch resolves the bound employee via its stored UUID and checks `vanillaLevel > data.tier()`; when true it sets `SoulAltarBlockEntity#requestPromotionRitual()` and opens the menu, which is the only path reaching `PromotionRitualMenu` (verified by `non_promotable_bound_employee_gets_the_plain_occupied_path`, which confirms the plain occupied path is still taken when not promotable). `PromotionRitualMenu` rolls exactly the employee's next tier's pool (`promotion_ritual_menu_rolls_the_employees_next_tier_pool`) and computes `pickCount = min(2, poolSize)`. Existing trades are never shown or touched by this screen at all — "locked" by omission, matching the established precedent that no altar surface yet re-displays a bound employee's current trades (05-VERIFICATION.md GUI-03). |
| 4 | Confirming installs the chosen trades, retains all prior tiers' trades, and advances the employee's tier | ✓ VERIFIED | `EmployeeManager.installPromotion` merges the chosen offers onto a COPY of the existing `data.offers()` list (never replaces it) and writes `tier = newTier`. GameTest `install_promotion_merges_new_offers_and_advances_tier` confirms both the original and new trade survive and the tier advances; `confirming_a_ritual_installs_the_chosen_trades_and_closes_the_menu` exercises the full menu-level round trip (roll → pack → `clickMenuButton` → verify); `a_malformed_confirm_click_never_installs_a_partial_promotion` confirms a forged/short selection is rejected outright, never partially applied. |

**Score:** 4/4 success criteria have a verified, working code path and automated coverage. 2 of the
4 additionally carry a human_needed item for the "does it feel right" / "does the new screen work
smoothly" dimension GameTest cannot judge (see frontmatter).

### Required Artifacts

| Artifact | Expected | Status |
|----------|----------|--------|
| `employee/EmployeeManager.java` | `installPromotion(villager, data, newTier, chosenOffers)` | ✓ VERIFIED |
| `event/EmployeeEvents.java` | Tier-check-and-revert in the existing periodic tick; `signalPromotable`; public `clearSignal` | ✓ VERIFIED |
| `menu/PromotionRitualMenu.java` (new) | Rolls the employee's next tier, packs/decodes "pick 2", installs the promotion | ✓ VERIFIED |
| `client/screen/PromotionRitualScreen.java` (new) | Enchanting-table texture, toggle-select list, Confirm button | ✓ VERIFIED — registered in `ClientModBusEvents`, confirmed loading cleanly in a real `runClient` boot |
| `client/screen/TradeCandidateList.java` | New toggle-select mode alongside the original immediate-select mode | ✓ VERIFIED — `BindingAltarScreen`'s existing usage unaffected (still single-select-immediate) |
| `content/blockentity/SoulAltarBlockEntity.java` | One-shot `promotionRitualRequested` flag routes `createMenu`/`getDisplayName` | ✓ VERIFIED — reset placement (in `getDisplayName`, not `createMenu`) confirmed correct against decompiled `ServerPlayer#openMenu` call order |
| `content/block/SoulAltarBlock.java` | `useWithoutItem` checks promotability before falling back to the plain occupied message | ✓ VERIFIED |
| `trade/TradePoolCache.java` | `rollCandidatesForTier` public | ✓ VERIFIED |
| `menu/BindingAltarMenu.java` | Bind-time roll switched to tier 1 | ✓ VERIFIED |

### Key Link Verification

| From | To | Via | Status |
|------|-----|-----|--------|
| `EmployeeEvents.onEntityTick` | `EmployeeManager.installPromotion`'s inputs | reads `vanillaLevel`/`data.tier()`, calls `villager.setOffers(data.offers())` | WIRED |
| `SoulAltarBlock.useWithoutItem` | `PromotionRitualMenu` | `be.requestPromotionRitual()` + `sp.openMenu(be, ...)` | WIRED |
| `PromotionRitualScreen`'s Confirm button | `PromotionRitualMenu.clickMenuButton` | `Minecraft#gameMode#handleInventoryButtonClick` with a packed id | WIRED |
| `PromotionRitualMenu.clickMenuButton` | `EmployeeManager.installPromotion` | direct call inside `confirmPromotion`'s `access.execute` lambda | WIRED |
| `HarvesterEvents.onDeath` / `EmployeeFiring.fire` | `EmployeeEvents.clearSignal` | direct call on every employee-removal path | WIRED |

## Issues Encountered

**`MenuProvider#createMenu` vs `getDisplayName()` ordering** — see `07-01-SUMMARY.md` Issues
Encountered for the full account. Caught and fixed during implementation, before any test run.

**No concurrent-agent collision this phase** — applying Phase 6's lesson, this entire phase was
implemented directly by the orchestrating session with no subagent dispatch.

## Next Phase Readiness

- Phase 8 (Mod-Owned Restock) and Phase 9 (Quarters & Happiness) can both proceed — neither
  depends on anything Phase 7-specific beyond the tier/progression shape now established.
- **For the user:** please playtest the 2 human_needed items above when you're back — neither is
  expected to be broken (both have verified underlying logic and a clean client boot), but the
  promotion signal's timing/visibility and the ritual screen's toggle-select/Confirm UX are
  genuinely new interactions that only a human can judge.

---
*Verified: 2026-09-08T05:35:00Z*
*Verifier: Claude (self-verified, autonomous session)*
