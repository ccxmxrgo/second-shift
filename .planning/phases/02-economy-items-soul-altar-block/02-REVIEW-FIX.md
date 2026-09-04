---
phase: 02-economy-items-soul-altar-block
fixed_at: 2026-09-04T00:00:00Z
review_path: .planning/phases/02-economy-items-soul-altar-block/02-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 2: Code Review Fix Report

**Fixed at:** 2026-09-04
**Source review:** .planning/phases/02-economy-items-soul-altar-block/02-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (1 Critical, 3 Warning — Info findings out of scope)
- Fixed: 4
- Skipped: 0

Validation: `./gradlew build` SUCCESSFUL; `./gradlew runGameTestServer` — all 8 GameTests
GREEN (6 pre-existing ECON-02 tests plus 2 new regression tests added by this pass).

## Fixed Issues

### CR-01: Soul Altar is permanently non-droppable once placed

**Files modified:** `src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json` (new file)
**Commit:** ba39588
**Applied fix:** Created the `minecraft:mineable/pickaxe` block tag (1.21.1 singular
`data/minecraft/tags/block/` path) with `"replace": false` and both `secondshift:soul_altar`
and `secondshift:soul_block`. This makes a pickaxe the "correct tool" so
`ServerPlayerGameMode.destroyBlock` runs `playerDestroy` / the `soul_altar.json` loot table
and the altar drops itself. `soul_block` was added too (it is meant to be pickaxe-mined per
its `strength(2.0F)`), harmless since it lacks `requiresCorrectToolForDrops()` and drops
regardless. Verified by the new `soul_altar_needs_pickaxe_to_drop` GameTest
(`ItemStack#isCorrectToolForDrops` is now true for a pickaxe, false for a stick).

### WR-01: Harvester never loses durability

**Files modified:** `src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java`
**Commit:** 3ad681a
**Status:** fixed: requires human verification (design-intent + behavioral change)
**Applied fix:** Durability is a real design goal here — `HarvesterItem#getEnchantmentValue`
exists specifically so "Unbreaking/Mending are meaningful (D-07)" and `durability(250)` is
set deliberately — so option (a) from the review was taken rather than deleting the
durability language. In `onDamagePre`, after the lethal `setNewDamage`, the reap now costs
one durability point: `weapon.hurtAndBreak(1, reaper, EquipmentSlot.MAINHAND)` where
`weapon = source.getWeaponItem()` (verified against the 21.1.248 decompile: this overload
exists, and `getWeaponItem()` delegates to the attacker's live main-hand `ItemStack`, so the
real held Harvester is damaged). Guarded by a re-check that the weapon is a `HarvesterItem`
and the attacker is a `LivingEntity`. Human check: confirm the intended cost is 1/hit (not
2, and not gated on "was actually lethal") and that a broken Harvester mid-swing behaves
acceptably.

### WR-02: Instakill and guaranteed-Fragment drop are not atomic

**Files modified:** `src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java`
**Commit:** 30c98f3
**Status:** fixed: requires human verification (event-flow change)
**Applied fix:** The Soul Fragment is now spawned from a new `LivingDeathEvent` handler
(`onDeath`) via `level.addFreshEntity(new ItemEntity(...))`, which does not depend on the
`LivingDropsEvent` pipeline (gated by `doMobLoot` / `shouldDropLoot()`). The harvest FX moved
into that handler alongside the spawn. The `LivingDropsEvent` handler (`onDrops`) is kept
but reduced to `event.getDrops().clear()` — it now only strips vanilla drops and no longer
carries the Fragment. `onDeath` bails on `event.isCanceled()` (a cancelled death is not a
reap). Class javadoc updated to match. New `harvester_kill_villager_drops_fragment_with_domobloot_false`
GameTest exercises a `doMobLoot=false` world (rule restored after the assertion). Human
check: confirm spawning at `LivingDeathEvent` time (before `dropAllDeathLoot`) is acceptable
and that no double-Fragment path exists.

### WR-03: Charged altar removed by a non-player breaker voids the socketed Soul Block

**Files modified:** `src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java`
**Commit:** 37fb4b0
**Applied fix:** Added an `onRemove` override (verified signature: `protected void
onRemove(BlockState, Level, BlockPos, BlockState, boolean)` in 21.1.248). On any removal
where the block actually changes, if the BE is non-empty and `wasBrokenWhileCharged()` was
NOT set (i.e. not the deliberate D-04 player charged-break suppression), it drops the held
Soul Block via `Containers.dropItemStack(...)` and then clears the stack so a re-entrant
removal cannot double-drop. The player charged-break path is unaffected: `playerWillDestroy`
already clears the stack and sets `brokenWhileCharged`, so `onRemove` skips it. Empty altars
also skip (nothing to drop). `super.onRemove(...)` is still called last.

## Skipped Issues

None — all 4 in-scope findings were fixed.

## Notes

- Info findings (IN-01 through IN-06) were out of scope (`fix_scope: critical_warning`) and
  were not touched.
- Two GameTests were added (commit e3875ec) as regression coverage for CR-01 and WR-02.
  All 8 GameTests pass.
- `hurtAndBreak(int, LivingEntity, EquipmentSlot)`, `DamageSource#getWeaponItem()`,
  `BlockBehaviour#onRemove`, `Containers#dropItemStack`, and `ItemStack#isCorrectToolForDrops`
  signatures were each verified against the decompiled 21.1.248 sources before use.

---

_Fixed: 2026-09-04_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
