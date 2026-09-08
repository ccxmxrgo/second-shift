---
phase: 05-profession-resolution-trade-picker
plan: 07
subsystem: ui
tags: [neoforge, enchantment-menu, object-selection-list, villager-trades]

requires:
  - phase: 05-profession-resolution-trade-picker (plans 01-06)
    provides: profession resolution, trade-pool rolling, employee bind/spawn logic
provides:
  - Binding Altar screen reusing vanilla's real EnchantmentMenu/background texture
  - Locked receipt slots showing consumed Soul Block + job item
  - A scrollable "career path" list of every listing the profession's highest tier offers
  - Immediate single-trade bind via vanilla's clickMenuButton RPC (no new network payload)
affects: [06-employee-traits-death-firing, 07-progression-promotion-ritual]

tech-stack:
  added: []
  patterns:
    - "Reuse a vanilla AbstractContainerMenu subclass (EnchantmentMenu) purely for its slot
       positions/background texture, without reusing its screen class, when the screen's
       inherited input handling would conflict with custom widgets"
    - "Sync a variable-count server-side list to the client via a generous fixed slot
       reservation (client and server always add the same slot count) rather than a truly
       dynamic slot count, which client/server can't agree on before any network round-trip"
    - "Route non-slot UI actions (a scrollable list row click) through vanilla's existing
       clickMenuButton RPC instead of a new custom network payload"

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/client/screen/TradeCandidateList.java
  modified:
    - src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java
    - src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java
    - src/main/java/com/cxmxrgo/secondshift/trade/TradePoolCache.java

key-decisions:
  - "The UI was redesigned five times after this plan was originally written, entirely in
     response to direct, iterative user feedback given live screenshots — not per the original
     05-06-PLAN.md custom-Screen design this checkpoint's steps describe. Final shipped design:
     a real vanilla EnchantmentMenu-derived menu (chosen specifically because the user asked for
     'the enchanting table GUI'), showing a scrollable list of every listing the profession's
     HIGHEST tier offers ('career path' picker), granted immediately on pick. See git log
     2026-09-08 for the full round-by-round history (rounds 10 through 15)."
  - "The original 2-trades pick-then-confirm mechanic was replaced with pick-1-immediately,
     an explicitly accepted tradeoff (EnchantmentMenu's click machinery has no multi-select
     concept) — confirmed with the user before implementing."
  - "The player-editable name field from the original design was dropped (round 6, prior to
     this checkpoint) — employees get an auto-generated default name."
  - "The 'guarantee the chosen trade only at the employee's max level' balancing idea the user
     proposed is deferred to Phase 7 (Progression & Promotion Ritual), which owns the leveling
     infrastructure this would require. See the saved memory note
     second-shift-phase7-tier-gated-trade-idea for full detail — surface it at Phase 7 discuss."

patterns-established:
  - "When extending a vanilla Screen conflicts with a real widget (ObjectSelectionList) because
     the vanilla class does hardcoded pre-widget-dispatch input handling, extend the more
     generic AbstractContainerScreen directly instead of the specific vanilla screen — verified
     safe here since nothing else from that vanilla screen was actually still in use."

requirements-completed: [ALTAR-02, "GUI-03 (amended scope — see 05-VERIFICATION.md resolution note)", PICK-05]

duration: ~6h across multiple live-iteration rounds (2026-09-08, spanning this session)
completed: 2026-09-08
---

# Phase 5: Profession Resolution & Trade Picker Summary

**Binding Altar reuses vanilla's real enchanting-table menu/texture, showing a scrollable "career path" list of every top-tier trade a profession offers, with locked receipt slots and immediate single-trade bind via vanilla's own clickMenuButton RPC**

## Performance

- **Duration:** ~6 hours of live, iterative redesign across a single continuous session
- **Completed:** 2026-09-08
- **Tasks:** 1 (this plan's single human-verify checkpoint), executed against a codebase that
  had already gone through 5 full redesign rounds since 05-06-SUMMARY.md was written
- **Files modified:** see key-files above (round-15 diff only; earlier rounds have their own
  commits — `565e3c1` through `1825e42`)

## Accomplishments
- Binding Altar screen genuinely reuses vanilla's enchanting-table texture and menu machinery
  (per explicit user request), not a hand-built lookalike — eliminating the exact class of
  pixel-coordinate bugs that consumed 9 rounds of the pre-round-10 custom-Screen design.
- Player-facing intuitiveness pass: every candidate shows its real cost and (for enchanted
  books) its real enchantment name inline, not just on hover.
- Genuine architecture fix found and applied before shipping to the user: `EnchantmentScreen`'s
  inherited `mouseClicked` would have silently corrupted scrolled-list clicks by calling
  `clickMenuButton` with a stale fixed index — resolved by extending `AbstractContainerScreen`
  directly instead.
- 44 GameTests cover the menu-side trust boundaries (bind-on-pick, double-pick race guard,
  out-of-range/locked-slot rejection, receipt-slot lock + no-duplication-on-close).

## Task Commits

This plan's single checkpoint task was satisfied by the cumulative work of 6 commits made
during this session in direct response to the user's own live testing and feedback (not a
single isolated task commit — see `git log` for the full sequence):

1. `189b1bd` — round-12: reuse vanilla's real enchanting table GUI
2. `fa3abc7` — round-13: locked receipt slots + Soul Fragment reroll
3. `b58f80d` — fix: trade-row Slots were never registered with the menu (crash fix)
4. `ad93745` / `4d36c84` / `d955cc3` / `c62af70` / `19f4712` — round-14: intuitiveness fixes
   (inline name, cost-in-name, reroll label, tooltip suppression, real enchantment name)
5. `1825e42` — round-15: career-path trade picker (scrollable list, max-tier pool)

## Files Created/Modified
- `src/main/java/com/cxmxrgo/secondshift/client/screen/TradeCandidateList.java` - real
  `ObjectSelectionList` widget showing every rolled candidate (icon + name + baked-in cost)
- `src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java` - now extends
  `AbstractContainerScreen<BindingAltarMenu>` directly; builds/positions the candidate list
- `src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java` - variable candidate count
  synced via a generous fixed slot reservation; `clickMenuButton` re-enabled with real bind logic
- `src/main/java/com/cxmxrgo/secondshift/trade/TradePoolCache.java` - added
  `rollMaxTierCandidates` (rolls the profession's highest registered tier)

## Decisions Made
See `key-decisions` in frontmatter above.

## Deviations from Plan

### This plan's exact verification steps no longer match the shipped UI

- **Found during:** Attempting to execute this plan's `how-to-verify` steps as literally written
- **Issue:** The steps describe a name-editable field, a "Confirm Hire" button, and a pick-2-
  then-confirm flow with a gold-border/checkmark selection state — all from the design in place
  when this plan was originally written (right after 05-06). That entire design was replaced
  across 5 further rounds of direct user-requested iteration (see git log), landing on the
  vanilla-enchanting-table-reuse design this summary documents.
- **Resolution:** Rather than execute stale steps against a UI that no longer exists, this
  summary documents the REAL verification state honestly:
  - **G-2 socket rendering** (Soul Block + job item both visible, no clipping) — confirmed
    working by the user via direct screenshots and explicit approval multiple times across
    rounds 10-14 of this session (e.g. "NICE, IT LOOKS AWESOME", "Looks nice, working properly").
  - **Binding Altar screen contents** (profession name, trades with real cost/enchantment info)
    — confirmed working by the user via direct screenshots and explicit approval for the
    round-14 design (inline names, baked-in cost, resolved enchantment names, locked receipt
    slots, tooltip behavior).
  - **A chosen trade displaying correctly in the employee's own real vanilla trade screen** —
    covered by GameTest (`clicking_a_candidate_via_click_menu_button_binds_exactly_one_employee`
    and related tests assert the employee spawns with exactly the chosen `MerchantOffer` and the
    altar reflects a successful bind), but **has not been explicitly re-confirmed by the user
    interactively opening the resulting employee's trade screen** in this session.
  - **The round-15 "career path" scrollable list itself** (the newest UI, built and deployed at
    the very end of this session as the user was going to bed) has ONLY been confirmed to boot
    without crashing (dev client loaded, world loaded, mod list resolved, no crash report) and
    to pass all 44 GameTests — it has **not yet been interactively confirmed** by the user
    actually scrolling the list, reading a row, and clicking one.
- **Committed in:** N/A — documentation only, no code change from this deviation.

---

**Total deviations:** 1 (plan steps stale relative to shipped design — documented above,
not auto-fixed, since it requires the user's own eyes, not a code change).
**Impact on plan:** The phase's underlying functionality (profession resolution, trade rolling,
bind-to-employee) is real, tested, and — for everything except the very last redesign round —
directly confirmed by the user. The one open item (round-15's scrollable list, visually) is a
pending "look at this when you're back" item, not a suspected bug; if it turns out broken,
{@code TradeCandidateList} and `BindingAltarScreen#init` are the two files to start with (a
handful of hardcoded row-area coordinates are the only remaining hand-picked numbers).

## Issues Encountered
None beyond the deviation documented above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 6 (Employee Traits, Death & Firing) and Phase 8 (Mod-Owned Restock) depend only on
  Phase 4 (already complete) and can proceed independently of this pending item.
- Phase 7 (Progression & Promotion Ritual) depends on this phase and should treat the
  "career path" picker as its foundation — see the saved memory note
  `second-shift-phase7-tier-gated-trade-idea` for the user's explicit design ask (guarantee the
  chosen trade only at max employee tier) before planning that phase.
- **Blocker/concern for the user's next session:** please open the altar once with the current
  build and confirm the scrollable career-path list renders and clicking a row still binds
  correctly — if you already did this after the last message in this conversation, this note is
  stale and Phase 5 can be marked fully closed.

---
*Phase: 05-profession-resolution-trade-picker*
*Completed: 2026-09-08*
