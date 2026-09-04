---
status: partial
phase: 03-menu-screen-harness-hard-gate
source: [03-VERIFICATION.md]
started: 2026-09-04T15:50:00Z
updated: 2026-09-04T15:50:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Screen opens and renders correctly
expected: Place a real job-site block (e.g. a cartography table) on top of a Soul Altar, right-click it holding a Soul Block. The "Binding Altar" screen opens showing the exact title, the 176×166 panel texture (recessed slot, soul-cyan glow ring), one visibly recessed slot showing the socketed Soul Block, and the player inventory grid — no visual glitches, no missing texture.
result: [pending]

### 2. No-job / no-Soul-Block feedback
expected: Right-click a bare altar empty-handed, and right-click an altar with a Soul Block in hand but no (or the wrong) job block above it. In both cases: no screen opens, no crash, and the themed action-bar message is legible and reads correctly (HR-necromancer tone).
result: [pending]

### 3. Forced-close on invalidation
expected: With the Binding Altar screen open, break the altar (or the job block above it), or walk more than ~8 blocks away. The screen force-closes automatically and the correct themed closed-reason message (altar gone / job gone / too far) appears on the action bar exactly once.
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
