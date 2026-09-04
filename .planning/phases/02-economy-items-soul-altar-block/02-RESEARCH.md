# Phase 2: Economy Items & Soul Altar Block - Research

**Researched:** 2026-09-04
**Domain:** NeoForge 1.21.1 (21.1.248) content mod — custom items, custom weapon behaviour via events, `EntityBlock` + `BlockEntity`, client-only `BlockEntityRenderer`, vanilla crafting recipes, custom advancement chain, creative tab, datagen-vs-handwritten decision
**Confidence:** HIGH for registration / block-entity / recipe / creative-tab / advancement mechanics (NeoForge 1.21.1 docs + the project's own binary-verified STACK.md); MEDIUM for the exact instakill event hook and the exact 1.21.1 `Item.Properties` enchantable/attribute helpers (flagged inline, verify against decompiled sources in the dev workspace)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Altar is an `EntityBlock` + `SoulAltarBlockEntity` with a **one-slot inventory** = exactly one Soul Block `ItemStack`. BE persists only that slot (`saveAdditional`/`loadAdditional` + `getUpdateTag`/`getUpdatePacket`). Stores nothing about professions/employees/binding. No menu, no `MenuProvider`, no client↔server channel this phase.
- **D-02:** Physical form — single **carved-stone pedestal** (waist-high lectern/enchanting-table silhouette) in blackstone or deepslate with soul-fire accents. Non-full-cube model. Pickaxe to mine, ~stone hardness (~3.5, enchanting-table-ish). Single block, no multiblock.
- **D-03:** Socketing is **one-way**. Right-click charged-empty altar holding a Soul Block → block consumed from hand into the BE slot. **No empty-hand retrieval. No hopper/dropper/automation access — do NOT expose a `Capability`/`IItemHandler`.** Player interaction only.
- **D-04:** Altar-break behaviour:
  - **Empty altar** → breaks normally, **drops itself** (ALTAR-07).
  - **Charged altar** (Soul Block socketed, no employee exists yet) → **no drops at all** (Soul Block destroyed, altar block also lost), **½ heart (1 dmg)** to the breaking player only (no block/environment damage), **cosmetic lightning bolt** (visual + thunder, no fire, no collateral). "Instakill the bound employee" half is **deferred to Phase 6 (ALTAR-06)**.
  - ⚠ **Planner flag:** ALTAR-07 only guarantees the *unbound/empty* self-drop. Confirm the "charged altar drops nothing, including the block" reading during planning; fallback is "altar block still drops, only the Soul Block is destroyed."
- **D-05:** Charged render — Soul Block **recessed/embedded into altar top, emissive (glowing), eye-of-ender style**, slow soul-particle wisp. **No bob.** Client-only `BlockEntityRenderer` (or emissive baked model + particle ticker — see Discretion). Socket-in + (empty) altar interactions play a borrowed vanilla soul sound (e.g. `SoundEvents.SOUL_ESCAPE`) + a small soul-particle burst. No retrieve sound (no retrieve).
- **D-06:** Altar recipe — shaped, mid-cost thematic, emerald as the "soul-binding" core: blackstone/deepslate frame + soul soil + a skull or bone + **1–2 emeralds**.
- **D-07:** Harvester form is a **scythe** (custom angled item model). Durable, enchantable tool: **~250 durability**, accepts enchantments (Unbreaking/Mending meaningful; Looting a no-op). **Vanilla sweep attack disabled** — single target only. **Left-click only** — no right-click ability this phase.
- **D-08:** **Instakill mechanic, not a damage stat.** Single melee hit on any `Villager` kills it instantly regardless of health/armor/resistance/absorption (soul-reap). Against **all non-villager entities** the Harvester deals **modest ~3–4 damage** (stone-sword-tier attack attribute) — weak, useless for mob farms.
- **D-09:** Target scope for instakill + guaranteed-Fragment — **any `net.minecraft.world.entity.npc.Villager`**, incl. baby villagers. **Wandering traders** (`WanderingTrader`, not a `Villager`) and **zombie villagers** (`ZombieVillager` extends `Zombie`) are **excluded** — normal ~3–4 damage. Planner may surface baby-villager inclusion to the user.
- **D-10:** Harvest FX + drop — Harvester kill of a villager: soul burst at body + a few wisps rising/drifting toward the player, vanilla soul sound (`SOUL_ESCAPE`). **All vanilla particles + sounds — no custom `SoundEvent`/`ParticleType` registration.** Drop via `LivingDropsEvent` (or `LivingDeathEvent`): Harvester kill of a villager → **clear vanilla drops, drop exactly 1 Soul Fragment**. Any non-Harvester kill of a plain villager → **vanilla loot unchanged, no Fragment**.
- **D-11:** Harvester recipe — shaped, scythe silhouette: blade = **2–3 emeralds (≈3)** + **bone haft** + **soul soil**. Obtainable at iron age + one Nether trip.
- **D-12:** Soul Fragment — normal item (drop + crafting ingredient only), **subtle enchant-style shimmer/foil** so a dropped Fragment is easy to spot. No light emission. **Sole source = villager harvest.**
- **D-13:** Soul Block — real placeable block (`BlockItem`). Recipes: `4 Soul Fragment → 1 Soul Block` and `1 Soul Block → 4 Soul Fragment`, both **vanilla shapeless, lossless** (ECON-03 locks the forward recipe as shapeless). Placed block: **low light (~7)**, occasional soul-wisp particles, buildable/stackable. Needs blockstate + model + loot table (drops itself).
- **D-14:** Creative tab — **one tab** containing every mod item + block. Registered via `CreativeModeTab` `DeferredRegister` + `BuildCreativeModeTabContentsEvent`. Icon + registry name + ordering + title are Claude's discretion (suggest Soul Block icon, title "Second Shift").
- **D-15:** Recipes gated behind a **guided custom advancement chain** (not plain `has_item` unlocks). Each advancement's description carries the clue for the next:
  1. **Emerald enters inventory** → advancement ("Necromantic Apprentice") + toast → **Harvester recipe unlocked**.
  2. **Harvest a villager** (obtain first Soul Fragment) → advancement → **Soul Block recipe unlocked**; description hints "four make something more".
  3. **Craft a Soul Block** → advancement → **Soul Altar recipe unlocked**; description hints "set a job-site block on top".
- **D-16:** Advancement trigger for step 2 — Claude's discretion; `inventory_changed` on Soul Fragment (simplest, robust) or `player_killed_entity`. POL-04 recipe-unlock advancements still fire the normal toast.
- **D-17:** ⚠ **datagen decision revisited for the planner.** STACK.md §12 says "hand-write JSON, no datagen for milestone 1". This phase adds ~3 items, 1 block, ~4 recipes, ~4 custom advancements, ~3 recipe-advancement files, loot tables, blockstates, models, lang. Near the "~10 registry objects / blockstate variants" threshold. **The planner decides** hand-written vs `GatherDataEvent` datagen; `en_us.json` stays hand-written regardless.

### Claude's Discretion

- datagen vs hand-written JSON for models/blockstates/recipes/advancements/loot tables (D-17).
- Harvester **item class** design (plain `Item` + attributes component vs `SwordItem`/`TieredItem` subclass) and **which event hook** implements the instakill (`LivingIncomingDamageEvent` / `AttackEntityEvent` / `LivingDamageEvent.Pre`) vs the drop replacement (`LivingDropsEvent` / `LivingDeathEvent`). Disable the vanilla sweep via the chosen item design.
- Exact recipe grid layouts and quantities within stated ranges (Harvester 2–3 emeralds, altar 1–2 emeralds).
- Exact vanilla sound IDs / particle types for socket / harvest / ambient FX; exact Soul Block light level (~7).
- Creative tab icon, registry id, item ordering, title string.
- BER implementation details (renderer vs emissive model + particle ticker); altar model geometry; exact block hardness/resistance numbers.
- Whether `SoulAltarBlockEntity` NBT carries a format/version int (cheap future-proofing).
- Advancement working names and exact clue wording (keep the HR/necromancer tone).

### Deferred Ideas (OUT OF SCOPE)

- **Custom paintings + in-village discovery hints** — new player-facing discovery/lore capability, needs painting registry + textures + village worldgen injection. **FLAG FOR ROADMAP** — its own small phase or a Phase 10 bolt-on.
- **Harvester right-click "inspect villager"** — deferred; revisit if scouting-before-bind proves annoying once binding exists (Phase 5+).
- **descriptionId / lang-key resolution self-check** (carried from Phase 1) — extend the startup guardrail to assert every registered object's `descriptionId` resolves to an `en_us.json` key. Phase 2 is the first phase with real translated content, so it *could* start here; **planner's call**, otherwise Phase 10.
- **Bespoke `SoundEvent` / `ParticleType` registration** — Phase 10 (POL-05) / v1.x.
- **Datagen adoption** (if planner keeps hand-written JSON for Phase 2) — revisit Phase 3–5.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ECON-01 | The Harvester is a craftable tool/weapon | Standard Stack §Harvester item; `crafting_shaped` recipe (§Recipes); creative-tab visibility |
| ECON-02 | Killing any villager with the Harvester drops exactly 1 Soul Fragment, guaranteed; no other vanilla drops changed | §Villager instakill + drop replacement — `LivingDamageEvent.Pre` (kill) + `LivingDropsEvent` (drop swap); `instanceof Villager` classification (D-09) |
| ECON-03 | 4 Soul Fragments craft into 1 Soul Block (vanilla shapeless recipe) | §Recipes — `minecraft:crafting_shapeless`, 1.21.1 result shape `{"id","count"}` |
| ALTAR-01 | The Soul Altar is a craftable block backed by a block entity | §Block entity (EntityBlock) — `newBlockEntity`, `BlockEntityType.Builder.of`, register on `BLOCK_ENTITIES` |
| ALTAR-07 | An unbound altar breaks normally and drops itself | §Altar break behaviour — `drops_self` loot table for empty; code branch in `playerWillDestroy`/`getDrops` for charged (D-04) |
| POL-01 | A creative mode tab contains every mod item and block | §Creative tab — `DeferredRegister.create(Registries.CREATIVE_MODE_TAB)` + `displayItems` / `BuildCreativeModeTabContentsEvent` |
| POL-03 | Every item and block has a model and texture (placeholder acceptable; missing is not) | §"Looks Done But Isn't"; §Models & blockstates — item models, blockstate JSON, non-full-cube altar model |
| POL-04 | Crafting recipes use vanilla recipe types (JEI/EMI automatic) and emit recipe-unlock advancements | §Recipes (vanilla serializers) + §Advancement chain — `rewards.recipes` grants recipe + fires "recipe unlocked" toast |

**Deferred boundary (do NOT implement — Phase 6):** ECON-04 (Harvester kill of your own employee → 1 Fragment only), ALTAR-06 (charged-altar break also instakills the bound employee). Phase 2 has no `EmployeeData` attachment, so the Harvester/drop handlers treat all `Villager`s identically; the handler must be written so a future `&& !hasData(EMPLOYEE)` branch slots in cleanly.
</phase_requirements>

## Summary

Everything in this phase is standard NeoForge 1.21.1 content-mod work with **one genuinely custom mechanic** (the villager instakill) and **one client/server-split risk** (the charged-altar renderer). The registration patterns, block-entity API, recipe JSON, creative tab, and advancement chain are all well-documented and already partially specified in the project's binary-verified `STACK.md`. There is **no new external dependency** — NeoForge 21.1.248 is already pinned and resolved from Phase 1.

The **instakill** should be implemented as a game-bus `LivingDamageEvent.Pre` handler (NeoForge 1.21.1's post-mitigation damage hook): detect "player + Harvester weapon + target `instanceof Villager`", then `setNewDamage(health + absorption + 1)` so the kill is lethal regardless of armor/Resistance/absorption. The **guaranteed Soul Fragment** is a separate `LivingDropsEvent` handler that clears the drop collection and inserts exactly one Fragment when the same "killed by Harvester" condition holds. Disable the sweep attack simply by **not** subclassing `SwordItem` — the sweep is innate to `SwordItem` instances only; a plain `Item` (or `Item`/`DiggerItem` with an `ItemAttributeModifiers` component) never sweeps.

The **Soul Altar** is a textbook `EntityBlock` + `BlockEntity` with a 1-slot inventory, `saveAdditional`/`loadAdditional` + `getUpdateTag`/`getUpdatePacket` for the charged render, socketed via `useItemOn` returning `ItemInteractionResult` (1.21.1's odd-one-out signature — see PITFALLS §10), and break consequences in `playerWillDestroy`/`getDrops`. The charged render is a client-only `BlockEntityRenderer` registered from `EntityRenderersEvent.RegisterRenderers` in a `Dist.CLIENT` class under `client/`.

**On D-17:** hand-written JSON is still the right call for this phase — but the deciding factor is not the raw count, it's the **advancement chain**. The custom recipe-gating (`rewards.recipes` on hand-authored advancements, and deliberately *omitting* the auto-generated recipe-advancement) is awkward to express through `RecipeProvider`, which insists on an unlock criterion and auto-generates a `advancement/recipes/...` file that would unlock the recipe on item pickup and defeat D-15. Hand-writing ~18 small JSON files keeps full control and avoids a second run configuration. Recommend adopting datagen at Phase 3–5 when `MenuType`/blockstate variants grow. Details and the counter-argument are in §Datagen Decision.

**Primary recommendation:** Hand-write all JSON. Harvester = plain `Item` subclass (`HarvesterItem extends Item`) with an `ItemAttributeModifiers` component and `.durability(250)`; no `SwordItem`. Instakill via `LivingDamageEvent.Pre`; Fragment via `LivingDropsEvent`; both keyed on `DamageSource#getWeaponItem()`. Altar = `EntityBlock` with a 1-slot `SimpleContainer`-backed BE, socket via `useItemOn`, break consequences in `playerWillDestroy`. Charged render = client-only `BlockEntityRenderer`. One `./gradlew runServer` this phase to catch a renderer leak.

## Architectural Responsibility Map

Minecraft-mod "tiers" are logical sides + the resource/data layer, not web tiers.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Item/block/BE/tab **registration** | Common (mod bus, `SecondShift` ctor) | — | `DeferredRegister` objects are common registry state; must be class-loaded from the constructor (PITFALLS §1) |
| Villager instakill decision | Logical server (`!level.isClientSide`) | — | Damage/death is server-authoritative; `LivingDamageEvent.Pre` fires both sides — guard it |
| Soul Fragment drop swap | Logical server | — | `LivingDropsEvent` is server-only in practice; item entities spawn server-side |
| Altar socket (consume Soul Block into BE) | Logical server | Common (block class) | Mutate BE + `setChanged()` only on server; `useItemOn` runs both sides, branch on `level.isClientSide` |
| Altar BE persistence | Logical server | — | `saveAdditional`/`loadAdditional` are server-side chunk save/load |
| Altar BE → client sync (charged flag + stack) | Logical server → logical client | — | `getUpdateTag`/`getUpdatePacket`; needed or the renderer shows stale state |
| Charged-altar render (embedded emissive Soul Block) | **Physical client only** | — | `BlockEntityRenderer`, `PoseStack`, `MultiBufferSource` do not exist on a dedicated server — must live in `client/` + `Dist.CLIENT` (PITFALLS §9) |
| Ambient particles (Soul Block wisps, altar wisp) | Logical client | — | `Block#animateTick` / client BE ticker; particles are client-only cosmetic |
| Break consequences (½-heart, lightning, no-drop) | Logical server | — | `player.hurt(...)`, `LightningBolt` spawn, drop suppression are server-side |
| Recipes / loot tables / advancements | Data pack (`data/secondshift/...`) | — | Pure JSON, loaded by both sides; singular 1.21 folder names |
| Models / blockstates / textures / lang | Resource pack (`assets/secondshift/...`) | Physical client | Client rendering + `en_us.json` |
| Creative tab contents | Logical client (`BuildCreativeModeTabContentsEvent` fires client-only) | Common (tab registration) | Tab object is common; population event is client-side |

## Standard Stack

**No new libraries.** Everything is NeoForge 21.1.248 + Minecraft 1.21.1, already resolved in Phase 1.

### Core APIs used this phase

| API | Purpose | Provenance |
|-----|---------|-----------|
| `DeferredRegister.Items` / `.Blocks` / `DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ...)` / `DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ...)` | Register items, blocks, BE type, creative tab | [VERIFIED: STACK.md §1, binary-verified against `neoforge-21.1.248-universal.jar`] |
| `DeferredRegister.Items#registerItem(String, Function<Item.Properties, ? extends Item>, Item.Properties)` | Register `HarvesterItem` with custom class | [VERIFIED: STACK.md §1] |
| `DeferredRegister.Items#registerSimpleBlockItem(String, Supplier<? extends Block>, Item.Properties)` | Soul Block's `BlockItem` | [VERIFIED: STACK.md §1] |
| `DeferredRegister.Blocks#registerBlock(String, Function<BlockBehaviour.Properties, ? extends Block>, BlockBehaviour.Properties)` | Register `SoulAltarBlock`, `SoulBlock` | [VERIFIED: STACK.md §1] |
| `BlockEntityType.Builder.of(SoulAltarBlockEntity::new, ModBlocks.SOUL_ALTAR.get()).build(null)` | BE type | [VERIFIED: STACK.md §6] |
| `EntityBlock` + `newBlockEntity(BlockPos, BlockState)` | Attach BE to block | [CITED: docs.neoforged.net/docs/1.21.1/blockentities/] |
| `BlockEntity#saveAdditional(CompoundTag, HolderLookup.Provider)` / `loadAdditional(CompoundTag, HolderLookup.Provider)` — always call `super` | BE persistence | [VERIFIED: STACK.md §6] |
| `BlockEntity#getUpdateTag(HolderLookup.Provider)` + `getUpdatePacket()` → `ClientboundBlockEntityDataPacket.create(this)` | BE → client sync for the charged render | [VERIFIED: STACK.md §6] + [CITED: docs.neoforged.net/docs/1.21.1/blockentities/] |
| `BlockBehaviour#useItemOn(ItemStack, BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)` → **`ItemInteractionResult`** | Socket a Soul Block (item-in-hand path) | [VERIFIED: PITFALLS §10, decompiled `BlockBehaviour` from `client-1.21.1-...-srg.jar`] |
| `BlockBehaviour#useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)` → **`InteractionResult`** | Empty-hand path (a no-op this phase — return `PASS`/`CONSUME`) | [VERIFIED: PITFALLS §10] |
| `Block#playerWillDestroy(Level, BlockPos, BlockState, Player)` | Charged-altar consequences (½-heart, lightning, suppress drop) — has the `Player` ref | [ASSUMED] verify signature against decompiled `Block.java` (1.21.1 added a return type change) |
| `BlockBehaviour#getDrops(BlockState, LootParams.Builder)` — read `LootContextParams.BLOCK_ENTITY` | Return empty list when charged; otherwise defer to loot table | [ASSUMED] verify param constant name (`BLOCK_ENTITY` vs `THIS_ENTITY`) against `LootContextParams` |
| `LivingDamageEvent.Pre` (game bus) — `getEntity()`, `getSource()`, `getNewDamage()`, `setNewDamage(float)`, `getContainer()` | Villager instakill (post-mitigation → beats armor/Resistance/absorption) | [CITED: docs.blamejared.com/1.21/en/neoforge/api/event/entity/living/LivingDamageEvent/] — MEDIUM: verify exact method names against the 21.1.248 jar |
| `LivingIncomingDamageEvent` (game bus, `ICancellableEvent`) — `getAmount()`/`setAmount()`, `getSource()` | Alternative/earlier hook; use if the design needs to cancel i-frames or veto the hit before mitigation | [CITED: docs.blamejared.com/1.21.1/.../LivingIncomingDamageEvent/] |
| `LivingDropsEvent` (game bus) — `getDrops()` returns `Collection<ItemEntity>`, `getSource()`, `getEntity()` | Clear vanilla drops, add exactly 1 Soul Fragment | [VERIFIED: STACK.md §10, CLAUDE.md API Surface §10] |
| `DamageSource#getWeaponItem()` → `@Nullable ItemStack` | Identify "killed by a Harvester" without relying on current held item | [ASSUMED] added in 1.20.5/1.21 — verify present on `DamageSource` in the 21.1.248 jar; fallback: `source.getEntity()` held-item check |
| `Item.Properties#durability(int)` / `#attributes(ItemAttributeModifiers)` / `#stacksTo(1)` | Harvester tool properties | [VERIFIED: STACK.md §11] |
| `SwordItem.createAttributes(Tier, int damage, float speed)` → `ItemAttributeModifiers` (reusable on a non-sword item) | Build the Harvester's attack modifiers | [VERIFIED: STACK.md §11] + [CITED: docs.neoforged.net/docs/1.21.1/items/tools/] |
| `DataComponents.ENCHANTABLE` / `new Enchantable(int)` via `Item.Properties#component(...)` **or** `Item.Properties#enchantable(int)` | Make the Harvester enchantable in an enchanting table | [ASSUMED] — verify which form 1.21.1 exposes against decompiled `net.minecraft.world.item.Items` (see how `IRON_SWORD` is built) |
| `DataComponents.ENCHANTMENT_GLINT_OVERRIDE` (`true`) on the Soul Fragment | D-12 "subtle enchant-style shimmer/foil" | [ASSUMED] — verify component name; alternative is `foil` in the item model or a `minecraft:rarity` |
| `CreativeModeTab.builder().title(...).icon(...).displayItems((params, output) -> ...)` | The one mod tab | [CITED: docs.neoforged.net/docs/1.21.1/items/#creative-tabs] |
| `BuildCreativeModeTabContentsEvent` (mod bus, client) — `getTabKey()`, `accept(...)` | Alternative/adjunct population path | [CITED: same] |
| `BlockBehaviour.Properties#lightLevel(state -> 7)` | Soul Block ambient light | [ASSUMED] method name `lightLevel` in 1.21.1 — verify (`lightLevel` vs `lightEmission`) |
| `Block#animateTick(BlockState, Level, BlockPos, RandomSource)` | Client-side ambient soul-wisp particles for Soul Block + altar | [ASSUMED] verify signature (1.21.1 uses `RandomSource`) |
| `EntityRenderersEvent.RegisterRenderers` (mod bus, `Dist.CLIENT`) — `registerBlockEntityRenderer(type, ctx -> renderer)` | Register the charged-altar `BlockEntityRenderer` | [CITED: docs.neoforged.net/docs/1.21.1/blockentities/ + rendering docs] |
| `LightningBolt` via `EntityType.LIGHTNING_BOLT.create(level)` + `setVisualOnly(true)` + `moveTo(...)` + `level.addFreshEntity(...)` | Cosmetic lightning on charged-altar break (D-04) | [ASSUMED] `setVisualOnly` is the vanilla method for "no fire, no damage" lightning — verify name |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `HarvesterItem extends Item` + attributes component | `SwordItem` subclass | `SwordItem` gets the sweep attack for free — the opposite of D-07. Would need to override attack behaviour to suppress it. Plain `Item` never sweeps. **Use plain `Item`.** |
| `HarvesterItem extends Item` | `DiggerItem` / a hoe-like `TieredItem` | Gives durability + tier-based enchantability + a repair ingredient for free, still no sweep. Viable if you want emerald as the repair item. Slightly more ceremony (need a `Tier`). Reasonable second choice. |
| `LivingDamageEvent.Pre` for the kill | `LivingIncomingDamageEvent` + `setAmount(MAX_VALUE)` | `LivingIncomingDamageEvent` is *pre-mitigation* — Resistance V (100% reduction) or huge absorption could still survive `MAX_VALUE`. `LivingDamageEvent.Pre` is *post-mitigation*: `setNewDamage(health + absorption + 1)` is guaranteed lethal. |
| `LivingDamageEvent.Pre` for the kill | `AttackEntityEvent` (cancel vanilla hit, then `villager.hurt(...)` / `villager.kill()`) | Cleanest "left-click detection" but you then own the whole death: must pick a lethal `DamageSource`, and `kill()`/`genericKill` can still be reduced by Resistance. More moving parts. |
| `LivingDropsEvent` for the Fragment | `LivingDeathEvent` (spawn the item entity manually) | `LivingDeathEvent` has no drop collection — you'd `level.addFreshEntity(new ItemEntity(...))` and separately suppress vanilla drops (harder). `LivingDropsEvent` gives you `getDrops()` to clear + repopulate in one place. |
| Hand-written JSON | `GatherDataEvent` datagen | See §Datagen Decision. Datagen wins past ~10 registry objects / when blockstate variants appear; the advancement-gating friction tips this phase toward hand-written. |
| Client-only `BlockEntityRenderer` for the charged render | Emissive baked model + `animateTick` particle ticker | A static model can't do "recessed Soul Block appears only when charged" cleanly without extra blockstate properties, and vanilla block models have no emissive flag (that needs a custom model loader / `neoforge:` render type tricks). BER is the least-surprising path and also owns the wisp. **Use BER.** |
| One-slot BE inventory as a raw `ItemStack` field | `ItemStackHandler` (NeoForge) capability | D-03 explicitly forbids exposing an `IItemHandler`/`Capability` (no hopper access). A plain `ItemStack` field (or private `SimpleContainer`) with manual save/load is correct here. |

**Installation:** none — no `build.gradle` dependency changes. If datagen is adopted, the `data` run config already exists in `build.gradle` (STACK.md) and needs only a `@EventBusSubscriber`/`modBus.addListener` for `GatherDataEvent`.

**Version verification:** `neo_version=21.1.248` confirmed in `gradle.properties`; matches the CurseForge "test" instance (STACK.md, binary-verified). No registry lookups needed.

## Package Legitimacy Audit

**Not applicable — this phase installs no external packages.** All APIs are from `net.minecraft.*` (Minecraft 1.21.1) and `net.neoforged.*` (NeoForge 21.1.248), both already resolved and pinned in Phase 1. `slopcheck` / `npm view` / `pip index` are not relevant to a Gradle/NeoForge mod with zero new dependencies. If the planner adopts datagen, it uses only NeoForge-bundled providers — still no new artifact.

## Architecture Patterns

### System Architecture Diagram

```
                         ┌──────────────────────────── PLAYER ACTIONS ────────────────────────────┐
                         │                                                                        │
        left-click villager w/ Harvester                            right-click altar w/ Soul Block in hand
                         │                                                                        │
                         ▼                                                                        ▼
      ┌──────────────────────────────────┐                        ┌──────────────────────────────────────────┐
      │ GAME BUS  LivingDamageEvent.Pre  │  (logical server)      │ SoulAltarBlock#useItemOn                  │
      │  is source entity a Player?      │                        │  level.isClientSide? ── yes ─► return     │
      │  source.getWeaponItem()          │                        │        SUCCESS (client feedback only)     │
      │    instanceof HarvesterItem?     │                        │  stack.is(SOUL_BLOCK_ITEM)?               │
      │  target instanceof Villager?     │                        │  BE.isEmpty()?                            │
      │   └► setNewDamage(hp+absorb+1)   │                        │   └► BE.setStack(stack.copyWithCount(1))  │
      └───────────────┬──────────────────┘                        │      stack.shrink(1); BE.setChanged()     │
                      │ villager dies                             │      level.playSound(SOUL_ESCAPE)         │
                      ▼                                           │      level.sendBlockUpdated(...)          │
      ┌──────────────────────────────────┐                        │      return ItemInteractionResult        │
      │ GAME BUS  LivingDropsEvent       │                        │        .SUCCESS                           │
      │  same "killed by Harvester" gate │                        └───────────────────┬──────────────────────┘
      │   └► drops.clear()               │                                            │ setChanged() marks dirty
      │      drops.add(1× SoulFragment)  │                                            ▼
      │  else: leave drops untouched     │                    ┌──────────────────────────────────────────────┐
      └───────────────┬──────────────────┘                    │ SoulAltarBlockEntity                          │
                      │                                       │  ItemStack heldSoulBlock  (the ONLY state)    │
              soul burst FX + SOUL_ESCAPE                      │  saveAdditional / loadAdditional (+super)     │
              (server broadcasts particles/sound)             │  getUpdateTag / getUpdatePacket ──────────┐   │
                                                              └──────────────────────────────────────────┼───┘
                                                                                                         │ BE data packet
                                                    ┌────────────────────────────────────────────────────▼──────────┐
                                                    │ CLIENT  SoulAltarRenderer (BlockEntityRenderer)               │
                                                    │  heldSoulBlock empty?  ── yes ─► render nothing extra         │
                                                    │  else: render Soul Block recessed in altar top, FULL_BRIGHT   │
                                                    │        + slow soul-particle wisp (no bob)                     │
                                                    │  registered from EntityRenderersEvent.RegisterRenderers       │
                                                    │  (Dist.CLIENT class in client/)                               │
                                                    └──────────────────────────────────────────────────────────────┘

   break altar ─► SoulAltarBlock#playerWillDestroy (logical server)
        BE.isEmpty()?  ── yes ─► normal path, drops_self loot table (ALTAR-07)
        else (charged): spawn visual-only LightningBolt at pos; player.hurt(1.0);
                        set a "suppress drops" flag; getDrops returns EMPTY (D-04)

   RECIPE DISCOVERY (data pack + advancements, both sides load JSON):
        emerald → inventory  ──► advancement "necromantic_apprentice"  ──rewards.recipes──► secondshift:harvester
        Soul Fragment → inventory ──► advancement "first_harvest"      ──rewards.recipes──► secondshift:soul_block
        craft Soul Block ──► advancement "soul_mason"                  ──rewards.recipes──► secondshift:soul_altar
        (the vanilla auto recipe-advancement is DELIBERATELY NOT authored, so the recipe
         is only ever granted by its gating advancement — D-15)
```

### Recommended Project Structure (extends the Phase 1 skeleton; matches ARCHITECTURE.md)

```
src/main/java/com/cxmxrgo/secondshift/
├── SecondShift.java                    // + register ModBlocks/ModBlockEntities/ModCreativeTab;
│                                       //   + addListener for the two game-bus handlers (or @EventBusSubscriber)
├── ModRegistrySelfCheck.java           // + add ModBlocks.BLOCKS, ModBlockEntities.*, ModCreativeTab.* to Stream.of(...)
├── registry/
│   ├── ModItems.java                   // DELETE debug_marker; add HARVESTER, SOUL_FRAGMENT, SOUL_BLOCK_ITEM
│   ├── ModBlocks.java                  // NEW — SOUL_BLOCK, SOUL_ALTAR
│   ├── ModBlockEntities.java           // NEW — SOUL_ALTAR_BE
│   └── ModCreativeTab.java             // NEW — one tab
├── content/
│   ├── item/HarvesterItem.java         // extends Item; attributes component; no SwordItem
│   ├── block/SoulBlock.java            // extends Block; animateTick particles; lightLevel(7)
│   ├── block/SoulAltarBlock.java       // implements EntityBlock; useItemOn; playerWillDestroy; getDrops; getShape
│   └── blockentity/SoulAltarBlockEntity.java
├── event/
│   └── HarvesterEvents.java            // @EventBusSubscriber(bus = GAME) — LivingDamageEvent.Pre + LivingDropsEvent
└── client/
    ├── ClientModBusEvents.java         // + EntityRenderersEvent.RegisterRenderers
    └── render/SoulAltarRenderer.java   // BlockEntityRenderer<SoulAltarBlockEntity>

src/main/resources/
├── assets/secondshift/
│   ├── lang/en_us.json                 // fill: 4 objects + tab title + 3 advancement title/desc pairs
│   ├── models/item/{harvester,soul_fragment,soul_block,soul_altar}.json
│   ├── models/block/{soul_block,soul_altar}.json
│   ├── blockstates/{soul_block,soul_altar}.json
│   └── textures/{item,block}/*.png
└── data/secondshift/
    ├── recipe/{harvester,soul_block,soul_fragment_from_block,soul_altar}.json
    ├── loot_table/blocks/{soul_block,soul_altar}.json
    └── advancement/{necromantic_apprentice,first_harvest,soul_mason}.json
        // NOTE: NO advancement/recipes/*.json  ← deliberate (D-15)
```

### Pattern 1: Villager instakill via post-mitigation damage override

**What:** A game-bus handler that forces lethal damage after all vanilla reductions.
**When to use:** "ignore health/armor/resistance/absorption" (D-08).

```java
// event/HarvesterEvents.java
@EventBusSubscriber(modid = SecondShift.MODID)   // bus derived from event type (game bus) — PITFALLS §2
public final class HarvesterEvents {

    @SubscribeEvent
    static void onDamagePre(LivingDamageEvent.Pre event) {          // NeoForge 1.21.1 post-mitigation hook
        if (!isHarvesterKillOfVillager(event.getEntity(), event.getSource())) return;
        LivingEntity target = event.getEntity();
        // guaranteed lethal regardless of Resistance/absorption:
        event.setNewDamage(target.getHealth() + target.getAbsorptionAmount() + 1.0F);
    }

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        if (!isHarvesterKillOfVillager(event.getEntity(), event.getSource())) return;
        event.getDrops().clear();                                   // ECON-02: replace, don't add
        Level level = event.getEntity().level();
        var frag = new ItemEntity(level,
                event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(),
                new ItemStack(ModItems.SOUL_FRAGMENT.get()));
        event.getDrops().add(frag);
    }

    private static boolean isHarvesterKillOfVillager(LivingEntity target, DamageSource src) {
        if (target.level().isClientSide) return false;
        if (!(target instanceof Villager)) return false;            // D-09: excludes WanderingTrader + ZombieVillager
        if (!(src.getEntity() instanceof Player)) return false;
        ItemStack weapon = src.getWeaponItem();                     // snapshot of the weapon used
        return weapon != null && weapon.getItem() instanceof HarvesterItem;
        // Phase 6 (ECON-04): add `&& !((Villager) target).hasData(ModAttachments.EMPLOYEE)`
        //   to a SEPARATE branch that still drops 1 Fragment but never a Soul Block.
    }
}
```

Notes: `src.getWeaponItem()` is the robust check (survives the player swapping hotbar slots between hit and death). If it turns out not to exist on `DamageSource` in 21.1.248, fall back to `src.getEntity() instanceof Player p && p.getMainHandItem().getItem() instanceof HarvesterItem` — acceptable because the kill is a single instant melee hit.

### Pattern 2: Soul Altar socket via `useItemOn` (1.21.1 `ItemInteractionResult`)

```java
// content/block/SoulAltarBlock.java
@Override
protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
    if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be))
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    if (!stack.is(ModItems.SOUL_BLOCK_ITEM.get()) || !be.isEmpty())
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;   // D-03: one-way, no retrieval
    if (!level.isClientSide) {
        be.setHeldSoulBlock(stack.copyWithCount(1));
        stack.consume(1, player);                                         // respects creative mode
        be.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);       // push getUpdatePacket to trackers
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 0.8F, 1.0F);
        // small server-broadcast soul-particle burst via level.sendParticles(...) if this is a ServerLevel
    }
    return ItemInteractionResult.sidedSuccess(level.isClientSide);
}

@Override
protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
    return InteractionResult.PASS;   // no menu, no retrieval this phase (D-01/D-03)
}
```

⚠ **Always `@Override`** so a wrong signature fails to compile (PITFALLS §10). `ItemInteractionResult` values: `SUCCESS`, `CONSUME`, `CONSUME_PARTIAL`, `FAIL`, `PASS_TO_DEFAULT_BLOCK_INTERACTION`, `SKIP_DEFAULT_BLOCK_INTERACTION`.

### Pattern 3: Block entity with exactly one persisted thing + client sync

```java
// content/blockentity/SoulAltarBlockEntity.java
public class SoulAltarBlockEntity extends BlockEntity {
    private static final int NBT_VERSION = 1;                     // D-17 discretion: cheap future-proofing
    private ItemStack heldSoulBlock = ItemStack.EMPTY;

    public SoulAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOUL_ALTAR_BE.get(), pos, state);
    }

    public boolean isEmpty()             { return heldSoulBlock.isEmpty(); }
    public ItemStack getHeldSoulBlock()  { return heldSoulBlock; }
    public void setHeldSoulBlock(ItemStack s) { this.heldSoulBlock = s; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("DataVersion", NBT_VERSION);
        if (!heldSoulBlock.isEmpty())
            tag.put("SoulBlock", heldSoulBlock.save(reg));         // ItemStack#save(HolderLookup.Provider) — 1.21 shape
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        heldSoulBlock = tag.contains("SoulBlock")
                ? ItemStack.parse(reg, tag.getCompound("SoulBlock")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {   // chunk-load sync
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, reg);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {    // block-update sync (renderer needs this)
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
```

Verify the exact `ItemStack` save/parse helpers against decompiled `ItemStack` (1.21.1 uses `Codec`-based `save(HolderLookup.Provider)` / `parse` / `parseOptional`).

### Pattern 4: Charged-altar break consequences (D-04) — code, not just a loot table

```java
// content/block/SoulAltarBlock.java
@Override
public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
    if (!level.isClientSide
            && level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be && !be.isEmpty()) {
        // cosmetic lightning: no fire, no block damage, no entity damage
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(pos));
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        player.hurt(level.damageSources().magic(), 1.0F);           // ½ heart, player only — pick a source that
                                                                    //   bypasses armor for consistency
        be.setHeldSoulBlock(ItemStack.EMPTY);                       // Soul Block destroyed → nothing to drop
        // mark "this altar drops nothing" — simplest: a transient field read by getDrops(), OR
        // override onRemove/getDrops to return empty when the block still has (had) a charged BE.
    }
    return super.playerWillDestroy(level, pos, state, player);
}
```

For the "charged altar drops nothing including the block itself" reading (⚠ planner flag in D-04): the cleanest implementation is `getDrops` returning an empty list when the BE was charged. Because `playerWillDestroy` runs before the BE is cleared, you can stash a boolean on the block instance is *not* safe (blocks are singletons) — instead read the BE inside `getDrops` via `params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)`, which is still present during drop computation.

### Anti-Patterns to Avoid

- **Subclassing `SwordItem` for the Harvester** — drags in the sweep attack (innate to `SwordItem`), contradicting D-07. Use plain `Item`.
- **Instakill via `LivingIncomingDamageEvent.setAmount(BIG)`** — pre-mitigation; Resistance V / absorption can survive it. Use `LivingDamageEvent.Pre.setNewDamage(...)`.
- **Adding the Fragment with `event.getDrops().add(...)` without clearing** — ECON-02 says "no other vanilla drops changed"; villagers normally drop nothing, but a clear-then-add is explicit and correct.
- **Exposing an `IItemHandler`/`Capability` on the altar BE** — D-03 forbids it (no hopper access). Plain `ItemStack` field.
- **Referencing `SoulAltarRenderer` / `Minecraft` / `PoseStack` from `content/` or `event/`** — client-class leak (PITFALLS §9). Renderer lives in `client/render/`, referenced only from the `Dist.CLIENT` `EntityRenderersEvent` handler. Run `./gradlew runServer` once to confirm.
- **Plural data folders** (`recipes/`, `loot_tables/`, `advancements/`) — silently ignored in 1.21. Singular only. Phase 1 already stubbed the singular form; don't regress.
- **Authoring the vanilla auto recipe-advancement** (`advancement/recipes/secondshift/*.json`) — it would grant the recipe on item pickup and defeat the D-15 gating. Omit it entirely.
- **Custom `RecipeType`/`RecipeSerializer`** — CLAUDE.md "What NOT to Use". All four recipes are `crafting_shaped` / `crafting_shapeless`.
- **Forgetting to add the new registers to `ModRegistrySelfCheck`'s `Stream.of(...)`** — PITFALLS §1; the guardrail only checks what it's told to.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| "Killed by weapon X" detection | A `Map<UUID, ItemStack>` populated on `AttackEntityEvent` and read on death | `DamageSource#getWeaponItem()` | Vanilla already snapshots the weapon on the damage source in 1.21 |
| Guaranteeing a lethal hit | Manually zeroing armor/effects, or `entity.remove()` | `LivingDamageEvent.Pre#setNewDamage(health + absorption + 1)` | Post-mitigation hook; one line; death path (and thus `LivingDropsEvent`) still fires normally |
| BE → client sync | A custom `CustomPacketPayload` for the altar | `getUpdateTag` + `getUpdatePacket` + `level.sendBlockUpdated` | Vanilla BE sync; no payload registration, no `RegisterPayloadHandlersEvent` (that's Phase 3) |
| ItemStack NBT persistence | Hand-written `CompoundTag` field-by-field | `ItemStack#save(HolderLookup.Provider)` / `ItemStack#parse(...)` | 1.21 data-components serialization is codec-driven; hand-rolling it breaks on enchanted/named Soul Blocks |
| Recipe-unlock toast | A custom advancement trigger + chat message | `rewards: { "recipes": [...] }` on the gating advancement | Vanilla fires the "recipe unlocked" toast automatically when a not-yet-known recipe is granted |
| "Item appears in creative" | Manual `CreativeModeTabs` mixin | `displayItems` callback or `BuildCreativeModeTabContentsEvent` | Standard NeoForge |
| Cosmetic lightning | Particle + sound + light hackery | `LightningBolt#setVisualOnly(true)` | Vanilla flag for "thunder + flash, no fire, no damage" |
| Non-full-cube collision/render | Custom `RenderShape` enum value | `Properties#noOcclusion()` + `getShape()` returning a `VoxelShape` + a normal model | Vanilla pedestal blocks (lectern, enchanting table) do exactly this |

**Key insight:** the only thing genuinely custom in this phase is *the decision* "this hit kills this villager". Everything downstream (death, drops, XP, advancements, particles) is vanilla plumbing you should let run.

## Datagen Decision (D-17) — concrete analysis

### Asset inventory for this phase

| Category | Files (hand-written) | Notes |
|----------|---------------------|-------|
| Item models | 4 (`harvester`, `soul_fragment`, `soul_block`, `soul_altar`) | `harvester`/`soul_fragment` = `item/generated`; `soul_block` = `block/soul_block` parent; `soul_altar` = `builtin/entity` or a simple parent |
| Block models | 2 (`soul_block` cube_all, `soul_altar` custom elements) | The altar model is the only non-trivial one — hand-modelled either way (BlockBench), datagen can't design geometry |
| Blockstates | 2 (`soul_block` single variant, `soul_altar` single variant or facing) | Trivial |
| Recipes | 4 (`harvester` shaped, `soul_block` shapeless, `soul_fragment_from_block` shapeless, `soul_altar` shaped) | |
| Loot tables | 2 (`soul_block` drops_self, `soul_altar` drops_self + code branch) | |
| Advancements | 3 gating (`necromantic_apprentice`, `first_harvest`, `soul_mason`) | Plus a hidden `root` if you want a tab, or parent to `minecraft:recipes/root` |
| Recipe-advancements | **0 (deliberately omitted)** | This is the crux |
| Lang | 1 (`en_us.json`) | Hand-written **regardless** (STACK.md §12) |
| **Total JSON** | **~18** | Excluding the hand-modelled altar geometry |

### The deciding factor: advancement-gated recipes fight `RecipeProvider`

`net.minecraft.data.recipes.RecipeProvider` / `RecipeBuilder#save` **require** an unlock criterion (`unlockedBy(...)`) and **auto-generate** a `advancement/recipes/<namespace>/<path>.json` with an `inventory_changed` criterion and `rewards.recipes`. That auto-advancement unlocks the recipe the moment the player picks up the ingredient — exactly what D-15 forbids. Suppressing it through datagen means either overriding `RecipeProvider.buildAdvancement`/passing a null advancement holder (fragile, version-specific) or post-processing the output. Hand-writing the 4 recipe JSONs sidesteps this entirely: you simply don't create the recipe-advancement file, and the recipe is only ever granted by `rewards.recipes` on your 3 custom advancements.

### Recommendation: **hand-write all JSON this phase.**

- ~18 small files, mostly 3–8 lines each; the altar model is hand-modelled either way.
- No second run configuration, no `src/generated/resources` churn, no "provider ran but produced nothing" class of failure while you're also introducing the first `BlockEntity` and the first custom renderer.
- Full control over the advancement chain (the whole point of D-15).
- **Counter-argument (for the planner):** if datagen lands here, `BlockStateProvider`/`ModelProvider` give compile-time-checked links between the registry name and the model path, which kills the "rename → purple cube" failure mode (PITFALLS technical-debt table). That benefit compounds from Phase 3 on (menus, more blocks). A defensible alternative is: **datagen for models/blockstates/loot tables/lang-keys-as-a-check only, hand-write recipes + advancements.** This is more moving parts for Phase 2 but front-loads the Phase 3–5 win.
- **Trigger to adopt full datagen:** the first phase that adds a `MenuType` + a blockstate with real variants (Phase 3), or when the registry-object count clearly passes ~10 (it's ~7 now: 3 items + 2 blocks + 1 BE + 1 tab).

### If datagen IS adopted — wiring (NeoForge 1.21.1)

```java
@EventBusSubscriber(modid = SecondShift.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DataGen {
    @SubscribeEvent
    static void gather(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        PackOutput out = gen.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        ExistingFileHelper efh = event.getExistingFileHelper();
        gen.addProvider(event.includeServer(), new ModRecipeProvider(out, lookup));
        gen.addProvider(event.includeServer(), ModLootTableProvider.create(out, lookup));
        gen.addProvider(event.includeServer(), new ModAdvancementProvider(out, lookup, efh, List.of(new ModAdvancements())));
        gen.addProvider(event.includeClient(), new ModBlockStateProvider(out, efh));
        gen.addProvider(event.includeClient(), new ModItemModelProvider(out, efh));
    }
}
```
Run with `./gradlew runData` (the `data` run config already exists per STACK.md `build.gradle`). Providers: `RecipeProvider`, `LootTableProvider`, `BlockStateProvider`, `ItemModelProvider`, `AdvancementProvider` (NeoForge variant), `LanguageProvider` (skip — hand-write lang). Verify exact provider constructor signatures against the MDK / current NeoForge sources at implementation time — datagen constructor signatures shift between 1.21.x patches.

## Common Pitfalls

### Pitfall 1: Wrong interaction signature (PITFALLS §10 — lands in this phase)
**What goes wrong:** Copying 1.21.2+/1.20.x altar code — `useItemOn` compiles as an unrelated overload and right-click-with-item silently does nothing; or `use(...)` (the 1.20 combined method) doesn't exist.
**Why:** 1.21.1 is the odd one out — two methods, two result types (`ItemInteractionResult useItemOn(...)`, `InteractionResult useWithoutItem(...)`). 1.21.2 unified them.
**How to avoid:** `@Override` on both. Pin the mapping in review: "1.21.1 → `ItemInteractionResult useItemOn`, `InteractionResult useWithoutItem`."
**Warning signs:** right-click-with-item does nothing while empty-hand works (or vice-versa); a method without `@Override`.

### Pitfall 2: Client-class leak in the renderer (PITFALLS §9)
**What goes wrong:** `SoulAltarRenderer` (or `Minecraft.getInstance()`) referenced from common code → `NoClassDefFoundError` on a dedicated server. Invisible in single-player because the integrated server shares the JVM.
**How to avoid:** renderer in `client/render/`; registered only from `EntityRenderersEvent.RegisterRenderers` in a `@EventBusSubscriber(value = Dist.CLIENT, bus = MOD)` class. Common code (the block, the BE) never names a render type.
**Warning signs:** none locally — **run `./gradlew runServer` once this phase** (D-CONTEXT: "one runServer this phase").

### Pitfall 3: BE data not saving / not syncing
**What goes wrong:** Soul Block "socketed" but gone after relog, or the charged render never appears / shows stale.
**Why:** missing `setChanged()` after mutation; missing `getUpdateTag`/`getUpdatePacket`; not calling `super` in `saveAdditional`/`loadAdditional`; forgetting `level.sendBlockUpdated(...)` after the socket so the update packet is actually sent.
**How to avoid:** the four-part checklist — `super` in both save/load; `setChanged()` on every mutation; `getUpdateTag` + `getUpdatePacket` both overridden; `sendBlockUpdated` (or `setChanged` for chunk-load-only) after the socket.
**Warning signs:** works in the same session, breaks on relog (persistence); works on relog, renderer stale (sync).

### Pitfall 4: Unbound register (PITFALLS §1 — recurs every phase that adds a register)
**What goes wrong:** `ModBlocks` / `ModBlockEntities` / `ModCreativeTab` created but not `.register(modBus)`'d in the `SecondShift` constructor, or not added to `ModRegistrySelfCheck`.
**How to avoid:** one visible block of `.register(modBus)` calls (Phase 1 D-08/D-10); add each new register to the self-check `Stream.of(...)`. The self-check will hard-abort with a named list if you miss the `.register` — but only if the register is *in the stream*.
**Warning signs:** items work, blocks don't (or the creative tab is missing); startup does NOT abort (means the register isn't in the self-check stream).

### Pitfall 5: `getOffers()` / villager trade internals (PITFALLS §3) — NOT this phase, but tempting
**What goes wrong:** touching `Villager#getOffers()` in the harvest handler → `IllegalStateException` on the client, or lazily fabricates 2 random trades server-side.
**How to avoid:** the harvest path never reads offers. Kill + drop only. (Full note carried for the planner: keep it out of scope.)

### Pitfall 6: Missing block completeness (PITFALLS "Looks Done But Isn't")
**What goes wrong:** Soul Block / Soul Altar renders as a purple-black cube, or breaks and drops nothing.
**Why:** every custom block needs **blockstate JSON + block model + item model + loot table** — four files, easy to miss one.
**How to avoid:** per-block checklist. For the altar also: `RenderShape.MODEL` (default), `noOcclusion()` if non-full-cube, a `getShape` VoxelShape, and `requiresCorrectToolForDrops()` + the pickaxe tag if it should need a pickaxe.
**Warning signs:** purple cube (model/blockstate); "no drop on break" (loot table missing or `getDrops` override wrong); block is full-cube-shaped despite a slim model (missing `getShape`).

### Pitfall 7: Baby / trader / zombie-villager misclassification (D-09)
**What goes wrong:** wandering trader instakilled, or zombie villager drops a Fragment, or a baby villager is *excluded* when D-09 says include it.
**Why:** `WanderingTrader` and `Villager` both extend `AbstractVillager`; `ZombieVillager` extends `Zombie`.
**How to avoid:** the gate is exactly `target instanceof net.minecraft.world.entity.npc.Villager` — this includes babies (`isBaby()` is irrelevant), excludes `WanderingTrader` (not a `Villager`) and `ZombieVillager` (not a `Villager`). Do **not** use `instanceof AbstractVillager`.
**Warning signs:** trader dies in one hit; zombie villager drops a Fragment.

### Pitfall 8: Advancement JSON shape drift (1.21.1 specifics)
**What goes wrong:** advancement silently never loads (typo in a criterion trigger id, wrong `conditions` shape), or the recipe is never granted.
**Why:** 1.21 tightened several predicate shapes; `item` predicates use `"items": [...]` (a list / tag), recipe result is `{"id": ..., "count": ...}` not `{"item": ...}`.
**How to avoid:** validate against a known-good vanilla advancement from the 1.21.1 jar (`data/minecraft/advancement/recipes/...`). Test by launching a fresh world and checking the toast fires. Keep `"show_toast": true`, `"announce_to_chat": true`.
**Warning signs:** `/advancement grant @s only secondshift:necromantic_apprentice` works but the trigger never fires naturally → criterion conditions are malformed.

## Code Examples

### Recipe: Harvester (hand-written, 1.21.1 shape)
```json
// data/secondshift/recipe/harvester.json
{
  "type": "minecraft:crafting_shaped",
  "category": "equipment",
  "pattern": [" EE", " B ", "B  "],
  "key": {
    "E": { "item": "minecraft:emerald" },
    "B": { "item": "minecraft:bone" }
  },
  "result": { "id": "secondshift:harvester", "count": 1 }
}
```
Note: soul soil per D-11 can be added as a third key. Verify `"result"` uses `"id"` (1.21+) — vanilla `data/minecraft/recipe/*.json` in the 1.21.1 jar is the reference.

### Recipe: 4 Soul Fragment → 1 Soul Block (shapeless, ECON-03)
```json
// data/secondshift/recipe/soul_block.json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    { "item": "secondshift:soul_fragment" },
    { "item": "secondshift:soul_fragment" },
    { "item": "secondshift:soul_fragment" },
    { "item": "secondshift:soul_fragment" }
  ],
  "result": { "id": "secondshift:soul_block", "count": 1 }
}
```
Reverse recipe `soul_fragment_from_block.json`: shapeless, one `secondshift:soul_block` → `{ "id": "secondshift:soul_fragment", "count": 4 }`.

### Advancement: step 1 of the chain (D-15) — gates the Harvester recipe
```json
// data/secondshift/advancement/necromantic_apprentice.json
{
  "criteria": {
    "got_emerald": {
      "trigger": "minecraft:inventory_changed",
      "conditions": { "items": [ { "items": [ "minecraft:emerald" ] } ] }
    }
  },
  "requirements": [ [ "got_emerald" ] ],
  "rewards": { "recipes": [ "secondshift:harvester" ] },
  "display": {
    "icon": { "id": "secondshift:harvester" },
    "title":       { "translate": "advancement.secondshift.necromantic_apprentice.title" },
    "description": { "translate": "advancement.secondshift.necromantic_apprentice.description" },
    "frame": "task",
    "show_toast": true,
    "announce_to_chat": true,
    "hidden": false
  }
}
```
Step 2 `first_harvest.json`: `inventory_changed` on `secondshift:soul_fragment` (D-16 recommended — simplest, and the Fragment's sole source is a harvest), `rewards.recipes: ["secondshift:soul_block"]`, description hints "four make something more". Step 3 `soul_mason.json`: `trigger: "minecraft:recipe_crafted"` with `"recipe_id": "secondshift:soul_block"` (or `inventory_changed` on `secondshift:soul_block`), `rewards.recipes: ["secondshift:soul_altar"]`, description hints "set a job-site block on top". Optionally chain them with `"parent"` for a visible tree; not required for the reward mechanic.

### Creative tab (D-14)
```java
// registry/ModCreativeTab.java
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
`TABS.register(modBus)` in the `SecondShift` ctor **and** add `ModCreativeTab.TABS` to `ModRegistrySelfCheck`.

### Harvester item (plain Item, no sweep)
```java
// content/item/HarvesterItem.java
public class HarvesterItem extends Item {
    public HarvesterItem(Properties props) { super(props); }
    // no attack override needed — a non-SwordItem never triggers the sweep
}

// registry/ModItems.java
public static final DeferredItem<HarvesterItem> HARVESTER = ITEMS.registerItem("harvester",
        HarvesterItem::new,
        new Item.Properties()
                .stacksTo(1)
                .durability(250)
                .attributes(SwordItem.createAttributes(Tiers.STONE, 0, -2.8f))  // ~3 dmg total; tune per D-08
                .enchantable(15));   // verify the exact helper name/param vs decompiled Items.IRON_SWORD
```
`SwordItem.createAttributes` just returns an `ItemAttributeModifiers` — reusing it on a non-sword item is fine. `Tiers.STONE` bonus is 1, so `createAttributes(STONE, 0, ...)` → +1 modifier → ~2 total; use a small positive `damage` arg to reach 3–4. If `.enchantable(int)` isn't on `Item.Properties` in 21.1.248, use `.component(DataComponents.ENCHANTABLE, new Enchantable(15))`.

## Runtime State Inventory

Phase 2 is greenfield content — it removes one throwaway item and adds new content. There is no rename/refactor of persisted data. One deletion has a runtime footprint:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | **None** — no world has been saved with `secondshift` content yet (Phase 1 shipped only `debug_marker`, never placed/persisted). No BE data, no attachments exist. | None |
| Live service config | **None** — not applicable to a Minecraft mod. | None |
| OS-registered state | **None.** | None |
| Secrets/env vars | **None.** | None |
| Build artifacts | `debug_marker` (D-07) is deleted this phase: its registry entry, and any `en_us.json`/model stub. `en_us.json` is currently `{}` so nothing to remove there. No model/lang stub for `debug_marker` was ever created (Phase 1 D-07 said "needs no texture, model, recipe"). The stale `second-shift-0.1.0.jar` in the test instance was already removed in Phase 1 (STACK.md / Phase 1 D-05). | Delete `DEBUG_MARKER` from `ModItems.java`; confirm `ModRegistrySelfCheck` still compiles (it references `ModItems.ITEMS`, not the field). `./gradlew deployToTest` overwrites the jar in the instance (Phase 1 glob-replace). |

**Nothing else found.** If the user has already loaded a dev world with `debug_marker` in an inventory, removing the item will log an "unknown item" warning on load and drop it — harmless, and dev worlds are disposable.

## State of the Art

| Old Approach (pre-1.21 / training-data default) | Current (1.21.1 / NeoForge 21.1.248) | Impact |
|--------------|------------------|--------|
| `LivingHurtEvent` / `LivingAttackEvent` / `LivingDamageEvent` (single) | `LivingIncomingDamageEvent` (pre-mitigation, cancellable) → `LivingDamageEvent.Pre` (post-mitigation, `setNewDamage`) → `LivingDamageEvent.Post` | Pick the right stage: veto = incoming; force lethal = `Pre` |
| `Block#use(...)` single method | `useItemOn` (→ `ItemInteractionResult`) + `useWithoutItem` (→ `InteractionResult`), 1.21.1 only | Two overrides, two result types |
| `SwordItem(Tier, int dmg, float speed, Properties)` | `SwordItem(Tier, Properties)` + `Item.Properties#attributes(SwordItem.createAttributes(...))` | Attributes are a data component now |
| `ItemStack` NBT (`getOrCreateTag`) | Data components; `ItemStack#save(HolderLookup.Provider)` / `parse` | BE ItemStack persistence is codec-driven |
| `saveAdditional(CompoundTag)` / `load(CompoundTag)` | `saveAdditional(CompoundTag, HolderLookup.Provider)` / `loadAdditional(CompoundTag, HolderLookup.Provider)` | Extra `HolderLookup.Provider` param |
| Plural data folders (`recipes/`, `advancements/`, `loot_tables/`) | Singular (`recipe/`, `advancement/`, `loot_table/`) | Plural silently ignored |
| `recipe result { "item": "...", "count": n }` | `{ "id": "...", "count": n }` | Recipe JSON |
| `MenuScreens.register` from `FMLClientSetupEvent` | `RegisterMenuScreensEvent` (mod bus, `Dist.CLIENT`) — *Phase 3, not this phase* | — |
| `GatherDataEvent` with manual `runData` args each time | `data` run config in MDG `build.gradle` (already present) | Datagen is one `@SubscribeEvent` away if adopted |

**Deprecated/outdated — do not use (CLAUDE.md "What NOT to Use"):** `net.minecraftforge.*`, `@Mod.EventBusSubscriber`, `DistExecutor`, `SimpleChannel`, `Item#getAttributeModifiers` override, `Codec` in `RecipeSerializer#codec()` (it's `MapCodec`), custom `RecipeType` "for the ritual", `[[mixins]]`/`accesstransformer.cfg` "just in case", bumping `neo_version` past `21.1.248`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `DamageSource#getWeaponItem()` exists on 1.21.1 `DamageSource` and returns the weapon used | Pattern 1, Standard Stack | LOW — fallback (`source.getEntity()` held-item check) works for a single instant melee hit; only affects code shape |
| A2 | `LivingDamageEvent.Pre` in NeoForge 21.1.248 exposes `getNewDamage()`/`setNewDamage(float)` (post-mitigation) | Pattern 1 | MEDIUM — if the method names differ, the instakill needs a different call; the *approach* (post-mitigation override) still holds. Verify against the jar. |
| A3 | `Item.Properties#enchantable(int)` exists in 21.1.248 (vs needing `.component(DataComponents.ENCHANTABLE, new Enchantable(n))`) | Harvester item, Standard Stack | LOW — one of the two forms works; check `net.minecraft.world.item.Items` source for `IRON_SWORD` |
| A4 | `Block#playerWillDestroy(Level, BlockPos, BlockState, Player)` is the 1.21.1 signature and runs server-side before BE removal | Pattern 4 | MEDIUM — 1.21.x tweaked the return type; wrong signature = no `@Override` = consequences never fire. Compiler-caught if `@Override` is used. |
| A5 | `LootContextParams.BLOCK_ENTITY` is the constant name for reading the BE in `getDrops` | Pattern 4, Standard Stack | LOW — verify against `LootContextParams`; alternative is handling drop suppression in `onRemove`/`playerWillDestroy` with `Block.dropResources` skipped |
| A6 | `LightningBolt#setVisualOnly(boolean)` produces flash+thunder with no fire/damage | Pattern 4 | LOW — well-known vanilla method (`/summon lightning_bolt` with the flag); verify exact name |
| A7 | Recipe JSON `"result": { "id": ..., "count": ... }` and `item` predicate `"items": [...]` are the 1.21.1 shapes | Code Examples | MEDIUM — validate against `data/minecraft/` in the 1.21.1 client jar before writing 4 files with the wrong shape |
| A8 | Omitting the auto recipe-advancement means the recipe is only granted via `rewards.recipes` (i.e. recipes are NOT known-by-default without an unlocking advancement) | Datagen Decision, D-15 | MEDIUM — if 1.21.1 auto-unlocks recipes that have no gating advancement, D-15's gating fails and needs `recipe book` / `/recipe` handling. Test: fresh creative world, confirm `secondshift:harvester` is NOT in the recipe book before the advancement. |
| A9 | `Block#animateTick` with `RandomSource` is the 1.21.1 ambient-particle hook and runs client-side | Standard Stack, Soul Block | LOW — verify signature; alternative is a client BE ticker |
| A10 | `BuildCreativeModeTabContentsEvent` / `displayItems` populate reliably for a mod-owned tab in 21.1.248 | Creative tab | LOW — standard; `displayItems` alone is sufficient, the event is optional |

## Open Questions (RESOLVED)

> All five resolved during planning of `02-01`…`02-05`. Disposition noted under each.

1. **Charged-altar "drops nothing including the block itself" (D-04 ⚠ planner flag).**
   - What we know: D-04 locks this reading; ALTAR-07 only guarantees the *empty* self-drop; ALTAR-06 (the bound-employee half) is Phase 6.
   - What's unclear: whether losing the altar block entirely will feel like a bug in play (vs. "Soul Block destroyed, altar drops").
   - Recommendation: implement D-04 as written (charged → no drops at all), but structure `getDrops` so flipping to "altar still drops" is a one-line change. Surface it to the user during discuss/verify.
   - RESOLVED: implemented as written in `02-04` Task 2 — `getDrops` returns `List.of()` when the BE was charged; the `getDrops` branch is structured for a one-line fallback flip, its exact location recorded in `02-04-SUMMARY`, and it is surfaced to the user via `02-04` `<verify_time_flags>`.

2. **Exact instakill hook + Resistance-V edge case.**
   - What we know: `LivingDamageEvent.Pre.setNewDamage(health + absorption + 1)` is post-mitigation and should be unconditionally lethal.
   - What's unclear: whether any vanilla/modded effect re-clamps damage *after* `LivingDamageEvent.Pre` (e.g. a `LivingDamageEvent.Post` mod, or Totem of Undying — which would trigger and save the villager... villagers can't hold totems, so N/A).
   - Recommendation: spike it in the first plan task — bind Resistance V + absorption to a test villager via command, confirm one Harvester hit kills. Fallback: also `target.setHealth(0)` + `target.die(source)` in the same handler.
   - RESOLVED: `02-02` Task 1 uses the game-bus `LivingDamageEvent.Pre` post-mitigation override; the Resistance-V + absorption spike runs inside the RED→GREEN loop in `02-02` Task 1, with the `setHealth(0)` + `die(source)` fallback wired if the GameTest shows survival. Covered by GameTest `harvester_kill_resistance_and_absorption_villager_still_one_shot` (authored RED in `02-01` Task 3).

3. **Should the descriptionId/lang-key self-check start now?** (Phase 1 Deferred Idea, carried.)
   - What we know: Phase 2 is the first phase with real translated content; the check would assert every registered object's `descriptionId` resolves in `en_us.json`.
   - Recommendation: **planner's call.** Cheap to add (~15 lines in `ModRegistrySelfCheck` or a new `FMLLoadCompleteEvent` handler) and it directly prevents the PITFALLS "untranslated key in UI" failure. Lean yes — but it's additive scope; defer to Phase 10 if the plan is already large.
   - RESOLVED: **YES** — added this phase in `02-01` Task 2 as a second `@SubscribeEvent` handler on `FMLLoadCompleteEvent` in `ModRegistrySelfCheck`, with a documented `Dist.CLIENT` gate fallback if `Language.getInstance().has(...)` is not populated on the dedicated server. Verified clean on both `runClient` and `runServer` in `02-01` Task 2.

4. **Harvester enchantability vs Looting.** D-07 says Looting is a no-op (drop fixed at 1). The drop handler ignores `Enchantments.LOOTING` entirely (it `.clear()`s and adds exactly 1) — so no code needed, but worth a one-line comment so a future reader doesn't "fix" it.
   - RESOLVED: no code — `02-02` Task 1 step 4 adds the one-line comment on the `LivingDropsEvent` handler ("Looting is intentionally ignored (D-07) — always exactly 1"); the drop is fixed at 1 by construction.

5. **Altar facing.** D-02 doesn't say the pedestal has a facing direction. A `HorizontalDirectionalBlock` (facing property) adds a blockstate variant (4 rotations) and nudges toward datagen. Recommend **no facing** for MVP (symmetric pedestal model) unless the soul-fire accents need orientation.
   - RESOLVED: **no facing property** — single-variant blockstates (`{"variants": {"": {...}}}`) for both the Soul Block (`02-01`/`02-03`) and the Soul Altar (`02-01`/`02-04`); symmetric pedestal model.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Temurin JDK 21 | Gradle toolchain / compile | ✓ | 21.0.12 (verified STACK.md) | `foojay-resolver-convention` auto-provisions |
| Gradle wrapper | all `./gradlew` tasks | ✓ | 9.2.1 (Phase 1) | — |
| NeoForge 21.1.248 + MC 1.21.1 | compile + run | ✓ | resolved Phase 1 | — |
| `./gradlew runClient` | in-dev verification (models, tab, recipes, altar, harvest) | ✓ | Phase 1 green | — |
| `./gradlew runServer` | client-class-leak check (renderer) | ✓ | Phase 1 green | — |
| `./gradlew deployToTest` | user in-game test in CurseForge "test" instance | ✓ | Phase 1 task | fails loud if game holds a file lock (Phase 1 D-04) |
| `./gradlew runData` | **only if datagen adopted (D-17)** | ✓ (`data` run config in `build.gradle`) | untested in this project | hand-write JSON (the recommendation anyway) |
| BlockBench (or hand-authored JSON) | the non-full-cube altar model geometry | n/a (external, offline) | — | hand-write the `elements` array; small pedestal is ~4–6 boxes |
| Textures (16×16 PNGs) | POL-03 | author-supplied | — | placeholder solid-colour PNGs are acceptable (POL-03: "placeholder quality acceptable; missing is not") |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** `runData` only matters if datagen is adopted; the recommendation is hand-written JSON, so nothing blocks.

## Validation Architecture

`workflow.nyquist_validation` is `true` in `.planning/config.json` — this section applies.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | **NeoForge GameTest** (bundled; `neoforge.enabledGameTestNamespaces=secondshift` already set on the `client` and `server` run configs in `build.gradle`). No JUnit in the project. |
| Config file | none — GameTests are `@GameTest`-annotated methods discovered by namespace; the standalone `gameTestServer` run was **removed** in Phase 1 (STACK.md: "crashes on startup when no gametests are registered") — re-add it *only* if GameTests are written |
| Quick run command | `./gradlew runClient` (manual, primary loop) — models/tab/recipes/altar visible in seconds |
| Full suite command | `./gradlew build` (compile + resource validation) then `./gradlew deployToTest` + a real launch in the CurseForge instance (integration gate) |
| Optional automated | `./gradlew runServer` with `@GameTest` methods in the `secondshift` namespace (server-side, deterministic) — see Wave 0 |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Command / Method | Exists? |
|--------|----------|-----------|-----------------|---------|
| ECON-01 | Harvester is craftable | manual (JEI) + build | craft at a table in `runClient`; recipe visible in JEI/EMI if installed | ❌ manual |
| ECON-01 | Harvester deals ~3–4 dmg to non-villagers, no sweep | manual | hit a zombie + a line of 3 pigs in `runClient`; only the struck pig takes damage; ~2 hits to kill a pig | ❌ manual |
| ECON-02 | Any villager + Harvester → exactly 1 Soul Fragment, guaranteed | **GameTest** (best candidate) | `@GameTest`: spawn a `Villager` (+ variants: baby, Resistance V), a fake player or direct `LivingDamageEvent` path, assert 1 `secondshift:soul_fragment` `ItemEntity` and nothing else | ❌ Wave 0 |
| ECON-02 | Wandering trader / zombie villager + Harvester → normal damage, no Fragment | **GameTest** | `@GameTest`: `WanderingTrader` + `ZombieVillager` survive one hit, no Fragment | ❌ Wave 0 |
| ECON-02 | Non-Harvester villager kill → vanilla loot unchanged, no Fragment | **GameTest** / manual | kill a villager with a sword → no Fragment | ❌ Wave 0 |
| ECON-03 | 4 Fragment → 1 Soul Block (shapeless) + reverse | manual (JEI) | craft both directions in `runClient` | ❌ manual |
| ALTAR-01 | Altar is a craftable block backed by a BE | manual | place altar; `/data get block <pos>` shows the BE; craft recipe works | ❌ manual |
| ALTAR-01 | Socket persists across relog | manual | socket a Soul Block, save-quit-reload, BE still holds it, renderer still charged | ❌ manual |
| ALTAR-07 | Empty altar breaks → drops itself | manual + build (loot table present) | break an empty altar in `runClient` → altar item drops | ❌ manual |
| ALTAR-07 (D-04) | Charged altar breaks → no drops, ½-heart to breaker, cosmetic lightning | manual | break a charged altar → no items, player loses 1 hp, lightning flash + thunder, no fire | ❌ manual |
| POL-01 | One creative tab with every mod item + block | manual | open creative menu in `runClient`, "Second Shift" tab has 4 entries | ❌ manual |
| POL-03 | Every item/block has model + texture (no missing) | manual + **launch in non-EN locale** | no purple cubes; no `item.secondshift.*` raw keys | ❌ manual |
| POL-04 | Recipes are vanilla types (JEI auto) + emit recipe-unlock toast | manual | pick up an emerald in a fresh survival world → "Necromantic Apprentice" toast + "recipe unlocked" toast; Harvester now in recipe book; it was NOT before | ❌ manual |
| POL-04 (D-15) | Advancement chain: recipe hidden until its gating advancement | manual | fresh world: `secondshift:harvester` absent from recipe book until emerald obtained; `soul_block` absent until first Fragment; `soul_altar` absent until a Soul Block crafted | ❌ manual |
| — | No client-class leak | automated | `./gradlew runServer` reaches "Done" with no `NoClassDefFoundError` | ✅ (Phase 1 harness) |
| — | No unbound register | automated | startup self-check (`ModRegistrySelfCheck`) — extend `Stream.of(...)`; deliberately un-`.register` one to confirm it still aborts | ✅ (Phase 1 harness, needs the new registers added) |

### Sampling Rate

- **Per task commit:** `./gradlew build` (compile + JSON/resource load validation).
- **Per wave merge:** `./gradlew runClient` smoke (tab + one recipe + place/break altar) **and** `./gradlew runServer` (client-leak gate).
- **Phase gate:** full manual checklist above via `./gradlew deployToTest` + a real launch in the CurseForge "test" instance, **plus** a launch with the game language set to a non-English locale (POL-03 raw-key check), before `/gsd:verify-work`.

### Wave 0 Gaps

- [ ] `src/test`-equivalent: a `secondshift` GameTest class (e.g. `content/HarvesterGameTests.java`) with `@GameTest` methods for ECON-02 (villager → 1 Fragment; baby villager; Resistance-V villager; wandering trader unaffected; zombie villager unaffected; sword kill → no Fragment). Register via `@GameTestHolder(SecondShift.MODID)` / `RegisterGameTestsEvent`.
- [ ] Re-add the `gameTestServer` run to `build.gradle` **only if** GameTests are written (STACK.md removed it because an empty gametest set crashes it) — or run them via `./gradlew runServer` with the namespace enabled.
- [ ] Extend `ModRegistrySelfCheck.Stream.of(...)` with `ModBlocks.BLOCKS`, `ModBlockEntities.BLOCK_ENTITIES`, `ModCreativeTab.TABS` (this is a code task, but it's also test infrastructure — the guardrail).
- [ ] Decide (planner): is the descriptionId/lang-key resolution self-check in scope this phase? If yes, it belongs in Wave 0 as a guardrail alongside the register self-check.

*If the planner judges GameTests as over-investment for a solo personal mod at this stage: the fallback is the manual checklist above run via `deployToTest`, which is the established Phase 1 loop. Recommendation: write the ECON-02 GameTests — the instakill is the one genuinely custom mechanic and its edge cases (baby / trader / zombie villager / Resistance) are exactly what a deterministic test protects.*

## Security Domain

`security_enforcement: true`, `security_asvs_level: 1`, `security_block_on: high`. This phase has a **very small** security surface: no networking (payloads are Phase 3), no menu, no persisted user-supplied strings, single-player target.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | n/a (single-player mod) |
| V3 Session Management | no | n/a |
| V4 Access Control | no | n/a — no menu, no ownership model this phase |
| V5 Input Validation | **partially** | The only "input" is a block interaction. `useItemOn` runs on both logical sides — mutate the BE **only** inside `if (!level.isClientSide)`. Validate `stack.is(SOUL_BLOCK_ITEM)` and `be.isEmpty()` before consuming (prevents item loss / dupe on a malformed interaction). No index/count comes from the client. |
| V6 Cryptography | no | n/a |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Client-side BE mutation → desync / ghost item | Tampering | All BE writes gated on `!level.isClientSide`; `setChanged()` + `sendBlockUpdated` so the client mirrors server truth |
| Item duplication via break-while-socketing / interaction race | Tampering | Socket is a single server-side transaction: read BE → set stack → `stack.consume` → `setChanged`. Break path clears the BE stack before `super.playerWillDestroy`. No window where the stack exists in both hand and BE. |
| Drop-then-re-harvest dupe (Soul Fragment) | Tampering | `LivingDropsEvent` fires once per death; `event.getDrops().clear()` then add exactly one — no path to two |
| Client-class leak crashing a dedicated server | Denial of Service | `client/` package rule + `./gradlew runServer` gate (PITFALLS §9) |
| Malformed recipe/advancement JSON silently disabling content | (not security) | validate against vanilla 1.21.1 reference JSON; fresh-world toast test |

**No `security_block_on: high` items identified for this phase.** The real client↔server trust boundary (untrusted menu payloads, `stillValid`, index validation — PITFALLS §12) arrives in **Phase 3** and must be researched then.

## Sources

### Primary (HIGH confidence)
- `.planning/research/STACK.md` — binary-verified (`javap` against `neoforge-21.1.248-{client,universal}.jar` on this machine) API signatures: `DeferredRegister` helpers (§1), block-entity API (§6), recipe guidance (§7), Harvester/`SwordItem.createAttributes` (§11), datagen note (§12).
- `.planning/research/PITFALLS.md` — §10 (`ItemInteractionResult` vs `InteractionResult`, 1.21.1-specific, decompiled `BlockBehaviour`), §1 (unbound register + self-check), §9 (client-class leak), §2 (`@EventBusSubscriber` bus is derived), "Looks Done But Isn't" checklist, Pitfall-to-Phase mapping (recipes: no smithing filler; assets/lang/loot re-checked every phase).
- `.planning/research/ARCHITECTURE.md` — Build Order Slices 1–3 (= this phase), "altar persists exactly one thing", flat `registry/` vs `content/` structure, anti-patterns (no client import from common, no BE-persisted employee state, no custom `RecipeType`).
- `.planning/research/FEATURES.md` — economy-items table, table-stakes (creative tab, complete lang, models, JEI-visible recipes, recipe advancements), Malum scythe precedent, "the ritual is not a recipe".
- `CLAUDE.md` — stack pins, "What NOT to Use", API Surface §1/§6/§7/§10/§11, build/run commands, GSD workflow enforcement.
- `docs.neoforged.net/docs/1.21.1/blockentities/` — `EntityBlock`, `newBlockEntity`, `saveAdditional`/`loadAdditional`, `getUpdateTag`/`handleUpdateTag`, `getUpdatePacket`/`onDataPacket`, `Level#sendBlockUpdated`, ticking via `EntityBlock#getTicker`.
- `docs.neoforged.net/docs/1.21.1/items/tools/` — `SwordItem(Tier, Properties)`, `SwordItem.createAttributes(Tier, int, float)`, `Item.Properties#attributes`, attack-speed math (`-2.4f` → 1.6 for a sword).
- `docs.neoforged.net/docs/1.21.1/items/#creative-tabs` — `DeferredRegister.create(Registries.CREATIVE_MODE_TAB)`, `CreativeModeTab.builder().title/.icon/.displayItems`, `BuildCreativeModeTabContentsEvent` (client-only).
- `docs.neoforged.net/docs/1.21.1/resources/server/advancements/` — advancement JSON: criteria triggers, `rewards.recipes`, `display` (`show_toast`, `announce_to_chat`, `frame`), parent chains, `AdvancementProvider` (NeoForge) for datagen.
- `docs.neoforged.net/docs/1.21.1/resources/#data-generation` — `GatherDataEvent`, `event.getGenerator().addProvider`, `PackOutput`, `ExistingFileHelper`, `CompletableFuture<HolderLookup.Provider>`, provider list (`RecipeProvider`, `LootTableProvider`, `BlockStateProvider`, `ItemModelProvider`, `AdvancementProvider`, `LanguageProvider`), `data` run config.
- `minecraft.wiki/w/Sweeping_Edge` — sweep attack is **innate to swords** (`SwordItem`); requires on-ground + not sprinting + attack cooldown ≥ 84.8%; Sweeping Edge only scales the AoE damage, doesn't enable it. ⇒ a non-`SwordItem` never sweeps.

### Secondary (MEDIUM confidence — verify against the jar at implementation time)
- `docs.blamejared.com/1.21/en/neoforge/api/event/entity/living/LivingDamageEvent/` and `.../LivingIncomingDamageEvent/` — NeoForge 1.21 damage pipeline: `LivingIncomingDamageEvent` (pre-mitigation, `ICancellableEvent`, `getAmount`/`setAmount`) and `LivingDamageEvent.Pre` (post-mitigation); `LivingDropsEvent` fires on death, `getDrops()` is `Collection<ItemEntity>`. Exact method names on `LivingDamageEvent.Pre` (`getNewDamage`/`setNewDamage`) to be confirmed against `neoforge-21.1.248`.
- Existing project source (`src/main/java/com/cxmxrgo/secondshift/*`) — the Phase 1 skeleton: `SecondShift` constructor is the single wiring point; `ModRegistrySelfCheck` `Stream.of(...)` at line ~43; `client/ClientModBusEvents` is the `Dist.CLIENT` mod-bus subscriber to extend with `EntityRenderersEvent.RegisterRenderers`; `ModItems` holds only `DEBUG_MARKER` (delete).

### Tertiary (LOW confidence — flagged in Assumptions Log, must verify)
- Training knowledge for: `DamageSource#getWeaponItem()` (A1), `Item.Properties#enchantable(int)` vs `DataComponents.ENCHANTABLE` (A3), `Block#playerWillDestroy` 1.21.1 signature/return (A4), `LootContextParams.BLOCK_ENTITY` name (A5), `LightningBolt#setVisualOnly` (A6), 1.21.1 recipe/predicate JSON shapes (A7), recipes-not-known-without-a-gating-advancement (A8), `Block#animateTick(…, RandomSource)` (A9). Verify each against the decompiled 1.21.1 sources available in the dev workspace (Parchment-mapped) or vanilla `data/minecraft/` JSON in the client jar.

## Metadata

**Confidence breakdown:**
- Standard stack / registration / block-entity / creative-tab / recipes: **HIGH** — NeoForge 1.21.1 docs + binary-verified STACK.md; these are unchanged, well-trodden APIs.
- Advancement chain + recipe gating: **MEDIUM-HIGH** — mechanism (`rewards.recipes`, omit auto-advancement) is well-established; the exact 1.21.1 predicate/recipe JSON shapes need a quick check against vanilla reference JSON (A7, A8).
- Villager instakill hook: **MEDIUM** — approach (post-mitigation `LivingDamageEvent.Pre`) is sound; exact NeoForge 21.1.248 method names and the Resistance-V edge case need a spike (Open Question 2).
- `Item.Properties` enchantable/attribute helpers, `playerWillDestroy` signature, misc vanilla method names: **MEDIUM-LOW** — training-data-dated; all compiler-caught with `@Override` or quick to verify (Assumptions Log).
- Datagen recommendation: **MEDIUM** — a judgement call about solo-project iteration speed, not a technical constraint; the advancement-gating friction is a real, specific reason to defer.

**Research date:** 2026-09-04
**Valid until:** ~2026-10-04 for the NeoForge/MC API surface (1.21.1 is frozen; NeoForge 21.1.x only gets bugfixes). The datagen recommendation should be revisited at Phase 3 planning regardless.
