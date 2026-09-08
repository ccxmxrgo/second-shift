---
phase: 07-progression-promotion-ritual
plan: 01
subsystem: gameplay
tags: [neoforge-events, villager, menu, trading-xp, gametest]

requires:
  - phase: 05-profession-resolution-trade-picker
    provides: BindingAltarMenu/Screen, TradeCandidateList, TradePoolCache
  - phase: 06-employee-traits-death-firing
    provides: EmployeeEvents periodic per-employee tick check, altar<->employee bidirectional link
provides:
  - Tier progression derived from vanilla's own VillagerData level vs EmployeeData.tier (no new field)
  - Auto-appended vanilla trade revert on the existing 40-tick periodic check
  - The PROG-03 "ready for promotion" signal (particles + action-bar/chat message)
  - PromotionRitualMenu/Screen — a "pick 2" picker reusing Phase 5's scrollable-list pattern
  - EmployeeManager.installPromotion — merges a promotion's chosen offers onto the existing set
  - TradeCandidateList toggle-select mode (client-side multi-select before a single Confirm send)
affects: [08-mod-owned-restock, 09-quarters-happiness]

tech-stack:
  added: []
  patterns:
    - "Derive transient game state from two numbers that already exist (vanilla level vs stored
       tier) instead of adding a persisted flag — avoids EmployeeData's already-full 6-component
       StreamCodec.composite ceiling entirely."
    - "'Revert, don't prevent' on an existing periodic tick check, rather than a new hook or a
       Mixin, for a vanilla behavior with no clean interception point."
    - "Pack a bounded small selection into a single int for an existing 'menu button' RPC instead
       of adding a new CustomPacketPayload — established by BindingAltarMenu in Phase 5, reused
       here for a 2-of-N selection instead of a 1-of-N one."

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/menu/PromotionRitualMenu.java
    - src/main/java/com/cxmxrgo/secondshift/client/screen/PromotionRitualScreen.java
    - src/main/java/com/cxmxrgo/secondshift/gametest/PromotionRitualGameTests.java
  modified:
    - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java
    - src/main/java/com/cxmxrgo/secondshift/event/EmployeeEvents.java
    - src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java
    - src/main/java/com/cxmxrgo/secondshift/event/EmployeeFiring.java
    - src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java
    - src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java
    - src/main/java/com/cxmxrgo/secondshift/client/screen/TradeCandidateList.java
    - src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java
    - src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java
    - src/main/java/com/cxmxrgo/secondshift/trade/TradePoolCache.java
    - src/main/java/com/cxmxrgo/secondshift/registry/ModMenus.java
    - src/main/java/com/cxmxrgo/secondshift/client/ClientModBusEvents.java
    - src/main/resources/assets/secondshift/lang/en_us.json

key-decisions:
  - "Promotable is derived (vanillaLevel > EmployeeData.tier()), never a persisted boolean — see
     07-CONTEXT.md D-01. Avoids a 7th EmployeeData field past Phase 6's already-full
     StreamCodec.composite 6-component ceiling."
  - "The Binding Altar's bind-time picker now rolls TIER 1's pool, not the profession's max tier.
     Phase 5 round-15 shipped 'grant a chosen max-tier trade immediately' as an EXPLICIT interim
     stopgap (see that class's own doc comment history) because leveling infrastructure didn't
     exist yet. Now that Phase 7 builds it, the honest version of the user's original 'grant only
     at max tier' balancing idea generalizes cleanly: every tier, including the profession's
     highest, is earned through the same Promotion Ritual mechanism instead of being a special
     case bolted onto bind time. See 07-CONTEXT.md D-01 and the memory note
     second-shift-phase7-tier-gated-trade-idea."
  - "'Pick 2' needed no new network payload. Selection toggles entirely client-side
     (TradeCandidateList's new toggle mode); one Confirm click packs up to two chosen indices into
     a single int (idxA*32+idxB, 31=no second pick) sent through vanilla's existing
     clickMenuButton RPC — the same one BindingAltarMenu already established for single-select.
     See 07-CONTEXT.md D-04."
  - "MenuProvider#createMenu runs BEFORE getDisplayName() in ServerPlayer#openMenu (verified
     against decompiled source) — SoulAltarBlockEntity's one-shot promotionRitualRequested flag
     is reset in getDisplayName(), not createMenu(), or the ritual's title packet would never
     actually fire (createMenu would still correctly build the right menu, but the open-screen
     packet's title would read as a plain Binding Altar)."

patterns-established:
  - "TradePoolCache#rollCandidatesForTier is now public — any tier can be rolled, not just tier 1
     or the profession's max, since Promotion Ritual needs an arbitrary in-between tier."

requirements-completed: [PROG-01, PROG-02, PROG-03, PROG-04]

duration: ~1.5h (autonomous overnight session, continuing directly from Phase 6's verified boot)
completed: 2026-09-08
---

# Phase 7: Progression & Promotion Ritual Summary

**Employees level through real vanilla trading XP; vanilla's own auto-appended trades are reverted
within one periodic check so the player never sees a trade they didn't choose; reaching a new tier
fires an unmissable particle+message signal; and right-clicking a bound altar with a promotable
employee opens a "pick 2" ritual, reusing Phase 5's scrollable-list picker pattern, that installs
the chosen trades onto the employee's existing set and advances its tier.**

## Performance

- **Duration:** ~1.5 hours of direct implementation (no concurrent-agent dispatch — Phase 6's
  collision lesson applied: this entire phase was built directly by the orchestrating session).
- **Started:** 2026-09-08 (continuing the `/gsd-autonomous` overnight session immediately after
  confirming Phase 6's clean client boot).
- **Completed:** 2026-09-08T05:35:00Z.
- **Tasks:** implemented directly (one focused set of changes, no formal multi-plan PLAN.md).
- **Files modified:** 11 modified, 3 created (2 production classes, 1 GameTest suite).

## Accomplishments

- PROG-01: tier progression rides real vanilla trade XP with zero new leveling logic — the
  employee's `VillagerData.getLevel()` IS the source of truth; `EmployeeData.tier()` only tracks
  what's been officially installed so far.
- PROG-02: vanilla's auto-appended trades are reverted within one 40-tick check of a level-up,
  reusing Phase 6's existing per-employee periodic tick instead of adding new ticking
  infrastructure or a Mixin.
- PROG-03: an unmissable `HAPPY_VILLAGER` particle burst + action-bar/chat message fires once per
  newly-reached tier (in-memory dedup, self-heals on restart rather than silently dropping a
  promotion the player hasn't seen yet).
- PROG-04: `PromotionRitualMenu`/`Screen` — the same enchanting-table-texture scrollable list
  pattern Phase 5 established, extended with a client-side toggle-select mode and a Confirm button
  for "pick 2", with zero new network payloads.
- The Binding Altar's own bind-time picker was corrected to roll tier 1 (not the max tier) now
  that there's a real progression path for every later tier, including the highest one — closing
  out the user's original balancing idea in its fully general form.
- 9 new GameTests, all passing alongside the full pre-existing 58 (67/67 total).
- Verified crash-free with a real `runClient` boot: both `BindingAltarScreen` and
  `PromotionRitualScreen` register cleanly, resource reload and texture-atlas loading complete
  with no new crash report.

## Files Created/Modified

- `menu/PromotionRitualMenu.java` (new) — the ritual's server-authoritative menu: rolls the
  employee's actual next tier, packs/decodes the "pick 2" confirm click, installs the promotion.
- `client/screen/PromotionRitualScreen.java` (new) — the ritual's screen: same texture/list
  pattern as `BindingAltarScreen`, plus a Confirm button wired to the toggle-select list.
- `gametest/PromotionRitualGameTests.java` (new) — 9 tests covering tier derivation, the revert,
  `installPromotion`'s merge semantics, the menu's tier/pool resolution, a full confirm round
  trip, a rejected malformed confirm, and the altar's non-promotable fallback path.
- `employee/EmployeeManager.java` — `installPromotion(villager, data, newTier, chosenOffers)`.
- `event/EmployeeEvents.java` — the tier-check-and-revert block in the existing periodic tick,
  `signalPromotable`, and the public `clearSignal` hygiene hook.
- `event/HarvesterEvents.java`, `event/EmployeeFiring.java` — call `EmployeeEvents.clearSignal` on
  their own employee-removal paths.
- `menu/BindingAltarMenu.java`, `client/screen/BindingAltarScreen.java` — bind-time roll switched
  from `rollMaxTierCandidates` to tier 1; doc comments updated to explain the supersession.
- `client/screen/TradeCandidateList.java` — new toggle-select constructor/mode, kept the original
  immediate-select behavior for `BindingAltarScreen`'s unaffected callsite.
- `content/blockentity/SoulAltarBlockEntity.java` — `promotionRitualRequested` one-shot flag,
  `createMenu`/`getDisplayName` branch on it.
- `content/block/SoulAltarBlock.java` — `useWithoutItem`'s occupied branch now checks
  promotability first and opens the ritual instead of just messaging.
- `trade/TradePoolCache.java` — `rollCandidatesForTier` made public.
- `registry/ModMenus.java`, `client/ClientModBusEvents.java` — registered
  `secondshift:promotion_ritual`.
- `lang/en_us.json` — new promotion-related keys.

## Decisions Made

See `key-decisions` in frontmatter above — full rationale for all of them is in `07-CONTEXT.md`
D-01 through D-06, written before implementation began this same session.

## Deviations from Plan

This phase had no formal multi-plan PLAN.md structure (implemented directly, matching Phase 6's
precedent, given the orchestrating session's judgment that a focused set of changes didn't warrant
one) and no deviations from the design captured in `07-CONTEXT.md`.

**One real design supersession worth flagging explicitly (not a deviation from THIS phase's own
plan, but from Phase 5's shipped behavior):** the Binding Altar's bind-time trade choice used to
roll the profession's max tier and grant it immediately (Phase 5 round-15, documented there as an
explicit interim stopgap). This phase changes that roll to tier 1, since the Promotion Ritual now
provides the real mechanism the stopgap was standing in for. See `key-decisions` above and
`07-CONTEXT.md` D-01 for the full reasoning — this was a deliberate design choice made by this
session under the `/gsd-autonomous` mandate ("use your own judgment on open design questions"),
not something requiring a user check-in, but it is called out here for full transparency about
what actually changed versus what Phase 5 shipped.

## Issues Encountered

**`MenuProvider#createMenu` vs `getDisplayName()` call order (caught before shipping):** verified
against decompiled `ServerPlayer#openMenu` source that `createMenu()` always runs before
`getDisplayName()` for the same open call. The one-shot `promotionRitualRequested` flag on
`SoulAltarBlockEntity` therefore resets itself inside `getDisplayName()`, not `createMenu()` — the
first draft had it backwards, which would have opened the correct menu but shown the wrong title.
Caught during implementation, not by a test (GameTests don't exercise the full `openMenu` packet
path — see the established pattern of constructing menus directly against a mock player).

**No concurrent-agent dispatch this phase.** Applying Phase 6's lesson directly: this entire phase
was implemented by the orchestrating session itself, in one pass, with no subagent dispatch at
any point.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Phase 8 (Mod-Owned Restock) and Phase 9 (Quarters & Happiness) can both proceed — neither
  depends on anything Phase 7-specific beyond the tier/progression shape now established.
- **For the user:** 2 items are `human_needed` (the promotion-ready signal's feel, and the
  Promotion Ritual screen's Confirm-button UX) — see `07-VERIFICATION.md` frontmatter for exactly
  what to check. Neither is expected to be broken; both have verified underlying logic and
  automated coverage plus a clean client boot.
- **Accepted, documented limitation:** up to a ~2-second window between a real level-up and this
  mod's revert where vanilla's own auto-picked trade is briefly live. See `07-CONTEXT.md`
  "Issues / accepted limitations" — a zero-window guarantee needs a Mixin into
  `Villager#updateTrades`, deliberately not reached for on a first pass.

---
*Phase: 07-progression-promotion-ritual*
*Completed: 2026-09-08*
