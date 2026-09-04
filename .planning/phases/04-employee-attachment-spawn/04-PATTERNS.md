# Phase 4: Employee Attachment & Spawn - Pattern Map

**Mapped:** 2026-09-04
**Files analyzed:** 8 (5 new, 3 modified)
**Analogs found:** 7 / 8

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `employee/EmployeeData.java` | model (record + codec) | serialize/sync (transform) | No in-repo record+CODEC+STREAM_CODEC exists yet — closest shape is `SoulAltarBlockEntity`'s NBT save/load (manual, not codec-based) | partial (spec fully given in RESEARCH.md Code Examples instead) |
| `registry/ModAttachments.java` | config / registry | CRUD (register) | `registry/ModMenus.java` (single-holder `DeferredRegister` pattern) | exact (role+shape), different registry type |
| `employee/EmployeeManager.java` | service | event-driven (bind triggered by payload) / CRUD (entity creation) | `content/block/SoulAltarBlock.java`'s `useItemOn` (server-side mutation + `addFreshEntity`-adjacent flow) and `trade/ProfessionResolver.java` (static utility, no state) | role-match |
| `employee/EmployeeNames.java` (optional) | utility | transform (pure function) | `trade/ProfessionResolver.java` (static final utility class, private ctor) | role-match |
| `network/BindEmployeePayload.java` | model/DTO (payload) | request-response (C→S) | **No analog exists in this codebase** — no `network/` package yet | none — use CLAUDE.md §3 spec |
| `network/ServerPayloadHandler.java` | controller (payload handler) | request-response | **No analog** — closest precedent is `SoulAltarBlock#useItemOn`'s server-side `sp.openMenu(...)` validate-then-act shape | none — use CLAUDE.md §3 spec |
| `client/screen/BindingAltarScreen.java` (MODIFIED) | component (screen) | request-response (button → payload send) | itself (Phase 3, already shipped) — add a `Button.Builder`/`onPress` per vanilla `AbstractContainerScreen` conventions | exact (same file, additive change) |
| `ModRegistrySelfCheck.java` (MODIFIED) | config (guardrail) | CRUD (extend Stream.of) | itself (Phase 2/3, already shipped — extend `Stream.of(...)`) | exact (same file, additive change) |
| `SecondShift.java` (MODIFIED) | config (bootstrap) | CRUD (register-in-constructor) | itself (Phase 2/3, already shipped) | exact (same file, additive change) |

## Pattern Assignments

### `employee/EmployeeData.java` (model, serialize/sync)

**Analog:** No direct in-repo analog (first `RecordCodecBuilder` + `StreamCodec.composite` record in this codebase). Use the fully-specified shape already synthesized in `.planning/phases/04-employee-attachment-spawn/04-RESEARCH.md` under "Code Examples" — it is the authoritative source, not a derived pattern.

**Full shape to copy verbatim (adjust field list only if the planner adds fields beyond RESEARCH.md's minimum):**
```java
public record EmployeeData(
        int version,               // schema version — start at 1
        String name,
        ResourceLocation profession,
        int tier,
        MerchantOffers offers
) {
    public static final EmployeeData EMPTY = new EmployeeData(
            1, "", ResourceLocation.withDefaultNamespace("none"), 0, new MerchantOffers());

    public static final Codec<EmployeeData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("version").forGetter(EmployeeData::version),
            Codec.STRING.fieldOf("name").forGetter(EmployeeData::name),
            ResourceLocation.CODEC.fieldOf("profession").forGetter(EmployeeData::profession),
            Codec.INT.fieldOf("tier").forGetter(EmployeeData::tier),
            MerchantOffers.CODEC.fieldOf("offers").forGetter(EmployeeData::offers)
    ).apply(inst, EmployeeData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EmployeeData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EmployeeData::version,
                    ByteBufCodecs.STRING_UTF8, EmployeeData::name,
                    ResourceLocation.STREAM_CODEC, EmployeeData::profession,
                    ByteBufCodecs.VAR_INT, EmployeeData::tier,
                    MerchantOffers.STREAM_CODEC, EmployeeData::offers,
                    EmployeeData::new);
}
```
(Source: `04-RESEARCH.md` "Code Examples" § `EmployeeData` record shape, itself synthesized from `.planning/research/ARCHITECTURE.md` field list + `.planning/research/STACK.md` §4/§8 codec existence.)

**Documentation-comment convention to copy** (from `SoulAltarBlockEntity.java` lines 22-42 and `BindingAltarMenu.java` lines 15-25): a class-level Javadoc block citing the decision IDs (D-xx) and requirement IDs (EMP-xx) it satisfies, plus a one-line "what this class deliberately does NOT do yet" scope note. Follow this exact convention for `EmployeeData`.

---

### `registry/ModAttachments.java` (config, CRUD-register)

**Analog:** `src/main/java/com/cxmxrgo/secondshift/registry/ModMenus.java` (single-holder `DeferredRegister` pattern — closest existing shape since both are "one named holder, one register call, forward-reference to another new class").

**Imports pattern** (`ModMenus.java` lines 1-9, adapt registry type):
```java
package com.cxmxrgo.secondshift.registry;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
```
For `ModAttachments`, swap to (per RESEARCH.md "Code Examples" § `ModAttachments` registration, STACK.md §4/§1-verified):
```java
package com.cxmxrgo.secondshift.registry;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.employee.EmployeeData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
```

**Core registration pattern** (`ModMenus.java` lines 22-32 — final-class, private-ctor, one `DeferredRegister` field, one holder constant):
```java
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SecondShift.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<BindingAltarMenu>> BINDING_ALTAR =
            MENUS.register("binding_altar",
                    () -> IMenuTypeExtension.create(BindingAltarMenu::new));

    private ModMenus() {}
}
```
Adapted target shape (per RESEARCH.md, verbatim):
```java
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SecondShift.MODID);

    public static final Supplier<AttachmentType<EmployeeData>> EMPLOYEE =
            ATTACHMENT_TYPES.register("employee", () ->
                    AttachmentType.builder(() -> EmployeeData.EMPTY)
                            .serialize(EmployeeData.CODEC)
                            .sync(EmployeeData.STREAM_CODEC)
                            .build());

    private ModAttachments() {}
}
```
**Note:** `ModMenus.BINDING_ALTAR` is a `DeferredHolder<MenuType<?>, MenuType<BindingAltarMenu>>` (registrar returns a typed holder); `ModAttachments.EMPLOYEE`'s registrar per RESEARCH.md is typed `Supplier<AttachmentType<EmployeeData>>` — both are `.register(...)`'s return type from a generic `DeferredRegister`, so use whichever the actual `DeferredRegister<AttachmentType<?>>.register(...)` return type resolves to at compile time (likely `DeferredHolder<AttachmentType<?>, AttachmentType<EmployeeData>>` — verify against the other four registers' declared types in `ModBlockEntities`/`ModItems`/`ModBlocks` for the project's own convention, which favors explicit `Supplier<T>`/`DeferredItem<T>`/`DeferredHolder<..>` typed fields, never raw `var`).

**Class-level Javadoc convention** to copy (`ModBlockEntities.java` lines 11-21, `ModMenus.java` lines 11-21): cite the requirement ID, one-sentence scope statement, and a note on which plan/task created it and why (mirrors the "D-14" self-check cross-reference already used).

---

### `employee/EmployeeManager.java` (service, event-driven + CRUD entity creation)

**Analog:** `content/block/SoulAltarBlock.java`'s `useItemOn` method (server-side gate → mutate → `addFreshEntity`-adjacent open-menu flow) for the "validate on server, then act" shape, plus `trade/ProfessionResolver.java` for the static-utility-class shape (no instance state, private ctor, single well-named static entry point).

**Static-utility class shape** (`ProfessionResolver.java` lines 31-47, copy directly):
```java
public final class ProfessionResolver {

    private ProfessionResolver() {}

    /** Resolves the profession (if any) mapped to the job-site block directly above {@code altarPos}. */
    public static Optional<VillagerProfession> fromAbove(Level level, BlockPos altarPos) {
        return PoiTypes.forState(level.getBlockState(altarPos.above())).flatMap(ProfessionResolver::fromPoi);
    }
    ...
}
```
`EmployeeManager` should follow this exact shape: `public final class EmployeeManager { private EmployeeManager() {} public static Villager bind(ServerLevel level, BlockPos altarPos) { ... } }` — a single static entry point, no instance state.

**Server-side validate-before-mutate pattern** (`SoulAltarBlock.java` lines 101-133, the `useItemOn` gate-then-act shape — directly transferable to `EmployeeManager.bind`'s internal ordering discipline and to `ServerPayloadHandler`'s validation of the incoming bind request):
```java
if (!level.isClientSide) {
    if (ProfessionResolver.fromAbove(level, pos).isEmpty()) {
        player.displayClientMessage(Component.translatable("message.secondshift.altar.not_a_workstation"), true);
        return ItemInteractionResult.CONSUME;
    }
    be.setHeldSoulBlock(stack.copyWithCount(1));
    stack.consume(1, player);
    be.setChanged();
    level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    ...
}
```
Apply the same "compute state → validate → mutate in order → `addFreshEntity`" discipline to `EmployeeManager.bind`, following RESEARCH.md's Pattern 2 ordering exactly (`create` → `moveTo` → `setVillagerData` → `setVillagerXp` → `setOffers` LAST → `setCustomName`/`setCustomNameVisible` → `setData(EMPLOYEE, ...)` → `addFreshEntity`) — this ordering is authoritative and non-negotiable per RESEARCH.md Pitfall 4/Pattern 2, not something to infer from the analog.

**`hasData` guard convention** (RESEARCH.md Pattern 1, already anticipated in `HarvesterEvents.java` line 206's forward-looking comment: `((Villager) target).hasData(EMPLOYEE)`) — every future employee-only branch must use `hasData`, never `getData`, as the identity predicate. `EmployeeManager.bind` itself is the one legitimate `setData(EMPLOYEE, ...)` call site this phase.

---

### `employee/EmployeeNames.java` (utility, transform — optional per Claude's discretion)

**Analog:** `trade/ProfessionResolver.java` (same static-utility-class shape as above — final class, private ctor, static methods only).

**Pattern** (copy the class skeleton, not the domain logic):
```java
public final class EmployeeNames {
    private static final List<String> POOL = List.of(/* themed pool */);
    private EmployeeNames() {}
    public static String pickRandom(RandomSource random) {
        return POOL.get(random.nextInt(POOL.size()));
    }
}
```

---

### `network/BindEmployeePayload.java` + `network/ServerPayloadHandler.java` (DTO + controller, request-response)

**No analog exists in this codebase** — there is no `network/` package yet (verified: `find src/main/java -name "*.java"` returned no `network/*` files). CLAUDE.md §3 "Networking" is the only in-project source and it is a spec, not code:

> `playToServer` / `playToClient` / `playBidirectional` take `StreamCodec<? super RegistryFriendlyByteBuf, T>` in the play phase... `IPayloadContext#player()` returns `Player`, not `ServerPlayer` — cast on the server side. Handlers run on the main thread by default... `PacketDistributor.sendToServer(payload)` to send... Serverbound payloads are capped at <32 KiB.

**Recommended structural precedent from this codebase instead:** the "re-derive server-side state, never trust client position" discipline already proven in `SoulAltarBlock.useItemOn`/`useWithoutItem` (validate against the BE/menu server-side, never trust a client-supplied `BlockPos`) and `BindingAltarMenu.stillValid` (server re-derives validity from `access`/`ProfessionResolver`, not from client claims). Per D-01, `BindEmployeePayload` needs **zero fields** (no name field) — the simplest possible `CustomPacketPayload` record, e.g.:
```java
public record BindEmployeePayload() implements CustomPacketPayload {
    public static final Type<BindEmployeePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SecondShift.MODID, "bind_employee"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BindEmployeePayload> STREAM_CODEC =
            StreamCodec.unit(new BindEmployeePayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```
`ServerPayloadHandler.handleBindEmployee` should mirror `BindingAltarMenu.stillValid`'s "re-derive from `player.containerMenu`, never trust payload data" discipline (per RESEARCH.md's Security Domain table): validate `player.containerMenu instanceof BindingAltarMenu`, derive the altar `BlockPos` from server-side menu state, then call `EmployeeManager.bind(...)`.

**Registration site:** per CLAUDE.md, payloads register via `RegisterPayloadHandlersEvent` — this event/registrar has no existing call site in `SecondShift.java` yet (only `DeferredRegister`s are wired there currently); the planner must add a new `@SubscribeEvent` handler (likely in `SecondShift.java` or a new dedicated class) for `RegisterPayloadHandlersEvent`, following the same "register on the mod bus, in one visible place" discipline the constructor already establishes for `DeferredRegister`s.

---

### `client/screen/BindingAltarScreen.java` (MODIFIED — component, request-response)

**Analog:** itself, the already-shipped Phase 3 file (read in full above).

**Current shape to extend** (`BindingAltarScreen.java` lines 22-37):
```java
public class BindingAltarScreen extends AbstractContainerScreen<BindingAltarMenu> {
    private static final ResourceLocation TEXTURE = ...;
    public BindingAltarScreen(BindingAltarMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
}
```
**Addition needed:** override `init()` to add a temporary `Button` (vanilla `Button.builder(Component.translatable(...), btn -> PacketDistributor.sendToServer(new BindEmployeePayload())).bounds(...).build()`, added via `this.addRenderableWidget(...)`). Keep the class-level Javadoc's "client-class isolation" note (lines 11-21) intact and extend it to mention the temporary Confirm button is throwaway per CONTEXT.md D-01.

---

### `ModRegistrySelfCheck.java` (MODIFIED — config, CRUD)

**Analog:** itself (already shipped; read in full above).

**Exact line to extend** (line 77):
```java
List<String> unbound = Stream.of(ModItems.ITEMS, ModBlocks.BLOCKS, ModBlockEntities.BLOCK_ENTITIES, ModCreativeTab.TABS, ModMenus.MENUS)
        .flatMap(dr -> dr.getEntries().stream())
```
Change to add `ModAttachments.ATTACHMENT_TYPES` as the 6th entry:
```java
List<String> unbound = Stream.of(ModItems.ITEMS, ModBlocks.BLOCKS, ModBlockEntities.BLOCK_ENTITIES, ModCreativeTab.TABS, ModMenus.MENUS, ModAttachments.ATTACHMENT_TYPES)
        .flatMap(dr -> dr.getEntries().stream())
```
Add the import `com.cxmxrgo.secondshift.registry.ModAttachments` alongside the existing 5 registry imports (lines 3-7). Per RESEARCH.md Pitfall C, also repeat the D-14 manual-detach verification: temporarily comment out `ATTACHMENT_TYPES.register(modBus)` in `SecondShift.java`, rebuild, confirm the named hard-abort fires, then restore.

---

### `SecondShift.java` (MODIFIED — config, CRUD register-in-constructor)

**Analog:** itself (already shipped; read in full above).

**Exact block to extend** (lines 39-43):
```java
ModItems.ITEMS.register(modBus);
ModBlocks.BLOCKS.register(modBus);
ModBlockEntities.BLOCK_ENTITIES.register(modBus);
ModCreativeTab.TABS.register(modBus);
ModMenus.MENUS.register(modBus);
```
Add: `ModAttachments.ATTACHMENT_TYPES.register(modBus);` as the 6th line, plus the corresponding import. If a `network/` payload registrar is added this phase, its `RegisterPayloadHandlersEvent` listener should also be wired here or in a new class following the same "one visible wiring point" discipline the class-level Javadoc (lines 18-25) already documents — extend that Javadoc's "every new registry/Mod* class MUST be added here and to ModRegistrySelfCheck" note to also cover the payload registrar if one is added.

---

## Shared Patterns

### `hasData` not `getData` as the employee-identity predicate
**Source:** RESEARCH.md Architecture Pattern 1; anticipated in `event/HarvesterEvents.java` line 206's Phase-6 forward comment.
**Apply to:** `EmployeeManager.bind` (the one legitimate write site) and any future/adjacent read of `ModAttachments.EMPLOYEE` this phase touches (e.g. a diagnostic client-read log line for the LIGHT spike). Never call `.getData(ModAttachments.EMPLOYEE)` as an existence check — it silently creates the default attachment.

### Server-side re-derivation of trust boundaries — never trust client-supplied position/state
**Source:** `content/block/SoulAltarBlock.java` (`useItemOn`/`useWithoutItem` gate-before-mutate), `menu/BindingAltarMenu.java` (`stillValid` re-derives from `access`/`ProfessionResolver`, not client claims).
**Apply to:** `network/ServerPayloadHandler.handleBindEmployee` — derive the altar `BlockPos` from the player's currently-open `BindingAltarMenu` server state, not from any client-supplied payload field (D-01 already reduces the attack surface by removing the name field; RESEARCH.md's Security Domain table makes this explicit for position too).

### `DeferredRegister` single-holder registry class shape
**Source:** `registry/ModMenus.java`, `registry/ModBlockEntities.java` (both: `public final class`, one `DeferredRegister<T>` field, one or more named holder constants, `private NoArgCtor() {}`).
**Apply to:** `registry/ModAttachments.java`.

### Static-utility class shape (no instance state)
**Source:** `trade/ProfessionResolver.java` (`public final class`, `private Ctor() {}`, static methods only, no fields beyond constants).
**Apply to:** `employee/EmployeeManager.java`, `employee/EmployeeNames.java`.

### Class-level Javadoc convention citing decision/requirement IDs
**Source:** every existing class in this codebase (`SoulAltarBlockEntity`, `BindingAltarMenu`, `SoulAltarBlock`, `ModBlockEntities`, `ModMenus`, `ModRegistrySelfCheck`) opens with a Javadoc block citing `D-xx`/requirement IDs and a scope note ("what this class deliberately does NOT do yet").
**Apply to:** every new file this phase.

### `@EventBusSubscriber` self-registration (no manual wiring line) vs. `DeferredRegister` (must be wired in `SecondShift` constructor)
**Source:** `event/HarvesterEvents.java` (`@EventBusSubscriber(modid = SecondShift.MODID)` — self-registers, no `SecondShift.java` wiring needed) vs. `registry/ModMenus.java` (needs an explicit `.register(modBus)` call in `SecondShift.java`).
**Apply to:** if `ServerPayloadHandler` or any new event-listening class this phase uses `@EventBusSubscriber`, no `SecondShift.java` wiring is needed for it — only `ModAttachments.ATTACHMENT_TYPES` (a `DeferredRegister`) needs the explicit register-in-constructor line + `ModRegistrySelfCheck` coverage. Do not conflate the two wiring styles.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `network/BindEmployeePayload.java` | model/DTO | request-response | No `network/` package exists yet in this codebase — this is the first `CustomPacketPayload`. Use CLAUDE.md §3's spec and the vanilla `CustomPacketPayload`/`StreamCodec` API directly; no in-repo shape to copy beyond the trust-boundary discipline noted in Shared Patterns. |
| `network/ServerPayloadHandler.java` | controller | request-response | Same as above — no existing payload handler. Structural precedent (validate-before-act) borrowed from `SoulAltarBlock`/`BindingAltarMenu` as noted in Pattern Assignments. |

## Metadata

**Analog search scope:** `src/main/java/com/cxmxrgo/secondshift/**` (all 19 existing `.java` files read or grepped)
**Files scanned:** 19 existing source files (registry/, menu/, client/, client/screen/, content/block/, content/blockentity/, content/item/, event/, gametest/, trade/, root package)
**Pattern extraction date:** 2026-09-04
