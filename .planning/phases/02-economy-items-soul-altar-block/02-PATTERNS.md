# Phase 2: Economy Items & Soul Altar Block - Pattern Map

**Mapped:** 2026-09-04
**Files analyzed:** 13 Java (3 modified, 10 new) + ~20 resource files
**Analogs found:** 8 with a real in-repo analog / 13 Java files (the rest are greenfield — use RESEARCH.md § Code Examples)

> The Phase 1 skeleton is tiny: 4 Java files, an empty `en_us.json` (`{}`), `pack.mcmeta`, and
> three `.gitkeep` stubs under `data/secondshift/`. There is **no existing block, block entity,
> item subclass, renderer, game-bus handler, model, blockstate, recipe, loot table, or
> advancement** in the repo. Analogs therefore cover: the `DeferredRegister` holder shape, the
> constructor wiring block, the self-check stream, and the `@EventBusSubscriber` conventions.
> Everything domain-specific (item behavior, BE persistence, `useItemOn`, renderer, all JSON) is
> greenfield and maps to `02-RESEARCH.md` § Code Examples / § Architecture Patterns.

---

## Package layout note (planner decision)

The spawn prompt lists flat `content/HarvesterItem.java`; `02-RESEARCH.md` lines 216-251 and
`ARCHITECTURE.md` lines 105-112 use sub-packages (`content/item/`, `content/block/`,
`content/blockentity/`, `event/`, `client/render/`). Both are internally consistent. This map
uses the RESEARCH.md sub-package form; planner picks one and applies it uniformly. Nothing in
Phase 1 constrains this (only `registry/` and `client/` packages exist today).

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `registry/ModItems.java` (mod) | registry holder | registration | *itself* (Phase 1) | exact |
| `registry/ModBlocks.java` (new) | registry holder | registration | `registry/ModItems.java` | role-match (blocks vs items) |
| `registry/ModBlockEntities.java` (new) | registry holder | registration | `registry/ModItems.java` | role-match |
| `registry/ModCreativeTab.java` (new) | registry holder | registration + event-driven (`displayItems`) | `registry/ModItems.java` + RESEARCH.md 583-599 | role-match |
| `SecondShift.java` (mod) | entrypoint / wiring | registration | *itself* (Phase 1) | exact |
| `ModRegistrySelfCheck.java` (mod) | guardrail | batch validation | *itself* (Phase 1) | exact |
| `content/item/HarvesterItem.java` (new) | item (tool) | request-response (melee hit) | no analog — RESEARCH.md 602-618 | greenfield |
| `content/block/SoulBlock.java` (new) | block | event-driven (`animateTick` particles) | no analog — RESEARCH.md § Standard Stack | greenfield |
| `content/block/SoulAltarBlock.java` (new) | block (`EntityBlock`) | request-response (`useItemOn`) + event-driven (break) | no analog — RESEARCH.md 296-399 | greenfield |
| `content/blockentity/SoulAltarBlockEntity.java` (new) | block entity | file-I/O (NBT) + pub-sub (client sync) | no analog — RESEARCH.md 326-372 | greenfield |
| `event/HarvesterEvents.java` (new) | event handler (game bus) | event-driven | `ModRegistrySelfCheck.java` (subscriber shape only) | role-match (annotation pattern), greenfield (logic) |
| `client/render/SoulAltarRenderer.java` (new) | renderer (client-only) | transform (BE state → geometry) | no analog — RESEARCH.md § Arch Pattern 3 diagram | greenfield |
| `client/ClientModBusEvents.java` (mod) | client bus subscriber | registration | *itself* (Phase 1) | exact |
| `assets/.../lang/en_us.json` (mod) | resource (lang) | — | *itself* (`{}`) | exact (structure only) |
| `assets/.../models/item/*.json` (new ×4) | resource (model) | — | no analog — RESEARCH.md 432-442 | greenfield |
| `assets/.../models/block/*.json` (new ×2) | resource (model) | — | no analog — RESEARCH.md 432-442 | greenfield |
| `assets/.../blockstates/*.json` (new ×2) | resource (blockstate) | — | no analog — RESEARCH.md 436 | greenfield |
| `data/.../recipe/*.json` (new ×4) | data (recipe) | — | no analog — RESEARCH.md 525-556 | greenfield |
| `data/.../loot_table/blocks/*.json` (new ×2) | data (loot) | — | no analog — RESEARCH.md 438 | greenfield |
| `data/.../advancement/*.json` (new ×3) | data (advancement) | — | no analog — RESEARCH.md 558-581 | greenfield |

---

## Pattern Assignments

### `registry/ModItems.java` (modified — registry holder, registration)

**Analog:** itself, `src/main/java/com/cxmxrgo/secondshift/registry/ModItems.java` lines 13-21.

**Current file (the pattern to keep):**
```java
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SecondShift.MODID);

    public static final DeferredItem<Item> DEBUG_MARKER =
            ITEMS.registerSimpleItem("debug_marker", new Item.Properties());

    private ModItems() {}
}
```

**Change:** delete `DEBUG_MARKER` (and its Javadoc — the class Javadoc lines 8-12 explicitly says
"Delete when ModItems gets real content in Phase 2"). Add three holders. Keep `ITEMS`, keep the
private constructor, keep `SecondShift.MODID`.

- `HARVESTER` — use `ITEMS.registerItem("harvester", HarvesterItem::new, props)` (custom class,
  needs the `Function<Item.Properties, Item>` overload — CLAUDE.md API Surface §1, RESEARCH.md 611).
- `SOUL_FRAGMENT` — `ITEMS.registerSimpleItem("soul_fragment", props)` (same call shape as
  `DEBUG_MARKER`), props carries the foil component (D-12).
- `SOUL_BLOCK_ITEM` — `ITEMS.registerSimpleBlockItem("soul_block", ModBlocks.SOUL_BLOCK, props)`
  (CLAUDE.md API Surface §1). Creates a forward reference to `ModBlocks` — fine, both register
  from the same constructor.

**Harvester `Item.Properties` (from RESEARCH.md 611-619, all flagged for jar-verification):**
```java
new Item.Properties()
        .stacksTo(1)
        .durability(250)
        .attributes(SwordItem.createAttributes(Tiers.STONE, 0, -2.8f))  // ~3 dmg; tune per D-08
        .enchantable(15);   // fallback: .component(DataComponents.ENCHANTABLE, new Enchantable(15))
```

---

### `registry/ModBlocks.java` (new — registry holder, registration)

**Analog:** `registry/ModItems.java` lines 13-21 (holder shape); CLAUDE.md API Surface §1 for the
`DeferredRegister.Blocks` calls.

**Copy this structure:**
```java
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SecondShift.MODID);

    public static final DeferredBlock<Block> SOUL_BLOCK = BLOCKS.registerBlock("soul_block",
            SoulBlock::new,
            BlockBehaviour.Properties.of()/* ...lightLevel(s -> 7), sound, strength... */);

    public static final DeferredBlock<SoulAltarBlock> SOUL_ALTAR = BLOCKS.registerBlock("soul_altar",
            SoulAltarBlock::new,
            BlockBehaviour.Properties.of()/* ...noOcclusion(), strength(3.5f), requiresCorrectToolForDrops()... */);

    private ModBlocks() {}
}
```
`registerBlock(String, Function<BlockBehaviour.Properties, ? extends Block>, Properties)` per
CLAUDE.md API Surface §1. Private constructor + `SecondShift.MODID` exactly as `ModItems`.

---

### `registry/ModBlockEntities.java` (new — registry holder, registration)

**Analog:** `registry/ModItems.java` lines 13-21 (holder shape); RESEARCH.md line 118 for the
builder call.

```java
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SecondShift.MODID);

    public static final Supplier<BlockEntityType<SoulAltarBlockEntity>> SOUL_ALTAR_BE =
            BLOCK_ENTITIES.register("soul_altar",
                    () -> BlockEntityType.Builder.of(SoulAltarBlockEntity::new, ModBlocks.SOUL_ALTAR.get()).build(null));

    private ModBlockEntities() {}
}
```
Note the generic `DeferredRegister.create(Registries.X, MODID)` form (not a `.Items`/`.Blocks`
helper) — same as the creative-tab register below.

---

### `registry/ModCreativeTab.java` (new — registry holder, registration + event-driven population)

**Analog:** `registry/ModItems.java` holder shape + **RESEARCH.md lines 583-599** (near-complete
implementation).

```java
public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SecondShift.MODID);

public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.secondshift.main"))
        .icon(() -> new ItemStack(ModItems.SOUL_BLOCK_ITEM.get()))
        .displayItems((params, output) -> {
            output.accept(ModItems.HARVESTER.get());
            output.accept(ModItems.SOUL_FRAGMENT.get());
            output.accept(ModItems.SOUL_BLOCK_ITEM.get());
            output.accept(ModBlocks.SOUL_ALTAR.get());
        })
        .build());
```
Title string / icon / id / ordering are D-14 discretion. `displayItems` alone is sufficient — the
`BuildCreativeModeTabContentsEvent` game-bus path is optional (RESEARCH.md A10).

---

### `SecondShift.java` (modified — entrypoint / wiring)

**Analog:** itself, `src/main/java/com/cxmxrgo/secondshift/SecondShift.java` lines 28-47.

**Current constructor (the "one visible block" — lines 28-37):**
```java
public SecondShift(IEventBus modBus, ModContainer container) {
    LOGGER.info("[SecondShift] loading {} on NeoForge", container.getModInfo().getVersion());

    // Register every DeferredRegister on the mod bus here, in one visible block
    // (D-08/D-10). Each new registry/Mod* class MUST be added here and to
    // ModRegistrySelfCheck.
    ModItems.ITEMS.register(modBus);

    modBus.addListener(this::commonSetup);
}
```

**Change:** extend the one visible block to:
```java
    ModItems.ITEMS.register(modBus);
    ModBlocks.BLOCKS.register(modBus);
    ModBlockEntities.BLOCK_ENTITIES.register(modBus);
    ModCreativeTab.TABS.register(modBus);
```
Plus register the game-bus Harvester handlers. Two options (Claude's discretion in D-15 context):
either `NeoForge.EVENT_BUS.register(HarvesterEvents.class)` here, or make `HarvesterEvents` a
`@EventBusSubscriber(modid = SecondShift.MODID)` class (game bus is the default) — the latter
matches how `ModRegistrySelfCheck` self-registers (line 26) and needs **no** constructor line.
Recommend the annotation form for consistency with the existing two subscriber classes.

**`commonSetup` (lines 39-47):** currently logs registered item ids. Extend the log to include
block / BE / tab counts if useful (D-context "Update the `commonSetup` log ... if useful"), or
leave. Keep the `event.enqueueWork(...)` wrapper (main-thread safety).

---

### `ModRegistrySelfCheck.java` (modified — guardrail, batch validation)

**Analog:** itself, `src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java` lines 41-55.

**Current stream (lines 43-51):**
```java
List<String> unbound = Stream.of(
                // D-10: add every registry/Mod* DeferredRegister to this
                // Stream.of(...) as later phases introduce them.
                ModItems.ITEMS)
        .flatMap(dr -> dr.getEntries().stream())
        .filter(holder -> !holder.isBound())
        .map(holder -> holder.getId().toString())
        .sorted()
        .toList();
if (!unbound.isEmpty()) {
    throw new IllegalStateException("Unbound registry entries: " + unbound);
}
```

**Change:** `Stream.of(ModItems.ITEMS, ModBlocks.BLOCKS, ModBlockEntities.BLOCK_ENTITIES, ModCreativeTab.TABS)`.
All four are `DeferredRegister<?>` and expose `getEntries()` — the `.flatMap` downstream is
type-agnostic, so only the `Stream.of(...)` line changes. Nothing else in the file moves.

**Optional (Open Question 3, planner's call):** add a second `FMLLoadCompleteEvent` handler (or
extend this one) asserting every registered object's `descriptionId` resolves to an `en_us.json`
key — Phase 2 is the first phase with real translated content. ~15 lines. Class Javadoc lines 24-25
explicitly scoped this out for Phase 1 ("no descriptionId / lang-key resolution check in Phase 1").

---

### `content/item/HarvesterItem.java` (new — item / tool)

**Analog:** none in repo. Use **RESEARCH.md lines 602-608**:
```java
public class HarvesterItem extends Item {
    public HarvesterItem(Properties props) { super(props); }
    // no attack override needed — a non-SwordItem never triggers the sweep
}
```
Constructor signature `(Item.Properties)` must match the `HarvesterItem::new` method reference in
`ModItems.registerItem`. Do **not** extend `SwordItem` (anti-pattern, RESEARCH.md 403 — drags in
the sweep). The class is essentially a marker; all behavior lives in `HarvesterEvents`.

---

### `content/block/SoulBlock.java` (new — block)

**Analog:** none. Plain `extends Block` with a constructor taking `BlockBehaviour.Properties`.
Override `animateTick(BlockState, Level, BlockPos, RandomSource)` for the client-side ambient
soul-wisp particles (D-13; RESEARCH.md line 137, signature flagged A9). Light level is set on the
`Properties` in `ModBlocks`, not here. No loot logic here (loot table JSON handles drops-self).

---

### `content/block/SoulAltarBlock.java` (new — block, `EntityBlock`)

**Analog:** none. Use **RESEARCH.md § Architecture Patterns 2 & 4** verbatim as the starting point:

**`EntityBlock` + `newBlockEntity`** — RESEARCH.md lines 119, 232:
```java
public class SoulAltarBlock extends Block implements EntityBlock {
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SoulAltarBlockEntity(pos, state);
    }
}
```

**Socket path (`useItemOn` → `ItemInteractionResult`)** — RESEARCH.md lines 298-322. Key points:
`@Override` mandatory (PITFALLS §10 — wrong signature compiles as an unrelated overload and
silently no-ops); mutate BE only inside `if (!level.isClientSide)`; gate on
`stack.is(ModItems.SOUL_BLOCK_ITEM)` AND `be.isEmpty()` (D-03 one-way, no retrieval); call
`be.setChanged()` + `level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL)` after; play
`SoundEvents.SOUL_ESCAPE`.

**Empty-hand path (`useWithoutItem` → `InteractionResult`)** — RESEARCH.md lines 318-321: return
`InteractionResult.PASS` (no menu, no retrieval this phase — D-01/D-03).

**Break consequences (`playerWillDestroy`)** — RESEARCH.md lines 376-397 (signature flagged A4 —
`@Override` catches a mismatch): charged BE → spawn `EntityType.LIGHTNING_BOLT` with
`setVisualOnly(true)`, `player.hurt(damageSources().magic(), 1.0F)`, clear the BE stack.

**Drop suppression (`getDrops`)** — RESEARCH.md lines 125, 399: read
`params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)` (constant name flagged A5); return
empty list when the BE was charged, otherwise `super.getDrops(...)` (defers to `drops_self` loot
table). ⚠ D-04 planner flag: structure this so flipping to "altar still drops, only Soul Block
destroyed" is a one-line change.

**Non-full-cube:** `noOcclusion()` on Properties (in `ModBlocks`) + override `getShape` returning a
`VoxelShape` (RESEARCH.md line 508, "Don't Hand-Roll" row). D-02: waist-high pedestal, no facing
property recommended (RESEARCH.md Open Question 5).

---

### `content/blockentity/SoulAltarBlockEntity.java` (new — block entity)

**Analog:** none. Use **RESEARCH.md lines 328-370 verbatim** as the implementation skeleton.
Checklist (RESEARCH.md Pitfall 3, lines 490-494):

1. `extends BlockEntity`, constructor `(BlockPos, BlockState)` calling
   `super(ModBlockEntities.SOUL_ALTAR_BE.get(), pos, state)` — must match the `::new` ref in
   `ModBlockEntities` and the `BlockEntityType.Builder.of` there.
2. Single field `private ItemStack heldSoulBlock = ItemStack.EMPTY;` — **no** `IItemHandler` /
   `Capability` (D-03, anti-pattern RESEARCH.md 406).
3. `saveAdditional(CompoundTag, HolderLookup.Provider)` / `loadAdditional(...)` — **call `super`
   first** in both; persist with `heldSoulBlock.save(reg)` / `ItemStack.parse(reg, tag)`
   (codec-driven, RESEARCH.md line 420; helper names flagged for jar-verification line 372).
4. `getUpdateTag(HolderLookup.Provider)` delegating to `saveAdditional`, **and**
   `getUpdatePacket()` → `ClientboundBlockEntityDataPacket.create(this)` — both required or the
   renderer shows stale state.
5. `isEmpty()` / `getHeldSoulBlock()` / `setHeldSoulBlock(ItemStack)` accessors.
6. Optional `NBT_VERSION` int (D-context discretion / D-17; RESEARCH.md line 331).

---

### `event/HarvesterEvents.java` (new — game-bus event handler)

**Analog for the subscriber shell:** `ModRegistrySelfCheck.java` lines 26-32 & 41-42 — the
`@EventBusSubscriber(modid = SecondShift.MODID, ...)` + `private` constructor + `static`
`@SubscribeEvent` method conventions. Game bus is the default `bus` value (RESEARCH.md line 260:
"bus derived from event type"); the Phase 1 comment at `ModRegistrySelfCheck` lines 29-31 documents
that `bus` is documentation-only in FML 4.0.43 — keep an explicit value for parity anyway.

**Analog for the logic:** none. Use **RESEARCH.md § Architecture Pattern 1, lines 258-294 verbatim**:
- `onDamagePre(LivingDamageEvent.Pre)` → `event.setNewDamage(health + absorption + 1.0F)` when
  `isHarvesterKillOfVillager(...)` (method names flagged A2).
- `onDrops(LivingDropsEvent)` → `event.getDrops().clear()` then add one `SoulFragment` ItemEntity.
- `isHarvesterKillOfVillager` gate: `target.level().isClientSide` guard, `target instanceof
  net.minecraft.world.entity.npc.Villager` (exact — NOT `AbstractVillager`; RESEARCH.md 514),
  `src.getEntity() instanceof Player`, `src.getWeaponItem().getItem() instanceof HarvesterItem`
  (`getWeaponItem` flagged A1, fallback = held-item check).
- Leave the documented Phase-6 seam comment (`&& !hasData(EMPLOYEE)`) — RESEARCH.md line 288, 72.

---

### `client/render/SoulAltarRenderer.java` (new — client-only renderer)

**Analog:** none. `implements BlockEntityRenderer<SoulAltarBlockEntity>`. Guidance from
RESEARCH.md § Architecture diagram lines 194-201 and D-05: if `be.isEmpty()` render nothing extra;
else render the Soul Block model recessed into the altar top at `LightTexture.FULL_BRIGHT`,
eye-of-ender style, slow soul-particle wisp, **no bob**.

**Critical constraint (PITFALLS §9, RESEARCH.md 486-488):** this class and everything it imports
(`PoseStack`, `MultiBufferSource`, `Minecraft`, `BlockEntityRenderer`) must **never** be referenced
from `content/` or `event/`. One `./gradlew runServer` this phase to confirm no leak.

---

### `client/ClientModBusEvents.java` (modified — client bus subscriber)

**Analog:** itself, `src/main/java/com/cxmxrgo/secondshift/client/ClientModBusEvents.java` lines 21-32.

**Current file:** `@EventBusSubscriber(modid = SecondShift.MODID, bus = Bus.MOD, value = Dist.CLIENT)`,
private constructor, one `static @SubscribeEvent onClientSetup(FMLClientSetupEvent)` that logs.

**Change:** add a second handler:
```java
@SubscribeEvent
static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(ModBlockEntities.SOUL_ALTAR_BE.get(), SoulAltarRenderer::new);
}
```
`EntityRenderersEvent.RegisterRenderers` is a mod-bus event (RESEARCH.md line 138), so the existing
`bus = Bus.MOD` on the class is correct. This is the only place `SoulAltarRenderer` may be named.
The class Javadoc lines 15-16 ("No screen / menu / render code (none exists yet)") should be
updated.

---

## Shared Patterns

### Pattern: `DeferredRegister` holder class

**Source:** `registry/ModItems.java` lines 13-21.
**Apply to:** `ModBlocks`, `ModBlockEntities`, `ModCreativeTab`.

- `public final class` with a `private` no-arg constructor.
- `public static final DeferredRegister.X <NAME> = DeferredRegister.createX(SecondShift.MODID);`
  (or `DeferredRegister.create(Registries.<KEY>, SecondShift.MODID)` for BE types and creative tabs).
- Holder constants are `public static final DeferredHolder/DeferredBlock/DeferredItem/Supplier`.
- Behavior classes (`SoulAltarBlock`, `SoulAltarBlockEntity`, `HarvesterItem`, `SoulBlock`) live
  under `content/`, never in `registry/` (ARCHITECTURE.md lines 156-162).

### Pattern: one-visible-block registration wiring

**Source:** `SecondShift.java` lines 31-36.
**Apply to:** every new `DeferredRegister`.

Every `registry/Mod*` register gets exactly one `.register(modBus)` call in the `SecondShift`
constructor, grouped in the single commented block. This forces class-load → static init → entry
queue before `RegisterEvent` fires (RESEARCH.md Pitfall 4; ARCHITECTURE.md lines 238-244). Missing
this is the exact crash class Phase 1 was built to prevent.

### Pattern: self-check registration

**Source:** `ModRegistrySelfCheck.java` lines 43-46.
**Apply to:** every new `DeferredRegister`.

Add the register to the `Stream.of(...)` literal. The guardrail only checks what it is told (D-10,
hand-maintained, no reflection). The Wave-0 test (RESEARCH.md line 736) is: un-`.register` one
deliberately and confirm the hard abort still fires with a named list.

### Pattern: `@EventBusSubscriber` conventions

**Source:** `ModRegistrySelfCheck.java` lines 26, 32, 41 (mod bus) and
`client/ClientModBusEvents.java` line 21 (client, mod bus).

- `@EventBusSubscriber(modid = SecondShift.MODID, bus = EventBusSubscriber.Bus.MOD[, value = Dist.CLIENT])`.
- `bus` is **documentation-only** in FML 4.0.43 (derived from event type) — Phase 1 keeps it
  explicit for parity (`ModRegistrySelfCheck` lines 29-31). Follow that: state the bus you mean.
- `private` constructor; handlers are `static` methods annotated `@SubscribeEvent`.
- For `HarvesterEvents` the bus is GAME (default) — an `@EventBusSubscriber(modid = SecondShift.MODID)`
  with no `bus` (or `bus = Bus.GAME`) self-registers with no constructor line needed.

### Pattern: logger

**Source:** `SecondShift.java` lines 4, 26; `ClientModBusEvents.java` lines 8, 24.
`private static final Logger LOGGER = LogUtils.getLogger();` (`com.mojang.logging.LogUtils`,
`org.slf4j.Logger`). Log lines are prefixed `[SecondShift]`.

### Pattern: Javadoc cites decision IDs

**Source:** all four Phase 1 files (e.g. `ModItems.java` lines 8-12 cites D-07;
`ModRegistrySelfCheck.java` lines 11-25 cites D-08/D-09/D-10). New files should cite the D-IDs and
requirement IDs (ECON-*, ALTAR-*, POL-*) they satisfy in the class Javadoc.

---

## No Analog Found

The following have **no** in-repo precedent. Planner assigns patterns from `02-RESEARCH.md`.

| File(s) | Role | RESEARCH.md reference |
|---------|------|-----------------------|
| `content/item/HarvesterItem.java` | item subclass | § Code Examples lines 602-619 (impl + `Item.Properties`) |
| `content/block/SoulBlock.java` | block | § Standard Stack lines 136-137 (`lightLevel`, `animateTick`) |
| `content/block/SoulAltarBlock.java` | `EntityBlock` | § Architecture Patterns 2 & 4, lines 296-399 |
| `content/blockentity/SoulAltarBlockEntity.java` | block entity | § Architecture Pattern 3, lines 326-372 |
| `event/HarvesterEvents.java` (logic only) | game-bus handler | § Architecture Pattern 1, lines 258-294 |
| `client/render/SoulAltarRenderer.java` | BER | § Architecture diagram lines 194-201; D-05 |
| `assets/.../models/item/*.json` ×4 | item models | § Datagen Decision lines 432-435 (parents per item) |
| `assets/.../models/block/*.json` ×2 | block models | lines 435 (`soul_block` cube_all; altar hand-modelled) |
| `assets/.../blockstates/*.json` ×2 | blockstates | line 436 (single variant, no facing — Open Q5) |
| `data/.../recipe/harvester.json` | shaped recipe | § Code Examples lines 525-539 (full JSON) |
| `data/.../recipe/soul_block.json` | shapeless recipe | § Code Examples lines 541-555 (full JSON) |
| `data/.../recipe/soul_fragment_from_block.json` | shapeless reverse | line 556 |
| `data/.../recipe/soul_altar.json` | shaped recipe | D-06 (frame + soul soil + skull/bone + 1-2 emerald); shape = lines 525-539 pattern |
| `data/.../loot_table/blocks/soul_block.json` | loot (drops_self) | line 438; validate against vanilla 1.21.1 `data/minecraft/loot_table/blocks/*.json` |
| `data/.../loot_table/blocks/soul_altar.json` | loot (drops_self) | line 438 + `getDrops` code branch for charged |
| `data/.../advancement/necromantic_apprentice.json` | advancement | § Code Examples lines 559-580 (full JSON) |
| `data/.../advancement/first_harvest.json` | advancement | line 581 (`inventory_changed` on `soul_fragment`, D-16) |
| `data/.../advancement/soul_mason.json` | advancement | line 581 (`recipe_crafted` / `inventory_changed` on `soul_block`) |

**Deliberately NOT created:** `data/secondshift/advancement/recipes/*.json` (the vanilla
auto-recipe-advancement) — it would unlock recipes on ingredient pickup and defeat D-15 gating
(RESEARCH.md lines 409, 444-446, 250).

**Datagen note (D-17):** RESEARCH.md § Datagen Decision (lines 428-475) recommends **hand-written
JSON** for this phase (the advancement-gating friction with `RecipeProvider` is the deciding
factor, not the file count). If the planner adopts datagen anyway, wiring skeleton is at
RESEARCH.md lines 458-475 and there is no in-repo analog (`DataGen` class would be greenfield).

---

## Resource file conventions (from the Phase 1 stubs)

| Convention | Source | Value |
|------------|--------|-------|
| `pack_format` | `src/main/resources/pack.mcmeta` line 4 | `48` — do not change |
| Data folders | `data/secondshift/{recipe,loot_table,advancement}/.gitkeep` | **singular** — already correct, do not regress to plural (RESEARCH.md 644) |
| Lang file | `assets/secondshift/lang/en_us.json` line 1 | currently `{}` — fill; keys: `item.secondshift.*` ×3, `block.secondshift.soul_block`, `block.secondshift.soul_altar`, `itemGroup.secondshift.main`, `advancement.secondshift.<name>.{title,description}` ×3 |
| Namespace | all | `secondshift` (`SecondShift.MODID`) |

---

## Metadata

**Analog search scope:** `src/main/java/com/cxmxrgo/secondshift/**` (4 files),
`src/main/resources/**` (lang, pack.mcmeta, 3 gitkeep stubs).
**Files scanned:** 4 Java + 6 resource entries — the entire Phase 1 output.
**Pattern extraction date:** 2026-09-04

---

## PATTERN MAPPING COMPLETE

**Phase:** 2 - Economy Items & Soul Altar Block
**Files classified:** 13 Java (3 modified, 10 new) + ~20 resource files
**Analogs found:** 8 in-repo / 13 Java files (remaining are greenfield → RESEARCH.md § Code Examples)

### Coverage
- Files with exact analog: 5 (`ModItems`, `SecondShift`, `ModRegistrySelfCheck`, `ClientModBusEvents`, `en_us.json` — all self/modified)
- Files with role-match analog: 3 (`ModBlocks`, `ModBlockEntities`, `ModCreativeTab` → `ModItems` holder shape; `HarvesterEvents` shell → `ModRegistrySelfCheck` subscriber shape)
- Files with no analog: 10 Java-logic + all ~20 JSON/model/texture files → map to `02-RESEARCH.md` § Architecture Patterns 1-4 and § Code Examples

### Key Patterns Identified
- **`DeferredRegister` holder shape** (`ModItems.java` 13-21): `public final class`, private ctor, `static final` register keyed on `SecondShift.MODID`, holder constants — replicated for blocks / BE types / creative tab.
- **One-visible-block wiring** (`SecondShift.java` 31-36) + **hand-maintained self-check stream** (`ModRegistrySelfCheck.java` 43-46): every new register touched in both places or it silently no-ops / isn't guarded.
- **`@EventBusSubscriber` conventions** (`ModRegistrySelfCheck.java` 26-32, `ClientModBusEvents.java` 21): explicit `bus` (documentation-only in FML 4.0.43), private ctor, static `@SubscribeEvent`; client render registration extends the existing `ClientModBusEvents` class rather than a new one.
- **All domain logic is greenfield** — Harvester behavior, altar `useItemOn`/`playerWillDestroy`, BE persistence + client sync, the BER, and every JSON file copy directly from `02-RESEARCH.md` (§ Architecture Patterns 1-4, § Code Examples), with ~10 API signatures flagged there for jar-verification at implementation time.

### File Created
`C:\Users\user\Documents\PROJECTS\necromancy-mod\.planning\phases\02-economy-items-soul-altar-block\02-PATTERNS.md`

### Ready for Planning
Pattern mapping complete. Planner can reference the Phase 1 analog excerpts (with line numbers)
for the registry/wiring/subscriber layer, and `02-RESEARCH.md` § Code Examples for all
content-specific files.
