---
phase: 05-profession-resolution-trade-picker
plan: 06
subsystem: ui
tags: [minecraft-gui, objectselectionlist, editbox, container-screen, villager-trades]

# Dependency graph
requires:
  - phase: 05-profession-resolution-trade-picker (Plan 05-04)
    provides: "BindingAltarMenu GUI-03 read contract (getCandidateOffers, isAutoLocked, getProfession, getTier, getDefaultName)"
  - phase: 05-profession-resolution-trade-picker (Plan 05-05)
    provides: "SelectTradesPayload + ServerPayloadHandler trust-boundary (indices/name validated server-side regardless of what this screen sends)"
provides:
  - "TradeCandidateList — vanilla ObjectSelectionList-based candidate row widget with all 4 visual states, 2-cap reject-flash, and auto-locked display"
  - "BindingAltarScreen — full real 200x222 UI: name EditBox, candidate list, real Confirm wiring to SelectTradesPayload"
  - "ModRegistrySelfCheck's EXTRA_LANG_KEYS now covers every message.secondshift.*/gui.secondshift.* key (closes WR-04's recurring gap)"
affects: [05-07, phase-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ObjectSelectionList.Entry click-to-toggle with a per-entry tick-decremented reject-flash counter (no built-in tick hook on AbstractSelectionList.Entry — decremented once per render call, acceptable per CONTEXT.md's low-friction micro-interaction framing)"
    - "AbstractContainerScreen render-order fact (verified via javap bytecode disassembly of the 1.21.1 merged jar, not assumed): renderBg and addRenderableWidget widgets both render BEFORE the leftPos/topPos pose translate, so both must use absolute leftPos+x/topPos+y coordinates; renderLabels renders AFTER the translate, so it must use screen-relative coordinates. Mixing the two conventions in one override is a real bug source this plan verified against ground truth."
    - "Dependency-free PNG regeneration (stdlib zlib/struct, no PIL) — same technique as Phase 3, reused instead of introducing an image-library dependency"

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/client/screen/TradeCandidateList.java
  modified:
    - src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java
    - src/main/resources/assets/secondshift/textures/gui/binding_altar.png
    - src/main/resources/assets/secondshift/lang/en_us.json
    - src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java

key-decisions:
  - "MerchantOffer.getCostB() returns a plain ItemStack (empty if absent), not an Optional<ItemCost> as the plan's illustrative pseudocode implied — used ItemStack#isEmpty() instead of an Optional#isPresent() check. Compile-correctness fix, no behavioral change from the plan's intent."
  - "AbstractSelectionList's scrollbar-position hook on this NeoForge version is named getScrollbarPosition(), not scrollBarX() as the plan's illustrative sketch suggested — verified via javap against the actual merged jar rather than assumed, since a naming mismatch would silently fail to override (not compile-fail if the vanilla method were absent, but here it did fail to compile, catching it immediately)"
  - "Verified the AbstractContainerScreen render/translate ordering by disassembling the real 1.21.1 bytecode (javap -c) rather than relying on training-data recall, after noticing the existing (already-shipped, UAT-passed) Plan 05-05 renderBg code used absolute leftPos/topPos coordinates — confirmed renderBg and widget-render both happen before the pose translate (absolute coords required) while renderLabels happens after (relative coords required), and wrote the new renderLabels override accordingly"
  - "binding_altar.png regenerated at exactly 200x222 (not padded into a larger atlas like the original 256x256) — simpler blit (source region == full image) and no wasted canvas, matching the plan's literal imageWidth/imageHeight values"

patterns-established: []

requirements-completed: [PICK-03, PICK-04, PICK-07, PICK-08, GUI-03]

# Metrics
duration: 40min
completed: 2026-09-05
---

# Phase 5 Plan 6: Second Shift Binding Altar Real UI Summary

**Replaced the Binding Altar screen's Phase-4-style placeholder Confirm button with the full 05-UI-SPEC.md-contracted UI: a 200x222 canvas holding an editable name field, a scrollable `TradeCandidateList` implementing all 4 candidate-row visual states (unselected/hovered/selected/locked) with a 2-cap reject-flash micro-interaction, and a Confirm button that now sends the player's real selections through the already-hardened `SelectTradesPayload` trust boundary.**

## Performance

- **Duration:** ~40 min
- **Started:** 2026-09-05T05:20:00Z (approx.)
- **Completed:** 2026-09-05T05:45:00Z (approx.)
- **Tasks:** 3
- **Files modified:** 5 (1 created, 4 modified)

## Accomplishments
- `TradeCandidateList extends ObjectSelectionList<Entry>` renders every materialized tier-1 candidate as a single click-target row (icon, name, price, optional "(locked)" tag) with all 4 documented visual states distinctly colored per 05-UI-SPEC.md, and enforces the 2-selection cap with a 6-tick destructive-red reject-flash instead of silently replacing the oldest selection.
- Auto-locked pools (`isAutoLocked()` true, `candidateCount <= 2`) render every row pre-selected and non-interactive (`mouseClicked` returns `false` immediately) — matching D-04's "no-op, don't disable-gray-out" tone.
- `BindingAltarScreen` grew from the Phase 3 placeholder 176x166 to the UI-SPEC's real 200x222 canvas: a pre-filled, 32-char-capped name `EditBox`, the wired `TradeCandidateList`, a bold gold scale-1.5 profession Display label, a static muted-gray "Happiness: N/A" placeholder (GUI-03, real data deferred to Phase 9 per CONTEXT.md's explicit discretion note), and a PICK-08 empty-pool inline message ("Nothing to Offer" + exact UI-SPEC body copy in destructive red) for the zero-candidate case — the screen still opens, never crashes on an empty pool.
- Confirm's click handler now reads `TradeCandidateList#getSelectedIndices()` and the `EditBox`'s live text into a real `SelectTradesPayload`, completely replacing Plan 05-05's interim `Math.min(2, ...)`/empty-name placeholder — the server-side trust boundary (Plan 05-05) needed zero changes since it already re-derives/validates everything independent of client intent.
- Closed the WR-04 lang-guardrail gap the phase's own research flagged: every `message.secondshift.*`/`gui.secondshift.*` key in `en_us.json` is now covered by `ModRegistrySelfCheck.EXTRA_LANG_KEYS`, not just item/block `descriptionId`s.
- `./gradlew compileJava` green after every task; `./gradlew runServer` reached `Done (0.336s)!` cleanly with no guardrail exception (lang self-check + client-class-leak gate both pass).

## Task Commits

Each task was committed atomically:

1. **Task 1: TradeCandidateList widget (click-to-toggle rows, all 4 visual states)** - `042e9f0` (feat)
2. **Task 2: BindingAltarScreen real layout — name field, list wiring, real Confirm, 200x222 canvas** - `288dd4f` (feat)
3. **Task 3: Lang keys + full guardrail sweep (closes WR-04's recurring gap)** - `7eb0321` (feat)

**Plan metadata:** (this commit)

## Files Created/Modified
- `src/main/java/com/cxmxrgo/secondshift/client/screen/TradeCandidateList.java` - new `ObjectSelectionList<Entry>` candidate row widget (4 visual states, 2-cap reject-flash, locked-row no-op)
- `src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java` - real 200x222 UI: name EditBox, candidate list, profession Display label, empty-pool message, real Confirm wiring
- `src/main/resources/assets/secondshift/textures/gui/binding_altar.png` - regenerated at 200x222 (stdlib zlib/struct, no new dependency)
- `src/main/resources/assets/secondshift/lang/en_us.json` - 3 new keys (`name_label`, `trade_locked`, `empty_pool`)
- `src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java` - `EXTRA_LANG_KEYS` extended to cover every existing `message.secondshift.*`/`gui.secondshift.*` key

## Decisions Made
- Used `ItemStack#isEmpty()` for the `costB` presence check (the real `MerchantOffer.getCostB()` return type is `ItemStack`, not `Optional<ItemCost>`).
- Renamed the plan's illustrative `scrollBarX()` override target to the real 1.21.1 method name `getScrollbarPosition()`, found by direct `javap` inspection of the actual merged Minecraft/NeoForge jar rather than assumed from memory.
- Verified via bytecode disassembly (not recall) that `renderBg`/widget rendering happen before the `leftPos`/`topPos` pose translate (absolute coordinates required) while `renderLabels` happens after (relative coordinates required) — this directly explains why the pre-existing `renderBg` blit call already used `leftPos`/`topPos` literally, and why the new `renderLabels` override correctly uses small relative offsets (8, 24, 44, 160, etc.) instead.
- Regenerated `binding_altar.png` at the exact 200x222 canvas size (not an oversized atlas) since the UI-SPEC's `imageWidth`/`imageHeight` values are the full intended blit region.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `MerchantOffer.getCostB()` returns `ItemStack`, not `Optional<ItemCost>`**
- **Found during:** Task 1 (TradeCandidateList price-text rendering)
- **Issue:** The plan's action text describes checking `costB.isPresent()`, but the real `MerchantOffer` API (verified via `javap` against the actual 1.21.1 merged jar) exposes `getCostB()` as a plain `ItemStack` (empty when there is no second cost), not an `Optional`.
- **Fix:** Used `!offer.getCostB().isEmpty()` as the presence check.
- **Files modified:** `src/main/java/com/cxmxrgo/secondshift/client/screen/TradeCandidateList.java`
- **Verification:** `./gradlew compileJava` clean.
- **Committed in:** `042e9f0` (Task 1 commit)

**2. [Rule 3 - Blocking] `AbstractSelectionList`'s scrollbar-position hook is `getScrollbarPosition()`, not `scrollBarX()`**
- **Found during:** Task 1 (TradeCandidateList compile)
- **Issue:** The plan's illustrative code implied a `scrollBarX()` override; the real 1.21.1 method (verified via `javap`) is `protected int getScrollbarPosition()`. Using the wrong name compiled to a stray unused method with `@Override` failing outright.
- **Fix:** Renamed the override to `getScrollbarPosition()`.
- **Files modified:** `src/main/java/com/cxmxrgo/secondshift/client/screen/TradeCandidateList.java`
- **Verification:** `./gradlew compileJava` clean.
- **Committed in:** `042e9f0` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 3 — real-API-vs-plan-sketch compile-correctness fixes, found by verifying against the actual 1.21.1 merged jar via `javap` rather than assuming API shape)
**Impact on plan:** Both fixes were necessary for the plan's own stated tasks to compile as specified. No scope creep, no weakening of the UI-SPEC contract.

## Issues Encountered
- Before writing `BindingAltarScreen`'s `renderLabels` override, disassembled `AbstractContainerScreen.render()`'s bytecode (`javap -c`) to settle whether widget/renderBg coordinates are absolute or pose-translated-relative, since the plan's interfaces section didn't specify this and getting it wrong would have silently misplaced every new label. Confirmed empirically (not assumed) that `renderBg` and widget rendering are absolute (`leftPos`/`topPos`-based) while `renderLabels` is relative — consistent with, and explaining, the already-shipped Plan 05-05 `renderBg` code's use of literal `leftPos`/`topPos`.

## User Setup Required
None - no external service configuration required. Manual visual/interaction verification of the full screen (row click-to-toggle feel, reject-flash timing, empty-state rendering) is explicitly deferred to Plan 05-07 per this plan's own `<verification>` section (not GameTest-observable per 05-VALIDATION.md).

## Next Phase Readiness
- The full vertical slice (Plans 05-01 through 05-06) is now player-usable end to end: socket a job item + Soul Block, open the Binding Altar, see the resolved profession, edit the employee's name, click-to-toggle real tier-1 trade candidates (or observe the auto-locked/empty-pool states), and Confirm Hire sends the player's actual choices through the already-hardened `SelectTradesPayload` trust boundary from Plan 05-05.
- Plan 05-07 (manual verification / UAT) is unblocked — no code changes anticipated there, only human confirmation of feel/visuals per this plan's deferred verification note.
- No blockers. `./gradlew compileJava` and `./gradlew runServer` (lang guardrail + client-class-leak gate) both pass.

---
*Phase: 05-profession-resolution-trade-picker*
*Completed: 2026-09-05*

## Self-Check: PASSED

All modified/created files and all three task commit hashes verified present on disk / in git history.
