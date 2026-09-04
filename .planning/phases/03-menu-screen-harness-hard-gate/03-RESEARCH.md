# Phase 3: Menu & Screen Harness (HARD GATE) - Research

**Researched:** 2026-09-04
**Domain:** NeoForge 1.21.1 (21.1.248) menu/screen registration, POI→profession resolution, server-authoritative menu validation
**Confidence:** HIGH — every API signature below was read directly from `build/moddev/artifacts/neoforge-21.1.248-sources.jar` (decompiled, on this machine) or Mojang's official 1.21.1 mappings, cross-checked against `.planning/research/{STACK,ARCHITECTURE,PITFALLS}.md`. All four CONTEXT.md "reconcile" flags are resolved below with a concrete recommendation.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Open trigger & Soul Block handling**
- D-01: Right-click **with a Soul Block in hand** on an altar that is (a) empty and (b) has a profession-mapped job block directly above → socket the Soul Block into the BE AND open the Binding Altar screen in the same click (one action).
- D-02: Empty-hand right-click (`useWithoutItem`) re-opens the screen for an altar that is already charged AND has a profession-mapped job block above.
- D-03: No consumption / no binding this phase. The socketed Soul Block stays in the BE (still one-way, no retrieval).
- D-04: Right-click with a Soul Block on an already-charged altar just opens the screen (no second socket, no error).

**The empty screen**
- D-05: Real `AbstractContainerMenu` → `MenuType` → `AbstractContainerScreen` plumbing — NOT a bare `Screen`.
- D-06: `BindingAltarScreen` renders vanilla dialog chrome, player inventory, the altar's one slot (display-only — `mayPickup`/`mayPlace` → false, or a read-only `Slot` subclass). Nothing else.
- D-07: Title = `Component.translatable("container.secondshift.binding_altar")` → "Binding Altar" (exact string fixed by SC1).

**"Job block on top" gate**
- D-08: Phase 3 builds the runtime resolver (research §9 — iterate the villager-profession registry, match on the profession's job-site POI; no hardcoded block list). `PoiTypes.forState(level.getBlockState(altarPos.above()))` → `PoiType` → profession-or-nothing.
- D-09: The gate is "resolver returns a profession." The resolved `VillagerProfession` is NOT stored on the BE and NOT used for anything this phase — only the boolean.
- D-10: Resolver lives in a shared common util (`ProfessionResolver`), reused by `SoulAltarBlock` now and Phase 5 later.

**Invalid-state feedback (POL-08, reachable-now subset)**
- D-11: Themed `Component.translatable` action-bar/chat messages now for: no job block on top; block on top is not a job site (POI present but unmapped, or no POI); no Soul Block in hand and the altar is empty. HR-necromancer tone.
- D-12: On forced close — `stillValid` fails because the altar was broken, the job block was removed, or the player moved out of range — the screen closes AND the player gets a themed message naming the reason.
- D-13: Deferred POL-08 cases (empty trade pool, no valid quarters) are explicitly NOT in Phase 3.

**Registration guardrail (locked by SC2 — not a discussion choice)**
- D-14: Extend `ModRegistrySelfCheck`'s `Stream.of(ModItems.ITEMS, ModBlocks.BLOCKS, ModBlockEntities.BLOCK_ENTITIES, ModCreativeTab.TABS)` to include `ModMenus.MENUS` (4 → 5 registers). A deliberate detach of `ModMenus` must hard-abort startup naming `secondshift:binding_altar`.
- D-15: SC2 says the guardrail logs the menu key from `FMLCommonSetupEvent`; Phase 1's guardrail currently throws from `FMLLoadCompleteEvent`. Research/planning must reconcile — keep one event, ensure the menu key is asserted bound and logged. Do not silently add a second parallel guardrail path.

**Server-authoritative open (SC4)**
- D-16: `BindingAltarMenu` has the two standard ctors: client `(int, Inventory, RegistryFriendlyByteBuf)` reading the block pos, server `(int, Inventory, SoulAltarBlockEntity)` (or `ContainerLevelAccess`). `stillValid` = `AbstractContainerMenu.stillValid(ContainerLevelAccess.create(level, pos), player, ModBlocks.SOUL_ALTAR.get())` plus the vanilla ~8-block distance check. No custom payload.

### Claude's Discretion
- Exact GUI texture / background dimensions and the 1-slot position (UI-SPEC already locked these — see UI-SPEC coordinates below; this is now settled, not open).
- Precise message strings (tone fixed: HR-necromancer; UI-SPEC already provides exact copy).
- Whether the read-only slot is a `Slot` subclass or flag overrides (UI-SPEC contract chose subclass — settled).
- Whether to add the SC4 GameTest as a new method in `HarvesterGameTests` or a new `BindingAltarGameTests` class.

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope. The user's "thorough" picks (profession resolver, full POL-08) were folded into Phase 3 scope, not deferred.

**Out of scope (Phases 4–9), reiterated:** any trade pool / `MerchantOffer` list / trade picker / `MenuType` open-data beyond the block pos; storing/using the resolved profession for binding (ALTAR-02, Phase 5); consuming the Soul Block / job block, spawning the employee, `EmployeeData` (ALTAR-04, Phase 4–5); `ContainerData`/payload sync of employee state (GUI-02/03, Phase 5); name-entry widget, career-path preview (Phase 5+); any `CustomPacketPayload` (Phase 5); POL-08's later cases (empty trade pool, no valid quarters, Phases 5/9).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| GUI-01 | The altar `MenuType` + `Screen` are registered correctly and an (initially empty) altar screen opens under `runClient` without crashing — proven before any trade logic is built | Framework Quick Reference (verified `MenuType`/`RegisterMenuScreensEvent`/`IMenuTypeExtension` signatures), Anti-Pattern 1 avoidance, D-14/D-15 guardrail reconciliation |
| ALTAR-03 | Inserting a Soul Block into an altar that has a job block on top opens the binding GUI | Data Flow (Soul-Block-in-hand → socket + open in one click), `useItemOn`/`useWithoutItem` fallthrough pattern, `ProfessionResolver` |
</phase_requirements>

## Summary

This phase is purely plumbing risk, not gameplay risk: every API this phase touches (`MenuType`, `AbstractContainerMenu`, `AbstractContainerScreen`, `RegisterMenuScreensEvent`, `PoiTypes.forState`, `VillagerProfession`) has already been read directly out of the decompiled NeoForge 21.1.248 sources jar and confirmed to match `.planning/research/STACK.md` §2 and §9 exactly — there is no unverified API surface left in this phase. The prior draft's crash (`Trying to access unbound value: ResourceKey[minecraft:menu / secondshift:binding_altar]`) has a single root cause (the `MenuType` `DeferredRegister` was never `.register(modBus)`'d, or its holder class was never touched before `RegisterEvent` fired) and a single structural fix: add `ModMenus.MENUS.register(modBus)` to the one visible register block in `SecondShift`'s constructor, exactly like the four existing registers.

The phase has four "reconcile" flags from CONTEXT.md; all four resolve cleanly:
1. **Framework API surface** — fully verified (see Framework Quick Reference below). No `[ASSUMED]` tags needed for any Minecraft/NeoForge API in this phase.
2. **POI→profession resolver** — `PoiTypes.forState(BlockState) -> Optional<Holder<PoiType>>` (verified, line 100 of `PoiTypes.java`) composed with `BuiltInRegistries.VILLAGER_PROFESSION.stream().filter(p -> p.heldJobSite().test(holder))`, excluding `VillagerProfession.NONE`/`NITWIT` by identity.
3. **SC2/D-15 guardrail reconciliation** — keep the single `FMLLoadCompleteEvent` hard-abort in `ModRegistrySelfCheck` (extend its `Stream.of(...)` to 5 registers); additionally extend the *existing* `SecondShift.commonSetup` (`FMLCommonSetupEvent`, already present, already does an `enqueueWork` log) with one line logging `BuiltInRegistries.MENU.getKey(ModMenus.BINDING_ALTAR.get())`. This satisfies SC2's literal wording ("the guardrail logs the menu key from `FMLCommonSetupEvent`") as an *informational* log, while the actual hard-abort mechanism stays exactly where Phase 1 established it. This is not a second guardrail — it is one abort path plus one log line reusing an already-existing handler.
4. **`Dist.CLIENT` isolation** — extend the *existing* `ClientModBusEvents` class (already `@EventBusSubscriber(bus=MOD, Dist.CLIENT)`, already hosts the BER registration) with one `RegisterMenuScreensEvent` handler. No new client entry point needed.

**Primary recommendation:** Build in this order — `ModMenus` registry → `BindingAltarMenu` (both ctors + `stillValid` + `quickMoveStack`) → `RegisterMenuScreensEvent` binding in `ClientModBusEvents` → `SoulAltarBlockEntity implements MenuProvider` → `ProfessionResolver` → wire `SoulAltarBlock#useItemOn`/`useWithoutItem` → extend `ModRegistrySelfCheck` → `en_us.json` keys → GameTest. Verify with `runClient` after the menu/screen skeleton exists and BEFORE wiring the profession gate, so a menu-plumbing bug and a resolver bug are never conflated in the same failed test.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `MenuType<BindingAltarMenu>` registration | API / Backend (common) | — | `MenuType` is a registry object that must exist identically on both physical sides; it is not a client concept even though only the client renders a screen for it |
| `BindingAltarMenu` (slots, `stillValid`, quick-move) | API / Backend (common) | — | `AbstractContainerMenu` is a common class instantiated on both sides (server = real, client = mirror from buffer); it owns validation, which is server-authoritative logic |
| `BindingAltarScreen` (rendering, chrome, title) | Browser / Client | — | Extends `AbstractContainerScreen`/`Screen`, references `GuiGraphics`/`Font` — physically client-only classes that crash a dedicated server if loaded |
| `RegisterMenuScreensEvent` binding | Browser / Client | — | The event itself `implements IModBusEvent` but is dispatched only under `Dist.CLIENT`; the handler class must live in `client/` |
| Open-trigger logic (`useItemOn`/`useWithoutItem`) | API / Backend (common) | — | Block interaction methods run on both sides but the actual socket/open mutation is gated `!level.isClientSide`; the decision of *whether* to open is server logic |
| `ProfessionResolver` (POI → profession) | API / Backend (common) | — | Pure registry lookup against `BuiltInRegistries`, no client dependency; reused by both `SoulAltarBlock` now and Phase 5's bind logic |
| `SoulAltarBlockEntity implements MenuProvider` | Database / Storage (BE persistence) + API/Backend (menu provider) | — | The BE is the persistence tier for the held Soul Block; `createMenu`/`getDisplayName` are the API-tier contract vanilla's open-packet machinery calls into |
| Forced-close messaging (POL-08) | API / Backend (common, server-sent) | Browser / Client (HUD rendering) | Messages are `Component.translatable` sent server-side via `displayClientMessage`; the client only renders the action bar, it does not decide *when* to show one |
| Registration self-check guardrail | API / Backend (common, both sides) | — | Runs identically on client and dedicated server at `FMLLoadCompleteEvent`; not a client concept |

## Standard Stack

No new external libraries or dependencies are introduced by this phase — every API used is either vanilla Minecraft or NeoForge, both already pinned exactly (`21.1.248`) in the existing `build.gradle` per `.planning/research/STACK.md`. There is nothing to add to `build.gradle`, `settings.gradle`, or `neoforge.mods.toml`.

### Core

| Component | Source | Purpose | Confidence |
|-----------|--------|---------|------------|
| `MenuType<T>` / `IMenuTypeExtension.create(IContainerFactory<T>)` | `net.neoforged.neoforge.common.extensions.IMenuTypeExtension` (verified: `build/moddev/artifacts/neoforge-21.1.248-sources.jar`) | Buffer-aware menu-type factory | HIGH — read directly from decompiled source |
| `AbstractContainerMenu` | `net.minecraft.world.inventory.AbstractContainerMenu` (verified) | Base class for `BindingAltarMenu`; owns `stillValid`, `quickMoveStack`, slot list | HIGH |
| `AbstractContainerScreen<T>` | `net.minecraft.client.gui.screens.inventory.AbstractContainerScreen` (verified) | Base class for `BindingAltarScreen` | HIGH |
| `RegisterMenuScreensEvent` | `net.neoforged.neoforge.client.event.RegisterMenuScreensEvent` (verified) | Mod-bus, `Dist.CLIENT`, binds `MenuType` → screen constructor | HIGH |
| `PoiTypes` / `PoiType` / `VillagerProfession` | `net.minecraft.world.entity.ai.village.poi.*`, `net.minecraft.world.entity.npc.VillagerProfession` (verified) | POI → profession resolution, no hardcoded list | HIGH |
| `ContainerLevelAccess` | `net.minecraft.world.inventory.ContainerLevelAccess` (verified) | `stillValid` block/position re-check helper | HIGH |

### Supporting

Nothing beyond the Core table — this phase adds zero new dependencies. `Component.translatable`, `en_us.json`, `GameTestHelper` are all already-established project patterns from Phase 1/2.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `IMenuTypeExtension.create(IContainerFactory<T>)` | `new MenuType<>(MenuType.MenuSupplier<T>, FeatureFlagSet)` (vanilla, no extra-data buffer) | Vanilla ctor cannot read a `BlockPos` from the open buffer — required by D-16's client ctor. Not viable here. |
| `Slot` subclass overriding `mayPickup`/`mayPlace` (chosen by UI-SPEC) | Flag-based approach on a stock `Slot` | UI-SPEC already locked the subclass approach as "self-documenting and reused in Phase 5" — no alternative to evaluate. |
| GameTest for SC4 in a new `BindingAltarGameTests` class | Add methods to existing `HarvesterGameTests` | Both work; a new class keeps menu-domain tests separate from harvest-domain tests as the suite grows — left to planner's discretion per CONTEXT.md. |

**Installation:** None — no new packages.

**Version verification:** N/A — no new registry-hosted dependencies (npm/PyPI/crates). All APIs are part of the already-pinned `neoforge:21.1.248` artifact.

## Package Legitimacy Audit

**Not applicable.** This phase installs zero external packages. Every class referenced is either vanilla Minecraft (`net.minecraft.*`) or NeoForge (`net.neoforged.*`), both already declared in the existing `build.gradle` (`neoForge { version = project.neo_version }`, pinned to `21.1.248`) and already running in the project since Phase 1. The Package Legitimacy Gate protocol is skipped — there is nothing to run `slopcheck` or `npm view` against.

## Architecture Patterns

### System Architecture Diagram

```
                         PLAYER RIGHT-CLICK ON SOUL ALTAR
                                      │
                          ┌───────────┴───────────┐
                    holding Soul Block?      empty hand?
                          │                        │
                          ▼                        ▼
              SoulAltarBlock#useItemOn   SoulAltarBlock#useWithoutItem
              (ItemInteractionResult)         (InteractionResult)
                          │                        │
          ┌───────────────┼──────────┐             │
    be.isEmpty()    be NOT empty     │             │
    && item==SoulBlk (D-04: already  │             │
          │          charged)        │             │
          ▼               │          │             │
  ProfessionResolver       └──────────┼─────────────┘
  .fromAbove(level, pos)              │  falls through as
          │                           │  PASS_TO_DEFAULT_BLOCK_INTERACTION
   ┌──────┴──────┐                    ▼
   │             │         ProfessionResolver.fromAbove(level, pos)
no profession   profession found              │
   │             │                   ┌─────────┴─────────┐
   ▼             ▼               no profession      profession found
 send POL-08   [D-01] socket        │              (D-02 reopen /
 message,      Soul Block into      ▼               D-04 already-charged)
 no menu       BE, setChanged   send POL-08              │
               │                message,                 ▼
               ▼                no menu          serverPlayer.openMenu(
       serverPlayer.openMenu(                       MenuProvider, buf ->
         MenuProvider, buf ->                        buf.writeBlockPos(pos))
         buf.writeBlockPos(pos))                            │
               │                                            │
               └───────────────────┬────────────────────────┘
                                    ▼
                    ClientboundOpenScreenPacket + buffer
                                    │
                                    ▼
              CLIENT: MenuType's IContainerFactory invokes
              BindingAltarMenu(int, Inventory, RegistryFriendlyByteBuf)
                                    │
                                    ▼
              RegisterMenuScreensEvent mapping (ClientModBusEvents)
              → new BindingAltarScreen(menu, playerInv, title)
                                    │
                                    ▼
              Vanilla chrome + player inventory + ONE display-only
              slot (mayPickup/mayPlace both false) + title "Binding Altar"

     ── every open tick: server re-checks stillValid ──
     AbstractContainerMenu.stillValid(ContainerLevelAccess.create(level, pos),
       player, ModBlocks.SOUL_ALTAR.get())
     altar broken / job block removed / player > ~8 blocks
                                    │
                                    ▼
                    send forced-close POL-08 message, THEN close container
```

### Recommended Project Structure

(Matches `.planning/research/ARCHITECTURE.md` exactly — this phase populates the `menu/`, `client/screen/`, and `trade/ProfessionResolver` slots that were placeholders in that document.)

```
src/main/java/com/cxmxrgo/secondshift/
├── registry/
│   └── ModMenus.java                 NEW — DeferredRegister<MenuType<?>>, "binding_altar"
├── menu/                             NEW package — common, both sides
│   ├── BindingAltarMenu.java         AbstractContainerMenu, two ctors, stillValid
│   └── SoulSlot.java                 Slot subclass, mayPickup/mayPlace → false (D-06)
├── trade/                            NEW package (research calls this "trade/" per ARCHITECTURE.md;
│   └── ProfessionResolver.java       may also live directly under a top-level package if the
│                                     planner prefers — D-10 only requires "shared common util")
├── content/
│   ├── block/SoulAltarBlock.java     EXTEND — useItemOn socket+open, useWithoutItem reopen
│   └── blockentity/
│       └── SoulAltarBlockEntity.java EXTEND — implements MenuProvider
├── ModRegistrySelfCheck.java         EXTEND — Stream.of(...) 4 → 5
├── SecondShift.java                  EXTEND — ModMenus.MENUS.register(modBus); commonSetup log line
└── client/
    ├── ClientModBusEvents.java       EXTEND — add onRegisterScreens(RegisterMenuScreensEvent)
    └── screen/
        └── BindingAltarScreen.java   NEW — AbstractContainerScreen<BindingAltarMenu>

src/main/resources/
├── assets/secondshift/
│   ├── lang/en_us.json               EXTEND — container.secondshift.binding_altar + 6 POL-08 keys
│   └── textures/gui/binding_altar.png NEW — 256×256 canvas, 176×166 content (UI-SPEC)
```

### Pattern 1: Register-in-constructor (the crash-prevention pattern)

**What:** Every `DeferredRegister`, including the new `ModMenus.MENUS`, is attached to the mod bus in the single `SecondShift` constructor block, alongside the four existing registers.
**When to use:** Always, for every registry class, no exceptions.
**Example:**
```java
// Source: verified decompiled DeferredHolder.java + .planning/research/STACK.md §2
public SecondShift(IEventBus modBus, ModContainer container) {
    ModItems.ITEMS.register(modBus);
    ModBlocks.BLOCKS.register(modBus);
    ModBlockEntities.BLOCK_ENTITIES.register(modBus);
    ModCreativeTab.TABS.register(modBus);
    ModMenus.MENUS.register(modBus);              // <-- the line the prior draft omitted
    modBus.addListener(this::commonSetup);
}
```

### Pattern 2: The `IMenuTypeExtension.create` buffer-aware factory

**What:** `MenuType<T>` created via `IMenuTypeExtension.create(IContainerFactory<T>)` rather than the vanilla `new MenuType<>(MenuSupplier, FeatureFlagSet)` constructor.
**When to use:** Whenever the menu needs data written into the open packet (here: the altar's `BlockPos`), which is every non-trivial custom menu.
**Example:**
```java
// Source: net/neoforged/neoforge/common/extensions/IMenuTypeExtension.java (decompiled, verified)
public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, SecondShift.MODID);

public static final DeferredHolder<MenuType<?>, MenuType<BindingAltarMenu>> BINDING_ALTAR =
        MENUS.register("binding_altar",
                () -> IMenuTypeExtension.create(BindingAltarMenu::new));
```
`IMenuTypeExtension.create` internally does `new MenuType<>(factory, FeatureFlags.DEFAULT_FLAGS)` — confirmed by reading the decompiled source; the planner does not need to touch `FeatureFlagSet` directly.

### Pattern 3: Two-constructor menu (client mirror vs. server real instance)

**What:** `BindingAltarMenu` has a client ctor taking `RegistryFriendlyByteBuf` (satisfies `IContainerFactory<T>`, which itself satisfies `MenuType.MenuSupplier<T>` via a default method) and a server ctor taking the `BlockEntity`/`ContainerLevelAccess` + `BlockPos` directly.
**When to use:** Any menu opened via `openMenu(MenuProvider, extraDataWriter)`.
**Example:**
```java
// Source: verified against decompiled AbstractContainerMenu.java, IContainerFactory.java
public class BindingAltarMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;

    // CLIENT ctor — bound by IMenuTypeExtension.create(BindingAltarMenu::new)
    public BindingAltarMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInv, ContainerLevelAccess.NULL, extraData.readBlockPos());
    }

    // SERVER ctor — invoked from the MenuProvider lambda
    public BindingAltarMenu(int containerId, Inventory playerInv, ContainerLevelAccess access, BlockPos pos) {
        super(ModMenus.BINDING_ALTAR.get(), containerId);
        this.access = access;
        // addSlot(new SoulSlot(container, 0, 80, 35));  -- UI-SPEC coordinates
        // addStandardInventorySlots(playerInv, 8, 84);   -- vanilla helper, verify exact name in AbstractContainerMenu
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(this.access, player, ModBlocks.SOUL_ALTAR.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // no shift-click behavior needed — the one slot is display-only (D-06)
    }
}
```
Note: `quickMoveStack` is `public abstract` on `AbstractContainerMenu` (verified, line 294) — it MUST be implemented or the class fails to compile. Returning `ItemStack.EMPTY` unconditionally is correct and safe for a display-only slot + a normal player inventory with no special shift-click routing needed this phase.

### Pattern 4: `useItemOn` → `PASS_TO_DEFAULT_BLOCK_INTERACTION` → `useWithoutItem` fallthrough (collapses D-02 and D-04 into one code path)

**What:** In 1.21.1, if `useItemOn` (the item-interaction path) returns `ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION`, the game then calls `useWithoutItem` on the same right-click (verified: `.planning/research/PITFALLS.md` Pitfall 10, "Returning `PASS_TO_DEFAULT_BLOCK_INTERACTION` from the main hand is what makes `useWithoutItem` run afterwards"; also documented at `docs.neoforged.net/docs/1.21.1/items/interactionpipeline/`).
**When to use:** Exactly this phase's D-02/D-04 combination — "holding a Soul Block on an already-charged altar" and "empty-handed on a charged altar" both want the *same* reopen-gate logic. The existing `useItemOn` already returns `PASS_TO_DEFAULT_BLOCK_INTERACTION` when `!stack.is(SOUL_BLOCK_ITEM) || !be.isEmpty()` (see current `SoulAltarBlock.java` line 96-99) — this is a happy accident that Phase 3 can lean on directly: **do not duplicate the "is the altar charged + does the job block map to a profession" gate check in both methods.** Put it once in `useWithoutItem` and let the already-charged branch of `useItemOn` fall through to it.
**Example:**
```java
// useItemOn (extend the existing method) — only the EMPTY-altar + Soul-Block-in-hand
// branch does anything new; everything else already falls through correctly.
@Override
protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
    if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be)) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    if (!stack.is(ModItems.SOUL_BLOCK_ITEM.get()) || !be.isEmpty()) {
        // D-04: already charged (or wrong item) — fall through to useWithoutItem's reopen gate.
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    if (!level.isClientSide) {
        var profession = ProfessionResolver.fromAbove(level, pos);
        if (profession.isEmpty()) {
            sendPol08NoJobOrUnmapped(player, level, pos); // D-11
            return ItemInteractionResult.CONSUME;
        }
        be.setHeldSoulBlock(stack.copyWithCount(1));
        stack.consume(1, player);
        be.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new BindingAltarMenu(id, inv, ContainerLevelAccess.create(level, pos), pos),
                    Component.translatable("container.secondshift.binding_altar")),
                buf -> buf.writeBlockPos(pos));
        }
    }
    return ItemInteractionResult.sidedSuccess(level.isClientSide);
}

// useWithoutItem — becomes the single reopen gate for D-02 AND (via fallthrough) D-04
@Override
protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                           BlockHitResult hit) {
    if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be) || be.isEmpty()) {
        if (!level.isClientSide && be != null && be.isEmpty()) {
            sendPol08NoSoulBlock(player); // D-11: only when truly empty-handed on an empty altar
        }
        return InteractionResult.PASS;
    }
    if (!level.isClientSide) {
        var profession = ProfessionResolver.fromAbove(level, pos);
        if (profession.isEmpty()) {
            sendPol08NoJobOrUnmapped(player, level, pos); // D-11
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new BindingAltarMenu(id, inv, ContainerLevelAccess.create(level, pos), pos),
                    Component.translatable("container.secondshift.binding_altar")),
                buf -> buf.writeBlockPos(pos));
        }
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
}
```
**Trade-offs:** Slightly less obvious control flow (the reader must know about the fallthrough rule) in exchange for zero duplicated gate logic. Comment the fallthrough explicitly, since Pitfall 10 documents this as the single most version-specific/non-obvious behavior in the whole interaction pipeline.

### Pattern 5: `ProfessionResolver` — no hardcoded list

**What:** `BlockState` (job site) → `Optional<VillagerProfession>` via the vanilla registries only.
**When to use:** Every "is this a real job site, and if so for which profession" check, this phase and Phase 5.
**Example:**
```java
// Source: net/minecraft/world/entity/ai/village/poi/PoiTypes.java line 100 (decompiled, verified)
//         net/minecraft/world/entity/npc/VillagerProfession.java (decompiled, verified — heldJobSite
//         and acquirableJobSite are IDENTICAL predicates for every vanilla profession, both built
//         from the same ResourceKey<PoiType> — see VillagerProfession#register private helpers)
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
**Why `heldJobSite()` and not `acquirableJobSite()`:** For every vanilla profession the two predicates are constructed identically (`register(name, jobSiteKey, sound)` sets both to `p -> p.is(jobSiteKey)` — verified by reading `VillagerProfession.java`'s private `register` overloads). `heldJobSite` is the semantically correct choice because it mirrors what vanilla's own `ResetProfession` brain behavior checks when deciding "does this villager still have a valid job site" — i.e., it is the "this POI type IS this profession's job site" predicate, not the "can an unemployed villager acquire this" predicate. A modded profession could theoretically diverge the two; `heldJobSite` is the more conservative, more correct choice for a gate check. **Confidence: HIGH** (verified from decompiled source, both the predicate identity for vanilla and the semantic distinction from `ResetProfession`'s usage documented in PITFALLS.md's Integration Gotchas table).

### Anti-Patterns to Avoid

- **Registering `ModMenus` but only referencing it from client screen code (Anti-Pattern 1, ARCHITECTURE.md):** The static initializer runs after `RegisterEvent` fires if nothing forces class-loading earlier. `ModMenus.MENUS.register(modBus)` in the constructor is what prevents this — it is the exact bug this phase exists to structurally kill.
- **Importing `client.screen.BindingAltarScreen` from any common class:** Never call `new BindingAltarScreen(...)` from `SoulAltarBlock` or any common code. The only path from server to screen is `serverPlayer.openMenu(MenuProvider, extraDataWriter)` — vanilla's own packet does the rest, and the client's `RegisterMenuScreensEvent` mapping builds the screen.
- **Trusting `getVillagerData()`/`getOffers()` anywhere in this phase:** N/A this phase — no villager is spawned or read; flagging only so the resolver code never accidentally touches `Villager` instance state (Pitfall 3/4 territory belongs to Phase 5).
- **Duplicating the profession-gate check in both `useItemOn` and `useWithoutItem`:** See Pattern 4 — use the fallthrough instead.
- **Hardcoding a `Block → VillagerProfession` map:** Explicitly forbidden by D-08. Always resolve at interaction time via the registries.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Menu open packet + client-side reconstruction | A custom `CustomPacketPayload` to tell the client "open this screen" | `serverPlayer.openMenu(MenuProvider, Consumer<RegistryFriendlyByteBuf>)` + `IMenuTypeExtension.create` | Vanilla's `ClientboundOpenScreenPacket` already does exactly this; hand-rolling it duplicates `MenuType`'s entire purpose and reintroduces exactly the registry-binding risk this phase exists to eliminate |
| "Is the player still near the altar" distance check | A manual `player.distanceToSqr(pos)` comparison scattered across the menu | `AbstractContainerMenu.stillValid(ContainerLevelAccess, Player, Block)` (static helper, verified present) | Vanilla's helper already re-validates the block is still the expected type at the expected position AND the ~8-block distance in one call; reimplementing it risks getting the distance constant or the block-identity check wrong |
| Block → profession mapping | A `Map<Block, ResourceLocation>` literal | `PoiTypes.forState` + `BuiltInRegistries.VILLAGER_PROFESSION` iteration | Explicitly forbidden by D-08; also would silently fail for any modded profession, defeating the stated project goal of modded-profession support "for free" |
| Read-only inventory slot | A slot that intercepts every click handler manually | `Slot` subclass overriding `mayPickup(Player) → false`, `mayPlace(ItemStack) → false` | Vanilla's `Slot.mayPickup`/`mayPlace` are exactly the two hook points that make a slot inert to both pickup and placement, and every downstream vanilla click-handling code path already respects them |

**Key insight:** Every piece of "hard" plumbing in this phase (menu open, distance validation, screen binding) is a vanilla or NeoForge one-liner. The only genuinely new logic this phase writes is the `ProfessionResolver` registry-iteration query and the interaction-fallthrough wiring in `SoulAltarBlock` — everything else is "call the right verified API in the right order."

## Common Pitfalls

(Pulled and phase-scoped from `.planning/research/PITFALLS.md`, which was already researched against this exact codebase and this exact NeoForge build.)

### Pitfall 1: Unbound `DeferredRegister` entry (the exact prior-draft crash)
**What goes wrong:** `NullPointerException: Trying to access unbound value: ResourceKey[minecraft:menu / secondshift:binding_altar]` at `RegisterMenuScreensEvent` dispatch.
**Why it happens:** `ModMenus.MENUS.register(modBus)` is missing from the `SecondShift` constructor, or `ModMenus` is never class-loaded before `RegisterEvent` fires.
**How to avoid:** Add the one line to the existing constructor block (Pattern 1). Add `ModMenus.MENUS` to `ModRegistrySelfCheck`'s `Stream.of(...)` (D-14).
**Warning signs:** Items/blocks load fine but the altar screen never opens; `logs/debug.log` under `forge.logging.markers=REGISTRIES` shows no `secondshift` entry under `minecraft:menu`.

### Pitfall 2: `@EventBusSubscriber(bus=...)` does not control which bus is used
**What goes wrong:** Assuming the `bus` attribute on `@EventBusSubscriber` matters.
**Why it happens:** NeoForge 21.1's `AutomaticEventSubscriber` (verified by decompiling `loader-4.0.43.jar`) ignores the `bus` field entirely and instead inspects whether each `@SubscribeEvent` method's parameter type implements `IModBusEvent`. `RegisterMenuScreensEvent implements IModBusEvent` — so `ClientModBusEvents.onRegisterScreens` will land on the mod bus regardless of the `bus=` attribute written on the class annotation.
**How to avoid:** Don't debug "wrong bus" theories; instead grep `logs/debug.log` for `Subscribing @EventBusSubscriber class … to the {game,mod} event bus`.
**Warning signs:** A handler that "should" fire but doesn't, with no obvious registration error.

### Pitfall 9: Client classes leaking into common code
**What goes wrong:** `NoClassDefFoundError: net/minecraft/client/gui/screens/Screen` on `runServer`, invisible in single-player.
**Why it happens:** `BindingAltarScreen` (or any client type) referenced from a common class's method signature or field type — even behind an `if` that's never taken, the class must still verify.
**How to avoid:** `BindingAltarScreen` lives in `client/screen/`, referenced ONLY from `ClientModBusEvents.onRegisterScreens`. Never call `new BindingAltarScreen(...)` or reference `Minecraft.getInstance()` from `SoulAltarBlock`, `BindingAltarMenu`, or `ProfessionResolver`.
**Warning signs / detection:** `./gradlew runServer` is the mandatory per-phase check (already an established project habit per Phase 2's SUMMARY).

### Pitfall 10: `ItemInteractionResult` vs `InteractionResult` — 1.21.1 is the odd one out
**What goes wrong:** Copying 1.21.2+ (unified `InteractionResult`) tutorial code — it either doesn't compile or silently compiles as an unrelated overload.
**Why it happens:** 1.21.1 uniquely has `ItemInteractionResult useItemOn(...)` and `InteractionResult useWithoutItem(...)` as two separate types; this is already correctly handled in the existing `SoulAltarBlock.java` (both methods have `@Override`).
**How to avoid:** Keep `@Override` on every interaction method extended this phase — the compiler catches a wrong 1.21.1 signature immediately.
**Warning signs:** Right-click with item does nothing while empty-hand works, or vice versa.

### Pitfall 12: Trusting client-sent menu interaction data (SC4's exact concern)
**What goes wrong:** A hand-crafted or malicious `ServerboundContainerClickPacket`/`ServerboundContainerButtonClickPacket` with an out-of-range slot/button index, or a player who moves away/breaks the altar while the menu is open.
**Why it happens:** `stillValid()` returning `true` unconditionally, or a menu with no slot-bounds checking.
**How to avoid (what vanilla gives for free vs. what this phase must add — see dedicated section below).**
**Warning signs:** `ArrayIndexOutOfBoundsException` in a menu class; a menu that stays open after the altar is broken.

### Phase-specific pitfall: SC2/D-15 guardrail placement mismatch
**What goes wrong:** Naively reading SC2 literally and adding a *second* hard-abort check in `FMLCommonSetupEvent`, duplicating the `FMLLoadCompleteEvent` check and creating two divergent guardrail code paths that could disagree.
**Why it happens:** SC2's wording ("the guardrail logs the menu key from `FMLCommonSetupEvent`") predates the Phase 1 architectural decision (already documented in `ModRegistrySelfCheck.java`'s javadoc) that hard-aborts must throw directly from `FMLLoadCompleteEvent`, never from `enqueueWork` inside `FMLCommonSetupEvent` (exceptions there are swallowed by FML's `DeferredWorkQueue` — see PITFALLS.md networking section for the general pattern, and `ModRegistrySelfCheck`'s own javadoc for the specific reasoning already established in this codebase).
**How to avoid:** See the SC2/D-15 Reconciliation section below — one abort path, one informational log line, reusing the existing `commonSetup` handler.

## Code Examples

Verified patterns from decompiled `neoforge-21.1.248-sources.jar` (this machine, `build/moddev/artifacts/`) and Mojang's official 1.21.1 mappings.

### Registering the menu type (`ModMenus.java`)
```java
// Source: net/neoforged/neoforge/common/extensions/IMenuTypeExtension.java (decompiled)
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SecondShift.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<BindingAltarMenu>> BINDING_ALTAR =
            MENUS.register("binding_altar",
                    () -> IMenuTypeExtension.create(BindingAltarMenu::new));

    private ModMenus() {}
}
```

### Binding the screen (`ClientModBusEvents.java` — extend the existing class)
```java
// Source: net/neoforged/neoforge/client/event/RegisterMenuScreensEvent.java (decompiled)
// Verified signature: register(MenuType<? extends M>, MenuScreens.ScreenConstructor<M, U>)
//   where U extends Screen & MenuAccess<M>
@SubscribeEvent
static void onRegisterScreens(RegisterMenuScreensEvent event) {
    event.register(ModMenus.BINDING_ALTAR.get(), BindingAltarScreen::new);
    LOGGER.info("[SecondShift] registered BindingAltarScreen for secondshift:binding_altar");
}
```

### `MenuProvider` on the block entity (`SoulAltarBlockEntity.java` — extend)
```java
// Source: net/minecraft/world/MenuProvider.java (decompiled) — interface has exactly
// two members: getDisplayName() and the inherited MenuConstructor#createMenu(int, Inventory, Player)
public class SoulAltarBlockEntity extends BlockEntity implements MenuProvider {
    @Override
    public Component getDisplayName() {
        return Component.translatable("container.secondshift.binding_altar");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new BindingAltarMenu(containerId, playerInv,
                ContainerLevelAccess.create(this.getLevel(), this.getBlockPos()), this.getBlockPos());
    }
}
```
Note: this makes `SoulAltarBlockEntity` a valid `MenuProvider` in its own right — an alternative to the `SimpleMenuProvider` lambda shown in Pattern 4. Either is correct; using `SoulAltarBlockEntity implements MenuProvider` directly (as CONTEXT.md's code_context section specifies) means `player.openMenu(be, buf -> buf.writeBlockPos(pos))` can pass `be` itself as the `MenuProvider`, which is slightly less code at the call site than constructing a `SimpleMenuProvider`. **Recommendation: use `SoulAltarBlockEntity implements MenuProvider` directly** — it matches the CONTEXT.md-specified integration point exactly ("`SoulAltarBlockEntity implements MenuProvider`; server opens via `serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(pos))`").

### The read-only slot (`SoulSlot.java`, per UI-SPEC D-06)
```java
// Source: net/minecraft/world/inventory/Slot.java (decompiled) — mayPickup/mayPlace
// are both plain overridable instance methods (verified, lines 59/114)
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
The slot's backing `Container` is a 1-slot wrapper over `SoulAltarBlockEntity#getHeldSoulBlock()`/`setHeldSoulBlock()` — a `SimpleContainer` will NOT work directly since it needs to read/write the BE's actual field, not an independent copy. Recommend a small inline `Container` implementation (or the BE itself implementing `Container` for exactly one slot) that delegates `getItem(0)`/`setItem(0, stack)` to the BE's existing `getHeldSoulBlock()`/`setHeldSoulBlock()` accessors — this keeps the BE as the single source of truth, consistent with ARCHITECTURE.md's "the BE persists only its Soul Block slot" pattern. **This detail was not explicitly resolved by CONTEXT.md and is flagged as an Open Question below** (SimpleContainer vs. custom Container).

### `stillValid` (verified static helper signature)
```java
// Source: net/minecraft/world/inventory/AbstractContainerMenu.java line 70 (decompiled)
// protected static boolean stillValid(ContainerLevelAccess access, Player player, Block targetBlock)
@Override
public boolean stillValid(Player player) {
    return AbstractContainerMenu.stillValid(this.access, player, ModBlocks.SOUL_ALTAR.get());
}
```
This one call gives, for free: (a) the block at `access`'s position is still `ModBlocks.SOUL_ALTAR.get()` (catches "altar was broken" and "altar was replaced by something else"), and (b) the player is within the vanilla `Container#isValidUseTarget` distance formula — verified to be a squared-distance check against `8.0` blocks, expanded by the target block's bounding box (this is where CONTEXT.md's D-16 "~8-block distance check" comes from — it is not a number the plan needs to implement, it is vanilla's existing constant, reused).

## State of the Art

| Old Approach (pre-1.21.1 / other MC versions) | Current Approach (1.21.1 / NeoForge 21.1.248) | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `MenuScreens.register(...)` from `FMLClientSetupEvent` (1.20.x pattern) | `RegisterMenuScreensEvent#register` on the mod bus, `Dist.CLIENT` | NeoForge's networking/registration rewrite (1.20.5+ era) | The vanilla screen map is not safe to mutate from arbitrary setup code; this project already avoids the old pattern per CLAUDE.md "What NOT to Use" |
| Unified `InteractionResult` (1.21.2+) | Two separate types: `ItemInteractionResult useItemOn(...)` and `InteractionResult useWithoutItem(...)` | 1.21.1 only — reunified in 1.21.2 | Every 1.21.2+ tutorial and most LLM training data is wrong for this exact target version; already correctly handled in existing code |
| `@Mod.EventBusSubscriber` (Forge) | `@EventBusSubscriber` (NeoForge, `net.neoforged.fml.common`) | Forge → NeoForge fork | Already correctly used throughout this codebase |

**Deprecated/outdated:** Nothing new deprecated by this phase specifically — the whole menu/screen API surface used here (`MenuType`, `AbstractContainerMenu`, `AbstractContainerScreen`, `RegisterMenuScreensEvent`) is the current, stable 1.21.1 API with no known pending replacement inside this Minecraft version.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `AbstractContainerMenu` exposes a vanilla helper method for adding the standard 3×9 + hotbar player-inventory slots at fixed offsets (referenced in Pattern 3's example as `addStandardInventorySlots`) — the exact method name was not independently re-verified in this research session (STACK.md/ARCHITECTURE.md do not name it, and it was not grepped from the decompiled source in this pass). | Code Examples / Pattern 3 | LOW — if the helper doesn't exist under that name, the planner/executor writes the standard 36-slot loop by hand (a well-known, copy-pasteable vanilla pattern from any stock `AbstractContainerMenu` subclass, e.g. `CraftingMenu`/`FurnaceMenu` source) instead. Either way the screen still opens; this only affects code tidiness, not GUI-01/ALTAR-03/SC1-4 correctness. |
| A2 | The backing `Container` for the one display-only slot should be a small custom `Container` (or the BE itself implementing `Container`) delegating to `SoulAltarBlockEntity#getHeldSoulBlock()`/`setHeldSoulBlock()`, rather than `SimpleContainer`. | Code Examples ("The read-only slot") | LOW — if `SimpleContainer` is used instead, the slot would show a copy of the item rather than the BE's live state, meaning the screen could show a stale/wrong item after the BE updates (e.g., after D-01 sockets a new Soul Block while the screen somehow stays open). Flagged as an Open Question for the planner to settle explicitly, since D-06/UI-SPEC did not specify the container-wiring mechanism, only the slot-flag behavior. |

**All other claims in this document are `[VERIFIED]`** — confirmed either by direct inspection of `build/moddev/artifacts/neoforge-21.1.248-sources.jar` (the exact decompiled source of the pinned NeoForge build) or by cross-reference against `.planning/research/{STACK,ARCHITECTURE,PITFALLS}.md`, which were themselves produced with `javap` against the installed jars per this project's established research methodology (see CLAUDE.md Sources list).

## Open Questions (RESOLVED)

1. **Backing `Container` implementation for the display-only slot (A2 above). — RESOLVED: option (b), `AltarSoulContainer`.** 03-01-PLAN.md Task 1 picks the dedicated wrapper class over `SoulAltarBlockEntity implements Container`, exactly per this recommendation.
   - What we know: The slot must be display-only (D-06, confirmed via `mayPickup`/`mayPlace` overrides) and must show the BE's `heldSoulBlock` field.
   - What's unclear: Whether to (a) make `SoulAltarBlockEntity implements Container` with a 1-element `getContainerSize()`, or (b) write a tiny anonymous/named `Container` wrapper class that delegates to the BE's existing `getHeldSoulBlock()`/`setHeldSoulBlock()` methods, keeping `Container` off the BE's own public interface.
   - Recommendation: Option (b) — a small dedicated wrapper — keeps `SoulAltarBlockEntity`'s public surface unchanged from Phase 2 (no new `Container` methods like `clearContent()`/`isEmpty()` that could collide with the existing `isEmpty()` semantics, which currently means "no Soul Block socketed," not "container has size 0"). Low risk either way; flag for planner's `PLAN.md` task breakdown to pick one explicitly.

2. **Exact vanilla helper name for adding the 36 player-inventory slots (A1 above). — RESOLVED: no shared helper, write the loop directly.** 03-01-PLAN.md Task 1 writes the standard two `for` loops inline (as every vanilla menu does) rather than depending on an unverified named helper.
   - What we know: Every stock `AbstractContainerMenu` subclass (furnace, crafting table, etc.) has this loop; it is extremely standard 1.21.1 boilerplate.
   - What's unclear: Whether `AbstractContainerMenu` itself exposes a named helper (e.g., `addStandardInventorySlots(Inventory, int, int)`) or whether every vanilla menu just inlines the two `for` loops.
   - Recommendation: Not worth a follow-up research pass — this is copy-paste boilerplate available in any decompiled vanilla menu class (e.g. `net/minecraft/world/inventory/CraftingMenu.java` in the same sources jar) and does not block planning; the executor can grep the sources jar directly during implementation if needed.

## Environment Availability

Skipped — this phase has no external dependencies beyond the already-verified, already-running toolchain (NeoForge 21.1.248, Gradle 9.2.1, Java 21, all confirmed operational since Phase 1/2). `./gradlew runClient`, `./gradlew runServer`, and `./gradlew runGameTestServer` are all already-working run configurations in the current `build.gradle` (`runGameTestServer` confirmed present and gated to the `secondshift` namespace via `systemProperty 'neoforge.enabledGameTestNamespaces'`).

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | NeoForge GameTest (`net.minecraft.gametest.framework`, `net.neoforged.neoforge.gametest`) — already in use (`HarvesterGameTests.java`) |
| Config file | `build.gradle` `runs.gameTestServer` (already present, confirmed) |
| Quick run command | `./gradlew runClient` (manual/visual — menu open + title check, SC1/SC3) |
| Full suite command | `./gradlew runGameTestServer` (automated — SC4 stillValid rejection) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| GUI-01 / SC1 | Menu opens under `runClient`, titled "Binding Altar", no crash | manual (human-verify — screen *rendering* cannot be asserted headless) | `./gradlew runClient` | N/A — visual check |
| GUI-01 / SC2 | `runServer` loads clean; guardrail asserts `secondshift:binding_altar` bound | automated | `./gradlew runServer` (crash = fail); log-grep for the commonSetup line | ✅ existing `ModRegistrySelfCheck` pattern, extend Stream.of(...) |
| ALTAR-03 / SC1 | No job block / no Soul Block → no crash, no menu | GameTest | `./gradlew runGameTestServer` | ❌ Wave 0 — new test method |
| SC4 | Malformed/out-of-range interaction rejected server-side; `stillValid` false after altar break or player move | GameTest | `./gradlew runGameTestServer` | ❌ Wave 0 — new test method(s) |

### Sampling Rate
- **Per task commit:** `./gradlew build` (compile check) + targeted manual `runClient` spot-check when menu/screen code changes
- **Per wave merge:** `./gradlew runGameTestServer` (full GameTest suite) + `./gradlew runServer` (client-class-leak gate, per Phase 2's established habit)
- **Phase gate:** `runClient` visual pass (SC1, SC3) + `runServer` clean load (SC2) + `runGameTestServer` green (SC4) before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] A `BindingAltarGameTests.java` (or new methods on `HarvesterGameTests.java` — planner's discretion per CONTEXT.md) covering:
  - Open menu server-side via `helper.makeMockServerPlayerInLevel()` (verified present: `GameTestHelper#makeMockServerPlayerInLevel() -> ServerPlayer`, decompiled, line 291) + a manually-constructed `BindingAltarMenu`; assert non-null menu instance and `stillValid(player) == true` while altar + job block + proximity are all valid.
  - Break the altar block (or the job block above it) mid-test; assert `stillValid(player) == false`.
  - Move the mock player far away (`player.teleportTo(...)`, > 8 blocks); assert `stillValid(player) == false`.
  - No job block / non-job-site block above the altar + right-click with Soul Block in hand (`helper.useBlock(pos, player)` per the verified `GameTestHelper#useBlock` helper, decompiled, line 237) → assert no exception, no menu opened, altar remains unsocketed.
- [ ] `ProfessionResolver` unit-style GameTest (or plain assertion inside one of the above): place a real vanilla job-site block (e.g. `Blocks.CARTOGRAPHY_TABLE`) above the altar, assert `ProfessionResolver.fromAbove(...)` returns `VillagerProfession.CARTOGRAPHER`; place a non-POI block (e.g. `Blocks.STONE`), assert `Optional.empty()`.

*(No Wave 0 gap for GUI-01/SC1/SC2/SC3 — those are the pre-existing manual `runClient`/`runServer` loop already established in Phase 1/2 and require no new test infrastructure.)*

## Security Domain

`security_enforcement` is enabled (`.planning/config.json` — absent key treated as enabled; `security_asvs_level: 1`, `security_block_on: "high"`).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Single-player, no auth boundary in this phase |
| V3 Session Management | No | N/A |
| V4 Access Control | Yes | `stillValid()` is the access-control boundary for the menu — re-checked every server tick the menu is open |
| V5 Input Validation | Yes | Any menu interaction packet (slot click, button click) must be bounds-checked server-side; SC4 is explicitly an input-validation acceptance criterion |
| V6 Cryptography | No | N/A |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Hand-crafted `ServerboundContainerClickPacket` with an out-of-range slot index | Tampering | Vanilla's own slot-index bounds-checking in `AbstractContainerMenu#clicked` (this phase has only one custom slot, display-only — the highest-risk custom logic is `mayPickup`/`mayPlace` returning `false`, which vanilla's click-handling already respects without additional code) |
| Menu left open after the altar is broken or the player walks away (state-confusion / stale-reference) | Tampering / Denial of Service | `stillValid()` implemented via the verified `AbstractContainerMenu.stillValid(ContainerLevelAccess, Player, Block)` static helper (Pattern 3) — re-checked automatically by vanilla's menu-tick loop; D-12 additionally requires sending the forced-close message before the container closes |
| A modified/malicious client sending a menu-open request or interaction for an altar it hasn't actually right-clicked | Spoofing | N/A this phase — the client never initiates the open; the server decides to open (D-01/D-02) and the client only *receives* the open packet. No client→server payload exists this phase (explicitly out of scope) |

No new cryptography, no new auth boundary, no new persisted secrets this phase — the security surface is entirely "does `stillValid` correctly reject a menu whose backing state changed," which is directly covered by the SC4 GameTest plan above.

## Sources

### Primary (HIGH confidence)
- `build/moddev/artifacts/neoforge-21.1.248-sources.jar` (decompiled on this machine) — `net/minecraft/world/inventory/{AbstractContainerMenu,ContainerLevelAccess,Slot,MenuType}.java`, `net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java`, `net/minecraft/world/{MenuProvider,SimpleMenuProvider}.java`, `net/minecraft/world/entity/player/{Player,ServerPlayer... via server/level/ServerPlayer}.java`, `net/minecraft/world/entity/npc/VillagerProfession.java`, `net/minecraft/world/entity/ai/village/poi/{PoiType,PoiTypes}.java`, `net/minecraft/gametest/framework/GameTestHelper.java`, `net/neoforged/neoforge/common/extensions/{IMenuTypeExtension,IPlayerExtension}.java`, `net/neoforged/neoforge/network/IContainerFactory.java`, `net/neoforged/neoforge/client/event/RegisterMenuScreensEvent.java` — read directly in this research session
- `.planning/research/STACK.md` §2 (menu crash diagnosis + registration pattern), §9 (POI→profession) — HIGH, itself sourced from `javap` against the same pinned binary
- `.planning/research/ARCHITECTURE.md` — common/client split, class table, build-order Slice 4 (this phase) — HIGH
- `.planning/research/PITFALLS.md` — Pitfalls 1, 2, 9, 10, 12 — HIGH, sourced from decompiled bytecode + the prior draft's actual crash report and logs
- `src/main/java/com/cxmxrgo/secondshift/{SecondShift,ModRegistrySelfCheck}.java`, `content/block/SoulAltarBlock.java`, `content/blockentity/SoulAltarBlockEntity.java`, `client/ClientModBusEvents.java`, `registry/{ModBlocks,ModBlockEntities}.java`, `gametest/HarvesterGameTests.java`, `build.gradle` — current repo state, read directly this session

### Secondary (MEDIUM confidence)
None needed — every claim in this document traces to a primary source.

### Tertiary (LOW confidence)
- A1 (exact name of a vanilla player-inventory-slot-adding helper) — not independently re-verified this session; flagged in the Assumptions Log with a low-risk, non-blocking resolution path.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — zero new dependencies, every API decompiled and read directly this session
- Architecture: HIGH — matches `.planning/research/ARCHITECTURE.md`'s Slice 4 exactly, which was itself the phase gate this document plans
- Pitfalls: HIGH — sourced from actual prior-draft crash reports/logs plus decompiled bytecode, not training-data recollection

**Research date:** 2026-09-04
**Valid until:** No expiry expected within this project's lifetime — the toolchain is pinned (`neo_version=21.1.248`, no upgrade planned) and every API used is stable, non-experimental 1.21.1 surface.
