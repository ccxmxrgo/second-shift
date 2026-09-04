---
phase: 02-economy-items-soul-altar-block
plan: 04
subsystem: content
tags: [neoforge, entityblock, blockentity, useItemOn, ItemInteractionResult, playerWillDestroy, getDrops, loot-table, advancement, recipe, blockmodel, json-1.21.1]

requires:
  - phase: 02-economy-items-soul-altar-block
    provides: "02-01 — SoulAltarBlock EntityBlock skeleton (newBlockEntity + getShape) + SoulAltarBlockEntity one-slot persistence/getUpdateTag/getUpdatePacket; ModItems.SOUL_BLOCK_ITEM; ModBlocks.SOUL_ALTAR; ModBlockEntities.SOUL_ALTAR_BE; en_us.json incl. soul_mason keys; lang-key + unbound-register self-check"
  - phase: 02-economy-items-soul-altar-block
    provides: "02-03 — hand-written 1.21.1 datapack JSON proven for recipes/advancements/loot/blockstate/models; drops-self loot table shape; inventory_changed advancement -> rewards.recipes gating pattern"
provides:
  - "SoulAltarBlock#useItemOn (ItemInteractionResult) — one-way Soul Block socket: gate on ModItems.SOUL_BLOCK_ITEM + be.isEmpty(), server-side BE mutation, setChanged() + sendBlockUpdated(), SOUL_ESCAPE sound + soul-particle burst"
  - "SoulAltarBlock#useWithoutItem -> InteractionResult.PASS (no menu / no retrieval this phase)"
  - "SoulAltarBlock#playerWillDestroy — charged-altar break (D-04): visual-only LightningBolt, 1.0F magic damage to the breaker only, clears the socketed stack, marks the BE for drop suppression"
  - "SoulAltarBlock#getDrops — List.of() when the BE was broken-while-charged, else super (drops-self loot table); one-line D-04 fallback flip marked in a comment"
  - "SoulAltarBlockEntity#brokenWhileCharged transient flag + wasBrokenWhileCharged()/markBrokenWhileCharged() accessors"
  - "ModItems.SOUL_ALTAR_ITEM — the secondshift:soul_altar BlockItem (was missing; required by the recipe result, loot drop, item model and creative tab)"
  - "data/secondshift/loot_table/blocks/soul_altar.json — drops-self (ALTAR-07 empty-altar path)"
  - "data/secondshift/recipe/soul_altar.json — crafting_shaped: blackstone frame + soul soil + bone + 1 emerald core (D-06)"
  - "data/secondshift/advancement/soul_mason.json — recipe_crafted(secondshift:soul_block) -> rewards.recipes [secondshift:soul_altar] (D-15 step 3)"
  - "assets/secondshift/{blockstates,models/block,models/item,textures/block}/soul_altar.* — single-variant blockstate, hand-authored 3-element pedestal model matching the VoxelShape, item model, 16x16 placeholder texture"
affects: [02-05-altar-renderer]

tech-stack:
  added: []
  patterns:
    - "1.21.1 socket interaction: @Override ItemInteractionResult useItemOn + @Override InteractionResult useWithoutItem; BE writes gated on !level.isClientSide; setChanged() + level.sendBlockUpdated(pos,state,state,Block.UPDATE_ALL) after every mutation"
    - "Break-time drop suppression without a block-singleton flag: transient boolean on the BlockEntity, set in playerWillDestroy, read in getDrops via LootParams.Builder#getOptionalParameter(LootContextParams.BLOCK_ENTITY) (same BE instance flows through the break pipeline)"
    - "Non-full-cube block model: parent minecraft:block/block + hand-authored elements array whose from/to boxes mirror the getShape VoxelShape 1:1"

key-files:
  created:
    - src/main/resources/data/secondshift/loot_table/blocks/soul_altar.json
    - src/main/resources/data/secondshift/recipe/soul_altar.json
    - src/main/resources/data/secondshift/advancement/soul_mason.json
    - src/main/resources/assets/secondshift/blockstates/soul_altar.json
    - src/main/resources/assets/secondshift/models/block/soul_altar.json
    - src/main/resources/assets/secondshift/models/item/soul_altar.json
    - src/main/resources/assets/secondshift/textures/block/soul_altar.png
  modified:
    - src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java
    - src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java
    - src/main/java/com/cxmxrgo/secondshift/registry/ModItems.java

key-decisions:
  - "playerWillDestroy signature (A4) confirmed: public BlockState playerWillDestroy(Level, BlockPos, BlockState, Player) — decompiled Block.java:467; @Override compiles"
  - "getDrops drop-suppression (A5): LootContextParams.BLOCK_ENTITY (LootContextParam<BlockEntity>) read via LootParams.Builder#getOptionalParameter; the break pipeline captures the BE before removal (ServerPlayerGameMode.destroyBlock) so a transient flag set in playerWillDestroy survives into getDrops"
  - "LightningBolt visual-only (A6): setVisualOnly(boolean) — LightningBolt.java:51; created via EntityType.LIGHTNING_BOLT.create(Level) + moveTo(Vec3.atBottomCenterOf(pos))"
  - "Soul Mason trigger: minecraft:recipe_crafted with conditions.recipe_id = secondshift:soul_block (RecipeCraftedTrigger.TriggerInstance CODEC verified) — semantically 'craft a Soul Block' per D-15 step 3, not inventory_changed"
  - "Altar recipe grid: [DED, DSD, DBD] D=minecraft:blackstone E=minecraft:emerald S=minecraft:soul_soil B=minecraft:bone => 6 blackstone frame + 1 emerald binding core (top) + 1 soul soil (center) + 1 bone (base)"
  - "SOUL_ALTAR BlockItem was never registered in 02-01 — added ModItems.SOUL_ALTAR_ITEM this plan (recipe result / loot drop / item model / creative tab all reference secondshift:soul_altar)"
  - "D-04 charged break implemented as written: charged altar drops NOTHING (not the Soul Block, not the altar block). Fallback flip is one line in SoulAltarBlock#getDrops"

patterns-established:
  - "Transient BE marker for break consequences: brokenWhileCharged set in playerWillDestroy, read in getDrops off the LootContextParams.BLOCK_ENTITY instance"
  - "Altar interaction contract: item-in-hand socket only (useItemOn), empty-hand is PASS (useWithoutItem), no capability/IItemHandler ever exposed"

requirements-completed: [ALTAR-01, ALTAR-07, POL-03, POL-04]

duration: 25min
completed: 2026-09-04
---

# Phase 2 Plan 04: Soul Altar Interaction & Break Behaviour Summary

**The Soul Altar is now fully behavioural: one-way Soul Block socketing via the 1.21.1 `ItemInteractionResult useItemOn` signature with server-synced BE state, D-04 charged-break consequences (visual-only lightning + half-heart + total drop suppression), a real 3-element pedestal model matching the VoxelShape, a blackstone/emerald shaped recipe, and the "Soul Mason" advancement gating that recipe behind crafting a Soul Block.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-09-04T10:44:00Z
- **Completed:** 2026-09-04T10:54:00Z
- **Tasks:** 3 completed
- **Files created/modified:** 10 (7 created, 3 modified)

## Accomplishments

- `SoulAltarBlock` fills every interaction/break method the 02-01 skeleton documented: `useItemOn`, `useWithoutItem`, `playerWillDestroy`, `getDrops` — all `@Override`, all verified against the decompiled 21.1.248 sources.
- One-way socket: right-click a charged-empty altar holding a `secondshift:soul_block` consumes exactly one into the BE slot, plays `SoundEvents.SOUL_ESCAPE` + an 8-particle `ParticleTypes.SOUL` burst, and calls both `be.setChanged()` and `level.sendBlockUpdated(...)` (the sync path the 02-05 renderer needs). No overwrite, no empty-hand retrieval, no capability exposed.
- D-04 charged-altar break: visual-only `LightningBolt` (flash + thunder, no fire/collateral), exactly `1.0F` armour-bypassing `magic()` damage to the breaking player only, the socketed stack is cleared, and `getDrops` returns `List.of()` — the altar block itself is lost too.
- The altar is a real non-full-cube pedestal: single-variant blockstate -> hand-authored 3-box model (base slab / column / top plate, geometry 1:1 with `SoulAltarBlock.SHAPE`) -> item model -> 16x16 blackstone+soul-fire placeholder texture. The missing `secondshift:soul_altar` BlockItem was registered.
- `soul_altar` shaped recipe (blackstone frame + soul soil + bone + 1 emerald core) is granted only by the new `soul_mason.json` advancement, which fires on `minecraft:recipe_crafted` of `secondshift:soul_block` (D-15 step 3).
- `./gradlew build` green after every task; `./gradlew runServer` reached `Done (0.316s)` — 1295 recipes / 1403 advancements loaded with zero parse errors, no client-class leak.

## Task Commits

1. **Task 1: SoulAltarBlock one-way socket path (useItemOn / useWithoutItem)** — `92cdbaa` (feat)
2. **Task 2: altar break behaviour (D-04) — empty drops self, charged drops nothing** — `fb52353` (feat)
3. **Task 3: altar model, blockstate, item model, texture, recipe, Soul Mason advancement** — `fcbd5e8` (feat)

**Plan metadata:** (final docs commit)

## Files Created/Modified

- `content/block/SoulAltarBlock.java` — added `useItemOn`, `useWithoutItem`, `playerWillDestroy`, `getDrops`; kept `newBlockEntity` / `getShape`
- `content/blockentity/SoulAltarBlockEntity.java` — added transient `brokenWhileCharged` flag + `wasBrokenWhileCharged()` / `markBrokenWhileCharged()`
- `registry/ModItems.java` — added `SOUL_ALTAR_ITEM` (`registerSimpleBlockItem("soul_altar", ModBlocks.SOUL_ALTAR, ...)`)
- `data/secondshift/loot_table/blocks/soul_altar.json` — `minecraft:block` drops-self, `survives_explosion`
- `data/secondshift/recipe/soul_altar.json` — `crafting_shaped`, `category: misc`, pattern `[DED, DSD, DBD]`
- `data/secondshift/advancement/soul_mason.json` — `recipe_crafted` on `secondshift:soul_block`, `rewards.recipes: [secondshift:soul_altar]`, toast + chat on
- `assets/secondshift/blockstates/soul_altar.json` — single `""` variant
- `assets/secondshift/models/block/soul_altar.json` — `parent block/block` + 3-element pedestal
- `assets/secondshift/models/item/soul_altar.json` — `{"parent": "secondshift:block/soul_altar"}`
- `assets/secondshift/textures/block/soul_altar.png` — 16x16 RGBA placeholder (blackstone base + pale soul-fire flecks, top-biased)

## Plan `<output>` — required records

| Item | Result |
|------|--------|
| **A4 — `playerWillDestroy` signature/return** | `public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)` — decompiled `net.minecraft.world.level.block.Block:467`. Body runs before BE removal; ends with `return super.playerWillDestroy(...)`. |
| **A5 — `LootContextParams` constant** | `LootContextParams.BLOCK_ENTITY` (`LootContextParam<BlockEntity>`), read via `LootParams.Builder#getOptionalParameter(LootContextParam)` -> `@Nullable T`. Verified the break pipeline passes the pre-removal BE instance through `Block.getDrops(state, level, pos, blockEntity, entity, tool)` (`ServerPlayerGameMode.destroyBlock` captures `blockentity` before `playerWillDestroy`). |
| **A6 — `LightningBolt` visual-only method** | `LightningBolt#setVisualOnly(boolean)` — decompiled `LightningBolt:51`. Entity built with `EntityType.LIGHTNING_BOLT.create(Level)` (nullable) + `moveTo(Vec3.atBottomCenterOf(pos))` + `level.addFreshEntity(bolt)`. |
| **Final altar recipe grid** | `crafting_shaped`, `category: misc`, `pattern: ["DED","DSD","DBD"]`, keys `D=minecraft:blackstone` (x6 frame), `E=minecraft:emerald` (x1 binding core, top-center), `S=minecraft:soul_soil` (x1, center), `B=minecraft:bone` (x1, base). `result: {"id":"secondshift:soul_altar","count":1}`. Within D-06 (1–2 emerald -> 1 used). |
| **`recipe_crafted` vs `inventory_changed` for Soul Mason** | **`minecraft:recipe_crafted`** with `conditions.recipe_id = "secondshift:soul_block"`. Verified against `RecipeCraftedTrigger.TriggerInstance.CODEC` (field `recipe_id` = `ResourceLocation.CODEC`, optional `ingredients`). Chosen over `inventory_changed` because D-15 step 3 is literally "craft a Soul Block". |
| **Exact one-line location of the D-04 drop-fallback branch** | `SoulAltarBlock#getDrops(BlockState, LootParams.Builder)` — the line `return List.of();` inside the `if (be instanceof SoulAltarBlockEntity altar && altar.wasBrokenWhileCharged())` block (marked with a `>>> D-04 FALLBACK` comment). Flip it to `return super.getDrops(state, params);` to make a charged break drop the altar block (only the Soul Block destroyed). |
| **Socket-persistence relog result** | Not manually run — `workflow.human_verify_mode: end-of-phase`, so the place/socket/relog/break checks are deferred to the phase gate (`02-VALIDATION.md` rows ALTAR-01 / ALTAR-07 / D-04). Structural guarantee: 02-01's `SoulAltarBlockEntity` already persists the one slot (`super` in save/loadAdditional, codec `ItemStack#save`/`parse`, `getUpdateTag`/`getUpdatePacket`); this plan adds `setChanged()` + `sendBlockUpdated()` after the socket. `runServer` loaded clean. |

## Decisions Made

See `key-decisions` frontmatter. Highlights: `recipe_crafted` (not `inventory_changed`) for Soul Mason; D-04 implemented as written (charged altar lost entirely) with a one-line fallback; transient BE flag for drop suppression rather than any block-singleton state.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Registered the missing `secondshift:soul_altar` BlockItem**
- **Found during:** Task 3 (recipe / loot / item model / creative tab)
- **Issue:** No `soul_altar` item was ever registered (02-01 only added `SOUL_BLOCK_ITEM`). The Task 3 recipe `result.id`, the loot-table entry, `models/item/soul_altar.json`, and the existing `ModCreativeTab` `output.accept(ModBlocks.SOUL_ALTAR.get())` all require `secondshift:soul_altar` to exist as an item — the datapack JSON would fail to load and the creative tab would try to accept an empty stack.
- **Fix:** Added `ModItems.SOUL_ALTAR_ITEM = ITEMS.registerSimpleBlockItem("soul_altar", ModBlocks.SOUL_ALTAR, new Item.Properties())`. Uses the block's `descriptionId` (`block.secondshift.soul_altar`, already in `en_us.json` — the 02-01 lang self-check still passes). No `ModCreativeTab` change needed.
- **Files modified:** `src/main/java/com/cxmxrgo/secondshift/registry/ModItems.java`
- **Verification:** `runServer` log — `4 item(s) ... registered: [secondshift:harvester, secondshift:soul_altar, secondshift:soul_block, secondshift:soul_fragment]`; recipes/advancements loaded with no errors.
- **Committed in:** `fcbd5e8` (Task 3 commit)

**2. [Rule 3 — Blocking] Transient `brokenWhileCharged` flag on `SoulAltarBlockEntity`**
- **Found during:** Task 2 (getDrops / playerWillDestroy)
- **Issue:** The plan's `getDrops` note says "if the BE was charged ... return `List.of()`", but `playerWillDestroy` clears the stack (`be.setHeldSoulBlock(EMPTY)`) *before* `getDrops` runs, so `!be.isEmpty()` is always false by then. A block-singleton boolean is unsafe (shared instance).
- **Fix:** Added a `transient boolean brokenWhileCharged` + `wasBrokenWhileCharged()` / `markBrokenWhileCharged()` to `SoulAltarBlockEntity`. `playerWillDestroy` calls `markBrokenWhileCharged()` before clearing the stack; `getDrops` reads it off the `LootContextParams.BLOCK_ENTITY` instance (same object, captured pre-removal by `ServerPlayerGameMode.destroyBlock`). Not persisted — it only lives for the break tick.
- **Files modified:** `src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java`
- **Verification:** `./gradlew build` green; flow traced through decompiled `Block.java` / `ServerPlayerGameMode.java`.
- **Committed in:** `fb52353` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 3 — blocking).
**Impact on plan:** Both were required to implement the plan as specified (the recipe/loot/tab could not work without the BlockItem; D-04 drop suppression could not work without the transient flag). No scope creep — no behaviour beyond what the plan describes.

## Issues Encountered

None. All three `./gradlew build` runs and the `runServer` smoke passed first try. The `git` CRLF warnings are cosmetic (autocrlf).

## Verify-time flags (surface at end-of-phase verification)

- **A charged altar, when broken, is lost entirely** — the altar block does NOT drop (D-04 as written). If this feels like a bug in play, flip the single marked line in `SoulAltarBlock#getDrops` to `return super.getDrops(state, params);` (the altar block then drops; only the Soul Block is destroyed). RESEARCH Open Question 1.
- Manual checks deferred to the phase gate (`human_verify_mode: end-of-phase`): socket one-way + FX + hopper rejection + save-quit-reload persistence (ALTAR-01); empty break drops the altar (ALTAR-07); charged break drops nothing + half-heart + cosmetic lightning + no collateral (D-04); altar renders as a pedestal in the "Second Shift" tab; `soul_altar` recipe hidden until a Soul Block is crafted, then "Soul Mason" + recipe-unlock toasts (POL-04 / D-15).

## Known Stubs

- `assets/secondshift/textures/block/soul_altar.png` — 16x16 procedurally-generated placeholder (POL-03 explicitly permits placeholder quality this phase). A hand-drawn blackstone/soul-fire texture is Phase 10 polish.
- The charged/socketed render (embedded glowing Soul Block) is **plan 02-05** — this plan syncs the BE state (`sendBlockUpdated`) but renders nothing extra yet. Not a gap: 02-05 is the renderer plan.

## Next Phase Readiness

- 02-05 (altar `BlockEntityRenderer` for the embedded Soul Block) is unblocked: `SoulAltarBlockEntity` syncs `heldSoulBlock` via `getUpdateTag`/`getUpdatePacket` (02-01) and this plan calls `level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL)` after the socket, so the client BE mirrors server truth.
- The Soul Altar is now the last tangible economy piece before the Phase 3 menu harness — craft, place, socket (one-way), break (empty vs charged) all behave.

## Self-Check: PASSED

All 7 created files present on disk; task commits `92cdbaa` / `fb52353` / `fcbd5e8` and summary commit `4ff4384` all in git history.

---
*Phase: 02-economy-items-soul-altar-block*
*Completed: 2026-09-04*
