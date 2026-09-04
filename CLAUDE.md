<!-- GSD:project-start source:PROJECT.md -->

## Project

**Second Shift**

Second Shift is a personal Minecraft mod (1.21.1, NeoForge) that reframes villager
trading as necromancy. You play a necromancer running a company of reanimated villager
"employees": instead of building confinement halls to pin down vanilla villagers, you
harvest their souls and bind them into employees whose trades you choose directly. It is
for the mod author's own play, built and iterated on locally.

**Core Value:** The bind-an-employee-and-choose-its-trades loop must work and feel good: harvest souls →
craft a Soul Block → bind a villager at the Soul Altar → pick its profession and choose
its trades at each level. If everything else is cut, this loop has to be reliable.

### Constraints

- **Tech stack**: Minecraft 1.21.1 + NeoForge 21.1.248, Java 21 — must match the CurseForge "test" instance exactly
- **Build ownership**: Claude sets up Gradle (NeoForge MDK + wrapper) and runs `./gradlew build` after every change; the user only launches the game to test. Each iteration's deliverable is a working jar copied to `C:\Users\user\curseforge\minecraft\Instances\test\mods\`
- **Feedback loop**: on failure, Claude reads `crash-reports\` and `logs\latest.log` from the test instance
- **Architecture**: employees are `minecraft:villager` + NeoForge data attachment; behavior lives in event handlers and the attachment, not in a subclass — Why: preserves compatibility with vanilla and modded villager systems
- **Distribution**: personal use only; MIT license retained, no publishing

<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->

## Technology Stack

## Verification note

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

## Installation / Bootstrap

# From the repo root (C:/Users/user/Documents/PROJECTS/necromancy-mod)

# Then write build.gradle / settings.gradle / gradle.properties / sources yourself (below).

### `settings.gradle`

### `gradle.properties`

# Parchment (verified present on maven.parchmentmc.org)

# Environment — must match the CurseForge "test" instance exactly

## Mod Properties

### `build.gradle`

### `src/main/templates/META-INF/neoforge.mods.toml`

### Minimum source layout to `./gradlew build`

## API Surface: what to use for each subsystem

### 1. Registration — `DeferredRegister`

- `DeferredRegister.Blocks#registerBlock(String, Function<BlockBehaviour.Properties, ? extends B>, BlockBehaviour.Properties)` → `DeferredBlock<B>`
- `DeferredRegister.Blocks#registerSimpleBlock(String, BlockBehaviour.Properties)` → `DeferredBlock<Block>`
- `DeferredRegister.Items#registerItem(String, Function<Item.Properties, ? extends I>, Item.Properties)` → `DeferredItem<I>`
- `DeferredRegister.Items#registerSimpleItem(String, Item.Properties)` → `DeferredItem<Item>`
- `DeferredRegister.Items#registerSimpleBlockItem(String, Supplier<? extends Block>, Item.Properties)` → `DeferredItem<BlockItem>`
- `DeferredRegister.DataComponents#registerComponentType(String, UnaryOperator<Builder>)`

### 2. Menu + Screen — the known failure point

### 3. Networking (client → server trade selections)

- `playToServer` / `playToClient` / `playBidirectional` take `StreamCodec<? super RegistryFriendlyByteBuf, T>` in the play phase (registry-aware buffers, needed for `ItemStack`/`MerchantOffer`).
- `IPayloadContext#player()` returns `Player`, not `ServerPlayer` — cast on the server side.
- Handlers run on the **main thread** by default. Do not call `executesOn(HandlerThread.NETWORK)` here; the payload is tiny and touching level/entity state off-thread is a bug.
- `PacketDistributor.sendToServer(payload)` / `sendToPlayer(serverPlayer, payload)` to send.
- Serverbound payloads are capped at <32 KiB; clientbound at 1 MiB. Sending a whole trade pool as `MerchantOffer`s is fine, but prefer sending indices into a server-derived list.

### 4. Data attachments (the employee record on a vanilla villager)

| Method | Signature |
|---|---|
| `AttachmentType.builder` | `(Supplier<T>)` or `(Function<IAttachmentHolder, T>)` → `Builder<T>` |
| `AttachmentType.serializable` | `(Supplier<T extends INBTSerializable<S>>)` → `Builder<T>` |
| `Builder#serialize` | `(Codec<T>)`, `(Codec<T>, Predicate<? super T>)`, or `(IAttachmentSerializer<?, T>)` |
| `Builder#sync` | `(StreamCodec<? super RegistryFriendlyByteBuf, T>)`, `(BiPredicate<IAttachmentHolder, ServerPlayer>, StreamCodec<...>)`, or `(AttachmentSyncHandler<T>)` |
| `Builder#copyOnDeath` | `()` |
| `Builder#build` | `()` → `AttachmentType<T>` |

### 5. Data components (bound-soul item state)

### 6. Block entity (Soul Altar)

- The block must `implements EntityBlock` and override `newBlockEntity(BlockPos, BlockState)`.
- Persistence: override `loadAdditional(CompoundTag, HolderLookup.Provider)` and
- Client sync: override `getUpdateTag(HolderLookup.Provider)` (usually delegating to `saveAdditional`)
- Ticking: `EntityBlock#getTicker`. Only add if the altar needs a ritual timer.
- Call `setChanged()` after mutating fields, or the data silently fails to save.

### 7. Recipes

### 8. Villager trades — reading the vanilla pool and setting offers

### 9. POI → profession, at runtime, no hardcoded list

### 10. Employee traits — the event hooks that make them possible

| Trait | Hook | Evidence |
|---|---|---|
| Cannot be zombified | `LivingConversionEvent.Pre` (cancellable) | `patches/.../monster/Zombie.java.patch` gates the villager→zombie-villager conversion on `EventHooks.canLivingConvert(villager, EntityType.ZOMBIE_VILLAGER, ...)` |
| Not converted by lightning | `LivingConversionEvent.Pre` (cancellable) | `patches/.../npc/Villager.java.patch` gates `thunderHit` on `EventHooks.canLivingConvert(this, EntityType.WITCH, ...)` |
| Guaranteed Soul Fragment on Harvester kill | `LivingDropsEvent` / `LivingDeathEvent` | Check `event.getSource().getEntity()` and its held item; replace drops rather than adding to them |
| Employee drops Soul Block when killed by anything else | `LivingDropsEvent` | Same handler, inverted branch |
| **Cannot breed** | **No clean hook — GAP** | `patches/.../ai/behavior/VillagerMakeLove.java.patch` only adds an `isAddedToLevel()` check; `BabyEntitySpawnEvent` is **not** fired for villager breeding. `Villager#canBreed()` is public but unoverridable without a subclass |

### 11. Harvester tool item

### 12. Datagen — worth it for this mod?

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

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| `net.neoforged.moddev` 2.0.146 | Gradle 8.8 – 9.2.1 | 9.2.1 is what the official 1.21.1 MDK ships; MDG README claims 8.8 compatibility. Gradle configuration cache is enabled by default in the MDK and works |
| NeoForge 21.1.248 | Minecraft 1.21.1 only | The `21.1.x` line *is* 1.21.1. `21.0.x` is 1.21. Do not cross them |
| NeoForge 21.1.248 | FML loader 4.0.43 | Confirmed from the test instance's version JSON |
| Parchment `parchment-1.21.1:2024.11.17` | Minecraft 1.21.1 | Latest and final 1.21.1 release (Maven `lastUpdated` 2024-11-17). Dev-time only — never affects the shipped jar |
| Java 21 (Temurin 21.0.12) | MC 1.21.1 / NeoForge 21.1 | Already installed. `foojay-resolver-convention` 1.0.0 will provision it if the toolchain isn't found |
| `secondshift` jar | test instance mods (owo-lib, accessories, wildcard) | No declared interaction. Do not add dependencies on them |

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

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
