---
phase: 03-menu-screen-harness-hard-gate
reviewed: 2026-09-04T14:44:00Z
depth: standard
files_reviewed: 12
files_reviewed_list:
  - src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java
  - src/main/java/com/cxmxrgo/secondshift/SecondShift.java
  - src/main/java/com/cxmxrgo/secondshift/client/ClientModBusEvents.java
  - src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java
  - src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java
  - src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java
  - src/main/java/com/cxmxrgo/secondshift/gametest/BindingAltarGameTests.java
  - src/main/java/com/cxmxrgo/secondshift/menu/AltarSoulContainer.java
  - src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java
  - src/main/java/com/cxmxrgo/secondshift/menu/SoulSlot.java
  - src/main/java/com/cxmxrgo/secondshift/registry/ModMenus.java
  - src/main/java/com/cxmxrgo/secondshift/trade/ProfessionResolver.java
findings:
  critical: 1
  warning: 3
  info: 4
  total: 8
status: issues_found
---

# Phase 03: Code Review Report

**Reviewed:** 2026-09-04T14:44:00Z
**Depth:** standard
**Files Reviewed:** 12
**Status:** issues_found

## Summary

Reviewed the Binding Altar menu/screen/container-menu implementation: `SoulAltarBlock`,
`SoulAltarBlockEntity`, `BindingAltarMenu`, `AltarSoulContainer`, `SoulSlot`,
`BindingAltarScreen`, `ModMenus`, `ProfessionResolver`, plus the startup self-check and
GameTest suite.

The `stillValid`/`ContainerLevelAccess` wiring is solid: it delegates to
`AbstractContainerMenu.stillValid(access, player, block)` (the correct vanilla idiom), the
forced-close messaging correctly distinguishes altar-gone / job-gone / too-far, and the
GameTest suite (`BindingAltarGameTests`) exercises all three failure modes plus the
happy path server-side, matching SC4's acceptance criterion. `Dist.CLIENT` isolation is
also clean: `BindingAltarScreen` is referenced only from `ClientModBusEvents`
(`@EventBusSubscriber(..., value = Dist.CLIENT)`), and no common-code file (`menu/`,
`content/`) imports it or any other client-only class.

The one significant gap is that the "display-only slot" invariant (D-06) is enforced only
at the `Slot`/UI layer (`SoulSlot#mayPickup`/`mayPlace` return `false`) and by the
discipline of the single legitimate caller (`SoulAltarBlock#useItemOn`, which checks
`stack.is(SOUL_BLOCK_ITEM)` before writing). Neither `AltarSoulContainer#setItem` nor
`SoulAltarBlockEntity#setHeldSoulBlock` validate the incoming stack at all — the
container/BE layer will happily persist any item written into slot 0 by any other caller,
including vanilla interaction paths that write to a `Slot` without consulting
`mayPlace`. This is exactly the "not just at the UI layer" property the review was asked
to verify, and it does not hold end-to-end. See CR-01.

A secondary, self-inflicted gap: the mod's own lang-key self-check (`ModRegistrySelfCheck`,
whose entire purpose per its javadoc is to hard-abort on any missing/renamed lang key
before it reaches a player) does not include the new menu title key
(`container.secondshift.binding_altar`) introduced by this phase's `MenuProvider`. See WR-02.

## Critical Issues

### CR-01: Display-only altar slot is not enforced below the UI layer — any writer of the Container can corrupt the BE's held-item invariant

**File:** `src/main/java/com/cxmxrgo/secondshift/menu/AltarSoulContainer.java:65-71`, `src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java:79-81`

**Issue:** `SoulSlot#mayPickup`/`mayPlace` correctly return `false`, which blocks all of
vanilla's *click-routed* interactions (`ServerboundContainerClickPacket` pickup/place/
swap/drag/throw all consult `Slot#mayPickup`/`mayPlace` before touching the slot). However
`AltarSoulContainer#setItem(int, ItemStack)` forwards directly to
`SoulAltarBlockEntity#setHeldSoulBlock(ItemStack)`, and neither method validates the
stack — `setHeldSoulBlock` only guards against `null`, not against "is this even a Soul
Block". The read-only guarantee for slot 0 currently rests entirely on:
1. `Slot#mayPickup`/`mayPlace` gating the standard click-packet path, and
2. `SoulAltarBlock#useItemOn` being the only code that currently calls `setHeldSoulBlock`
   with a validated stack.

Vanilla's creative-mode "set slot" packet path (`ServerboundSetCreativeModeSlotPacket`,
handled server-side by writing directly into the currently open menu's `Slot` via
`Slot#set`/`Slot#setByPlayer`) does not route through `AbstractContainerMenu#doClick` and
is not guaranteed to consult `mayPlace` the way the ordinary click path does. Any player in
creative mode with the Binding Altar screen open is therefore a plausible vector for
writing an arbitrary item (any NBT/component payload, any count) into slot 0 while it is
open, silently corrupting the BE's "always empty or exactly one Soul Block" invariant that
every other consumer of this BE (the renderer, `SoulAltarBlock`'s own gates, and any future
phase reading `getHeldSoulBlock()`) relies on without re-checking. Independent of that
specific packet's exact behavior, the structural problem stands on its own: this is a
`Container` — a generic, widely-implemented vanilla interface — and nothing in this class
or the BE stops any future caller (a different menu, a mixin-free vanilla mechanic this
review didn't enumerate, a copy-pasted future container) from writing an unvalidated stack
here. Enforcing the invariant only at the `Slot` layer is not "end-to-end."

**Fix:** Validate the stack at the point where it enters persistent state, not only at the
UI layer:
```java
// AltarSoulContainer.java
@Override
public void setItem(int slot, ItemStack stack) {
    if (!(stack.isEmpty() || stack.is(ModItems.SOUL_BLOCK_ITEM.get()))) {
        return; // reject anything that isn't empty or a Soul Block
    }
    SoulAltarBlockEntity be = resolve();
    if (be != null) {
        be.setHeldSoulBlock(stack);
    }
}
```
or equivalently push the same guard into `SoulAltarBlockEntity#setHeldSoulBlock` itself, so
every current and future caller — not just this one container — is protected.

## Warnings

### WR-01: `stillValid(Player player)` parameter shadows the unused `this.player` field

**File:** `src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java:28,70`
**Issue:** `BindingAltarMenu` stores `this.player` in the constructor "for Plan 02 Task 3's
D-12 forced-close messaging; not used yet" (per the class javadoc), but the only method
that currently deals with forced-close messaging — `stillValid(Player player)` — takes a
`player` parameter that shadows the field for its entire body. `player.level()` and
`player.displayClientMessage(...)` inside `stillValid` both resolve to the parameter, never
the field. This is currently harmless (the parameter is always the correct player), but it
is a live footgun: a future edit intending to reach the stored field (e.g. once D-12 wiring
expands) will silently keep resolving to the parameter instead, and the shadowing makes
that mistake invisible at the call site.
**Fix:** Rename the field (e.g. `owningPlayer`) or the parameter, so the two are never
ambiguous within the same scope.

### WR-02: Self-check lang-key guardrail does not cover the menu's own title key

**File:** `src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java:61-68`
**Issue:** `ModRegistrySelfCheck`'s stated purpose (per its class javadoc) is to hard-abort
startup if any lang key used by the mod's UI is missing from `en_us.json`, specifically to
prevent "the raw `item.secondshift.*` key shown in the UI" failure mode (POL-03). This
phase introduces `SoulAltarBlockEntity#getDisplayName()`, which returns
`Component.translatable("container.secondshift.binding_altar")` — that key becomes the
Binding Altar screen's title bar text. It is not in `EXTRA_LANG_KEYS`, so if that key is
ever deleted or misspelled in `en_us.json`, `ModRegistrySelfCheck` will pass silently and
the game will show the raw key as the screen title instead of hard-aborting at startup —
exactly the class of bug this guardrail exists to catch.
**Fix:** Add `"container.secondshift.binding_altar"` to `EXTRA_LANG_KEYS`.

### WR-03: `getDrops` javadoc references a doc comment that was superseded but not removed

**File:** `src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java:197-213`
**Issue:** There are two adjacent javadoc blocks immediately above `getDrops` (lines
197-204 and 205-213) — the first describes "D-04 drop suppression" for `getDrops` itself,
and the second (labeled "WR-03 safety net for non-player removal") actually documents
`onRemove`, which follows. The first javadoc block is orphaned above the wrong method-doc
boundary (it reads as documentation for `getDrops`, which it is, but is immediately
followed by unrelated documentation for a different method with no intervening method
signature), making it easy to misattribute which prose describes which method on a future
skim. Not a functional bug, but worth tidying so plan-authored WR-* commentary doesn't get
confused with actual review findings (this document also mints WR-03, colliding in name
with the pre-existing in-code comment label).
**Fix:** Move the "WR-03 safety net" javadoc to sit directly above `onRemove`, with no
javadoc gap between it and the method it documents.

## Info

### IN-01: Fully-qualified inline type instead of import

**File:** `src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java:56,60`
**Issue:** `net.minecraft.world.inventory.Slot` is used fully-qualified inline twice
instead of being imported alongside the file's other `net.minecraft.world.inventory.*`
imports (`AbstractContainerMenu`, `ContainerLevelAccess` are imported normally).
**Fix:** `import net.minecraft.world.inventory.Slot;` and use the bare name.

### IN-02: `quickMoveStack` disables shift-click for the entire inventory, not just the altar slot

**File:** `src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java:88-91`
**Issue:** `quickMoveStack` unconditionally returns `ItemStack.EMPTY` for every slot index,
including the 36 player-inventory/hotbar slots. Because `SoulSlot#mayPickup` already
returns `false`, slot 0 never reaches `quickMoveStack` at all — vanilla's click handler
checks `mayPickup` before calling it. The practical effect of the current
`quickMoveStack` body is disabling the normal inventory<->hotbar shift-click shuffle for
the *player's own* slots while this menu is open, which is broader than what D-06 ("no
shift-click routing needed" for the altar slot) actually requires.
**Fix:** If the intent is genuinely "no shift-click functionality at all in this menu,"
document that explicitly; otherwise route indices 1-36 to the standard
`moveItemStackTo`-based player-inventory shuffle so shift-click keeps its normal vanilla
feel.

### IN-03: Forced-close message loses the "no job block at all" vs "unmapped block" distinction that `useWithoutItem` preserves

**File:** `src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java:73-81` vs `src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java:153-161`
**Issue:** `SoulAltarBlock#useWithoutItem` distinguishes "no job block at all"
(`message.secondshift.altar.no_job_block`) from "wrong/unmapped block above"
(`message.secondshift.altar.not_a_workstation`) using `PoiTypes.forState(...).isEmpty()`.
`BindingAltarMenu#stillValid`'s forced-close messaging collapses both cases into a single
`message.secondshift.altar.closed.job_gone` key. Purely cosmetic (both are valid,
already-defined message keys per D-11), but the two code paths solve the same problem with
different precision for no stated reason.
**Fix:** Either accept the coarser message for the close case (document why), or mirror
`useWithoutItem`'s `PoiTypes.forState(...).isEmpty()` check for parity.

### IN-04: `BindingAltarMenu.player` field is stored but never read

**File:** `src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java:28,49`
**Issue:** The field is assigned in the constructor and documented as "not used yet,"
reserved for a future D-12 messaging expansion. As written today it is dead state that
adds a constructor line and a stored reference with no current reader — combined with
WR-01's shadowing, it's easy to forget this field exists when the time comes to use it.
**Fix:** No action required if genuinely landing soon in a follow-up phase; otherwise
remove until it has a reader.

---

_Reviewed: 2026-09-04T14:44:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
