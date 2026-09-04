---
phase: 02-economy-items-soul-altar-block
verified: 2026-09-04T11:15:00Z
human_verified: 2026-09-04T12:05:00Z
status: passed
score: 4/4 must-haves verified (codebase + automated); human UAT passed 12/12 (see 02-HUMAN-UAT.md)
human_uat_note: "All 12 human verification items passed. Post-verification code-review fixes applied (CR-01 altar-drop blocker + WR-01/02/03) and re-checked green: ./gradlew build + runGameTestServer 8/8. One non-blocking polish item deferred — G-1: villager soul-harvest FX wants more visual impact."
overrides_applied: 0
mvp_mode: true
mvp_goal_format_discrepancy: "ROADMAP Goal for Phase 2 is not in User Story form ('As a ..., I want to ..., so that ...'). The 5 plans carry a derived User Story. Verification proceeded against the 4 ROADMAP Success Criteria (the contract). Recommend running /gsd mvp-phase 2 to reformat the ROADMAP goal."
human_verification:
  - test: "Open the creative menu in runClient. Find the 'Second Shift' tab."
    expected: "Exactly one mod tab; it contains Harvester, Soul Fragment, Soul Block, Soul Altar — each with a real icon, no black/magenta missing-texture cube."
    why_human: "Creative-menu rendering and 'is this a real texture' are visual — grep confirms registration + asset files exist, not how they draw."
  - test: "Hold the Harvester in runClient; hit a row of 3 pigs."
    expected: "Only the struck pig takes damage (no sweep arc); ~2 hits to kill one pig (modest ~3-4 damage). Harvester and Soul Fragment show real models in hand; Soul Fragment has an enchant-glint foil."
    why_human: "Combat feel, sweep visual, and foil shimmer are client-visual."
  - test: "Place a Soul Block in runClient; observe, then break it."
    expected: "Real textured block (no purple cube), emits light (~level 7), occasional rising soul wisps; breaking it drops exactly one Soul Block."
    why_human: "Block render, light propagation, particle cadence, and drop are in-world visual."
  - test: "In runClient with JEI/EMI (test instance): open the recipe viewer and search Harvester / Soul Block. Craft 4 Soul Fragments -> Soul Block, then Soul Block -> 4 Soul Fragments at a vanilla table."
    expected: "All three recipes (harvester, soul_block, soul_fragment_from_block) appear in JEI/EMI automatically; both conversion directions work at a crafting table with no material loss."
    why_human: "JEI/EMI is not in the dev client (CLAUDE.md: do not add); crafting-grid interaction is manual. Structurally guaranteed by vanilla crafting_shaped/crafting_shapeless types."
  - test: "Fresh SURVIVAL world in runClient. Check the recipe book before/after: pick up an emerald; later harvest a villager for a Soul Fragment; later craft a Soul Block."
    expected: "secondshift:harvester absent from the recipe book until an emerald is obtained, then 'Necromantic Apprentice' advancement toast + a recipe-unlock toast fire. soul_block (+ reverse) absent until the first Soul Fragment, then 'First Harvest' toast. soul_altar absent until a Soul Block is crafted, then 'Soul Mason' toast."
    why_human: "Recipe-book state and toast pop-ups are client UI over a multi-step play session; no auto advancement/recipes JSON exists so the gating is structural, but the toast + timing need eyes."
  - test: "Place a Soul Altar in runClient. Right-click it holding a Soul Block. Right-click again with another Soul Block. Right-click empty-handed. Try feeding it with a hopper/dropper. Then /data get block <pos>, save-quit-reload."
    expected: "First right-click consumes exactly one Soul Block into the altar (soul sound + particle burst). Second right-click and empty-hand do nothing (one-way, no retrieval). Hopper/dropper cannot insert or extract. The BE still holds the Soul Block after reload; the charged render persists."
    why_human: "Interaction, automation-rejection, and save/load round-trip are runtime behaviour."
  - test: "Break an EMPTY Soul Altar in runClient."
    expected: "The altar block drops itself (ALTAR-07)."
    why_human: "Block-break drop behaviour in-world."
  - test: "Socket a Soul Block, then break the CHARGED altar in runClient."
    expected: "Nothing drops at all (not the Soul Block, not the altar block); the breaking player loses exactly 1 hp (half a heart); a cosmetic lightning bolt flashes with thunder — no fire, no damage to nearby blocks or mobs."
    why_human: "Visual + damage side effects. Also confirm the D-04 design reading feels right — if 'charged altar lost entirely' plays like a bug, SoulAltarBlock#getDrops has a one-line marked fallback to drop the altar block."
  - test: "Charged altar renderer in runClient: socket a Soul Block, walk away past the chunk unload distance and back, and save-quit-reload."
    expected: "Embedded Soul Block appears in the altar top immediately on socket, full-bright even in shadow, no bob/spin, occasional slow soul wisp; render is still correct (not stale, not missing) after reload and chunk round-trip."
    why_human: "Live BER state, emissive lighting, and sync-after-reload are visual/runtime."
  - test: "Launch runClient (or the test instance) in a non-English locale."
    expected: "No raw item.secondshift.* / block.secondshift.* / advancement.secondshift.* keys shown anywhere; the startup lang-key self-check does not abort."
    why_human: "Locale-dependent UI text; self-check only guards en_us.json presence, not fallback rendering."
  - test: "Deploy the built jar to the CurseForge 'test' instance (./gradlew deployToTest) and launch it alongside owo-lib / accessories / wildcard."
    expected: "Game loads with no crash; Second Shift content present."
    why_human: "Real modpack co-existence cannot be reproduced in the dev client."
---

# Phase 2: Economy Items & Soul Altar Block — Verification Report

**Phase Goal:** The soul economy items and the Soul Altar block exist, craft with vanilla recipes, and behave — with zero GUI risk.
**Verified:** 2026-09-04T11:15:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## MVP Mode Note

Phase 2 is `mode: mvp`, but the ROADMAP `Goal:` line is a capability statement, not a User Story
(`As a …, I want to …, so that …`). The `user-story.validate` verb is not available in this
gsd-sdk build, so the format could not be machine-checked; by inspection it is not a User Story.
The 5 PLAN files each carry a derived User Story:

> «As a necromancer player, I want to craft a Harvester, reap villagers into Soul Fragments and
> Soul Blocks, and place a Soul Altar that accepts a socketed Soul Block, so that the tangible
> soul economy exists and behaves correctly before any binding GUI is built.»

Verification was performed goal-backward against the 4 ROADMAP Success Criteria (the contract).
**Recommendation:** run `/gsd mvp-phase 2` to reformat the ROADMAP goal so future UAT framing is
consistent. This does not block the phase.

## User Flow Coverage

User story (plan-derived): «As a necromancer player, I want to craft a Harvester, reap villagers
into Soul Fragments and Soul Blocks, and place a Soul Altar that accepts a socketed Soul Block,
so that the tangible soul economy exists and behaves correctly before any binding GUI is built.»

| Step | Expected | Evidence in codebase | Status |
|------|----------|----------------------|--------|
| Find the content in creative | One "Second Shift" tab holding all 4 objects, real icons | `registry/ModCreativeTab.java:26-35` single `TABS.register("main", …)` with `displayItems` accepting HARVESTER, SOUL_FRAGMENT, SOUL_BLOCK_ITEM, SOUL_ALTAR; runClient log: `4 item(s), 2 block(s), 1 creative tab(s)`, no missing-asset errors | ✓ code / ⧗ visual |
| Craft the Harvester | Shaped recipe at a vanilla table, JEI/EMI-visible, recipe-unlock toast | `data/secondshift/recipe/harvester.json` (`crafting_shaped`, emerald+bone+soul_soil); `advancement/necromantic_apprentice.json` `rewards.recipes:[secondshift:harvester]`, `show_toast:true` | ✓ code / ⧗ toast+JEI |
| Reap a villager | One Harvester hit kills any villager, drops exactly 1 Soul Fragment; sword drops none | `event/HarvesterEvents.java` — `LivingDamageEvent.Pre` instakill + `LivingDropsEvent` `getDrops().clear()` + one Fragment, gated on `HarvesterItem` weapon + exact `Villager`; **6/6 GameTests GREEN** (`./gradlew runGameTestServer` re-run this verification) | ✓ VERIFIED |
| Craft a Soul Block | 4 Fragments → 1 Block (shapeless, lossless), reverse too; toast-gated | `data/secondshift/recipe/soul_block.json` (4× soul_fragment → 1), `soul_fragment_from_block.json` (1 → 4); `advancement/first_harvest.json` `rewards.recipes:[soul_block, soul_fragment_from_block]` | ✓ code / ⧗ toast+JEI |
| Place & break a Soul Altar | Real pedestal block, breaks and drops itself when empty | `content/block/SoulAltarBlock.java:157-166` `getDrops` → `super.getDrops` (drops-self loot table) when not broken-while-charged; `loot_table/blocks/soul_altar.json` drops-self; `blockstates/soul_altar.json` + hand-authored `models/block/soul_altar.json` (3-element non-cube) + texture | ✓ code / ⧗ visual+break |
| Socket a Soul Block into the altar | One-way right-click socket, persists across reload, no automation | `SoulAltarBlock.java:89-119` `useItemOn` → `ItemInteractionResult`, gate `stack.is(SOUL_BLOCK_ITEM) && be.isEmpty()`, server-side only, `setChanged()` + `sendBlockUpdated`; `SoulAltarBlockEntity` codec persistence + `getUpdateTag`/`getUpdatePacket`; no `IItemHandler`/Capability | ✓ code / ⧗ reload |
| See the socketed block | Embedded emissive Soul Block in the altar top, no bob, wisp | `client/render/SoulAltarRenderer.java` `renderSingleBlock(..., LightTexture.FULL_BRIGHT, …)`, no time transform; registered from `ClientModBusEvents.onRegisterRenderers` (`Dist.CLIENT`) | ✓ code / ⧗ visual |
| Outcome — "soul economy exists and behaves, zero GUI" | Tangible items/blocks/recipes/altar all present and behaving; no menu/screen shipped | All of the above; no `MenuType`/`Screen`/`RegisterMenuScreensEvent` in this phase; `./gradlew runServer` reaches "Done" with no client-class leak | ✓ VERIFIED |

⧗ = automated/code evidence complete; in-game visual/interaction confirmation deferred to human (see `human_verification`).

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | All mod items and the Soul Altar appear in a single creative tab, each with a real (non-missing) texture and model | ✓ VERIFIED (code) | `ModCreativeTab` = one tab, 4 objects via `displayItems`. All 4 item/block model JSON present + parse; all 4 textures are valid 16×16 RGBA PNGs. runClient reached the sound engine with 0 missing-model/texture/lang errors for `secondshift`. Visual "no purple cube" = human. |
| 2 | Crafting a Harvester, and 4 Soul Fragments → Soul Block, both work at a vanilla table, show in JEI/EMI, and fire a recipe-unlock toast | ✓ VERIFIED (code) | `recipe/harvester.json` = `crafting_shaped`; `recipe/soul_block.json` = `crafting_shapeless` 4→1; both use 1.21.1 `result.{id,count}`. Vanilla recipe types → JEI/EMI auto. Toasts: `necromantic_apprentice` / `first_harvest` advancements grant via `rewards.recipes`, `show_toast:true`. Actual crafting + toast + JEI visibility = human. |
| 3 | Killing any villager with the Harvester drops exactly 1 Soul Fragment every time; killing one with a sword drops none and normal loot is unchanged | ✓ VERIFIED | `HarvesterEvents` gates instakill + exactly-1-Fragment on `getWeaponItem() instanceof HarvesterItem` AND `target instanceof Villager` (exact), server-side. Sword path never touches `getDrops()`. **`./gradlew runGameTestServer` re-run: "All 6 required tests passed"** — covers adult/baby/Resistance-V+absorption villager, wandering trader (excluded), zombie villager (excluded), sword-kill (no Fragment, vanilla loot). |
| 4 | A placed Soul Altar can be broken and drops itself; it renders as a real block, not a missing-texture cube | ✓ VERIFIED (code) | `SoulAltarBlock#getDrops` → `super.getDrops` (drops-self `loot_table/blocks/soul_altar.json`) on the empty/unbroken-charged path. `blockstates/soul_altar.json` → hand-authored `models/block/soul_altar.json` (3-box pedestal, `elements` array) → real `soul_altar.png`. runClient: no missing-model warnings. In-world break + render = human. |

**Score:** 4/4 success criteria verified at codebase + automated level. In-game visual/interaction confirmations pending (expected under `human_verify_mode: end-of-phase`).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `registry/ModItems.java` | HARVESTER (attributes, durability 250), SOUL_FRAGMENT (foil), SOUL_BLOCK_ITEM, SOUL_ALTAR_ITEM | ✓ VERIFIED | All 4 present; `SwordItem.createAttributes(Tiers.STONE,2,-2.8f)`; `ENCHANTMENT_GLINT_OVERRIDE`; `registerSimpleBlockItem` for both blocks. No `DEBUG_MARKER`. |
| `registry/ModBlocks.java` | SOUL_BLOCK (light 7), SOUL_ALTAR (noOcclusion, strength 3.5, requiresCorrectToolForDrops) | ✓ VERIFIED | `.lightLevel(state -> 7)`, `.strength(3.5F).requiresCorrectToolForDrops().noOcclusion()`. |
| `registry/ModBlockEntities.java` | SOUL_ALTAR_BE from `BlockEntityType.Builder.of` | ✓ VERIFIED | `Builder.of(SoulAltarBlockEntity::new, ModBlocks.SOUL_ALTAR.get())`. |
| `registry/ModCreativeTab.java` | one tab, all 4 objects | ✓ VERIFIED | Single `TABS.register("main", …)`, `displayItems` accepts all 4. |
| `content/blockentity/SoulAltarBlockEntity.java` | one-slot codec persistence + client sync, no Capability | ✓ VERIFIED | `super` first in save/load; `ItemStack#save`/`parse`; `getUpdateTag`+`getUpdatePacket`→`ClientboundBlockEntityDataPacket.create`; transient `brokenWhileCharged`; no `IItemHandler`. |
| `content/block/SoulAltarBlock.java` | useItemOn/useWithoutItem/playerWillDestroy/getDrops | ✓ VERIFIED | All `@Override`; `useItemOn`→`ItemInteractionResult`, one-way gate, server-side, `setChanged`+`sendBlockUpdated`; `playerWillDestroy`→visual-only LightningBolt + `hurt(magic(),1.0F)` + clear stack; `getDrops`→`List.of()` when charged-broken, else super. No client imports. |
| `event/HarvesterEvents.java` | game-bus instakill + exactly-1-Fragment swap | ✓ VERIFIED | `LivingDamageEvent.Pre` + `LivingDropsEvent`; exact `Villager`, no `AbstractVillager`, no `getOffers()`, no `LivingIncomingDamageEvent`. Harvest FX all vanilla. |
| `gametest/HarvesterGameTests.java` | 6 ECON-02 @GameTest methods | ✓ VERIFIED | 6 methods; all GREEN this run. |
| `ModRegistrySelfCheck.java` | 4-register unbound check + descriptionId/lang-key check | ✓ VERIFIED | `Stream.of(ModItems.ITEMS, ModBlocks.BLOCKS, ModBlockEntities.BLOCK_ENTITIES, ModCreativeTab.TABS)`; 2nd handler reads bundled `en_us.json` off classpath, server-safe. |
| `SecondShift.java` | 4 registers wired in one block | ✓ VERIFIED | All 4 `.register(modBus)` in the constructor block. |
| `en_us.json` | 11 keys (4 item/block + tab + 3 advancement title/desc pairs) | ✓ VERIFIED | All 11 present; enforced at startup by the self-check. |
| recipes ×4 | harvester (shaped), soul_block (4→1), soul_fragment_from_block (1→4), soul_altar (shaped) | ✓ VERIFIED | All vanilla crafting types, 1.21.1 `result.{id,count}`, no `unlockedBy`/`has_item`. |
| advancements ×3 | necromantic_apprentice, first_harvest, soul_mason — `rewards.recipes`, toast on | ✓ VERIFIED | Correct triggers (`inventory_changed` ×2, `recipe_crafted` ×1), `rewards.recipes` correct, `show_toast:true`. No `advancement/recipes/` dir. |
| loot tables ×2 | soul_block + soul_altar drops-self | ✓ VERIFIED | `minecraft:block`, `survives_explosion`, singular `loot_table/blocks/` path. |
| blockstates/models/textures | soul_block (cube_all), soul_altar (elements pedestal), + item models | ✓ VERIFIED | All present + parse; textures valid PNGs. |
| `client/render/SoulAltarRenderer.java` + `ClientModBusEvents.java` | BER + RegisterRenderers wiring | ✓ VERIFIED | `BlockEntityRenderer<SoulAltarBlockEntity>`, `FULL_BRIGHT`, no bob, throttled wisp; `registerBlockEntityRenderer(ModBlockEntities.SOUL_ALTAR_BE.get(), SoulAltarRenderer::new)`. |
| `build.gradle` | `gameTestServer` run re-added | ✓ VERIFIED | Present with `neoforge.enabledGameTestNamespaces`. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SecondShift` ctor | 4 DeferredRegisters | `.register(modBus)` in one block | ✓ WIRED | Lines 37-40. |
| `ModRegistrySelfCheck` | 4 registers | `Stream.of(ModItems.ITEMS, ModBlocks.BLOCKS, ModBlockEntities.BLOCK_ENTITIES, ModCreativeTab.TABS)` | ✓ WIRED | Line 75. |
| `ModBlockEntities` | `SoulAltarBlockEntity` / `ModBlocks.SOUL_ALTAR` | `BlockEntityType.Builder.of(SoulAltarBlockEntity::new, ModBlocks.SOUL_ALTAR.get())` | ✓ WIRED | Line 29. |
| `HarvesterEvents` | `HarvesterItem` | `src.getWeaponItem().getItem() instanceof HarvesterItem` | ✓ WIRED | Line 126. |
| `HarvesterEvents` | `ModItems.SOUL_FRAGMENT` | `getDrops().clear()` then add one `ItemEntity(new ItemStack(ModItems.SOUL_FRAGMENT.get()))` | ✓ WIRED | Lines 76-82. Confirmed live by GameTests. |
| `SoulAltarBlock#useItemOn` | `SoulAltarBlockEntity` | `be.isEmpty()` gate + `be.setHeldSoulBlock(stack.copyWithCount(1))` + `setChanged` + `sendBlockUpdated` | ✓ WIRED | Lines 95-104. |
| `SoulAltarBlock#getDrops` | `SoulAltarBlockEntity` | `params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)` → `wasBrokenWhileCharged()` | ✓ WIRED | Lines 159-163. |
| `necromantic_apprentice.json` | `recipe/harvester.json` | `rewards.recipes:[secondshift:harvester]` | ✓ WIRED | — |
| `first_harvest.json` | `recipe/soul_block.json` + reverse | `rewards.recipes:[secondshift:soul_block, secondshift:soul_fragment_from_block]` | ✓ WIRED | — |
| `soul_mason.json` | `recipe/soul_altar.json` | `recipe_crafted(secondshift:soul_block)` → `rewards.recipes:[secondshift:soul_altar]` | ✓ WIRED | — |
| `ClientModBusEvents` | `SoulAltarRenderer` + `SOUL_ALTAR_BE` | `event.registerBlockEntityRenderer(ModBlockEntities.SOUL_ALTAR_BE.get(), SoulAltarRenderer::new)` | ✓ WIRED | Line 41; renderer registration logged in runClient. |
| `SoulAltarRenderer` | `SoulAltarBlockEntity` | `be.isEmpty()` / `be.getHeldSoulBlock()` drive the render | ✓ WIRED | Lines 59-63. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `SoulAltarRenderer` | `be.isEmpty()` / held stack | `SoulAltarBlockEntity.heldSoulBlock`, set by `useItemOn`, synced via `getUpdatePacket` + `sendBlockUpdated` | Yes — real BE state, server-authoritative | ✓ FLOWING |
| `LivingDropsEvent` drop | `ModItems.SOUL_FRAGMENT.get()` | Live registry holder; GameTests observe exactly 1 `ItemEntity` | Yes | ✓ FLOWING |
| Creative tab | 4 `.get()` calls on registry holders | DeferredRegister entries, all bound (runClient/runServer logs) | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Project compiles + resources load-validate | `./gradlew build` | BUILD SUCCESSFUL | ✓ PASS |
| ECON-02 mechanic end-to-end | `./gradlew runGameTestServer` | `========= 6 GAME TESTS COMPLETE … All 6 required tests passed :)` | ✓ PASS |
| Dedicated server boots, no client-class leak | `run-until.sh runServer 'Done …'` | `Done (0.351s)! For help, type "help"`; `4 item(s), 2 block(s), 1 block-entity type(s), 1 creative tab(s)` bound; 0 matches for `NoClassDefFoundError\|ClassNotFoundException\|net/minecraft/client\|SoulAltarRenderer` | ✓ PASS |
| Dev client reaches menu, assets resolve | `run-until.sh runClient 'Sound engine started…'` | Sound engine started; 0 matches for `Unbound registry entries\|Unresolved lang keys\|NoClassDefFoundError\|Missing textures\|model` warnings for `secondshift` | ✓ PASS |
| No debt markers in phase code | grep `TODO\|FIXME\|XXX\|TBD\|HACK` under `src/main/java` | No matches | ✓ PASS |
| Client-class isolation | grep `SoulAltarRenderer\|PoseStack\|MultiBufferSource\|net.minecraft.client` under `secondshift/` | Only `client/` package matches | ✓ PASS |

### Probe Execution

No `scripts/*/tests/probe-*.sh` probes in the project. GameTest suite (`./gradlew runGameTestServer`) is the phase's automated ECON-02 driver and was executed by this verification — GREEN.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|----------------|-------------|--------|----------|
| ECON-01 | 02-02, 02-03 | Harvester is a craftable tool/weapon | ✓ SATISFIED | `HARVESTER` item with attack attributes; `recipe/harvester.json` shaped. Combat feel = human. |
| ECON-02 | 02-01, 02-02 | Kill any villager with Harvester → exactly 1 Soul Fragment; no other vanilla drops changed | ✓ SATISFIED | 6/6 GameTests GREEN; `HarvesterEvents` clear+add-one; sword path untouched. |
| ECON-03 | 02-03 | 4 Soul Fragments → 1 Soul Block (vanilla shapeless) | ✓ SATISFIED | `recipe/soul_block.json` `crafting_shapeless`, 4 ingredients → 1. |
| ALTAR-01 | 02-01, 02-04 | Soul Altar is a craftable block backed by a block entity | ✓ SATISFIED | `SoulAltarBlock implements EntityBlock`; `SoulAltarBlockEntity`; `SOUL_ALTAR_BE`; `recipe/soul_altar.json`. Socket-persist-reload = human. |
| ALTAR-07 | 02-04 | Unbound altar breaks normally and drops itself | ✓ SATISFIED | `getDrops` → drops-self loot table when not charged-broken. In-world break = human. |
| POL-01 | 02-01 | Creative tab contains every mod item and block | ✓ SATISFIED | `ModCreativeTab` one tab, all 4 objects. |
| POL-03 | 02-01…02-05 | Every item and block has a model and texture (placeholder OK) | ✓ SATISFIED | 4 model sets + 4 valid PNG textures; runClient no missing-asset errors. Non-English-locale raw-key check = human. |
| POL-04 | 02-03, 02-04 | Recipes use vanilla types (JEI/EMI auto) + emit recipe-unlock advancements | ✓ SATISFIED | 4 vanilla-type recipes; 3 advancements with `rewards.recipes` + `show_toast`. JEI/EMI + toast visibility = human. |

All 8 phase requirement IDs are declared across the plans (union = exactly the 8 in ROADMAP), each mapped to verified artifacts. **No orphaned requirements.** REQUIREMENTS.md already marks all 8 Complete for Phase 2. `ECON-04` and `ALTAR-06` (charged-altar-kills-employee, employee-only Fragment) are correctly deferred to Phase 6 — not in scope here.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | none | — | No `TODO`/`FIXME`/`XXX`/`TBD`/`HACK`/`PLACEHOLDER` markers in any phase-modified Java file. Placeholder 16×16 textures are explicitly permitted this phase (POL-03 = "non-missing", not final art). |

### Deviations noted in SUMMARYs (all benign, verified in code)

- `ModBlockEntities` created in plan 02-01 Task 1 instead of Task 2 (compile-order) — present and correct.
- `HarvesterItem` overrides `getEnchantmentValue(ItemStack)` — 1.21.1 has no `Item.Properties#enchantable(int)`; this is a quality bump, not an attack/sweep override (still `extends Item`, never `SwordItem`). Verified.
- `ModItems.SOUL_ALTAR_ITEM` added in plan 02-04 (was missing from 02-01) — present; lang key `block.secondshift.soul_altar` resolves.
- Transient `brokenWhileCharged` flag on the BE for D-04 drop suppression (stack is cleared before `getDrops` runs) — present and sound.

### Verify-Time Design Flags (for human decision, not gaps)

1. **D-04 — a charged Soul Altar broken is lost entirely** (the altar block itself does NOT drop). Implemented as specified. If this plays like a bug, `SoulAltarBlock#getDrops` has a single marked line to flip (`return List.of();` → `return super.getDrops(state, params);`) so only the Soul Block is destroyed.
2. **Baby villagers are one-shot by the Harvester and drop a Fragment** (D-09 "a soul is a soul"). Matches ECON-02 "any villager". Confirm this feels right in play.
3. **Renderer placement constants** (`EMBED_Y = 0.78`, `EMBED_SCALE = 0.5`) are a first pass — nudge if the embedded block reads as floating rather than recessed.

### Gaps Summary

**No blocking gaps.** Every ROADMAP Success Criterion is satisfied at the codebase level, and the
one genuinely custom mechanic (ECON-02 villager instakill + guaranteed single Soul Fragment) is
covered by a GREEN 6-case GameTest suite that this verification re-ran. `./gradlew build`,
`runGameTestServer`, `runServer` (client-class-leak gate), and `runClient` (asset/registry sanity)
all pass. No debt markers, no client-class leak, no orphaned requirements.

Status is **human_needed** solely because Phase 2's success criteria include inherently visual /
interactive outcomes — creative-tab appearance, JEI/EMI recipe visibility, recipe-unlock toasts,
in-world block render + break behaviour, socket interaction, save/load persistence, and the
charged-altar break FX — which the project's `human_verify_mode: end-of-phase` defers to the
developer. These are enumerated in the frontmatter `human_verification` list.

---

_Verified: 2026-09-04T11:15:00Z_
_Verifier: Claude (gsd-verifier)_
