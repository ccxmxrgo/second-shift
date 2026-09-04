# Stack Research

**Domain:** Minecraft mod — NeoForge 1.21.1 (21.1.x line), Java 21, single-player personal mod
**Researched:** 2026-09-04
**Confidence:** HIGH (versions verified against live Maven metadata and the official 1.21.1 MDK; API signatures verified by `javap` against the actual `neoforge-21.1.248` jar on this machine)

---

## Verification note

Nothing in this document is from training data alone. Every version number was read from live Maven
metadata or the official MDK template, and every Java API signature below was disassembled from
`C:\Users\user\curseforge\minecraft\Install\libraries\net\neoforged\neoforge\21.1.248\neoforge-21.1.248-{client,universal}.jar`
— the exact binary the test instance runs — or read from Mojang's official 1.21.1 mappings
(`client.txt`, sha `2244b6f0`). Confidence is marked per item where it is anything less than HIGH.

The test instance was confirmed to be `neoforge-21.1.248` on `1.21.1` (FML loader 4.0.43) by reading
`C:\Users\user\curseforge\minecraft\Instances\test\minecraftinstance.json`. `second-shift-0.1.0.jar`
is still present in that instance's `mods/` folder and must be deleted before the first test run.

---

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Minecraft | `1.21.1` | Target game | Hard constraint — must match the CurseForge `test` instance |
| NeoForge | `21.1.248` | Mod loader + API | Hard constraint — exact version installed in `test`. `21.1.249` is the newest 21.1.x on Maven; do **not** bump past what the instance runs |
| Java | Temurin JDK 21 | Toolchain | Mojang ships Java 21 for 1.21.x. Already installed at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` (verified) |
| ModDevGradle (MDG) | `2.0.146` | Gradle plugin | Current stable; what the **official** `MDK-1.21.1-ModDevGradle` template ships. Latest on both NeoForged Maven and the Gradle Plugin Portal as of 2026-08-31 |
| Gradle | `9.2.1` (wrapper) | Build | Exact wrapper version in the official 1.21.1 MDK. MDG 2.x supports Gradle 8.8+; 9.2.1 is the tested pairing |
| Parchment | `org.parchmentmc.data:parchment-1.21.1:2024.11.17` | Param names + javadoc | Latest (and final) 1.21.1 release on `maven.parchmentmc.org`. Makes decompiled MC sources readable — worth it, costs nothing |
| foojay-resolver-convention | `1.0.0` | Auto-provision JDK 21 | Settings plugin in the official MDK; avoids "no matching toolchain" failures |

### Supporting Libraries

**None.** Do not add any.

| Library | Verdict | Reason |
|---------|---------|--------|
| Mixin | **Not needed** | Every behavior this mod needs is reachable through NeoForge events and public APIs (proven below: conversion is gated by `EventHooks.canLivingConvert`, offers by public `Villager#setOffers`). Mixin adds a load-order failure surface and mod-conflict risk for zero gain here. Omit `[[mixins]]` from `neoforge.mods.toml` |
| Access Transformers | **Probably not needed** | MDG auto-detects `src/main/resources/META-INF/accesstransformer.cfg`. Only add one if a specific private member proves unavoidable. `Villager#setOffers`, `setVillagerData`, `setVillagerXp`, `getOffers` are all already public — verified |
| owo-lib / accessories / wildcard | **Do not depend on** | Present in the test instance but unrelated. Do not declare them in `neoforge.mods.toml`; they are incidental to the instance |
| JEI, Curios, etc. | **Do not add** | Personal mod, no integration requirement |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `./gradlew build` | Produces `build/libs/secondshift-<version>.jar` | `base.archivesName = mod_id`, so the jar is named from `mod_id` |
| `./gradlew runClient` | Launches a dev client with the mod | Primary iteration loop for Claude; catches registration/GUI crashes without touching the CurseForge instance |
| `./gradlew runServer` | Dedicated server | Optional — single-player is the target, but it catches client-only class leaks into common code |
| `./gradlew runData` | Datagen (see Datagen section) | Only wire this in if datagen is adopted |
| `forge.logging.markers=REGISTRIES` | Logs registry event firing | Already set by the MDK run configs — invaluable for diagnosing the unbound-holder class of crash |

---

## Installation / Bootstrap

MDG needs the Gradle wrapper JAR, which cannot be authored as text. Bootstrap from the official
template, then overwrite the text files:

```bash
# From the repo root (C:/Users/user/Documents/PROJECTS/necromancy-mod)
curl -L -o mdk.zip https://codeload.github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/zip/refs/heads/main
unzip -q mdk.zip
cp -r MDK-1.21.1-ModDevGradle-main/gradle MDK-1.21.1-ModDevGradle-main/gradlew MDK-1.21.1-ModDevGradle-main/gradlew.bat .
cp MDK-1.21.1-ModDevGradle-main/.gitignore .
rm -rf MDK-1.21.1-ModDevGradle-main mdk.zip
# Then write build.gradle / settings.gradle / gradle.properties / sources yourself (below).
```

Also delete the stale artifact before the first in-game test:

```bash
rm "C:/Users/user/curseforge/minecraft/Instances/test/mods/second-shift-0.1.0.jar"
```

### `settings.gradle`

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

rootProject.name = 'secondshift'
```

### `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true

# Parchment (verified present on maven.parchmentmc.org)
parchment_minecraft_version=1.21.1
parchment_mappings_version=2024.11.17

# Environment — must match the CurseForge "test" instance exactly
minecraft_version=1.21.1
minecraft_version_range=[1.21.1]
neo_version=21.1.248
neo_version_range=[21.1,)
loader_version_range=[1,)

## Mod Properties
mod_id=secondshift
mod_name=Second Shift
mod_license=MIT
mod_version=0.1.0
mod_group_id=com.cxmxrgo.secondshift
```

Note the deliberate split: **compile** against `21.1.248` (`neo_version`) but **declare** a runtime
range of `[21.1,)` (`neo_version_range`). The stock MDK writes `versionRange="[${neo_version},)"`,
which pins the jar to `>= 21.1.248` and makes it silently refuse to load if the instance is ever
rolled back a patch. `[21.1,)` accepts any 21.1.x. Confidence: HIGH (Maven range semantics; the MDK's
own comment notes the loader range "can only use the major version of FML as bounds", which is why
`loader_version_range` stays `[1,)`).

### `build.gradle`

```groovy
plugins {
    id 'java-library'
    id 'net.neoforged.moddev' version '2.0.146'
    id 'idea'
}

version = mod_version
group = mod_group_id

base {
    archivesName = mod_id
}

// Mojang ships Java 21 to end users in 1.21.1, so mods must target Java 21.
java.toolchain.languageVersion = JavaLanguageVersion.of(21)

sourceSets.main.resources {
    srcDir('src/generated/resources')          // datagen output (harmless if unused)
    exclude('src/generated/**/.cache')
}

repositories {
    // MDG adds the NeoForged and Parchment repos automatically. Nothing needed here.
}

neoForge {
    version = project.neo_version

    parchment {
        minecraftVersion = project.parchment_minecraft_version
        mappingsVersion  = project.parchment_mappings_version
    }

    // Fail the build on bad AT targets if an accesstransformer.cfg is ever added.
    validateAccessTransformers = true

    runs {
        client {
            client()
            systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
        }
        server {
            server()
            programArgument '--nogui'
            systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
        }
        data {
            data()
            programArguments.addAll '--mod', project.mod_id,
                    '--all',
                    '--output',   file('src/generated/resources/').getAbsolutePath(),
                    '--existing', file('src/main/resources/').getAbsolutePath()
        }
        configureEach {
            // "REGISTRIES" logs each registry event as it fires — the single most useful
            // marker for diagnosing unbound-DeferredHolder crashes.
            systemProperty 'forge.logging.markers', 'REGISTRIES'
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        "${mod_id}" {
            sourceSet(sourceSets.main)
        }
    }
}

// Expand ${...} placeholders in src/main/templates into the built resources.
var generateModMetadata = tasks.register('generateModMetadata', ProcessResources) {
    var replaceProperties = [
            minecraft_version      : minecraft_version,
            minecraft_version_range: minecraft_version_range,
            neo_version            : neo_version,
            neo_version_range      : neo_version_range,
            loader_version_range   : loader_version_range,
            mod_id                 : mod_id,
            mod_name               : mod_name,
            mod_license            : mod_license,
            mod_version            : mod_version,
    ]
    inputs.properties replaceProperties
    expand replaceProperties
    from 'src/main/templates'
    into 'build/generated/sources/modMetadata'
}
sourceSets.main.resources.srcDir generateModMetadata
neoForge.ideSyncTask generateModMetadata

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

idea {
    module {
        downloadSources = true
        downloadJavadoc = true
    }
}
```

Differences from the stock MDK, and why: `maven-publish` and the `publishing {}` block are removed
(no publishing, per project constraints); the `gameTestServer` run is removed (no gametests planned —
it crashes on startup when no gametests are registered); `neo_version_range` is added for the reason
above; `-Xmx2G` because decompilation of MC 1.21.1 with 1G is tight.

### `src/main/templates/META-INF/neoforge.mods.toml`

Lives under `src/main/templates/`, **not** `src/main/resources/` — `generateModMetadata` expands the
`${...}` placeholders into `build/generated/sources/modMetadata/META-INF/neoforge.mods.toml`.
Putting it in `resources/` produces a jar containing literal `${mod_id}` and a load failure.

```toml
modLoader="javafml"
loaderVersion="${loader_version_range}"
license="${mod_license}"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
authors="cxmxrgo"
description='''
Harvest villager souls, bind them into employees, and choose their trades.
'''

[[dependencies.${mod_id}]]
    modId="neoforge"
    type="required"
    versionRange="${neo_version_range}"
    ordering="NONE"
    side="BOTH"

[[dependencies.${mod_id}]]
    modId="minecraft"
    type="required"
    versionRange="${minecraft_version_range}"
    ordering="NONE"
    side="BOTH"
```

No `[[mixins]]` block and no `[[accessTransformers]]` block. Add either only when a concrete need is
proven, not preemptively.

### Minimum source layout to `./gradlew build`

```
src/main/java/com/cxmxrgo/secondshift/SecondShift.java     @Mod("secondshift") + mod constructor
src/main/resources/assets/secondshift/lang/en_us.json      {} is valid to start
src/main/templates/META-INF/neoforge.mods.toml             as above
```

---

## API Surface: what to use for each subsystem

### 1. Registration — `DeferredRegister`

Verified against `neoforge-21.1.248-universal.jar`.

```java
public static final DeferredRegister.Blocks  BLOCKS = DeferredRegister.createBlocks(MODID);
public static final DeferredRegister.Items   ITEMS  = DeferredRegister.createItems(MODID);
public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, MODID);
public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);
public static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
```

Registry key constants verified present in 1.21.1: `Registries.MENU`, `Registries.BLOCK_ENTITY_TYPE`,
`Registries.DATA_COMPONENT_TYPE`, `Registries.RECIPE_TYPE`, `Registries.RECIPE_SERIALIZER`,
`Registries.VILLAGER_PROFESSION`, `Registries.POINT_OF_INTEREST_TYPE`;
`NeoForgeRegistries.ATTACHMENT_TYPES` (both the `Registry` and the `Keys` `ResourceKey` form).

Verified helper methods on the specialized registers (use these; they are terser and type-safe):

- `DeferredRegister.Blocks#registerBlock(String, Function<BlockBehaviour.Properties, ? extends B>, BlockBehaviour.Properties)` → `DeferredBlock<B>`
- `DeferredRegister.Blocks#registerSimpleBlock(String, BlockBehaviour.Properties)` → `DeferredBlock<Block>`
- `DeferredRegister.Items#registerItem(String, Function<Item.Properties, ? extends I>, Item.Properties)` → `DeferredItem<I>`
- `DeferredRegister.Items#registerSimpleItem(String, Item.Properties)` → `DeferredItem<Item>`
- `DeferredRegister.Items#registerSimpleBlockItem(String, Supplier<? extends Block>, Item.Properties)` → `DeferredItem<BlockItem>`
- `DeferredRegister.DataComponents#registerComponentType(String, UnaryOperator<Builder>)`

**Every `DeferredRegister` must be attached in the mod constructor:**

```java
@Mod(SecondShift.MODID)
public class SecondShift {
    public static final String MODID = "secondshift";

    public SecondShift(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);              // <-- the one that was missing before
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        // ...
    }
}
```

### 2. Menu + Screen — the known failure point

**Root cause of the prior crash, confirmed.** The message
`NullPointerException: Trying to access unbound value: ResourceKey[minecraft:menu / secondshift:binding_altar]`
is thrown verbatim by `DeferredHolder#value()` in
`neoforged/NeoForge@1.21.1:src/main/java/net/neoforged/neoforge/registries/DeferredHolder.java:103`:

```java
public T value() {
    bind(true);
    if (this.holder == null) {
        throw new NullPointerException("Trying to access unbound value: " + this.key);
    }
    return (T) this.holder.value();
}
```

It means the entry `secondshift:binding_altar` was **never actually added to `BuiltInRegistries.MENU`**.
`RegisterMenuScreensEvent` is merely the first code that dereferenced the holder. The three ways to
cause it, in likelihood order:

1. **The menu `DeferredRegister` was never `.register(modEventBus)`d in the mod constructor.** Most
   common. Registers for blocks/items get attached, a later-added `MENUS` register gets forgotten.
2. **The holder class is never class-loaded before the registry events fire.** `DeferredRegister.create`
   runs in a static initializer. If `ModMenus` is only ever touched from client-side screen code, its
   static init happens *after* `RegisterEvent` and nothing is registered. Fix: reference the class from
   the mod constructor (calling `.register(modBus)` does exactly this).
3. **Registered on the wrong bus** (`NeoForge.EVENT_BUS` instead of the mod bus).

**Correct registration pattern (all signatures verified by `javap`):**

```java
// ModMenus.java  — common code, class-loaded from the mod constructor
public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, SecondShift.MODID);

// IMenuTypeExtension.create(IContainerFactory<T>) -> MenuType<T>   [verified]
// IContainerFactory<T>.create(int, Inventory, RegistryFriendlyByteBuf) -> T   [verified]
public static final DeferredHolder<MenuType<?>, MenuType<BindingAltarMenu>> BINDING_ALTAR =
        MENUS.register("binding_altar",
                () -> IMenuTypeExtension.create(BindingAltarMenu::new));
```

```java
// BindingAltarMenu.java
public class BindingAltarMenu extends AbstractContainerMenu {

    // CLIENT constructor — this is the one IMenuTypeExtension.create binds to.
    // A FriendlyByteBuf parameter is fine: RegistryFriendlyByteBuf extends it, so the
    // method reference still satisfies IContainerFactory.
    public BindingAltarMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInv, ContainerLevelAccess.NULL, extraData.readBlockPos() /*, ... */);
    }

    // SERVER constructor
    public BindingAltarMenu(int containerId, Inventory playerInv, ContainerLevelAccess access, BlockPos pos) {
        super(ModMenus.BINDING_ALTAR.get(), containerId);
        // addSlot(...) / addDataSlots(...)
    }

    @Override public boolean stillValid(Player p) {
        return AbstractContainerMenu.stillValid(this.access, p, ModBlocks.SOUL_ALTAR.get());
    }
    @Override public ItemStack quickMoveStack(Player p, int i) { /* required */ }
}
```

```java
// client/ClientModEvents.java
@EventBusSubscriber(modid = SecondShift.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.BINDING_ALTAR.get(), BindingAltarScreen::new);
    }
}
```

Verified signature: `RegisterMenuScreensEvent#register(MenuType<? extends M>, MenuScreens.ScreenConstructor<M, U>)`
where `U extends Screen & MenuAccess<M>`; the event `implements IModBusEvent` (mod bus, client only).

Opening it server-side:

```java
// In SoulAltarBlock#useWithoutItem
if (!level.isClientSide && player instanceof ServerPlayer sp) {
    sp.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new BindingAltarMenu(id, inv, ContainerLevelAccess.create(level, pos), pos),
            Component.translatable("menu.secondshift.binding_altar")),
        buf -> buf.writeBlockPos(pos));   // Consumer<RegistryFriendlyByteBuf> — only valid with IContainerFactory
}
return InteractionResult.sidedSuccess(level.isClientSide);
```

**Guardrails to add so this class of failure is caught in seconds, not in a crash report:**

```java
// In FMLCommonSetupEvent (mod bus, both sides) — fails loudly and early with a clear message
private void commonSetup(FMLCommonSetupEvent e) {
    LOGGER.info("binding_altar menu registered as {}",
            BuiltInRegistries.MENU.getKey(ModMenus.BINDING_ALTAR.get()));
}
```

The MDK run configs already set `forge.logging.markers=REGISTRIES`, which prints each registry event
as it fires — if `minecraft:menu` never shows a `secondshift` entry, the register was not attached.

**Phase ordering recommendation:** build and verify block → block entity → `MenuType` +
`AbstractContainerMenu` + `AbstractContainerScreen` opening with an empty screen, and confirm it opens
in `runClient`, **before** adding any trade logic. This is the known-risk area; isolate it.

### 3. Networking (client → server trade selections)

Verified against `PayloadRegistrar` and `IPayloadContext` in the 21.1.248 universal jar.

```java
@SubscribeEvent  // mod bus
static void register(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar("1");
    registrar.playToServer(
            SelectTradesPayload.TYPE,
            SelectTradesPayload.STREAM_CODEC,
            ServerPayloadHandler::handleSelectTrades);
}
```

```java
public record SelectTradesPayload(BlockPos altarPos, List<Integer> chosenIndices)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SelectTradesPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(SecondShift.MODID, "select_trades"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectTradesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,                               SelectTradesPayload::altarPos,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),   SelectTradesPayload::chosenIndices,
                    SelectTradesPayload::new);

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

Key verified facts:
- `playToServer` / `playToClient` / `playBidirectional` take `StreamCodec<? super RegistryFriendlyByteBuf, T>` in the play phase (registry-aware buffers, needed for `ItemStack`/`MerchantOffer`).
- `IPayloadContext#player()` returns `Player`, not `ServerPlayer` — cast on the server side.
- Handlers run on the **main thread** by default. Do not call `executesOn(HandlerThread.NETWORK)` here; the payload is tiny and touching level/entity state off-thread is a bug.
- `PacketDistributor.sendToServer(payload)` / `sendToPlayer(serverPlayer, payload)` to send.
- Serverbound payloads are capped at <32 KiB; clientbound at 1 MiB. Sending a whole trade pool as `MerchantOffer`s is fine, but prefer sending indices into a server-derived list.

**Security note for a client→server packet:** never trust the client's chosen indices. Re-derive the
legal trade pool server-side from the profession + tier and validate the indices against it, and
validate the player is actually within range of the altar (`AbstractContainerMenu#stillValid` protects
the menu, not a raw payload).

### 4. Data attachments (the employee record on a vanilla villager)

Verified against `AttachmentType` and `AttachmentType$Builder` in the 21.1.248 universal jar.

```java
public static final Supplier<AttachmentType<EmployeeData>> EMPLOYEE =
        ATTACHMENT_TYPES.register("employee", () ->
                AttachmentType.builder(() -> EmployeeData.EMPTY)
                        .serialize(EmployeeData.CODEC)                 // disk persistence
                        .sync(EmployeeData.STREAM_CODEC)               // automatic client sync
                        .build());
```

Verified builder surface:

| Method | Signature |
|---|---|
| `AttachmentType.builder` | `(Supplier<T>)` or `(Function<IAttachmentHolder, T>)` → `Builder<T>` |
| `AttachmentType.serializable` | `(Supplier<T extends INBTSerializable<S>>)` → `Builder<T>` |
| `Builder#serialize` | `(Codec<T>)`, `(Codec<T>, Predicate<? super T>)`, or `(IAttachmentSerializer<?, T>)` |
| `Builder#sync` | `(StreamCodec<? super RegistryFriendlyByteBuf, T>)`, `(BiPredicate<IAttachmentHolder, ServerPlayer>, StreamCodec<...>)`, or `(AttachmentSyncHandler<T>)` |
| `Builder#copyOnDeath` | `()` |
| `Builder#build` | `()` → `AttachmentType<T>` |

**Use `.serialize(Codec)`, not `IAttachmentSerializer`.** A record + `RecordCodecBuilder` is less code,
immutable, and reuses the same shape you need for `StreamCodec`. `IAttachmentSerializer` only earns its
keep for mutable handler-style objects (e.g. `ItemStackHandler`), which this is not.

**Important correction to the published docs:** `docs.neoforged.net/docs/1.21.1/datastorage/attachments/`
states "To sync block entity, chunk, or entity attachments to a client, you need to send a packet to the
client yourself." That page is **stale relative to 21.1.248** — `Builder#sync(StreamCodec)` exists in the
shipped binary and its javadoc reads *"Requests that this attachment be synced to all clients that
receive the holding object."* Confidence: HIGH for existence (disassembled from the jar and read from
the 1.21.1 branch source); MEDIUM for behaviour in practice — verify empirically in the first phase that
touches it, because if it works it removes an entire clientbound packet from the design.

Usage: `villager.getData(EMPLOYEE)`, `villager.hasData(EMPLOYEE)`, `villager.setData(EMPLOYEE, next)`.
`getData` on an absent attachment attaches a fresh default — always guard reads with `hasData` when
"is this an employee?" is the question. `setData` marks the holder dirty automatically; mutating an
object returned by `getData` does not.

Entity attachments persist across save/load automatically once a serializer is provided. `copyOnDeath`
is player-respawn semantics and is irrelevant here.

### 5. Data components (bound-soul item state)

For state carried on an `ItemStack` (which employee/profession/pending level a Soul Block holds), use
**data components, not attachments** — item-stack attachments were superseded by vanilla data components
in 1.20.5+.

```java
public static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

public static final DeferredHolder<DataComponentType<?>, DataComponentType<BoundSoul>> BOUND_SOUL =
        COMPONENTS.registerComponentType("bound_soul", builder -> builder
                .persistent(BoundSoul.CODEC)               // disk
                .networkSynchronized(BoundSoul.STREAM_CODEC));  // network
```

Rules verified from the 1.21.1 docs: the component value **must** implement `hashCode`/`equals` and be
immutable (use a record); at least one of `persistent` / `networkSynchronized` must be supplied or the
builder throws NPE; if only `persistent` is given, the `Codec` is wrapped as the stream codec.

### 6. Block entity (Soul Altar)

```java
public static final Supplier<BlockEntityType<SoulAltarBlockEntity>> SOUL_ALTAR_BE =
        BLOCK_ENTITIES.register("soul_altar", () ->
                BlockEntityType.Builder.of(SoulAltarBlockEntity::new, ModBlocks.SOUL_ALTAR.get())
                        .build(null));
```

- The block must `implements EntityBlock` and override `newBlockEntity(BlockPos, BlockState)`.
- Persistence: override `loadAdditional(CompoundTag, HolderLookup.Provider)` and
  `saveAdditional(CompoundTag, HolderLookup.Provider)` — **always call `super`**.
- Client sync: override `getUpdateTag(HolderLookup.Provider)` (usually delegating to `saveAdditional`)
  and `getUpdatePacket()` returning `ClientboundBlockEntityDataPacket.create(this)`. Without both, the
  block entity's state is server-only and the screen will render stale data.
- Ticking: `EntityBlock#getTicker`. Only add if the altar needs a ritual timer.
- Call `setChanged()` after mutating fields, or the data silently fails to save.

Menu data flowing to the screen has three channels, in increasing cost: `DataSlot`/`ContainerData`
(ints, auto-ticked, NeoForge patches the packet to carry the full 32-bit int rather than vanilla's
short); the `IContainerFactory` extra-data buffer (one-shot, at open time); a custom payload (anything,
any time). For a trade picker, the extra-data buffer at open time plus one serverbound selection
payload is the right shape — no per-tick sync needed.

### 7. Recipes

**Do not write a custom `RecipeType`/`RecipeSerializer`.** 4 Soul Fragments → 1 Soul Block is a plain
`minecraft:crafting_shapeless` JSON. The Harvester is a plain `minecraft:crafting_shaped`, or
`minecraft:smithing_transform` if it should be an upgrade — both are vanilla serializers driven purely
by JSON in `data/secondshift/recipe/*.json`, zero Java. A custom recipe type costs a `Recipe`
implementation, a `RecipeInput`, a `RecipeType`, a `RecipeSerializer`, a `MapCodec`, and a `StreamCodec`
and buys nothing here.

If a custom type ever *is* needed (e.g. the binding ritual as a data-driven recipe), the 1.21.1 shape is:
`RecipeSerializer<T>` exposing `MapCodec<T> codec()` and `StreamCodec<RegistryFriendlyByteBuf, T> streamCodec()`,
plus `RecipeType.simple(ResourceLocation)`. Note 1.21.1 uses `MapCodec` (not `Codec`) for the recipe codec.

### 8. Villager trades — reading the vanilla pool and setting offers

All verified by `javap` against the 21.1.248 binary and Mojang's 1.21.1 mappings.

**The vanilla pool, two access paths:**

```java
// Path A — the raw static table.
// VillagerTrades.TRADES : Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>>   [verified]
Int2ObjectMap<VillagerTrades.ItemListing[]> byTier = VillagerTrades.TRADES.get(profession);
VillagerTrades.ItemListing[] tier1 = byTier.get(1);   // keys 1..5, never null for those keys
```

```java
// Path B — the NeoForge event, which is what other mods actually add trades through.
// Fires on NeoForge.EVENT_BUS, during reload via TagsUpdatedEvent, once per registered profession.
// Note the different value type: List<ItemListing>, not ItemListing[].
@SubscribeEvent
static void onVillagerTrades(VillagerTradesEvent event) {
    VillagerProfession prof = event.getType();
    Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();  // mutable
    RegistryAccess access = event.getRegistryAccess();
}
```

**Read `VillagerTrades.TRADES` for the picker UI only if you accept missing modded trades.** Modded
trades are added via `VillagerTradesEvent` and are *not* written back into `VillagerTrades.TRADES`.
To honour modded professions and modded trades (a stated project goal), cache the per-profession,
per-tier `List<ItemListing>` from `VillagerTradesEvent` into your own map at reload time, and read from
that cache. Confidence: HIGH on the mechanism (read from NeoForge 1.21.1 source and the event javadoc);
MEDIUM on there being no other mod-visible aggregation point — I did not find one, and the event's own
javadoc describes itself as the place trades are "gathered".

**`ItemListing` → concrete offer:**

```java
// interface VillagerTrades.ItemListing { MerchantOffer getOffer(Entity trader, RandomSource random); }  [verified]
MerchantOffer offer = listing.getOffer(villager, villager.getRandom());
// getOffer may return null for conditional listings (e.g. TreasureMapForEmeralds with no structure)
```

Note the shape of a "trade choice": an `ItemListing` is a *generator*, not a fixed trade. Calling
`getOffer` twice gives two different randomized offers. To let the player pick a trade and then get
exactly that trade, you must generate the candidate `MerchantOffer`s once, show those, and persist the
chosen `MerchantOffer` — not the listing index. `MerchantOffer.CODEC` and `MerchantOffer.STREAM_CODEC`
both exist in 1.21.1 (verified), as do `MerchantOffers.CODEC` / `MerchantOffers.STREAM_CODEC`, so a
chosen offer can go straight into the attachment codec and the payload stream codec. This is a
significant design enabler — do not hand-roll offer serialization.

**Applying offers to a villager:**

```java
villager.setOffers(MerchantOffers);          // public on Villager                    [verified]
MerchantOffers current = villager.getOffers();      // AbstractVillager               [verified]
villager.overrideOffers(MerchantOffers);     // AbstractVillager                      [verified]
villager.overrideXp(int);                    // AbstractVillager                      [verified]
villager.setVillagerXp(int) / getVillagerXp();      // Villager                       [verified]
villager.setVillagerData(new VillagerData(type, profession, level));                // [verified]
villager.getVillagerData().getProfession() / .getLevel() / .getType();              // [verified]
MerchantOffers#copy() / #getRecipeFor(ItemStack, ItemStack, int)                     // [verified]
```

Caution: `Villager#updateTrades()` (protected) is what vanilla calls on level-up, and it *appends*
freshly rolled trades from the pool. If the design is "the player chooses every trade", you must
re-assert your chosen `MerchantOffers` after any vanilla level-up, or hook the level-up path. Vanilla
`shouldIncreaseLevel()` / `increaseMerchantCareer()` are private — so the intervention point is a
tick/interaction event that detects the level changed and rewrites offers, not an override.
Confidence: MEDIUM — this is the single most likely place for surprising behaviour; budget a
dedicated verification step for it.

### 9. POI → profession, at runtime, no hardcoded list

```java
// PoiTypes.forState(BlockState) -> Optional<Holder<PoiType>>                          [verified]
Optional<Holder<PoiType>> poi = PoiTypes.forState(jobSiteState);

// VillagerProfession is a record:
//   (String name, Predicate<Holder<PoiType>> heldJobSite,
//    Predicate<Holder<PoiType>> acquirableJobSite,
//    ImmutableSet<Item> requestedItems, ImmutableSet<Block> secondaryPoi,
//    SoundEvent workSound)                                                            [verified]
Optional<VillagerProfession> profession = poi.flatMap(holder ->
        BuiltInRegistries.VILLAGER_PROFESSION.stream()
                .filter(p -> p.acquirableJobSite().test(holder))
                .findFirst());
```

`BuiltInRegistries.VILLAGER_PROFESSION` is a `DefaultedRegistry` (default: `minecraft:none`) — filter
out `NONE` and `NITWIT` explicitly, since `NONE`'s predicate may match unexpectedly. Also verified:
`PoiTypes.hasPoi(BlockState)` for a cheap pre-check. This approach picks up modded professions with
zero code changes, which is the stated goal.

Also verified: `BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession)` → `ResourceLocation` for the
translation key. NeoForge patches `Villager#getTypeName()` so modded professions produce
`entity.minecraft.villager.<namespace>.<path>` — mirror that convention if you display profession names.

### 10. Employee traits — the event hooks that make them possible

Verified by reading NeoForge's own patch files on the `1.21.1` branch, i.e. the actual injection sites:

| Trait | Hook | Evidence |
|---|---|---|
| Cannot be zombified | `LivingConversionEvent.Pre` (cancellable) | `patches/.../monster/Zombie.java.patch` gates the villager→zombie-villager conversion on `EventHooks.canLivingConvert(villager, EntityType.ZOMBIE_VILLAGER, ...)` |
| Not converted by lightning | `LivingConversionEvent.Pre` (cancellable) | `patches/.../npc/Villager.java.patch` gates `thunderHit` on `EventHooks.canLivingConvert(this, EntityType.WITCH, ...)` |
| Guaranteed Soul Fragment on Harvester kill | `LivingDropsEvent` / `LivingDeathEvent` | Check `event.getSource().getEntity()` and its held item; replace drops rather than adding to them |
| Employee drops Soul Block when killed by anything else | `LivingDropsEvent` | Same handler, inverted branch |
| **Cannot breed** | **No clean hook — GAP** | `patches/.../ai/behavior/VillagerMakeLove.java.patch` only adds an `isAddedToLevel()` check; `BabyEntitySpawnEvent` is **not** fired for villager breeding. `Villager#canBreed()` is public but unoverridable without a subclass |

One `LivingConversionEvent.Pre` handler (game bus) covers both conversion requirements:

```java
@SubscribeEvent
static void onConvert(LivingConversionEvent.Pre event) {
    if (event.getEntity() instanceof Villager v && v.hasData(ModAttachments.EMPLOYEE)) {
        event.setCanceled(true);
    }
}
```

**Open gap — breeding prevention.** The realistic options, none verified working:
`FinalizeSpawnEvent` filtered on `MobSpawnType.BREEDING` near an employee; cancelling
`EntityJoinLevelEvent` for baby villagers; or accepting a targeted AT/Mixin as the one exception.
Confidence: LOW on any specific approach. Flag the phase that implements employee traits as needing
its own research spike; do not assume this is a one-liner.

### 11. Harvester tool item

1.21.1 tool constructors verified from Mojang mappings:

```java
new SwordItem(Tier, Item.Properties)                                    // [verified]
SwordItem.createAttributes(Tier tier, int damage, float speed) -> ItemAttributeModifiers  // [verified]
Item.Properties#attributes(ItemAttributeModifiers) / #durability(int) / #stacksTo(int)
                                                / #component(DataComponentType, Object)  // [verified]
```

Custom tiers: `net.neoforged.neoforge.common.SimpleTier` or a vanilla `Tiers` constant. Attributes in
1.21.x live on the item's `ItemAttributeModifiers` data component, not on an overridden
`getAttributeModifiers` — the 1.20.x pattern is gone.

### 12. Datagen — worth it for this mod?

**Recommendation: no, not for the first milestone.** Hand-write the JSON.

The scope is roughly 3 items, 2 blocks, 2 recipes, and one lang file. `runData` adds a second runnable
configuration, a `src/generated/resources` source dir, and a class of "provider ran but produced
nothing" failures — all of which are noise while the actual risk is the GUI. The `data` run config is
kept in the `build.gradle` above so adopting it later costs nothing.

Adopt datagen (`GatherDataEvent` on the mod bus; verified present with
`addProvider` / `createProvider` / `createBlockAndItemTags` / `getLookupProvider`) when the item count
passes roughly ten, or when block variants with blockstates appear. Confidence: MEDIUM — this is a
judgement call about iteration speed on a solo project, not a technical constraint.

If datagen is adopted, the 1.21.1 providers are `RecipeProvider`, `ModelProvider`/`BlockStateProvider`,
`LanguageProvider`, `AdvancementProvider`, `TagsProvider`, wired through `GatherDataEvent#addProvider`.
`en_us.json` should stay hand-written regardless — `LanguageProvider` is pure overhead.

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| ModDevGradle 2.0.146 | NeoGradle | Only if the project needs multiple MC versions in one repo. NeoGradle is **not deprecated** — NeoForged endorses both — but MDG has the simpler buildscript and is the default in the official generator. Single-version personal mod → MDG |
| Vanilla villager + data attachment | Custom `EntityType` subclass | Never here — a subclass is the only clean way to override `canBreed()`/`updateTrades()`, but it breaks iron golems, villager-targeting mobs, entity tags, and other villager mods. The project already made this call correctly |
| `.serialize(Codec)` on the attachment | `IAttachmentSerializer` / `INBTSerializable` | Use `AttachmentType.serializable(...)` only for mutable handler objects like `ItemStackHandler` |
| `AttachmentType.Builder#sync` | Manual clientbound payload | Fall back to a manual payload if `sync` proves not to fire for entities in 21.1.248 (the 1.21.1 docs page predates it) |
| Vanilla JSON recipes | Custom `RecipeType` + `RecipeSerializer` | Only if the binding ritual itself should be data-driven and datapack-overridable |
| `VillagerTradesEvent` cache | Reading `VillagerTrades.TRADES` directly | `TRADES` is simpler and fine if modded trades don't matter. It won't see trades other mods add |
| Events + public API | Mixin | Only for the breeding gap, and only after `FinalizeSpawnEvent` / `EntityJoinLevelEvent` approaches are proven insufficient |
| Hand-written JSON | Datagen | Once past ~10 registry objects or when blockstate variants appear |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `MenuScreens.register(...)` called from `FMLClientSetupEvent` | The vanilla map is not safe to mutate from arbitrary setup code and this is the 1.20.x-era pattern | `RegisterMenuScreensEvent#register` on the mod bus, `Dist.CLIENT` |
| `net.minecraftforge.*` imports; `@Mod.EventBusSubscriber`; `FMLJavaModLoadingContext.get().getModEventBus()` | Forge-era APIs. NeoForge is `net.neoforged.*`, uses `@EventBusSubscriber`, and injects `IEventBus` into the mod constructor | `net.neoforged.*`; `@EventBusSubscriber(modid=…, bus=Bus.MOD)`; constructor injection |
| `mods.toml` at `META-INF/mods.toml` | Renamed in 1.20.5+ | `META-INF/neoforge.mods.toml`, in `src/main/templates/` |
| `SimpleChannel` / `NetworkRegistry` / `messageBuilder(...)` | Removed in 1.20.5+ | `RegisterPayloadHandlersEvent` + `CustomPacketPayload` + `StreamCodec` |
| `ItemStack` NBT (`getOrCreateTag`, `CompoundTag` on stacks) | Removed in 1.20.5+ | Data components |
| Item-stack data attachments | Superseded by vanilla data components | `DataComponentType` |
| `Item#getAttributeModifiers` override / `SwordItem(Tier, int, float, Properties)` | 1.20.x signatures; gone in 1.21 | `Item.Properties#attributes(SwordItem.createAttributes(tier, dmg, speed))` |
| `Codec` in `RecipeSerializer#codec()` | 1.21 uses `MapCodec` there | `MapCodec<T>` |
| Bumping `neo_version` past `21.1.248` | The test instance runs exactly 21.1.248; a newer compile target can reference symbols that don't exist at runtime | Pin `21.1.248`; declare a `[21.1,)` runtime range |
| `[[mixins]]` / `accesstransformer.cfg` "just in case" | Empty/unused entries are a load-failure surface and a compat risk for nothing | Omit until a concrete need is proven |
| Trusting client-sent trade indices | A client→server payload is unauthenticated input | Re-derive the legal pool server-side and validate |

---

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| `net.neoforged.moddev` 2.0.146 | Gradle 8.8 – 9.2.1 | 9.2.1 is what the official 1.21.1 MDK ships; MDG README claims 8.8 compatibility. Gradle configuration cache is enabled by default in the MDK and works |
| NeoForge 21.1.248 | Minecraft 1.21.1 only | The `21.1.x` line *is* 1.21.1. `21.0.x` is 1.21. Do not cross them |
| NeoForge 21.1.248 | FML loader 4.0.43 | Confirmed from the test instance's version JSON |
| Parchment `parchment-1.21.1:2024.11.17` | Minecraft 1.21.1 | Latest and final 1.21.1 release (Maven `lastUpdated` 2024-11-17). Dev-time only — never affects the shipped jar |
| Java 21 (Temurin 21.0.12) | MC 1.21.1 / NeoForge 21.1 | Already installed. `foojay-resolver-convention` 1.0.0 will provision it if the toolchain isn't found |
| `secondshift` jar | test instance mods (owo-lib, accessories, wildcard) | No declared interaction. Do not add dependencies on them |

---

## Sources

- `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml` — confirmed `21.1.248` and `21.1.249` exist; 21.1 is the 1.21.1 line. **HIGH**
- `https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/maven-metadata.xml` and `https://plugins.gradle.org/m2/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml` — MDG latest = `2.0.146` (2026-08-31), no 3.x line. **HIGH**
- `https://maven.parchmentmc.org/org/parchmentmc/data/parchment-1.21.1/maven-metadata.xml` — latest `2024.11.17`. **HIGH**
- `https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle` (`build.gradle`, `gradle.properties`, `settings.gradle`, `gradle-wrapper.properties`, `neoforge.mods.toml`) — the authoritative 1.21.1 template. **HIGH**
- `https://docs.neoforged.net/docs/1.21.1/` — versioned docs; source markdown read from `neoforged/Documentation@main:versioned_docs/version-1.21.1/` for `gui/menus.md`, `gui/screens.md`, `networking/payload.md`, `datastorage/attachments.md`, `items/datacomponents.md`, `blockentities/index.md`, `concepts/registries.md`, `resources/server/recipes/index.md`. **HIGH** (except the attachment-sync claim, which is stale — see §4)
- `neoforged/NeoForge@1.21.1` source: `registries/DeferredHolder.java` (exact crash message), `attachment/AttachmentType.java` (`sync` javadoc), `event/village/VillagerTradesEvent.java`, `patches/.../npc/Villager.java.patch`, `patches/.../monster/Zombie.java.patch`, `patches/.../ai/behavior/VillagerMakeLove.java.patch`. **HIGH**
- `javap` against `neoforge-21.1.248-client.jar` and `-universal.jar` on this machine — `VillagerTrades`, `VillagerTrades$ItemListing`, `PoiTypes`, `Villager`, `AbstractVillager`, `IMenuTypeExtension`, `RegisterMenuScreensEvent`, `AttachmentType(+Builder)`, `PayloadRegistrar`, `IPayloadContext`, `DeferredRegister$Items/$Blocks`, `LivingConversionEvent$Pre`, `BabyEntitySpawnEvent`, `GatherDataEvent`, `NeoForgeRegistries`. **HIGH — this is the exact binary the test instance loads**
- Mojang official 1.21.1 mappings (`client.txt`, sha `2244b6f072256667bcd9a73df124d6c58de77992`) — `VillagerProfession`, `VillagerData`, `MerchantOffer(s)`, `SwordItem`, `Item$Properties`, `Registries`, `BuiltInRegistries` member names and signatures. **HIGH**
- `C:\Users\user\curseforge\minecraft\Instances\test\minecraftinstance.json` — instance is `neoforge-21.1.248` / MC `1.21.1`, FML 4.0.43. **HIGH**
- `neoforged.net/news/moddevgradle2/` and the NeoForge MDK org listing (via web search) — MDG vs NeoGradle positioning; both endorsed, neither deprecated. **MEDIUM**

### Known gaps

1. **Breeding prevention has no verified hook.** `BabyEntitySpawnEvent` is not fired on the villager breeding path. Needs a dedicated spike in the employee-traits phase. Confidence: LOW on any candidate approach.
2. **`AttachmentType.Builder#sync` behaviour on entities in 21.1.248 is unverified empirically** — the API exists and its javadoc is unambiguous, but the 1.21.1 docs page contradicts it. Verify in the first phase that syncs employee data; fall back to a manual clientbound payload.
3. **Re-asserting chosen offers after vanilla level-up.** `Villager#updateTrades()` and `increaseMerchantCareer()` are non-public; the interception strategy is unproven. Confidence: MEDIUM.
4. **Whether `VillagerTradesEvent` is the only aggregation point for modded trades** — I found no other, but I did not exhaustively prove a negative.

---
*Stack research for: NeoForge 1.21.1 Minecraft mod (Second Shift)*
*Researched: 2026-09-04*
