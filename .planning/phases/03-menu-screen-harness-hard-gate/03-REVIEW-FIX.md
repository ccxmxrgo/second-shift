---
phase: 03-menu-screen-harness-hard-gate
fixed_at: 2026-09-04T15:52:00Z
review_path: .planning/phases/03-menu-screen-harness-hard-gate/03-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 03: Code Review Fix Report

**Fixed at:** 2026-09-04T15:52:00Z
**Source review:** .planning/phases/03-menu-screen-harness-hard-gate/03-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (1 critical, 3 warning)
- Fixed: 4
- Skipped: 0

## Fixed Issues

### CR-01: Display-only altar slot is not enforced below the UI layer

**Files modified:** `src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java`
**Commit:** 9c53d20
**Applied fix:** Pushed the "empty or exactly one Soul Block" validation into
`SoulAltarBlockEntity#setHeldSoulBlock` itself (the alternative the review explicitly
offered), rather than only in `AltarSoulContainer#setItem`. This protects every current
and future caller of the setter — the Container, `SoulAltarBlock#useItemOn`, and any
future code — not just the one call site the review cited. Confirmed
`ModItems.SOUL_BLOCK_ITEM` is the correct registry holder name before writing the guard.
Verified via `./gradlew compileJava`, `./gradlew build`, and `./gradlew runGameTestServer`
(all 14 GameTests still pass).

### WR-01: `stillValid(Player player)` parameter shadows the unused `this.player` field

**Files modified:** `src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java`
**Commit:** 2e2aad0
**Applied fix:** Renamed the stored field from `player` to `owningPlayer` (constructor
assignment and class javadoc updated to match) so it can no longer be shadowed by
`stillValid(Player player)`'s parameter. Kept the field (per the review's D-12
forward-looking note) rather than removing it, since IN-04 (out of scope for this fix
pass) explicitly defers removal to a judgment call. Verified via `./gradlew compileJava`.

### WR-02: Self-check lang-key guardrail does not cover the menu's own title key

**Files modified:** `src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java`
**Commit:** c8babf9
**Applied fix:** Added `"container.secondshift.binding_altar"` to `EXTRA_LANG_KEYS`.
Confirmed the key already exists in `assets/secondshift/lang/en_us.json` (so this does not
newly break startup) before committing. Verified via `./gradlew compileJava` and
`./gradlew runGameTestServer` (game boots and all 14 tests pass, so the guardrail addition
does not trip on a missing key).

### WR-03: `getDrops` javadoc references a doc comment that was superseded but not removed

**Files modified:** `src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java`
**Commit:** 96bbce9
**Applied fix:** Reordered the two adjacent javadoc blocks so the "D-04 drop suppression"
doc now sits directly above `getDrops` (the method it describes) and the "WR-03 safety net"
doc sits directly above `onRemove`, with no gap between either doc block and its method.
Pure documentation move — no code logic touched. Verified via `./gradlew compileJava`.

## Skipped Issues

None — all in-scope findings were fixed.

---

_Fixed: 2026-09-04T15:52:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
