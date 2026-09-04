---
phase: 02-economy-items-soul-altar-block
reviewed: 2026-09-04T00:00:00Z
depth: standard
files_reviewed: 15
files_reviewed_list:
  - src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java
  - src/main/java/com/cxmxrgo/secondshift/SecondShift.java
  - src/main/java/com/cxmxrgo/secondshift/client/ClientModBusEvents.java
  - src/main/java/com/cxmxrgo/secondshift/client/render/SoulAltarRenderer.java
  - src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java
  - src/main/java/com/cxmxrgo/secondshift/content/block/SoulBlock.java
  - src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java
  - src/main/java/com/cxmxrgo/secondshift/content/item/HarvesterItem.java
  - src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java
  - src/main/java/com/cxmxrgo/secondshift/gametest/HarvesterGameTests.java
  - src/main/java/com/cxmxrgo/secondshift/registry/ModBlockEntities.java
  - src/main/java/com/cxmxrgo/secondshift/registry/ModBlocks.java
  - src/main/java/com/cxmxrgo/secondshift/registry/ModCreativeTab.java
  - src/main/java/com/cxmxrgo/secondshift/registry/ModItems.java
  - build.gradle
findings:
  critical: 1
  warning: 3
  info: 6
  total: 10
status: issues_found
---

# Phase 2: Code Review Report

**Reviewed:** 2026-09-04
**Depth:** standard
**Files Reviewed:** 15
**Status:** issues_found

## Summary

Phase 2 delivers the Harvester item, Soul Fragment, Soul Block, and the Soul Altar
block + block entity + charged renderer, plus a startup self-check and a GameTest
suite. The registration wiring, side isolation, persistence, and client-sync code are
sound and follow the CLAUDE.md "What NOT to Use" list correctly (NeoForge APIs,
`RegisterRenderers`, data components over stack NBT, no Mixins/ATs).

One blocker: the Soul Altar can never be recovered once placed, because it is marked
`requiresCorrectToolForDrops()` but belongs to no `minecraft:mineable/*` block tag, so
no tool is ever "correct" and `playerDestroy` never runs the loot table. This breaks
D-06 and the core "build the Soul Altar" loop.

Three warnings concern the Harvester's advertised durability being a no-op, the
non-atomic instakill/drop split, and a narrow data-loss path when a charged altar is
removed by something other than a player.

## Critical Issues

### CR-01: Soul Altar is permanently non-droppable once placed

**File:** `src/main/java/com/cxmxrgo/secondshift/registry/ModBlocks.java:41`
**Issue:**
`SOUL_ALTAR` is built with `.requiresCorrectToolForDrops()`, but there is no
`data/minecraft/tags/block/mineable/pickaxe.json` (or any other `mineable/*` tag) that
contains `secondshift:soul_altar` — the resource tree has no `tags/` directory at all.

In 1.21.1, `ServerPlayerGameMode.destroyBlock` only calls `block.playerDestroy(...)`
(which runs `dropResources` / the loot table) when
`player.hasCorrectToolForDrops(state)` is true. That method returns
`!state.requiresCorrectToolForDrops() || selected.isCorrectToolForDrops(state)`.
With the flag set and the block in no tool tag, `isCorrectToolForDrops` is `false` for
every tool (vanilla `Tool` components match block tags), so the altar **never drops
anything** — not even when mined "correctly". The `soul_altar.json` loot table, the
`SOUL_ALTAR_ITEM` BlockItem, and `SoulAltarBlock#getDrops` are all dead for the normal
empty-altar break path (ALTAR-07 / D-06).

This also silently defeats `SoulAltarBlock#getDrops`'s "otherwise defer to the
drops-self loot table" branch.

**Fix:** either add the block to the pickaxe tag:
```json
// src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
{
  "replace": false,
  "values": [
    "secondshift:soul_altar",
    "secondshift:soul_block"
  ]
}
```
(add a `needs_*_tool` tag too if a tier gate is intended), or drop the flag if any tool
should work:
```java
BlockBehaviour.Properties.of()
        .strength(3.5F)
        .noOcclusion()
        .sound(SoundType.DEEPSLATE_BRICKS)   // no requiresCorrectToolForDrops()
```

## Warnings

### WR-01: Harvester never loses durability — `durability(250)` and "Mending is meaningful" are both no-ops

**File:** `src/main/java/com/cxmxrgo/secondshift/content/item/HarvesterItem.java:29-38`, `src/main/java/com/cxmxrgo/secondshift/registry/ModItems.java:33-39`
**Issue:**
`HarvesterItem` extends plain `Item`. In 1.21.1, per-hit durability loss on attack is
implemented by `SwordItem` / `DiggerItem` / `TieredItem` overriding `hurtEnemy(...)`
(the base `Item.hurtEnemy` returns `false` and applies no damage). Because the class is
deliberately a plain `Item` and the kill is decided in an event rather than via the
weapon's attack path, the Harvester takes **zero** durability damage from harvesting
(or from anything else — plain items also don't take damage breaking blocks). The
javadoc's claims that this is "a durability item" and that "Unbreaking/Mending are
meaningful (D-07)" are false: the item never degrades, so it never breaks and Mending
has nothing to repair.

**Fix:** if durability is a real design goal, damage the stack explicitly in the
`LivingDamageEvent.Pre` (or a `LivingDeathEvent`) handler after confirming the reap,
e.g.:
```java
if (src.getEntity() instanceof ServerPlayer sp) {
    weapon.hurtAndBreak(1, sp, EquipmentSlot.MAINHAND);
}
```
Otherwise, drop `durability(250)` and the durability/Mending language from the javadoc
so the item is honestly unbreakable.

### WR-02: Instakill and guaranteed-Fragment drop are two independently-gated handlers — the D-08 guarantee is not atomic

**File:** `src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java:55-103`
**Issue:**
`onDamagePre` forces the kill; `onDrops` produces the Fragment. They share only a
predicate, not a transaction. `LivingDropsEvent` is fired from
`LivingEntity.dropAllDeathLoot`, which is gated by
`level.getGameRules().getBoolean(RULE_DOMOBLOOT)` and `shouldDropLoot()`. With
`doMobLoot=false` the villager is still instakilled but **no Soul Fragment drops** — a
silent violation of "guaranteed Soul Fragment on Harvester kill". Another mod
cancelling or consuming `LivingDropsEvent` is a second vector. The GameTest suite never
exercises a `doMobLoot=false` world, so this passes CI.

**Fix:** spawn the Fragment from a handler that does not depend on the loot pipeline —
e.g. in `LivingDeathEvent` (or right after `setNewDamage` once you know the hit is
lethal), `level.addFreshEntity(new ItemEntity(...))`, and keep a `LivingDropsEvent`
handler only to `clear()` vanilla drops. Add a GameTest with
`helper.getLevel().getGameRules()` set to `doMobLoot=false`.

### WR-03: Charged altar removed by a non-player breaker voids the socketed Soul Block but still drops the altar

**File:** `src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java:132-166`, `src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java:46`
**Issue:**
`brokenWhileCharged` is only ever set from `playerWillDestroy`. Any removal path that
runs `getDrops` without going through `playerWillDestroy` — modded block breakers,
`Level.destroyBlock` calls from other mods, some tooling — hits the
`super.getDrops(state, params)` branch: the altar block item drops normally while the
`heldSoulBlock` ItemStack in the BE is discarded with no drop and no compensation.
That is item loss, and it is inconsistent with the player-break contract (where both
are destroyed together). Explosions happen to be consistent only because the loot
table carries `survives_explosion`.

**Fix:** drop the socketed stack in `SoulAltarBlockEntity` on removal for paths that
are not the deliberate D-04 suppression — override `onRemove` to spawn the held Soul
Block as an `ItemEntity` when the BE is non-empty and `brokenWhileCharged` was not set:
```java
@Override
public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
    if (!state.is(newState.getBlock())
            && level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be
            && !be.isEmpty() && !be.wasBrokenWhileCharged()) {
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.getHeldSoulBlock());
    }
    super.onRemove(state, level, pos, newState, moved);
}
```

## Info

### IN-01: `DATA_VERSION` is written but never read

**File:** `src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java:33-34,75,82-89`
**Issue:** `saveAdditional` writes `KEY_DATA_VERSION`, but `loadAdditional` never reads
it, so it currently provides no migration capability — it is a write-only field. The
javadoc acknowledges this ("read back for future migrations") but a reader can't tell
the version scheme is inert.
**Fix:** either read it in `loadAdditional` (even just
`int v = tag.getInt(KEY_DATA_VERSION);` with a comment), or drop it until a migration
actually exists.

### IN-02: `transient` on `brokenWhileCharged` has no effect

**File:** `src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java:46`
**Issue:** `BlockEntity` is never Java-serialized; persistence is entirely controlled
by `saveAdditional`/`loadAdditional`. The `transient` keyword is misleading — it
implies a serialization mechanism that isn't in play.
**Fix:** remove `transient`; keep the "never persisted" comment.

### IN-03: GameTest class and `empty.nbt` ship in the distributed jar

**File:** `src/main/java/com/cxmxrgo/secondshift/gametest/HarvesterGameTests.java:1-147`, `build.gradle:57-60`
**Issue:** `HarvesterGameTests` lives in `src/main/java` (not a test source set) and
`data/secondshift/structure/empty.nbt` in `src/main/resources`, so both are packaged
into the release jar shipped to the CurseForge instance. Harmless at runtime
(`@GameTestHolder` only activates with `neoforge.enabledGameTestNamespaces`) but it is
dead weight and leaks test scaffolding to end users.
**Fix:** move gametests to a dedicated source set wired via `neoForge { unitTest {} }`
or an `additionalSourceSets` entry, or accept it and record the decision.

### IN-04: Charged-altar wisp particles are spawned from `render()`, not a tick

**File:** `src/main/java/com/cxmxrgo/secondshift/client/render/SoulAltarRenderer.java:57-93`
**Issue:** `render` runs once per frame, so `emitWisp`'s `random.nextInt(WISP_ROLL)`
roll is frame-rate dependent: the wisp rate scales with FPS and stops entirely when
the game is paused with the world still. `WISP_ROLL = 50` "average frames between
wisps" is not a stable cadence.
**Fix:** gate on `be.getLevel().getGameTime()` (e.g. emit on `gameTime % N == 0` with a
small per-position offset) so the rate is wall-clock stable, or move ambient particles
to `SoulAltarBlock#animateTick` like `SoulBlock` already does.

### IN-05: Self-check lang handler can throw before the unbound-registry aggregate message

**File:** `src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java:71-117`
**Issue:** `onLoadComplete` (unbound check) and `onLoadCompleteLangKeys` are separate
`@SubscribeEvent` methods on the same event with undefined relative order. If a holder
is unbound and the lang handler runs first, `holder.value()` (line 94) throws
`"Trying to access unbound value: ..."` — one entry at a time — instead of the
intended sorted `"Unbound registry entries: [...]"` list. The class docstring's whole
premise is the aggregated message.
**Fix:** filter with `.filter(DeferredHolder::isBound)` before `.map(holder -> holder.value())`
in `onLoadCompleteLangKeys`, or merge both checks into one handler that does the
unbound check before touching `.value()`.

### IN-06: `SOUL_ALTAR_BE` typed as raw `Supplier`, inconsistent with the other registries

**File:** `src/main/java/com/cxmxrgo/secondshift/registry/ModBlockEntities.java:27-29`
**Issue:** `ModItems`/`ModBlocks` expose `DeferredItem`/`DeferredBlock`, but
`SOUL_ALTAR_BE` is declared `Supplier<BlockEntityType<SoulAltarBlockEntity>>`, hiding
`DeferredHolder` API (`isBound()`, `getId()`) from any future direct consumer.
`ModRegistrySelfCheck` only works because it goes through
`BLOCK_ENTITIES.getEntries()`.
**Fix:** declare it as
`DeferredHolder<BlockEntityType<?>, BlockEntityType<SoulAltarBlockEntity>>` for parity.

---

_Reviewed: 2026-09-04_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
