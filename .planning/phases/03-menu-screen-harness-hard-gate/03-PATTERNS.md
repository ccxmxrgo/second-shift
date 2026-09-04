# Phase 3: Menu & Screen Harness (HARD GATE) - Pattern Map

**Mapped:** 2026-09-04
**Files analyzed:** 11 (5 new, 6 modified)
**Analogs found:** 11 / 11

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|---------------|
| `registry/ModMenus.java` | registry | CRUD (registration) | `registry/ModBlockEntities.java` | exact |
| `menu/BindingAltarMenu.java` | model / controller (container) | request-response | `content/blockentity/SoulAltarBlockEntity.java` (persistence side) + NeoForge decompiled `AbstractContainerMenu` (research §Pattern 3) | role-match (no existing menu class in repo — first of its kind) |
| `menu/SoulSlot.java` | model (slot) | transform (display-only mirror of BE field) | none in repo — decompiled vanilla `Slot.java` (research) | no analog — vanilla-only |
| `client/screen/BindingAltarScreen.java` | component (screen) | request-response (render) | `client/render/SoulAltarRenderer.java` (closest existing client-only class) | role-match |
| `trade/ProfessionResolver.java` (or top-level util) | utility | transform (registry lookup) | none in repo — pure new util, no existing resolver-shaped class | no analog — new pattern |
| `client/ClientModBusEvents.java` (EXTEND) | provider / event handler | event-driven | itself (existing `onRegisterRenderers` method in same file) | exact |
| `content/blockentity/SoulAltarBlockEntity.java` (EXTEND — `implements MenuProvider`) | model / block-entity | CRUD | itself (existing file, extend in place) | exact |
| `content/block/SoulAltarBlock.java` (EXTEND — `useItemOn`/`useWithoutItem`) | controller (block interaction) | event-driven | itself (existing file, extend in place) | exact |
| `ModRegistrySelfCheck.java` (EXTEND — 4→5 registers) | utility (guardrail) | batch (startup validation) | itself (existing file, extend `Stream.of(...)`) | exact |
| `SecondShift.java` (EXTEND — register call + log line) | config (bootstrap) | event-driven | itself (existing constructor block) | exact |
| `gametest/BindingAltarGameTests.java` (NEW, or extend `HarvesterGameTests.java`) | test | request-response (GameTest) | `gametest/HarvesterGameTests.java` | exact |
| `assets/secondshift/lang/en_us.json` (EXTEND) | config | CRUD (key-value) | itself (existing file) | exact |

## Pattern Assignments

### `registry/ModMenus.java` (registry, CRUD)

**Analog:** `src/main/java/com/cxmxrgo/secondshift/registry/ModBlockEntities.java` (whole file, lines 1-32)

**Imports pattern** (lines 1-9 of `ModBlockEntities.java`):
```java
package com.cxmxrgo.secondshift.registry;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
```
For `ModMenus`, swap `Registries.BLOCK_ENTITY_TYPE` → `Registries.MENU`, `BlockEntityType` → `MenuType`, and add `net.neoforged.neoforge.common.extensions.IMenuTypeExtension` + `com.cxmxrgo.secondshift.menu.BindingAltarMenu`. Use `DeferredHolder<MenuType<?>, MenuType<BindingAltarMenu>>` (matching the `Supplier`/`DeferredHolder` shape already used by `ModBlockEntities`) rather than a bare `Supplier`.

**Core registration pattern** (lines 22-32, whole class):
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
Copy this shape exactly: field name `MENUS` (matches `BLOCK_ENTITIES` naming convention), a single `register("binding_altar", () -> IMenuTypeExtension.create(BindingAltarMenu::new))` entry, private no-arg constructor, `final class`. See RESEARCH.md Pattern 2/Code Examples for the exact `IMenuTypeExtension.create` call — that part has no in-repo analog since no menu registry exists yet.

**Doc-comment convention** (lines 11-21) — every `Mod*` registry class in this repo carries a class javadoc naming the requirement IDs it satisfies and a one-line description of what's registered. Follow the same style for `ModMenus` (reference GUI-01/D-14).

---

### `menu/BindingAltarMenu.java` (model/controller, request-response)

**No in-repo analog** — this is the first `AbstractContainerMenu` in the codebase. Use RESEARCH.md's "Code Examples" and "Pattern 3" sections verbatim (verified against decompiled NeoForge sources, not the repo). Key structural elements to copy from RESEARCH.md:
- Two ctors: client `(int, Inventory, RegistryFriendlyByteBuf)` delegating to a private/server ctor; server `(int, Inventory, ContainerLevelAccess, BlockPos)`.
- `stillValid(Player)` → `AbstractContainerMenu.stillValid(this.access, player, ModBlocks.SOUL_ALTAR.get())` (D-16, SC4).
- `quickMoveStack(Player, int)` → `ItemStack.EMPTY` (display-only slot, no shift-click routing needed, D-06).
- Add the one `SoulSlot` at UI-SPEC coordinates `(80, 35)` plus vanilla 36-slot player inventory loop at `(8, 84)`/`(8,102)`/`(8,120)`/`(8,142)` per UI-SPEC "Slot coordinates" table.

**Style precedent from this repo** (apply, even without a menu analog): javadoc naming the decisions/requirements satisfied — see `content/blockentity/SoulAltarBlockEntity.java` lines 14-30 for the tone/format:
```java
/**
 * Soul Altar block entity (D-01 / D-03 / ALTAR-01).
 *
 * <p>Holds exactly one thing: ...
 */
```
Mirror this pattern in `BindingAltarMenu`'s class javadoc (cite D-05/D-06/D-16, GUI-01).

**Access-field pattern** — model `ContainerLevelAccess access` as a `private final` field constructed once, exactly as shown in RESEARCH.md's Pattern 3 code example; do not recompute it per-call.

---

### `menu/SoulSlot.java` (model, transform)

**No in-repo analog.** Use RESEARCH.md's "The read-only slot" code example verbatim:
```java
public class SoulSlot extends Slot {
    public SoulSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }
}
```
**Backing container:** per RESEARCH.md's Open Question 1 recommendation, write a small dedicated `Container` wrapper (not `SimpleContainer`) delegating `getItem(0)`/`setItem(0, ...)` to `SoulAltarBlockEntity#getHeldSoulBlock()`/`setHeldSoulBlock()` — keeps the BE as sole source of truth (matches this repo's existing pattern of the BE owning `heldSoulBlock` as the single field, see `SoulAltarBlockEntity.java` lines 38, 64-70).

---

### `client/screen/BindingAltarScreen.java` (component, request-response/render)

**Analog:** `src/main/java/com/cxmxrgo/secondshift/client/render/SoulAltarRenderer.java` — read for the client-only package placement + `Dist.CLIENT` isolation convention (this is the only other client-only rendering class in the repo). Not read in full above but its role is confirmed by its registration site in `ClientModBusEvents.java` line 5, 41 — it lives in `client/render/`, is referenced ONLY from `ClientModBusEvents`, and is never imported by common code. Apply the identical isolation rule to `BindingAltarScreen`: it must live in `client/screen/` and be referenced ONLY from the new `onRegisterScreens` handler in `ClientModBusEvents.java`.

**Core pattern** — from RESEARCH.md (`AbstractContainerScreen<BindingAltarMenu>`):
- Constructor `(BindingAltarMenu menu, Inventory playerInv, Component title)` calling `super(menu, playerInv, title)`.
- Override `renderBg` to blit the UI-SPEC texture: `guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight)` where `TEXTURE = ResourceLocation.fromNamespaceAndPath(SecondShift.MODID, "textures/gui/binding_altar.png")`.
- `imageWidth = 176`, `imageHeight = 166` set in constructor (UI-SPEC "Screen Geometry" table).
- No custom `renderLabels` override needed — vanilla defaults already draw the title at `(8,6)` and "Inventory" at `(8,72)` per UI-SPEC.

---

### `trade/ProfessionResolver.java` (utility, transform)

**No in-repo analog** — first pure-lookup utility class. Use RESEARCH.md's "Pattern 5" code example verbatim:
```java
public final class ProfessionResolver {
    private ProfessionResolver() {}

    public static Optional<VillagerProfession> fromAbove(Level level, BlockPos altarPos) {
        BlockState above = level.getBlockState(altarPos.above());
        return PoiTypes.forState(above).flatMap(ProfessionResolver::fromPoi);
    }

    public static Optional<VillagerProfession> fromPoi(Holder<PoiType> poi) {
        return BuiltInRegistries.VILLAGER_PROFESSION.stream()
                .filter(p -> p != VillagerProfession.NONE && p != VillagerProfession.NITWIT)
                .filter(p -> p.heldJobSite().test(poi))
                .findFirst();
    }
}
```
**Style precedent to apply:** private no-arg constructor + `final class` pattern (matches `ModBlockEntities`, `ModBlocks`, `SoulAltarBlock` conventions throughout this repo) and a class javadoc citing D-08/D-09/D-10.

---

### `client/ClientModBusEvents.java` (EXTEND — provider, event-driven)

**Analog:** itself, lines 39-43 (existing `onRegisterRenderers` handler in the same file)

**Existing pattern to copy** (lines 34-43):
```java
@SubscribeEvent
static void onClientSetup(FMLClientSetupEvent event) {
    LOGGER.info("[SecondShift] client setup ok");
}

@SubscribeEvent
static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(ModBlockEntities.SOUL_ALTAR_BE.get(), SoulAltarRenderer::new);
    LOGGER.info("[SecondShift] registered SoulAltarRenderer for secondshift:soul_altar");
}
```
Add a third `@SubscribeEvent static void onRegisterScreens(RegisterMenuScreensEvent event)` method in the exact same style: one `event.register(...)` call + one `LOGGER.info(...)` confirmation line naming the registry key, matching the existing `[SecondShift] registered X for Y` log-message convention:
```java
@SubscribeEvent
static void onRegisterScreens(RegisterMenuScreensEvent event) {
    event.register(ModMenus.BINDING_ALTAR.get(), BindingAltarScreen::new);
    LOGGER.info("[SecondShift] registered BindingAltarScreen for secondshift:binding_altar");
}
```
**Class-level doc convention** (lines 14-26) — update the class javadoc to also mention the `RegisterMenuScreensEvent` binding, following the existing style of naming which events this class subscribes to and why (`Dist.CLIENT` isolation rationale already stated at lines 17-19).

**Import to add:** `net.neoforged.neoforge.client.event.RegisterMenuScreensEvent`, `com.cxmxrgo.secondshift.client.screen.BindingAltarScreen`, `com.cxmxrgo.secondshift.registry.ModMenus`.

---

### `content/blockentity/SoulAltarBlockEntity.java` (EXTEND — model, CRUD)

**Analog:** itself, whole file (already read above)

**Existing accessor pattern to preserve** (lines 52-70) — `isEmpty()`, `getHeldSoulBlock()`, `setHeldSoulBlock()` already exist and are the exact hooks `MenuProvider#createMenu` and the `SoulSlot`'s backing `Container` wrapper need. Do not change their signatures.

**Add `implements MenuProvider`** with two new methods, following RESEARCH.md's verified example:
```java
@Override
public Component getDisplayName() {
    return Component.translatable("container.secondshift.binding_altar");
}

@Override
public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
    return new BindingAltarMenu(containerId, playerInv,
            ContainerLevelAccess.create(this.getLevel(), this.getBlockPos()), this.getBlockPos());
}
```
**Class javadoc convention** (lines 14-30) — extend the existing javadoc block (do not replace it) to note the new `MenuProvider` responsibility, matching the file's existing style of documenting "what this class does NOT do yet" (e.g. line 17-19 "No professions, no employees, no binding state — those arrive in later phases").

---

### `content/block/SoulAltarBlock.java` (EXTEND — controller, event-driven)

**Analog:** itself, `useItemOn` (lines 90-113) and `useWithoutItem` (lines 116-120)

**Existing fallthrough pattern to extend, not replace** (lines 90-113):
```java
@Override
protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
    if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be)) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    if (!stack.is(ModItems.SOUL_BLOCK_ITEM.get()) || !be.isEmpty()) {
        // D-03: one-way — no overwrite, no retrieval, empty hand does nothing.
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    if (!level.isClientSide) {
        be.setHeldSoulBlock(stack.copyWithCount(1));
        stack.consume(1, player);
        be.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.8F, 1.0F);
        ...
    }
    return ItemInteractionResult.sidedSuccess(level.isClientSide);
}
```
Per RESEARCH.md Pattern 4, gate the ProfessionResolver check + `sp.openMenu(be, buf -> buf.writeBlockPos(pos))` call inside the existing `if (!level.isClientSide)` block, right after the current socket mutation (`be.setHeldSoulBlock`/`setChanged`/`sendBlockUpdated` lines) and before `return ItemInteractionResult.sidedSuccess(...)`. Do NOT duplicate the profession-gate check in both methods — put the gate check once in `useWithoutItem` and let the "already charged" branch of `useItemOn` (the existing line 96-99 `PASS_TO_DEFAULT_BLOCK_INTERACTION`) fall through to it (this is the existing, already-correct fallthrough — verify it still triggers `useWithoutItem` after adding new logic).

**Existing empty-hand stub to replace** (lines 116-120):
```java
/** D-01 / D-03: no menu, no retrieval this phase. */
@Override
protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                           BlockHitResult hit) {
    return InteractionResult.PASS;
}
```
Replace this stub body with the full D-02/D-04 reopen-gate logic from RESEARCH.md Pattern 4's `useWithoutItem` example (ProfessionResolver call, POL-08 messages, `sp.openMenu(be, buf -> buf.writeBlockPos(pos))`) — keep the `@Override` annotation (Pitfall 10 — this repo's established rule that `@Override` on every interaction method catches wrong 1.21.1 signatures at compile time, see class javadoc lines 44-52).

**`!level.isClientSide` server-gating convention** — every mutation in this file already follows the pattern of gating all side-effects and menu-opens behind `if (!level.isClientSide)` (see lines 100, 135, 168). Continue this exactly for the new POL-08 messages and `openMenu` calls.

**Doc-comment convention** — this file's javadoc cites decisions/requirements per-method (e.g. line 85-89 "D-03: one-way socket..."). Add matching javadoc for the new D-01/D-02/D-11 behavior.

---

### `ModRegistrySelfCheck.java` (EXTEND — utility guardrail, batch)

**Analog:** itself, lines 71-84 (`onLoadComplete`)

**Existing pattern to extend** (lines 71-84):
```java
@SubscribeEvent
static void onLoadComplete(FMLLoadCompleteEvent event) {
    // D-10: add every registry/Mod* DeferredRegister to this Stream.of(...) as later
    // phases introduce them, and mirror it in the SecondShift constructor.
    List<String> unbound = Stream.of(ModItems.ITEMS, ModBlocks.BLOCKS, ModBlockEntities.BLOCK_ENTITIES, ModCreativeTab.TABS)
            .flatMap(dr -> dr.getEntries().stream())
            .filter(holder -> !holder.isBound())
            .map(holder -> holder.getId().toString())
            .sorted()
            .toList();
    if (!unbound.isEmpty()) {
        throw new IllegalStateException("Unbound registry entries: " + unbound);
    }
}
```
Per D-14, simply add `ModMenus.MENUS` to the `Stream.of(...)` call (4 → 5 registers). No other change to this method's structure — the class-level javadoc already anticipates this exact extension point (comment at line 73-74). Add the `import com.cxmxrgo.secondshift.registry.ModMenus;` line.

**D-15 reconciliation (per RESEARCH.md):** do NOT add a second hard-abort path in `FMLCommonSetupEvent`. Keep this single `FMLLoadCompleteEvent` abort mechanism; instead add an informational log line to `SecondShift.commonSetup` (see next section) naming the menu key via `BuiltInRegistries.MENU.getKey(ModMenus.BINDING_ALTAR.get())`. This satisfies SC2's literal wording without duplicating the guardrail.

**Lang-key check** (`onLoadCompleteLangKeys`, lines 86-117) — this phase does not need to extend this method's `Stream.of(ModItems.ITEMS, ModBlocks.BLOCKS)` scan (menus don't have a `descriptionId`-style key resolved the same way; the container title key `container.secondshift.binding_altar` is validated by simply existing in `en_us.json`, not by this registry-driven scan). No change needed here — this section documents that this is a deliberate non-extension, not an oversight.

---

### `SecondShift.java` (EXTEND — config/bootstrap)

**Analog:** itself, lines 31-58 (whole class)

**Existing register-in-constructor pattern to extend** (lines 31-43):
```java
public SecondShift(IEventBus modBus, ModContainer container) {
    LOGGER.info("[SecondShift] loading {} on NeoForge", container.getModInfo().getVersion());

    // Register every DeferredRegister on the mod bus here, in one visible block
    // (D-08/D-10). Each new registry/Mod* class MUST be added here and to
    // ModRegistrySelfCheck.
    ModItems.ITEMS.register(modBus);
    ModBlocks.BLOCKS.register(modBus);
    ModBlockEntities.BLOCK_ENTITIES.register(modBus);
    ModCreativeTab.TABS.register(modBus);

    modBus.addListener(this::commonSetup);
}
```
Add exactly one line, `ModMenus.MENUS.register(modBus);`, immediately after `ModCreativeTab.TABS.register(modBus);` — this is literally the line Pattern 1 in RESEARCH.md calls out as "the line the prior draft omitted." Add `import com.cxmxrgo.secondshift.registry.ModMenus;`.

**Existing `commonSetup` log pattern to extend** (lines 45-58):
```java
private void commonSetup(FMLCommonSetupEvent event) {
    event.enqueueWork(() -> {
        List<String> ids = ModItems.ITEMS.getEntries().stream()
                .map(holder -> holder.getId().toString())
                .sorted()
                .toList();
        LOGGER.info("[SecondShift] common setup - {} item(s), {} block(s), {} block-entity type(s), {} creative tab(s) registered: {}",
                ids.size(),
                ModBlocks.BLOCKS.getEntries().size(),
                ModBlockEntities.BLOCK_ENTITIES.getEntries().size(),
                ModCreativeTab.TABS.getEntries().size(),
                ids);
    });
}
```
Per SC2/D-15, add one more log line inside the same `enqueueWork` lambda (or a second `LOGGER.info` call right after the existing one) that logs the resolved menu key:
```java
LOGGER.info("[SecondShift] menu registered: {}",
        BuiltInRegistries.MENU.getKey(ModMenus.BINDING_ALTAR.get()));
```
This is informational only — the hard-abort stays entirely in `ModRegistrySelfCheck`.

---

### `gametest/BindingAltarGameTests.java` (NEW class, test, request-response)

**Analog:** `src/main/java/com/cxmxrgo/secondshift/gametest/HarvesterGameTests.java` (whole file, lines 1-186)

**Class-level annotations + structure to copy** (lines 49-57):
```java
@GameTestHolder(SecondShift.MODID)
@PrefixGameTestTemplate(false)
public final class HarvesterGameTests {

    private static final BlockPos SPAWN = new BlockPos(4, 2, 4);
    ...
    private HarvesterGameTests() {}
```
Mirror this exactly for `BindingAltarGameTests`: same `@GameTestHolder(SecondShift.MODID)` + `@PrefixGameTestTemplate(false)`, same `secondshift:empty` structure template, private constructor, `final class`.

**Mock-player + helper pattern** (lines 161-172, `hit(...)` helper) — for this phase's tests, use `helper.makeMockServerPlayerInLevel()` (per RESEARCH.md, verified present) instead of `helper.makeMockPlayer(GameType.SURVIVAL)` (the existing helper is for damage-source tests, not menu tests) — but copy the same "one private static helper method per repeated setup step" structure this file uses (e.g. `hit(...)`, `assertReapedToOneFragment(...)`).

**Assertion style** (lines 174-184, `assertReapedToOneFragment`) — copy the `helper.assertTrue(condition, "message")` + `helper.succeedWhen(() -> ...)` / `helper.succeed()` idioms exactly; this repo never uses raw JUnit assertions inside GameTest methods.

**Test-per-scenario naming convention** (method names like `harvester_kill_villager_drops_one_fragment`, `harvester_hit_wandering_trader_no_fragment`) — name the new tests analogously: e.g. `binding_altar_menu_open_stillvalid_true`, `binding_altar_stillvalid_false_after_break`, `binding_altar_stillvalid_false_when_far`, `binding_altar_no_job_block_no_menu`.

**Javadoc convention** (lines 28-48) — class-level javadoc naming the requirement IDs (SC4, ALTAR-03) and describing RED/GREEN baseline expectations, matching this file's existing style.

---

### `assets/secondshift/lang/en_us.json` (EXTEND — config, CRUD)

**Analog:** itself (existing file — read its current key style before editing; not fully re-read here since it's a flat JSON key-value file with an established `item.secondshift.*` / `block.secondshift.*` / `itemGroup.secondshift.*` / `advancement.secondshift.*` naming convention, confirmed via `ModRegistrySelfCheck.EXTRA_LANG_KEYS`, lines 60-67).

**Keys to add** (exact copy, from UI-SPEC "Copywriting Contract" table):
```json
"container.secondshift.binding_altar": "Binding Altar",
"message.secondshift.altar.no_job_block": "The position is unfilled. Set a job-site block atop the altar before you file the paperwork.",
"message.secondshift.altar.not_a_workstation": "That block posts no job. Only a real workstation defines the role.",
"message.secondshift.altar.no_soul_block": "Nothing to bind. Bring a Soul Block to staff this altar.",
"message.secondshift.altar.closed.altar_gone": "The altar is gone. Consider the interview concluded.",
"message.secondshift.altar.closed.job_gone": "The workstation was removed. The position is rescinded.",
"message.secondshift.altar.closed.too_far": "You stepped away from the altar. The paperwork goes unfiled."
```
**D-17 convention (from Phase 2, cited in CONTEXT.md line 186):** hand-written `en_us.json`, no datagen — add these directly, matching the existing flat-file editing convention.

## Shared Patterns

### DeferredRegister-in-constructor (crash-prevention)
**Source:** `SecondShift.java` lines 37-40
**Apply to:** `ModMenus.MENUS.register(modBus)` — MUST be added to the existing constructor block, not a separate location. This is the single most important pattern this phase depends on (GUI-01/SC1/SC2 all hinge on it).

### ModRegistrySelfCheck hard-abort guardrail
**Source:** `ModRegistrySelfCheck.java` lines 71-84
**Apply to:** Add `ModMenus.MENUS` to the existing `Stream.of(...)` call — do not create a parallel/second guardrail (D-14/D-15).

### `!level.isClientSide` server-gating
**Source:** `content/block/SoulAltarBlock.java` lines 100, 135, 168
**Apply to:** All new mutation/open-menu/message-sending logic in `SoulAltarBlock#useItemOn`/`useWithoutItem` and any `stillValid`/forced-close logic in `BindingAltarMenu`.

### `@Override` on every interaction method (compile-time signature safety)
**Source:** `content/block/SoulAltarBlock.java` class javadoc lines 44-52 (Pitfall 10 — `ItemInteractionResult` vs `InteractionResult` split in 1.21.1)
**Apply to:** `useItemOn`, `useWithoutItem`, `stillValid`, `quickMoveStack`, `createMenu`, `getDisplayName` — every overridden vanilla/NeoForge method this phase touches.

### `Dist.CLIENT` class isolation
**Source:** `client/ClientModBusEvents.java` class javadoc lines 14-26, and the existing `client/render/SoulAltarRenderer.java` package placement
**Apply to:** `BindingAltarScreen` — must live in `client/screen/`, referenced ONLY from `ClientModBusEvents.onRegisterScreens`. Never import it from `menu/`, `content/block/`, or `content/blockentity/`.

### `LOGGER.info("[SecondShift] registered X for Y")` confirmation-log convention
**Source:** `client/ClientModBusEvents.java` line 42
**Apply to:** The new `onRegisterScreens` handler's log line, and the `SecondShift.commonSetup` menu-key log line (D-15).

### Private no-arg constructor + `final class` on all static-holder utility classes
**Source:** `registry/ModBlockEntities.java` line 31, `content/block/SoulAltarBlock.java` (not static-holder but same repo convention), `ModRegistrySelfCheck.java` line 69
**Apply to:** `ModMenus`, `ProfessionResolver`.

### Javadoc citing decision IDs (D-xx) / requirement IDs on every new/extended class
**Source:** Every file read above carries this convention (e.g. `SoulAltarBlockEntity.java` lines 14-30, `SoulAltarBlock.java` lines 36-58, `ModRegistrySelfCheck.java` lines 23-53)
**Apply to:** All new files (`ModMenus`, `BindingAltarMenu`, `SoulSlot`, `BindingAltarScreen`, `ProfessionResolver`, `BindingAltarGameTests`) and every extended method.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `menu/BindingAltarMenu.java` | model/controller | request-response | No `AbstractContainerMenu` subclass exists anywhere in this repo yet — first menu of the mod. Use RESEARCH.md's verified decompiled-source code examples (Pattern 3) instead of an in-repo analog. |
| `menu/SoulSlot.java` | model | transform | No `Slot` subclass exists in this repo. Use RESEARCH.md's "read-only slot" code example (decompiled `Slot.java`). |
| `client/screen/BindingAltarScreen.java` | component | request-response | No `AbstractContainerScreen` subclass exists in this repo. Package-placement/isolation convention borrowed from `client/render/SoulAltarRenderer.java`; rendering logic itself from RESEARCH.md + UI-SPEC pixel coordinates. |
| `trade/ProfessionResolver.java` | utility | transform | No registry-iteration resolver utility exists in this repo. Use RESEARCH.md's Pattern 5 code example (decompiled `PoiTypes`/`VillagerProfession`). |

## Metadata

**Analog search scope:** `src/main/java/com/cxmxrgo/secondshift/**` (all 14 existing source files), `src/main/resources/assets/secondshift/lang/en_us.json`
**Files scanned:** 14 Java files + 1 lang file (registry/*, content/block/*, content/blockentity/*, content/item/*, client/*, gametest/*, event/*, top-level SecondShift.java + ModRegistrySelfCheck.java)
**Pattern extraction date:** 2026-09-04
