# Architecture Research

**Domain:** Minecraft content mod — NeoForge 1.21.1 (21.1.248), single-player, villager/trading overhaul with a necromancy theme
**Researched:** 2026-09-04
**Confidence:** HIGH for structure and side-split (NeoForge docs + STACK.md's binary-verified API surface); MEDIUM for the level-up interception shape (STACK.md gap #3) and the promotion-ritual UX (open design question)

> Builds on `STACK.md` (toolchain, verified API signatures, the crash diagnosis) and `FEATURES.md`
> (scope, restock/profession-loss landmines, "pick 2 of N" picker). This document does **not** repeat
> API signatures — it defines *where code lives*, *what talks to what*, and *what order to build in*.
> Where STACK.md flags a verification spike, this document places it in the build sequence.

---

## Standard Architecture

### The shape of a NeoForge 1.21.1 mod of this scope

A mod this size is a **single Gradle module, single source set** (`src/main/java` + `src/main/resources`),
with client-only code isolated by package and by annotation — *not* by a separate source set. NeoForge's
`@EventBusSubscriber(value = Dist.CLIENT, ...)` and `@Mod(dist = Dist.CLIENT)` handle physical-side
class-loading; there is no `src/client` split in MDG the way old Forge/1.20.x sometimes did it.

Three conceptual layers:

```
┌───────────────────────────────────────────────────────────────────────┐
│  CLIENT  (physical client only — never class-loaded on a server)       │
│  ┌───────────────┐  ┌──────────────────┐  ┌────────────────────────┐   │
│  │ Screens +     │  │ RegisterMenu-    │  │ Render layers (v1.x),  │   │
│  │ widgets       │  │ ScreensEvent     │  │ particles, key maps    │   │
│  │ (EditBox,     │  │ handler          │  │                        │   │
│  │ trade picker) │  │ (mod bus)        │  │                        │   │
│  └──────┬────────┘  └────────┬─────────┘  └────────────────────────┘   │
│         │ reads menu fields  │ binds MenuType → Screen ctor            │
├─────────┼───────────────────┼──────────────────────────────────────────┤
│  COMMON │ (both sides — logic, sync boundary, registration)            │
│  ┌──────▼─────────┐  ┌───────▼──────────┐  ┌────────────────────────┐  │
│  │ BindingAltar-  │  │ registry/*       │  │ network/* payloads     │  │
│  │ Menu           │  │ (DeferredRegister│  │ + StreamCodecs         │  │
│  │ (Abstract-     │  │  holders — the   │  │ + ServerPayloadHandler │  │
│  │  ContainerMenu)│  │  crash surface)  │  │                        │  │
│  └──────┬─────────┘  └──────────────────┘  └───────────┬────────────┘  │
│  ┌──────▼──────────────┐  ┌────────────────┐  ┌────────▼────────────┐  │
│  │ content/            │  │ employee/      │  │ trade/              │  │
│  │  SoulAltarBlock     │  │  EmployeeData  │  │  TradePoolCache     │  │
│  │  SoulAltarBlockEnt. │  │  (attachment)  │  │  ProfessionResolver │  │
│  │  HarvesterItem      │  │  event handlers│  │  OfferCandidate     │  │
│  └─────────────────────┘  └───────┬────────┘  └────────────────────┘  │
├──────────────────────────────────┼───────────────────────────────────┤
│  VANILLA / NEOFORGE  (systems we hook, never fork)                    │
│  minecraft:villager entity · MerchantOffers · VillagerTrades pool ·   │
│  POI registry · LivingConversionEvent · LivingDropsEvent ·            │
│  VillagerTradesEvent · AttachmentType sync · game + mod event buses   │
└──────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Owns | Talks to | Side |
|-----------|------|----------|------|
| `SecondShift` (`@Mod`) | Wiring: attaches every `DeferredRegister` to the mod bus, registers config, registers game-bus handlers not covered by `@EventBusSubscriber` | all `registry/*`, `network/ModPayloads` | common |
| `registry/Mod*` | One `DeferredRegister` each (items, blocks, block entities, menus, attachments, data components, sounds, creative tab). Holds the `DeferredHolder` constants everything else references | vanilla registries | common |
| `content/block/SoulAltarBlock` | Block behaviour: `EntityBlock`, `useWithoutItem` → open menu, drop BE contents on break, read the block above it | `SoulAltarBlockEntity`, `BindingAltarMenu` (via `MenuProvider`), `ProfessionResolver` | common |
| `content/blockentity/SoulAltarBlockEntity` | **Only persistent altar state**: the inserted Soul Block `ItemStack` (1 slot). Save/load + `getUpdateTag`/`getUpdatePacket`. Nothing about the employee | vanilla BE sync | common |
| `content/item/HarvesterItem` | The tool. `SwordItem` subclass or plain item with attributes component. Marker for the harvest handler | — | common |
| `employee/EmployeeData` | The employee record: name, profession id, picked tier, chosen `MerchantOffers`, `pendingLevel`, `lastRestockTick`. `CODEC` + `STREAM_CODEC`. **This is the entire identity of an employee** | attachment registry | common |
| `employee/EmployeeManager` | Static ops: `bind(...)`, `promote(...)`, `rebuildOffers(...)`, `isEmployee(entity)` | `EmployeeData`, vanilla `Villager` API | common |
| `employee/EmployeeTier` | XP thresholds (0/10/70/150/250), `levelForXp(int)`, tier → HR job-title map | — | common |
| `employee/event/TraitHandlers` | Game bus: cancel `LivingConversionEvent.Pre`; breeding suppression | attachment | common |
| `employee/event/DeathDropHandler` | Game bus: `LivingDropsEvent` — Harvester kill on plain villager → 1 Soul Fragment; any death of an employee → Soul Block (+slime), Harvester kill → clean recovery | attachment, `ModItems` | common |
| `employee/event/LevelUpHandler` | Game bus, throttled entity tick: revert vanilla-appended offers to `EmployeeData.chosenOffers`; detect XP tier crossing → set `pendingLevel`, notify player | `EmployeeData`, `EmployeeTier` | common |
| `employee/event/RestockHandler` | Game bus, server tick: mod-owned restock timer per employee | `EmployeeData`, config | common |
| `trade/TradePoolHandler` | Game bus: `VillagerTradesEvent` → populate `TradePoolCache` (captures modded trades) | `TradePoolCache` | common |
| `trade/TradePoolCache` | `Map<professionId, Int2ObjectMap<List<ItemListing>>>` rebuilt on reload; `rollCandidates(profession, tier, level)` → `List<MerchantOffer>` (materialized) | vanilla `ItemListing` | common |
| `trade/ProfessionResolver` | `BlockState` (job site) → `Optional<VillagerProfession>` via `PoiTypes.forState` + profession registry, filtering `none`/`nitwit` | vanilla POI + profession registries | common |
| `menu/BindingAltarMenu` | `AbstractContainerMenu`. Holds, server-side, the transient candidate `MerchantOffer` list + mode (BIND / PROMOTE) + target employee UUID + resolved profession + tier. Client-side: the same data, read from the open-data buffer. No slots beyond player inv + the altar's 1 slot | `SoulAltarBlockEntity`, screen (indirectly) | common |
| `network/ModPayloads` | Mod bus: `RegisterPayloadHandlersEvent` registration | payloads, handlers | common |
| `network/SelectTradesPayload` | C→S record: altar pos, chosen indices, employee name | — | common |
| `network/ServerPayloadHandler` | Server-side: validate against the *server's* menu state, sanitize name, call `EmployeeManager.bind`/`promote` | `EmployeeManager`, `BindingAltarMenu` | common |
| `client/ClientModBusEvents` | `@EventBusSubscriber(bus = MOD, value = Dist.CLIENT)`: `RegisterMenuScreensEvent` → `BindingAltarScreen::new` | `ModMenus`, `BindingAltarScreen` | **client** |
| `client/screen/BindingAltarScreen` | `AbstractContainerScreen<BindingAltarMenu>`: renders profession header, `NameEntryWidget`, `TradePickerWidget`, career-path preview (v1.x); sends `SelectTradesPayload` on confirm | `BindingAltarMenu` (reads fields), `PacketDistributor` | **client** |
| `Config` | `ModConfigSpec`: fragment drop count, restock interval, immunity toggles | — | common |

---

## Recommended Project Structure

```
src/main/java/com/cxmxrgo/secondshift/
├── SecondShift.java                 @Mod("secondshift"). Constructor: attach every DeferredRegister
│                                    to modBus, register payloads, register game-bus handlers,
│                                    register config. THE single wiring point.
├── Config.java                      ModConfigSpec + values
│
├── registry/                        DeferredRegister holders — class-loaded from SecondShift ctor
│   ├── ModItems.java                Harvester, Soul Fragment, Soul Block (BlockItem)
│   ├── ModBlocks.java               Soul Altar
│   ├── ModBlockEntities.java        soul_altar
│   ├── ModMenus.java                binding_altar  ← the holder that was never bound in the draft
│   ├── ModAttachments.java          employee
│   ├── ModDataComponents.java       (only if a bound-soul item needs state — see STACK.md §5)
│   ├── ModSounds.java               (v1: optional; vanilla soul sounds suffice)
│   └── ModCreativeTab.java          one tab, all items
│
├── content/
│   ├── item/
│   │   └── HarvesterItem.java
│   ├── block/
│   │   └── SoulAltarBlock.java      implements EntityBlock
│   └── blockentity/
│       └── SoulAltarBlockEntity.java
│
├── employee/
│   ├── EmployeeData.java            record + CODEC + STREAM_CODEC
│   ├── EmployeeManager.java         bind / promote / rebuildOffers / isEmployee
│   ├── EmployeeTier.java            thresholds + job titles
│   └── event/
│       ├── TraitHandlers.java       @EventBusSubscriber(bus = GAME)
│       ├── DeathDropHandler.java    @EventBusSubscriber(bus = GAME)
│       ├── LevelUpHandler.java      @EventBusSubscriber(bus = GAME)
│       └── RestockHandler.java      @EventBusSubscriber(bus = GAME)
│
├── trade/
│   ├── TradePoolHandler.java        @EventBusSubscriber(bus = GAME) — VillagerTradesEvent
│   ├── TradePoolCache.java
│   ├── ProfessionResolver.java
│   └── OfferCandidate.java          MerchantOffer + display metadata (index, locked-restock hint)
│
├── menu/
│   └── BindingAltarMenu.java        AbstractContainerMenu (BOTH sides)
│
├── network/
│   ├── ModPayloads.java             @EventBusSubscriber(bus = MOD) — RegisterPayloadHandlersEvent
│   ├── SelectTradesPayload.java     CustomPacketPayload (C→S)
│   └── ServerPayloadHandler.java
│
└── client/                          EVERYTHING here is physical-client-only
    ├── ClientModBusEvents.java      @EventBusSubscriber(bus = MOD, value = Dist.CLIENT)
    ├── ClientGameBusEvents.java     @EventBusSubscriber(bus = GAME, value = Dist.CLIENT)  (if needed)
    └── screen/
        ├── BindingAltarScreen.java  AbstractContainerScreen<BindingAltarMenu>
        ├── TradePickerWidget.java   toggle list, enforces "max 2"
        └── NameEntryWidget.java     EditBox wrapper, pre-filled default

src/main/resources/
├── META-INF/  (neoforge.mods.toml lives in src/main/templates/ — see STACK.md)
├── assets/secondshift/
│   ├── lang/en_us.json
│   ├── models/ textures/ blockstates/
│   └── ...
└── data/secondshift/
    └── recipe/ soul_block.json, harvester.json          (plain vanilla serializers)
```

### Structure Rationale

- **`registry/` is flat and separate from `content/`.** The holder classes are pure data
  (`DeferredHolder` constants) and must be trivially class-loadable from the mod constructor. Keeping
  behaviour (`SoulAltarBlock`) out of them means loading `ModBlocks` doesn't drag in block logic, and
  nothing tempts you to reference a registry class only from client code. **This directly addresses the
  prior crash** (STACK.md §2): the fix is "reference every register from the constructor", and a flat
  `registry/` package makes that a single obvious block of `.register(modBus)` calls.
- **`employee/` is the domain core.** Everything that makes a villager an "employee" — the attachment,
  the manager, the four event handlers — is one package. The identity lives in `EmployeeData`; the
  handlers are stateless and key off `villager.hasData(EMPLOYEE)`. No subclass, per PROJECT constraint.
- **`trade/` is isolated because it is the risky, vanilla-coupled part.** Pool reading, offer
  materialization, and profession resolution all touch fragile vanilla internals (STACK.md §8–9). One
  package = one place to look when a modded profession breaks.
- **`menu/` is common, `client/screen/` is client.** This is the load-bearing split (below). The menu
  and the screen never share a package, so it is visually obvious which side a class is on.
- **`network/` keeps payload records common but the server handler explicit.** The C→S handler is the
  trust boundary; giving it its own file makes "re-validate everything here" unmissable.

---

## Common vs Client Split (the known failure area)

The prior draft **crashed at client init** during `RegisterMenuScreensEvent` with
`Trying to access unbound value: ResourceKey[minecraft:menu / secondshift:binding_altar]`. STACK.md §2
identifies the cause: the `MenuType` was never actually added to the registry (register not attached, or
holder class never loaded before `RegisterEvent`). The architecture must make that mistake structurally
hard.

### What MUST be physical-client-only

These reference classes that **do not exist on a dedicated server** (`Screen`, `AbstractContainerScreen`,
`Minecraft`, `GuiGraphics`, `EditBox`, `Font`, `RenderType`, `EntityRenderer`). Class-loading them
server-side = `NoClassDefFoundError` crash.

| Class / handler | Why client-only | How it's isolated |
|---|---|---|
| `client/screen/BindingAltarScreen` and all widgets | extends `AbstractContainerScreen` | in `client/`, only referenced from `ClientModBusEvents` |
| `client/ClientModBusEvents` (holds the `RegisterMenuScreensEvent` handler) | calls `event.register(type, Screen::new)` | `@EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)` — NeoForge only loads/registers the class on `Dist.CLIENT` |
| `client/ClientGameBusEvents` (client particles, key maps, if any) | client render/input APIs | `@EventBusSubscriber(bus = GAME, value = Dist.CLIENT)` |
| any custom render layer / model (v1.x) | render pipeline | `client/render/`, referenced only from a `Dist.CLIENT` mod-bus handler (`EntityRenderersEvent`) |

### What is COMMON (runs on both sides)

| Class | Note |
|---|---|
| **`registry/ModMenus`** and every other `registry/Mod*` | The `MenuType<?>` *itself* is a common registry object. Only the **Screen binding** is client. This distinction is exactly what the draft got wrong. |
| `menu/BindingAltarMenu` | `AbstractContainerMenu` is a common class. Instantiated on the server (real menu) **and** the client (mirror, built from the open-data buffer). Both constructors live here. |
| `content/**` — block, block entity, item | Block entities and blocks are server-authoritative with a client mirror via BE sync. |
| `employee/**` including all four event handlers | Trait/death/level-up/restock logic is server-side game logic; the handlers themselves are common classes on the game bus. |
| `trade/**` | Pool cache and resolver are server-side logic, common classes. |
| `network/**` payload records + `StreamCodec`s + `ServerPayloadHandler` | Records are common; the handler runs server-side but is a common class. |
| `Config` | Common. |

### How NeoForge 1.21.1 wants the separation done

1. **Annotation-driven, not source-set-driven.** Use
   `@EventBusSubscriber(modid = SecondShift.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)`
   on `ClientModBusEvents`. NeoForge scans for this annotation and only registers the class on the
   matching physical side. (`bus` defaults to `Bus.GAME`; **you must set `Bus.MOD` explicitly** for
   `RegisterMenuScreensEvent` because it is a mod-bus event.)
2. **Optionally** a second entrypoint: `@Mod(value = SecondShift.MODID, dist = Dist.CLIENT)` on a
   `SecondShiftClient` class whose constructor does client-only wiring. For this mod's scope the
   `@EventBusSubscriber` approach alone is enough — add `SecondShiftClient` only if client setup grows
   beyond screen registration.
3. **`neoforge.mods.toml` has no per-class side field.** `side = "BOTH"` on the dependency blocks
   (STACK.md) just means the mod loads on both physical sides. Class-level separation is purely the
   annotations above plus not importing `client/` from common code.
4. **Common code never imports `client/`.** To open a screen, common code calls
   `serverPlayer.openMenu(MenuProvider, extraDataWriter)` on the **server**. Vanilla sends the open
   packet; the client's `RegisterMenuScreensEvent` mapping constructs the screen. Common code never
   names a `Screen` type.
5. **Guardrail** (STACK.md §2): in `FMLCommonSetupEvent` log
   `BuiltInRegistries.MENU.getKey(ModMenus.BINDING_ALTAR.get())`. If it throws or logs null, the
   register was never attached — caught in seconds instead of a crash report. Keep
   `forge.logging.markers=REGISTRIES` on (MDK default).

### Registration timing (mod bus vs game bus)

| Bus | Accessor | What registers here | When |
|---|---|---|---|
| **Mod event bus** | `IEventBus` param of `SecondShift(IEventBus modBus, ModContainer c)` | `DeferredRegister.register(modBus)` for **all** registers; `RegisterPayloadHandlersEvent`; `RegisterMenuScreensEvent` (client); `BuildCreativeModeTabContentsEvent`; `FMLCommonSetupEvent`; `RegisterEvent` | Startup, once. Mod-bus events may run in parallel — use `event.enqueueWork()` for main-thread work in `FMLCommonSetupEvent`. |
| **Game event bus** | `NeoForge.EVENT_BUS` (or `@EventBusSubscriber` with default `Bus.GAME`) | `LivingConversionEvent.Pre`, `LivingDropsEvent`, `VillagerTradesEvent`, `EntityTickEvent`/`ServerTickEvent`, `FinalizeSpawnEvent`, `PlayerInteractEvent` | Gameplay, repeatedly. |

**The rule that prevents the crash:** every `DeferredRegister` is created in a `static` field of a
`registry/Mod*` class, and **every one of those classes is referenced from the `SecondShift`
constructor** via its `.register(modBus)` call. That reference forces class-loading → runs the static
initializer → queues the entries → the mod bus later fires `RegisterEvent` → entries land in the
registry. A register that is only touched from client screen code loads its class *after* `RegisterEvent`
has already fired and silently registers nothing.

```java
public SecondShift(IEventBus modBus, ModContainer container) {
    ModItems.ITEMS.register(modBus);
    ModBlocks.BLOCKS.register(modBus);
    ModBlockEntities.BLOCK_ENTITIES.register(modBus);
    ModMenus.MENUS.register(modBus);            // <-- the line whose absence crashed the draft
    ModAttachments.ATTACHMENT_TYPES.register(modBus);
    ModDataComponents.COMPONENTS.register(modBus);
    ModSounds.SOUNDS.register(modBus);
    ModCreativeTab.TABS.register(modBus);

    ModPayloads.register(modBus);               // adds the RegisterPayloadHandlersEvent listener
    modBus.addListener(this::commonSetup);      // guardrail log

    container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    // Game-bus @EventBusSubscriber classes register themselves via annotation scan.
}
```

---

## Data Flow 1: Binding an employee (step by step)

**Precondition:** Soul Altar placed; a vanilla job-site block sits directly above it (plain world
placement); player has a Soul Block.

| # | Side | Step |
|---|------|------|
| 1 | server | Player right-clicks the altar with a Soul Block → `SoulAltarBlock#useWithoutItem`. If the BE's slot is empty, insert the Soul Block into the BE slot, `setChanged()`, and return (no menu yet). Second right-click (slot full) proceeds. |
| 2 | server | Read `BlockState above = level.getBlockState(pos.above())`. `ProfessionResolver.fromState(above)` → `Optional<VillagerProfession>`. Empty → send `player.displayClientMessage("not a valid workstation")`, return `InteractionResult.CONSUME`. |
| 3 | server | `tier = 1` (initial bind). `TradePoolCache.rollCandidates(profession, tier=1, villagerLevel=1)`: for each `ItemListing` in the cached tier-1 pool, build a throwaway `Villager` (not added to level) with `VillagerData` set to `profession`, call `listing.getOffer(tempVillager, RandomSource)`, drop nulls → `List<MerchantOffer> candidates`. *(STACK.md §8: `getOffer` needs an `Entity`; the throwaway villager is the safe source. Verify no side effects — spike in Slice 7.)* |
| 4 | server | `serverPlayer.openMenu(provider, buf -> { buf.writeBlockPos(pos); ResourceLocation.STREAM_CODEC → professionId; buf.writeVarInt(tier); MerchantOffer.LIST_STREAM_CODEC → candidates; buf.writeByte(MODE_BIND); })` where `provider = new SimpleMenuProvider((id, inv, p) -> new BindingAltarMenu(id, inv, ContainerLevelAccess.create(level, pos), pos, profession, tier, candidates, MODE_BIND, null), title)`. The **server** menu instance now holds `candidates` — the authoritative list. |
| 5 | vanilla | `ClientboundOpenScreenPacket` + the extra buffer travel to the client. |
| 6 | client | `MenuType`'s `IContainerFactory` invokes `BindingAltarMenu(int, Inventory, RegistryFriendlyByteBuf)`. It reads pos, professionId, tier, `candidates`, mode from the buffer and builds the client mirror menu. |
| 7 | client | `RegisterMenuScreensEvent` mapping → `new BindingAltarScreen(menu, playerInv, title)`. Screen reads `menu.candidates()`, `menu.profession()`: renders the profession/HR header, `NameEntryWidget` pre-filled with a generated default (e.g. `Employee #0007`), `TradePickerWidget` listing each candidate as a toggle. If `candidates.size() <= 2`, all are pre-selected and locked (FEATURES.md: degrade gracefully). |
| 8 | client | Player toggles up to 2 candidates, optionally edits the name, clicks **Confirm** → `PacketDistributor.sendToServer(new SelectTradesPayload(altarPos, chosenIndices, name))`. |
| 9 | server | `ServerPayloadHandler.handleSelectTrades` (main thread). Validate: (a) `player.containerMenu` is a `BindingAltarMenu` whose `pos == altarPos` and `menu.stillValid(player)`; (b) `chosenIndices` all in `[0, menu.candidates().size())` and `count <= 2` (or `== candidates.size()` when pool ≤ 2); (c) sanitize `name` (trim, length cap, strip formatting codes). Reject → close menu, message. |
| 10 | server | Re-derive `profession` from `pos.above()` again (don't trust the menu's copy blindly — cheap re-check). Build `MerchantOffers offers` from `menu.candidates().get(i)` for each chosen `i`. |
| 11 | server | `EmployeeManager.bind(...)`: `Villager v = EntityType.VILLAGER.create(level)`; position at `pos.above()` or adjacent free block; `v.setVillagerData(new VillagerData(biomeType, profession, 1))`; `v.setOffers(offers)`; `v.setVillagerXp(1)` (**≥1 to permanently lock the profession** — FEATURES.md profession-loss immunity); `v.setCustomName(Component.literal(name))`; `v.setCustomNameVisible(true)`; `v.setData(ModAttachments.EMPLOYEE, new EmployeeData(name, professionId, 1, offers, 0, level.getGameTime()))`; `level.addFreshEntity(v)`. |
| 12 | server | Consume the Soul Block from the BE slot; `be.setChanged()`. Play `SoundEvents.SOUL_ESCAPE` + soul particles (server broadcasts). `serverPlayer.closeContainer()`. |
| 13 | server→client | The `EMPLOYEE` attachment auto-syncs via its `.sync(STREAM_CODEC)` (STACK.md §4 — verify empirically; fallback = manual clientbound payload). Client now has the employee record for any client-side display. |

**Altar state after bind:** empty slot, no binding flag, nothing employee-related. The BE is stateless
with respect to employees.

---

## Data Flow 2: Level-up / promotion (step by step)

**Precondition:** a bound employee exists; the player trades with it via the normal vanilla merchant
screen.

| # | Side | Step |
|---|------|------|
| 1 | server | Player trades (vanilla `MerchantMenu`). Vanilla awards villager XP and, when `villagerXp >= threshold && level < 5`, sets the internal `increaseProfessionLevelOnUpdate` flag. |
| 2 | server | On the next `customServerAiStep`, vanilla `updateTrades()` runs: it **bumps `VillagerData` level** and **appends up to 2 freshly-rolled offers** from the pool. `shouldIncreaseLevel`/`increaseMerchantCareer` are private — cannot be overridden (STACK.md §8, gap #3). |
| 3 | server | `LevelUpHandler` (game bus, `EntityTickEvent.Post` on villagers, throttled ~every 20 ticks, guarded by `hasData(EMPLOYEE)`): compares `v.getOffers()` to `EmployeeData.chosenOffers`. Any offer present in the villager but **not** in `chosenOffers` → `v.setOffers(EmployeeManager.rebuildOffers(data))`. This **continuously reverts** vanilla's append — the "suppression" is *revert every tick*, not *prevent*. Letting the vanilla level bump stand is deliberate: it makes `shouldIncreaseLevel` return false so vanilla stops re-triggering. |
| 4 | server | Same handler: `int mechanicalLevel = v.getVillagerData().getLevel()`. If `mechanicalLevel > data.tier` and `data.pendingLevel == 0` → `data = data.withPendingLevel(mechanicalLevel)`, `v.setData(EMPLOYEE, data)`. Notify the player: actionbar/chat "*Employee #0007 is eligible for promotion to Grade III — bring them to the Soul Altar*", plus promotion-ready particles above the employee (FEATURES.md: the signal the player cannot miss). |
| 5 | server | Player brings the employee near the altar and right-clicks the altar (empty hand). `SoulAltarBlock#useWithoutItem` scans `level.getEntitiesOfClass(Villager, aabb(pos, radius))` for employees with `pendingLevel > tier`. None → message. One → proceed. Several → open a minimal roster picker first, or pick nearest *(open design question — FEATURES.md Q7; roadmap decision)*. |
| 6 | server | `TradePoolCache.rollCandidates(profession, tier = data.pendingLevel, villagerLevel = data.pendingLevel)` → new `candidates`. `serverPlayer.openMenu(...)` with `MODE_PROMOTE`, the target villager's UUID, `data.pendingLevel`, and the new `candidates` in the open buffer. Also include `data.chosenOffers` (read-only) so the screen can show the existing career. |
| 7 | client | `BindingAltarScreen` renders in promote mode: existing trades shown locked/greyed, new tier's `candidates` as the active "pick 2" picker. No name field (already named) — or an optional rename. |
| 8 | client | Player picks 2, clicks Confirm → `SelectTradesPayload(altarPos, chosenIndices, name)` (name unchanged or new). |
| 9 | server | `ServerPayloadHandler`: validate menu is `MODE_PROMOTE` for `altarPos`; resolve the target villager by the UUID stored in the **server** menu (not from the payload); validate indices as in bind. |
| 10 | server | `EmployeeManager.promote(v, data, chosen)`: `MerchantOffers merged = concat(data.chosenOffers, chosenNewOffers)`; `data = data.withTier(data.pendingLevel).withPendingLevel(0).withOffers(merged)`; `v.setData(EMPLOYEE, data)`; `v.setOffers(merged)`; ensure `v.getVillagerData().getLevel() == data.tier`. Sound + particles. `closeContainer()`. |
| 11 | server→client | Attachment re-syncs. `LevelUpHandler` from step 3 now sees `chosenOffers == getOffers()` and stops reverting until the next tier. |

**Where pending state lives:** `EmployeeData.pendingLevel` (int; `0` = none) **on the entity
attachment**. If the player closes the promote menu without confirming, nothing changes and
`pendingLevel` stays set — they can retry any time. The altar BE holds nothing; the server menu instance
holds the transient target UUID + candidates only for the lifetime of the open screen.

---

## Where the employee's identity lives

**Entirely in the `EmployeeData` entity data attachment.** Fields:

```
EmployeeData(
    String name,                 // also mirrored to Entity#setCustomName for vanilla display
    ResourceLocation profession, // resolved once at bind; survives job-block changes
    int tier,                    // highest tier the PLAYER has picked trades for (1..5)
    MerchantOffers chosenOffers, // the authoritative trade list — MerchantOffers.CODEC / STREAM_CODEC
    int pendingLevel,            // 0, or the mechanical level awaiting a promotion ritual
    long lastRestockTick         // mod-owned restock timer anchor
)
```

- Persists across save/load automatically (`.serialize(CODEC)`).
- Syncs to the client automatically (`.sync(STREAM_CODEC)`) — pending STACK.md §4 verification.
- `villager.hasData(EMPLOYEE)` is the **only** "is this an employee?" check, used by all four event
  handlers. Never a class check, never a tag.

**The Soul Altar block entity persists exactly one thing: the inserted Soul Block `ItemStack`** (its
single input slot), so it drops on break and survives a relog mid-binding. It stores **nothing** about
professions, candidates, employees, or binding progress — all of that is recomputed each time the menu
opens. This keeps the altar reusable and stateless, and means losing/moving the altar never orphans an
employee.

---

## Build Order (dependency-ordered, each slice testable in-game)

Reconciled with STACK.md's recommendation: **block → block entity → MenuType + Menu + empty Screen
verified in `runClient` — before any trade logic.** That is Slices 3–4 below, and Slice 4 is a hard
gate.

### Slice 0 — Empty mod loads
`SecondShift` `@Mod` class, `neoforge.mods.toml` template, empty `en_us.json`.
**Test:** `./gradlew runClient` reaches the main menu; mod appears in the Mods list. `./gradlew runServer` starts.
**Depends on:** nothing. **Blocks:** everything.

### Slice 1 — Items + creative tab + recipes
`ModItems` (Harvester, Soul Fragment, Soul Block as `BlockItem` placeholder or plain item first),
`ModCreativeTab`, textures/models, `soul_block.json` shapeless recipe, `harvester.json` recipe.
**Test:** all three items in the creative tab; craft 4 Fragments → Soul Block; recipe shows in JEI if installed.
**Depends on:** 0. **Parallelizable:** textures/models independent of code.

### Slice 2 — Harvester harvest behaviour
`DeathDropHandler` (game bus): `LivingDropsEvent` — plain villager killed by a `HarvesterItem` holder → replace drops with exactly 1 Soul Fragment.
**Test:** kill a villager with the Harvester → 1 Fragment, guaranteed; kill with a sword → no Fragment.
**Depends on:** 1. **Parallelizable with:** Slice 3 (independent).

### Slice 3 — Soul Altar block + block entity (no menu)
`ModBlocks`, `ModBlockEntities`, `SoulAltarBlock implements EntityBlock`, `SoulAltarBlockEntity` with a
1-slot inventory + `loadAdditional`/`saveAdditional` + `getUpdateTag`/`getUpdatePacket`. Right-click with
Soul Block inserts it; break drops slot contents.
**Test:** place altar, insert Soul Block (visual/log confirm), break altar → Soul Block drops; relog → slot persists.
**Depends on:** 1 (Soul Block item). **Parallelizable with:** Slice 2.

### Slice 4 — MENU HARNESS (known-risk, isolate) — HARD GATE
`ModMenus` + `MENUS.register(modBus)` in the constructor. `BindingAltarMenu` (both constructors,
`quickMoveStack`, `stillValid`). `client/ClientModBusEvents` with `RegisterMenuScreensEvent` →
`BindingAltarScreen` (empty `AbstractContainerScreen` — background + title only). `SoulAltarBlock#useWithoutItem`
→ `serverPlayer.openMenu(SimpleMenuProvider, buf -> buf.writeBlockPos(pos))`. Add the `FMLCommonSetupEvent`
guardrail log.
**Test:** `./gradlew runClient` → right-click altar → empty screen opens, **no crash**. `./gradlew runServer`
loads with no client-class error. Guardrail logs a real `secondshift:binding_altar` key.
**Depends on:** 3. **Nothing else proceeds until this passes.**

### Slice 5 — Attachment + employee spawn (fixed profession, no picker)
`ModAttachments`, `EmployeeData` (record + `CODEC` + `STREAM_CODEC` + `.sync`). `EmployeeManager.bind`
with a hardcoded profession (e.g. FARMER) and default offers. Temporary "Confirm" button in the screen →
`SelectTradesPayload` (name only) → `ServerPayloadHandler` spawns the villager, sets XP≥1, name, attachment.
**Test:** confirm → named villager appears; `/data get entity <uuid>` shows the attachment; relog persists;
verify client sync (does `.sync` fire for entities? — STACK.md gap #2).
**Depends on:** 4, 3.

### Slice 6 — Profession resolution
`ProfessionResolver` (POI blockstate → profession). Altar reads `pos.above()`; menu carries the
`ResourceLocation`; screen displays it. `bind` uses the resolved profession.
**Test:** place different job blocks on the altar → bound employee gets the matching profession; a
non-job block → graceful "not a valid workstation" message; a modded job block resolves too.
**Depends on:** 5. **Parallelizable with:** Slice 7 development.

### Slice 7 — Trade pool cache + candidate materialization
`TradePoolHandler` (`VillagerTradesEvent` → `TradePoolCache`), `TradePoolCache.rollCandidates`,
`OfferCandidate`. Server rolls tier-1 candidates and ships them in the open buffer
(`MerchantOffer` stream codec list). Screen renders them **read-only**.
**Spike:** confirm `ItemListing.getOffer(throwawayVillager, random)` has no side effects and handles
null returns (treasure maps, conditional listings). — STACK.md §8.
**Test:** bind a farmer → screen shows the 5 real farmer-novice offers with correct items/prices.
**Depends on:** 6, 4.

### Slice 8 — Trade picker interaction + apply
`TradePickerWidget` (toggle, enforce max 2), `NameEntryWidget` (EditBox + generated default).
`SelectTradesPayload` carries chosen indices + name. `ServerPayloadHandler` validates indices against
the **server** menu's candidate list, sanitizes the name, builds `MerchantOffers`, spawns the employee
with them, consumes the Soul Block. Handle pool ≤ 2 (auto-select + lock).
**Test:** pick 2 farmer trades → confirm → trade with the employee → exactly those 2 offers, correct prices.
Bind a librarian-master-like small pool → auto-confirm path works.
**Depends on:** 7. **This completes the FEATURES.md core value.**

### Slice 9 — Employee traits
`TraitHandlers`: cancel `LivingConversionEvent.Pre` for employees (covers zombie + lightning-witch).
`DeathDropHandler` extended: any death of an employee → drop its Soul Block (+ slime); Harvester death →
clean recovery, no penalty. Breeding suppression — **spike** (`FinalizeSpawnEvent` filtered on
`MobSpawnType.BREEDING` near an employee, or cancel `EntityJoinLevelEvent` for the baby; STACK.md gap #1,
confidence LOW — budget real time).
**Test:** zombie attack → no conversion; lightning → no witch; kill employee with lava → Soul Block +
slime drop; kill with Harvester → Soul Block, clean; two adjacent employees → no baby.
**Depends on:** 5. **Parallelizable with:** Slices 7–8, 11.

### Slice 10 — Level-up detection + offer revert + promotion ritual
`LevelUpHandler` (throttled entity tick): revert vanilla-appended offers to `chosenOffers`; detect
mechanical level > `tier` → set `pendingLevel` + notify + particles. `BindingAltarMenu` gains
`MODE_PROMOTE` + target UUID. Altar right-click scans nearby employees with `pendingLevel > tier` → opens
the picker for the new tier. Apply → merge offers, `tier = pendingLevel`, `pendingLevel = 0`.
**Spike:** confirm the revert approach actually beats vanilla's append and doesn't cause per-tick offer
thrash or lost restock state — STACK.md gap #3, confidence MEDIUM.
**Test:** trade an employee to Apprentice XP → promotion prompt appears, **no unchosen trades on the
villager**; do the ritual → pick tier-2 trades → they apply, tier-1 trades retained.
**Depends on:** 8, 6. **Highest-risk slice after Slice 4.**

### Slice 11 — Restock (mod-owned timer)
`RestockHandler` (server tick): per loaded employee, if `gameTime - lastRestockTick >= configInterval`
→ restock the villager's offers, update `lastRestockTick`. Config value for the interval.
**Test:** exhaust a trade → wait the interval → trade unlocks again; a nearby **non-employee** villager
is unaffected.
**Depends on:** 5. **Parallelizable with:** Slices 9–10.

### Slice 12 — Polish (mostly parallel)
Sounds + particles on harvest/bind/promote; complete `en_us.json` with HR job titles; `Config` +
`IConfigScreenFactory`; altar GUI shows bound-employee state; promotion-ready signal finalised; every
invalid state covered (no job block / no soul block / unmapped block / empty pool / pool ≤ 2); recipe
advancements; employee name always visible.
**Depends on:** the relevant feature slice each polish item targets.

### Dependency graph (slices)

```
0 ─┬─> 1 ─┬─> 2 ───────────────────────────────────┐
   │      └─> 3 ──> 4 (GATE) ──> 5 ─┬─> 6 ──> 7 ──> 8 ──> 10
   │                                ├─> 9                 ^
   │                                └─> 11                │
   └────────────────────────────────────────  6 ─────────┘
                                            (10 needs 6 + 8)
12 polish: attaches to whichever slice each item belongs to
```

**Parallelizable once Slice 5 lands:** the trade chain (6→7→8), traits (9), and restock (11) are
independent workstreams. **Strictly sequential:** 0→1→3→4→5, and 8→10.

---

## Architectural Patterns

### Pattern 1: Attachment-as-identity, handlers-as-behaviour

**What:** The employee is a `minecraft:villager` + `EmployeeData` attachment. Every behavioural
difference is a game-bus event handler guarded by `if (!entity.hasData(EMPLOYEE)) return;`.
**When:** Whenever you'd be tempted to subclass an entity but need vanilla/mod compatibility.
**Trade-offs:** Compatibility with iron golems, raids, villager mods, entity tags (PROJECT constraint) —
at the cost of not being able to override `canBreed()`/`updateTrades()` cleanly (hence the breeding
spike and the offer-revert pattern below).

### Pattern 2: Revert, don't prevent (vanilla level-up)

**What:** Let vanilla `updateTrades()` bump the level and append offers, then a throttled tick handler
overwrites `getOffers()` back to `EmployeeData.chosenOffers`.
**When:** The vanilla mutation point is private and un-hookable, but the resulting state is observable
and cheap to correct.
**Trade-offs:** Simple and robust vs. a one-tick window where unchosen offers technically exist on the
server (never rendered to the player if the throttle is tight enough — verify). Avoids an AT/Mixin.

### Pattern 3: Server-authoritative menu, client mirror from open-data

**What:** The real `BindingAltarMenu` (server) holds the candidate `MerchantOffer` list. The client
menu is reconstructed from the `IContainerFactory` extra-data buffer at open time. The C→S payload
sends only indices; the server maps them against *its* list.
**When:** Transient per-open data too large/complex for `ContainerData` ints, not worth an attachment.
**Trade-offs:** One-shot — can't update candidates after open without a custom payload (fine here;
candidates don't change mid-screen). Keeps the trust boundary clean: the client can only pick from a
list the server built.

### Pattern 4: Recompute over persist (the altar)

**What:** Profession and candidates are derived fresh every time the altar menu opens; the BE persists
only the inserted Soul Block.
**When:** Derivation is cheap and the inputs (block above, vanilla pool) are always available.
**Trade-offs:** A tiny recompute cost per open vs. never having stale/orphaned altar state and a
trivially reusable altar.

---

## Anti-Patterns

### Anti-Pattern 1: Registering the `MenuType` lazily / only from client code
**What people do:** Create `ModMenus` but only ever reference it from the screen-registration handler.
**Why it's wrong:** The static initializer (which calls `DeferredRegister.create`) runs *after*
`RegisterEvent` fired → the menu is never added → `DeferredHolder#value()` throws
`Trying to access unbound value` at `RegisterMenuScreensEvent`. **This is the exact prior crash.**
**Instead:** `ModMenus.MENUS.register(modBus)` in the `SecondShift` constructor, alongside every other
register, as one visible block.

### Anti-Pattern 2: Importing `client/` types from common code
**What people do:** Open the screen by `new BindingAltarScreen(...)` from the block, or reference
`Minecraft.getInstance()` in a handler.
**Why it's wrong:** Class-loads a client type on the dedicated server → `NoClassDefFoundError` on
startup. (`./gradlew runServer` catches it; PROJECT's single-player target does *not* excuse it —
`runClient` integrated server hits the same path.)
**Instead:** `serverPlayer.openMenu(MenuProvider, extraDataWriter)` on the server; the client's
`RegisterMenuScreensEvent` mapping builds the screen.

### Anti-Pattern 3: Storing chosen trades as pool indices
**What people do:** Persist "trade #2 of the farmer novice pool".
**Why it's wrong:** `ItemListing` is a *generator* — `getOffer` re-randomises price, enchantments, maps
every call (STACK.md §8). The player picks a materialized offer and later gets a different one.
**Instead:** Persist the concrete `MerchantOffer` objects via `MerchantOffers.CODEC` in `EmployeeData`.

### Anti-Pattern 4: Persisting employee state in the block entity
**What people do:** Store the bound employee's UUID / profession / progress on the altar BE.
**Why it's wrong:** Couples the employee's lifecycle to a block that can be moved, broken, or reused;
creates orphan/desync bugs.
**Instead:** Identity lives on the entity attachment. The BE persists only its Soul Block slot.

### Anti-Pattern 5: Holding the villager's mechanical level down to block vanilla trades
**What people do:** Force `VillagerData` level back to 1 every tick so `updateTrades()` never appends.
**Why it's wrong:** `shouldIncreaseLevel()` stays true → vanilla re-triggers the level-up path *every
tick* → offer thrash, XP/particle spam.
**Instead:** Let the level rise; revert only the *offers* list (Pattern 2). Track the player-picked
tier separately in `EmployeeData.tier`.

### Anti-Pattern 6: Trusting the C→S payload's indices or name
**What people do:** Apply whatever offers/name the client sent.
**Why it's wrong:** Unauthenticated input; a modified client could grant itself arbitrary trades.
**Instead:** `ServerPayloadHandler` maps indices against the server menu's candidate list, re-checks
range to the altar, and sanitizes the name (STACK.md §3).

### Anti-Pattern 7: Custom `RecipeType` for the Soul Block / Harvester / altar ritual
**What people do:** Author a `Recipe`/`RecipeSerializer`/`RecipeType` for each.
**Why it's wrong:** Soul Block and Harvester are plain `crafting_shapeless`/`crafting_shaped` JSON
(free JEI). The altar ritual isn't a recipe at all — it's a block interaction (STACK.md §7,
FEATURES.md anti-features).
**Instead:** Vanilla recipe JSON for crafting; a tooltip + advancement documents the ritual.

---

## Integration Points

### Vanilla / NeoForge systems hooked (never forked)

| System | Hook | Notes |
|--------|------|-------|
| Villager → zombie / witch conversion | `LivingConversionEvent.Pre` (game bus, cancellable) | One handler covers both (STACK.md §10) |
| Mob death drops | `LivingDropsEvent` (game bus) | Harvest (plain villager) + employee death both here; branch on damage source |
| Villager trade pool | `VillagerTradesEvent` (game bus, fires on reload) | Populate `TradePoolCache` — captures modded trades that `VillagerTrades.TRADES` misses (STACK.md §8) |
| Block → profession | `PoiTypes.forState` + `BuiltInRegistries.VILLAGER_PROFESSION` | Runtime resolution; modded professions free (STACK.md §9) |
| Employee data persistence + sync | `AttachmentType.builder().serialize(CODEC).sync(STREAM_CODEC)` | Verify `.sync` fires for entities in 21.1.248 (STACK.md gap #2); fallback = manual clientbound payload |
| Applying trades | `Villager#setOffers` / `setVillagerXp` / `setVillagerData` (all public) | STACK.md §8 |
| Menu open | `ServerPlayer#openMenu(MenuProvider, extraDataWriter)` | `extraDataWriter` only works with `IMenuTypeExtension.create(IContainerFactory)` |
| Screen binding | `RegisterMenuScreensEvent` (mod bus, `Dist.CLIENT`) | Never `MenuScreens.register` from setup (STACK.md "What NOT to Use") |
| Networking | `RegisterPayloadHandlersEvent` + `CustomPacketPayload` + `StreamCodec` | Handlers on the main thread (STACK.md §3) |
| Breeding | **no clean hook** | Spike in Slice 9 — `FinalizeSpawnEvent`/`EntityJoinLevelEvent` (STACK.md gap #1) |
| Restock | mod-owned timer on server tick | Vanilla POI-based restock won't fire reliably for an altar-bound villager (FEATURES.md "The Restock Question") |

### Internal boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `SoulAltarBlock` → `BindingAltarMenu` | `SimpleMenuProvider` + open-data buffer | server only; buffer carries pos, profession, tier, candidates, mode |
| `BindingAltarMenu` (server) ↔ (client) | vanilla open packet + the extra buffer | client rebuilds from the buffer; no shared state |
| `BindingAltarScreen` → server | `SelectTradesPayload` only | the sole client→server channel; indices + name |
| server → client (employee state) | `EmployeeData.sync` stream codec | fallback: manual clientbound payload |
| any event handler ↔ employee | `villager.getData/hasData(EMPLOYEE)` | the only identity check anywhere |
| `TradePoolCache` ↔ vanilla pool | `VillagerTradesEvent` (in), `ItemListing.getOffer` (materialize) | cache rebuilt on reload |
| `ProfessionResolver` ↔ registries | `PoiTypes.forState`, profession registry stream | filter `minecraft:none` + `minecraft:nitwit` |
| `SoulAltarBlockEntity` → client | `getUpdateTag` + `getUpdatePacket` | needed or the screen renders a stale slot |

---

## Scale Considerations

Single-player, personal mod — "scale" means employee count and world size, not users.

| Scale | Consideration |
|-------|---------------|
| 1–10 employees | Nothing special. Throttled per-entity tick handler is negligible. |
| 10–50 employees | `LevelUpHandler` should iterate only loaded villagers with the attachment, on a stagger (e.g. `entity.tickCount % 20 == 0`), not a global list scan. `RestockHandler` likewise. |
| Many chunks / employees unloaded | Attachment persists with the entity; unloaded employees simply don't tick. `lastRestockTick` uses `level.getGameTime()` so a long-unloaded employee restocks once on reload (clamp to avoid a burst — cap catch-up to 1 restock). |
| Modded professions with huge/empty pools | `TradePoolCache` must tolerate empty tiers (show "no trades available", don't crash) and large pools (scrollable picker). Covered by Slice 12 invalid-state work. |

**First thing that would break:** an unthrottled per-tick scan over all employees calling `setOffers`.
Mitigation: stagger by `tickCount`, and only revert offers when a cheap equality check shows drift.

---

## Sources

- `.planning/research/STACK.md` — binary-verified API signatures (`javap` against `neoforge-21.1.248`),
  the crash diagnosis (`DeferredHolder#value()`), registration patterns, the four flagged gaps. **HIGH**
- `.planning/research/FEATURES.md` — scope, "pick 2 of N", the restock question, profession-loss
  immunity, MVP slice list. **MEDIUM-HIGH**
- `.planning/PROJECT.md` — constraints (vanilla villager + attachment, no custom entity, personal
  single-player), the prior-draft crash context. **HIGH**
- `https://docs.neoforged.net/docs/1.21.1/concepts/sides/` — physical vs logical sides, `Dist`,
  `@Mod(dist=)`, "transfer data → send a packet". **HIGH**
- `https://docs.neoforged.net/docs/1.21.1/concepts/events/` — `@EventBusSubscriber` params
  (`modid`/`bus`/`value`), `Bus.GAME` vs `Bus.MOD`, mod-bus parallelism + `enqueueWork`. **HIGH**
- `https://docs.neoforged.net/docs/1.21.1/gui/menus/` and `/gui/screens/` (via STACK.md) —
  `RegisterMenuScreensEvent`, `IContainerFactory`, `MenuProvider` open-data. **HIGH**

---
*Architecture research for: NeoForge 1.21.1 necromancy/villager-trading mod (Second Shift)*
*Researched: 2026-09-04*
