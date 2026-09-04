---
phase: 02-economy-items-soul-altar-block
plan: 01
subsystem: infra
tags: [neoforge, deferredregister, blockentity, creativetab, gametest, lang, java21]

requires:
  - phase: 01-skeleton-feedback-loop
    provides: "@Mod entrypoint with one-visible-block DeferredRegister wiring; FMLLoadCompleteEvent unbound-registry self-check; scripts/run-until.sh launch harness; singular 1.21 datapack folders"
provides:
  - registry/ModBlocks (DeferredRegister.Blocks) — SOUL_BLOCK (light 7), SOUL_ALTAR (noOcclusion, strength 3.5, requiresCorrectToolForDrops)
  - registry/ModBlockEntities — SOUL_ALTAR_BE from BlockEntityType.Builder.of(SoulAltarBlockEntity::new, SOUL_ALTAR)
  - registry/ModCreativeTab — one MAIN tab (secondshift:main), all 4 mod objects via displayItems
  - registry/ModItems — HARVESTER / SOUL_FRAGMENT / SOUL_BLOCK_ITEM (DEBUG_MARKER deleted)
  - content/item/HarvesterItem (plain Item marker, getEnchantmentValue override) — behaviour contract for 02-02
  - content/block/SoulBlock (animateTick soul-wisp particles)
  - content/block/SoulAltarBlock (EntityBlock skeleton — newBlockEntity + getShape; useItemOn/playerWillDestroy/getDrops contract documented for 02-04)
  - content/blockentity/SoulAltarBlockEntity — one-slot ItemStack persistence + getUpdateTag/getUpdatePacket client sync; isEmpty()/getHeldSoulBlock()/setHeldSoulBlock()
  - ModRegistrySelfCheck extended — Stream.of covers all 4 registers; new descriptionId → en_us.json resolution check (reads the jar resource directly; server-safe, no Dist.CLIENT gate)
  - Complete assets/secondshift/lang/en_us.json (11 keys)
  - gametest/HarvesterGameTests — 6 ECON-02 @GameTest methods (3 RED, 3 GREEN) in the secondshift namespace
  - data/secondshift/structure/empty.nbt — shared 9x5x9 test structure
  - build.gradle — gameTestServer run re-added (./gradlew runGameTestServer)
affects: [02-02-harvester-instakill, 02-03-soul-block-assets, 02-04-soul-altar-interaction, 02-05-altar-renderer]

tech-stack:
  added: []
  patterns:
    - "content/ sub-packages (item/, block/, blockentity/, gametest/) — behaviour classes never in registry/"
    - "Block-entity persistence: super first in save/loadAdditional; codec-driven ItemStack#save/parse; getUpdateTag delegates to saveAdditional; getUpdatePacket → ClientboundBlockEntityDataPacket.create"
    - "Startup lang-key guardrail reads the mod's bundled en_us.json off the classpath (not net.minecraft.locale.Language) so it runs identically on client and dedicated server"
    - "GameTests share one hand-built empty.nbt structure via @GameTest(template = \"empty\") + @PrefixGameTestTemplate(false)"

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/content/item/HarvesterItem.java
    - src/main/java/com/cxmxrgo/secondshift/content/block/SoulBlock.java
    - src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java
    - src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java
    - src/main/java/com/cxmxrgo/secondshift/registry/ModBlocks.java
    - src/main/java/com/cxmxrgo/secondshift/registry/ModBlockEntities.java
    - src/main/java/com/cxmxrgo/secondshift/registry/ModCreativeTab.java
    - src/main/java/com/cxmxrgo/secondshift/gametest/HarvesterGameTests.java
    - src/main/resources/data/secondshift/structure/empty.nbt
  modified:
    - src/main/java/com/cxmxrgo/secondshift/registry/ModItems.java
    - src/main/java/com/cxmxrgo/secondshift/SecondShift.java
    - src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java
    - src/main/resources/assets/secondshift/lang/en_us.json
    - build.gradle

key-decisions:
  - "descriptionId/lang-key self-check reads the mod jar's own en_us.json directly (Gson) rather than net.minecraft.locale.Language — deterministic and server-safe, so NOT Dist.CLIENT-gated"
  - "ModBlockEntities created in Task 1 (not Task 2 as planned) — SoulAltarBlockEntity's constructor references SOUL_ALTAR_BE and Task 1 would not compile otherwise"
  - "HarvesterItem overrides getEnchantmentValue(ItemStack)=15 — 1.21.1 has no Item.Properties#enchantable(int) and no DataComponents.ENCHANTABLE (both 1.21.2+); a durability item is already table-enchantable, the override only raises quality (D-07)"
  - "Harvester attack attribute: SwordItem.createAttributes(Tiers.STONE, 2, -2.8f) → 3 on the item / ~4 with player base (D-08 'modest ~3-4')"
  - "Soul Altar: no facing property, single blockstate variant (RESEARCH Open Question 5)"

patterns-established:
  - "BE client-sync quartet: getUpdateTag + getUpdatePacket both overridden; super called in save/loadAdditional; DataVersion int recorded"
  - "Guardrail resource-scan pattern: read a bundled JSON off the classpath at FMLLoadCompleteEvent and hard-throw with a named list of misses"

requirements-completed: [ECON-02, ALTAR-01, POL-01, POL-03]

duration: 41min
completed: 2026-09-04
---

# Phase 2 Plan 01: Economy Items & Soul Altar Wave-0 Foundation Summary

**Every Phase-2 DeferredRegister (items, blocks, block-entity type, creative tab) now stands up and is guarded — the four content classes exist as behaviour-ready skeletons, the startup guardrail also enforces lang-key resolution, and the ECON-02 instakill mechanic has a deterministic 3-RED / 3-GREEN GameTest baseline.**

## Performance

- **Duration:** ~41 min
- **Started:** 2026-09-04T09:43:00Z
- **Completed:** 2026-09-04T10:24:00Z
- **Tasks:** 3 completed
- **Files created/modified:** 14

## Accomplishments

- 4 `DeferredRegister`s attached in the one visible `SecondShift` constructor block and all covered by `ModRegistrySelfCheck.Stream.of(...)`; `DEBUG_MARKER` deleted.
- New descriptionId → `en_us.json` resolution self-check; verified it hard-aborts on a deleted key and that a detached register still hard-aborts with a named list.
- `SoulAltarBlockEntity` carries full one-slot persistence + client sync now so 02-04/02-05 can build straight on it.
- `runClient` and `runServer` both boot clean with every mod object bound and every lang key resolving.
- ECON-02 GameTest suite discovered under the `secondshift` namespace via `./gradlew runGameTestServer`; RED for the 3 instakill cases, GREEN for the 3 exclusions.

## Task Commits

1. **Task 1: Content skeletons + item/block/BE registry holders** — `2d6fca4` (feat)
2. **Task 2: Creative tab, one-block wiring, lang-key guardrail, complete lang** — `18fbe92` (feat)
3. **Task 3: ECON-02 GameTest scaffold (RED) + gameTestServer run** — `c7d95c9` (test)

## RESEARCH Assumptions Log — resolved values

| # | Claim | Resolution (verified against decompiled `neoforge-21.1.248-sources.jar`) |
|---|-------|--------------------------------------------------------------------------|
| A3 | `Item.Properties#enchantable(int)` / `DataComponents.ENCHANTABLE` | **Neither exists in 1.21.1** (both 1.21.2+). `Item#isEnchantable` is already true for a `stacksTo(1)` + `MAX_DAMAGE` item, so the Harvester is table-enchantable with `.durability(250)` alone; `HarvesterItem` overrides `getEnchantmentValue(ItemStack)=15` (NeoForge stack-sensitive overload; the no-arg vanilla one is `@Deprecated`) to raise enchant quality to iron-tier. |
| A4 | `Block#playerWillDestroy` 1.21.1 signature | Not implemented this plan (02-04). Documented in `SoulAltarBlock` Javadoc as `public BlockState playerWillDestroy(Level, BlockPos, BlockState, Player)`. |
| A5 | `ItemStack` save/parse helper names | `ItemStack#save(HolderLookup.Provider)` → `Tag` (line 419); `ItemStack.parse(HolderLookup.Provider, Tag)` → `Optional<ItemStack>` (line 294). Used verbatim in `SoulAltarBlockEntity`. |
| A9 | `Block#animateTick` signature | `public void animateTick(BlockState, Level, BlockPos, RandomSource)` (`Block.java:277`) — used in `SoulBlock`. |
| — | `lightLevel` vs `lightEmission` | The builder method is `BlockBehaviour.Properties#lightLevel(ToIntFunction<BlockState>)` (`BlockBehaviour.java:1190`); it stores into the private `lightEmission` field. Used as `.lightLevel(state -> 7)`. |

## Lang-key self-check: server behaviour

**Runs on the dedicated server, NOT `Dist.CLIENT`-gated.** `net.minecraft.locale.Language.getInstance()` is not populated with mod translations at `FMLLoadCompleteEvent` on the dedicated server (NeoForge's `LanguageHook.loadModLanguages` needs a live `MinecraftServer`, which is created after mod loading). Instead the check reads the mod's bundled `/assets/secondshift/lang/en_us.json` directly off the classpath with Gson and asserts every registered item/block `descriptionId` + the 7 tab/advancement keys are present. `runServer` reached `Done (…)! For help, type "help"` with the check active and no `Unresolved lang keys` / `NoClassDefFoundError`.

## Deliberate-abort tests (performed this task, restored after)

| Test | Observed abort message |
|------|------------------------|
| Delete `block.secondshift.soul_altar` from `en_us.json`, `runClient` | `java.lang.IllegalStateException: Unresolved lang keys: [block.secondshift.soul_altar]` — startup aborted (ModLoadingException); restored. |
| Comment out `ModBlockEntities.BLOCK_ENTITIES.register(modBus)`, `runClient` | `java.lang.IllegalStateException: Unbound registry entries: [secondshift:soul_altar]` — startup aborted; restored. |
| Comment out `ModBlocks.BLOCKS.register(modBus)`, `runClient` | Aborts **earlier and differently**: `NullPointerException: Trying to access unbound value: ResourceKey[minecraft:block / secondshift:soul_block]` thrown from `ModBlockEntities.lambda$static$0` during `RegisterEvent` (the BE-type builder needs concrete `Block` instances, so `SOUL_ALTAR.get()` fails before `FMLLoadCompleteEvent`). Still a hard launch abort with a crash report, just not the clean self-check message. Documented limitation — detaching a *leaf* register (items / BE types / tab) produces the clean named-list abort. |

## runGameTestServer RED output

```
6 GAME TESTS COMPLETE IN 611.5 ms
3 required tests failed :(
   - harvester_kill_villager_drops_one_fragment        (one Harvester hit must kill the villager (D-08))
   - harvester_kill_baby_villager_drops_one_fragment   (one Harvester hit must kill the villager (D-08))
   - harvester_kill_resistance_and_absorption_villager_still_one_shot  (one Harvester hit must kill the villager (D-08))
> Task :runGameTestServer FAILED   (gradle exit 1)
```

The 3 exclusion tests (`harvester_hit_wandering_trader_no_fragment`, `harvester_hit_zombie_villager_no_fragment`, `sword_kill_villager_no_fragment`) **pass now** and must stay GREEN after 02-02. Note: the plan's Task-3 verify regex (`[0-9]+ (required )?game ?tests? (failed|did not pass)`) does not literally match the 1.21.1 output string "3 required tests failed" (no "game" token) — the unambiguous RED signals are `> Task :runGameTestServer FAILED`, gradle exit 1, and the `N required tests failed :(` line.

## Deviations from Plan

### Auto-fixed / auto-adjusted

**1. [Rule 3 - Blocking] `ModBlockEntities.java` created in Task 1 instead of Task 2**
- **Found during:** Task 1
- **Issue:** `SoulAltarBlockEntity`'s constructor calls `super(ModBlockEntities.SOUL_ALTAR_BE.get(), …)`. The plan listed `ModBlockEntities` only under Task 2, so `./gradlew compileJava` (Task 1's verify) could not pass.
- **Fix:** Created the complete `ModBlockEntities` holder in Task 1. Task 2 then only added the `.register(modBus)` wiring and the `ModRegistrySelfCheck` coverage (as planned).
- **Files:** `src/main/java/com/cxmxrgo/secondshift/registry/ModBlockEntities.java`
- **Commit:** `2d6fca4`

**2. [Rule 3 - Blocking / A3 resolution] `HarvesterItem` has one method override**
- **Found during:** Task 1
- **Issue:** The plan's Properties recipe (`.enchantable(15)` with `.component(DataComponents.ENCHANTABLE, …)` fallback) uses APIs that do not exist in NeoForge 21.1.248 / MC 1.21.1.
- **Fix:** Rely on `Item#isEnchantable` (already true for a durability item) for table access; override `getEnchantmentValue(ItemStack)` to return 15 for iron-tier enchant quality (D-07 "Unbreaking/Mending meaningful"). This is not an attack/sweep override — a non-`SwordItem` still never sweeps.
- **Files:** `src/main/java/com/cxmxrgo/secondshift/content/item/HarvesterItem.java`
- **Commit:** `2d6fca4`
- **Note:** the plan's Task-1 acceptance line "zero overrides" was written against the (incorrect) assumption that `.enchantable(15)` existed. `! grep "extends SwordItem"` still passes.

**3. [Rule 2 - Missing infra] Hand-built `empty.nbt` test structure**
- **Found during:** Task 3
- **Issue:** NeoForge 21.1.248 has no `@EmptyTemplate` (1.21.4+); `@GameTest` methods require a real structure template and `StructureTemplateManager` only loads `.nbt` (not `.snbt`) from datapacks.
- **Fix:** Generated a minimal gzipped structure NBT (9×5×9, stone floor, `DataVersion 3955`) at `data/secondshift/structure/empty.nbt`; all 6 tests use `@GameTest(template = "empty")`.
- **Commit:** `c7d95c9`

**4. [test authoring] GameTest damage driver**
- The tests drive damage via `entity.hurt(damageSources().playerAttack(mockPlayer), amount)` rather than `mockPlayer.attack(entity)` — a never-ticked mock player has no equipped-item attribute modifiers, so `attack()` deals only base damage and the sword-kill / trader-damage assertions were unreliable. The direct `hurt` path also exactly matches what the 02-02 handler keys on (`DamageSource#getWeaponItem()`).

## Known Stubs

- `content/block/SoulAltarBlock` — intentionally a skeleton (no `useItemOn` / `playerWillDestroy` / `getDrops`). The 1.21.1 signatures + behaviour contract are in its class Javadoc; **plan 02-04** implements them.
- `content/item/HarvesterItem` — marker only; the instakill + Fragment-drop behaviour is **plan 02-02** (`event/HarvesterEvents`).
- No models / blockstates / textures / recipes / loot tables / advancements yet — purple cubes and `FileNotFoundException: secondshift:models/...` warnings under `runClient` are expected until plans 02-03 / 02-04. `en_us.json` is complete now (enforced by the new self-check).

These stubs do not block the plan goal (the Wave-0 registration + guardrail + test backbone); each names the plan that resolves it.

## Self-Check: PASSED
