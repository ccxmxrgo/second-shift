---
status: passed
phase: 03-menu-screen-harness-hard-gate
source: [03-VERIFICATION.md]
started: 2026-09-04T15:50:00Z
updated: 2026-09-04T18:00:00Z
---

## Current Test

[all tests complete — passed]

## Tests

### 1. Screen opens and renders correctly
expected: Place a real job-site block (e.g. a cartography table) on top of a Soul Altar, right-click it holding a Soul Block. The "Binding Altar" screen opens showing the exact title, the 176×166 panel texture (recessed slot, soul-cyan glow ring), one visibly recessed slot showing the socketed Soul Block, and the player inventory grid — no visual glitches, no missing texture.
result: pass — functionally correct. FOLLOW-UP: the job-site block sitting on top of the altar looks visually broken (see Gaps).

### 2. No-job / no-Soul-Block feedback
expected: Right-click a bare altar empty-handed, and right-click an altar with a Soul Block in hand but no (or the wrong) job block above it. In both cases: no screen opens, no crash, and the themed action-bar message is legible and reads correctly (HR-necromancer tone).
result: pass

### 3. Forced-close on invalidation
expected: With the Binding Altar screen open, break the altar (or the job block above it), or walk more than ~8 blocks away. The screen force-closes automatically and the correct themed closed-reason message (altar gone / job gone / too far) appears on the action bar exactly once.
result: pass

## Summary

total: 3
passed: 3
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

### G-2 — Job-site block on top of the altar looks visually broken (design, non-blocking)
A full-size vanilla job-site block (cartography table, blast furnace, etc.) placed on the altar's
narrow "waist-high pedestal" top (Phase 2 D-02, lectern/enchanting-table silhouette) reads as
floating/clipping — the proportions don't work. Not a Phase 3 correctness issue (all 4 success
criteria hold; `ProfessionResolver` correctly reads the block above); purely a look-and-feel
problem inherent to the current "place a real block on top" mechanic.

**Locked design decision for Phase 5 (ALTAR-02):** replace "place a real vanilla block on top" with
an item-socket mechanic matching the existing Soul Block socket — the player right-clicks the
job-site block (as an item) onto the altar, it is consumed into a second BE-stored slot, and a
custom `BlockEntityRenderer` draws it **hovering and slowly spinning above the altar** (same
technique as the enchanting table's floating book, and the same rendering approach already used
for the charged Soul Block — `BlockRenderDispatcher#renderSingleBlock`). This is a deliberate
scope decision, not a deferred idea to reconsider — see STATE.md Deferred Items G-2 for the full
rationale and implementation notes.

**Scope note:** this reworks parts of Phase 3's shipped code (`ProfessionResolver.fromAbove`,
`SoulAltarBlock.useItemOn`/`useWithoutItem`, `BindingAltarMenu.stillValid`, and the
`BindingAltarGameTests` that test block-placement) — it is NOT purely additive to Phase 5. Phase 3
remains closed and functionally correct as shipped; this rework happens when Phase 5 is planned.
