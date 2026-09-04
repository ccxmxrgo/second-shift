# Phase 2: Economy Items & Soul Altar Block - Context

**Gathered:** 2026-09-04
**Status:** Ready for planning

<domain>
## Phase Boundary

The tangible layer of the soul economy, built with **zero GUI risk**:

1. **Harvester** — a craftable scythe. A single melee hit instakills any villager (soul-reap;
   ignores health/armor). Modest weapon against everything else.
2. **Soul Fragment** — item; sole source is a Harvester kill on a villager.
3. **Soul Block** — placeable block; 4 Fragments ⇄ 1 Block (reversible, vanilla shapeless).
4. **Soul Altar** — a single carved-stone block backed by a block entity with a one-slot
   inventory. A Soul Block **sockets in one-way** (eye-of-ender style). Breaking the altar
   behaves differently empty vs. charged. **No menu, no binding, no profession logic.**
5. **Creative tab** — one tab, every mod item/block, real (non-missing) textures + models.
6. **Recipes** — vanilla crafting types only (free JEI/EMI); a guided **custom advancement
   chain** gates recipe discovery.
7. **en_us.json** — complete for everything this phase introduces.

**Requirements in scope:** ECON-01, ECON-02, ECON-03, ALTAR-01, ALTAR-07, POL-01, POL-03,
POL-04 (8 total).

**Explicitly NOT in this phase:**
- Any menu / screen / `MenuType` (that is Phase 3, the HARD GATE).
- Any binding, profession resolution, `EmployeeData`, or employee spawn (Phases 4–5).
- Instakilling / soul-recovering a **bound employee**, and the employee-kill half of the
  altar-destruction sequence (Phase 6, ALTAR-06 / ECON-04).
- Full bespoke sound + particle polish pass and a custom `SoundEvent`/`ParticleType`
  registry (Phase 10, POL-05) — this phase borrows vanilla soul sounds/particles only.
- Custom paintings / in-village discovery hints (see Deferred Ideas — flagged for roadmap).

</domain>

<decisions>
## Implementation Decisions

### Soul Altar — scope, form, behavior

- **D-01:** Pull ARCHITECTURE "Slice 3" forward. The altar is an `EntityBlock` with a
  `SoulAltarBlockEntity` holding a **one-slot inventory** = exactly one Soul Block
  `ItemStack`. The BE persists only that slot (`saveAdditional`/`loadAdditional` +
  `getUpdateTag`/`getUpdatePacket` for the charged render). It stores **nothing** about
  professions, employees, or binding progress. No menu, no `MenuProvider`, no client↔server
  channel this phase.
- **D-02:** Physical form — a single **carved-stone pedestal** (waist-high lectern/enchanting-
  table silhouette) in blackstone or deepslate with soul-fire accents. Non-full-cube model.
  Pickaxe to mine, roughly stone hardness (~3.5, enchanting-table-ish). **Single block** — the
  job-site block sits directly on top in later phases; no multiblock.
- **D-03:** Socketing is **one-way**. Right-click a charged-empty altar while holding a Soul
  Block → the Soul Block is consumed from hand into the BE slot. **No empty-hand retrieval.**
  **No hopper / dropper / automation access** — player interaction only; do NOT expose a
  `Capability`/`IItemHandler`. ("It's a ritual.")
- **D-04:** Altar-break behavior:
  - **Empty altar** (no Soul Block socketed) → breaks normally and **drops itself** (ALTAR-07).
  - **Charged altar** (Soul Block socketed; no employee exists yet) → **no drops at all**
    (the Soul Block is destroyed, and per the user's "no drops" intent the altar block is
    lost too), deals **½ heart (1 dmg)** to the breaking player only — no block or
    environment damage — and spawns a **cosmetic lightning bolt** (visual + thunder, no
    fire, no collateral). The "also instakill the bound employee" half is **deferred to
    Phase 6 (ALTAR-06)** — no employees exist yet.
  - ⚠ **Planner flag:** ALTAR-07 literally only guarantees the *unbound/empty* self-drop.
    Confirm the "charged altar drops nothing, including the block itself" reading against
    ALTAR-06/07 wording during planning; if it feels wrong in-game, the fallback is
    "altar block still drops, only the Soul Block is destroyed."
- **D-05:** Charged-state render — the Soul Block sits **recessed/embedded into the top of
  the altar, emissive (glowing), eye-of-ender style**, with a slow soul-particle wisp. **No
  bob.** Implemented as a client-only `BlockEntityRenderer` (or an emissive baked model +
  particle ticker if the planner prefers — see Discretion). Socket-in and (empty) altar
  interactions play a borrowed vanilla soul sound (e.g. `SoundEvents.SOUL_ESCAPE`) + a small
  soul-particle burst; retrieve has no sound because there is no retrieve.
- **D-06:** Altar recipe — shaped, **mid-cost thematic**, emerald as the "soul-binding" core
  for consistency with the Harvester: blackstone/deepslate frame + soul soil + a skull or
  bone + **1–2 emeralds**. Not trivial (one altar per future employee) but you will craft
  several over a playthrough.

### Harvester — form, combat, feel

- **D-07:** Form is a **scythe** (custom angled item model). Item is a durable, enchantable
  tool: **iron-ish durability (~250 uses)**, accepts enchantments (Unbreaking / Mending
  meaningful; Looting is a no-op since the drop is fixed at 1). **Vanilla sweep attack
  disabled** — single target only. **Left-click only** — no right-click / secondary ability
  this phase.
- **D-08:** **Instakill mechanic, not a damage stat.** A single melee hit on any `Villager`
  kills it **instantly, regardless of health / armor / resistance / absorption** — modeled
  as a soul-reap. Against **all non-villager entities** the Harvester deals **modest ~3–4
  damage** (stone-sword tier attack attribute) — a weak panic weapon, useless for mob farms.
- **D-09:** Target scope for the instakill + guaranteed-Fragment rule — **any
  `net.minecraft.world.entity.npc.Villager`**, matching ECON-02 ("any villager"). This
  includes baby villagers (a soul is a soul). **Wandering traders** (`WanderingTrader`, not
  a `Villager` subclass) and **zombie villagers** (a `Zombie` variant) are **excluded** —
  they take the normal ~3–4 damage. Planner may surface baby-villager inclusion back to the
  user if it feels off.
- **D-10:** Harvest FX + drop — on a Harvester kill of a villager: a **soul burst at the
  body + a few wisps rising and drifting toward the player/scythe**, plus a vanilla soul
  sound (`SOUL_ESCAPE` / enderman-death-ish). **All vanilla particles + sounds — no custom
  `SoundEvent`/`ParticleType` registration this phase.** Drop handling via `LivingDropsEvent`
  (or `LivingDeathEvent`): Harvester kill of a villager → **clear vanilla drops, drop exactly
  1 Soul Fragment**. Any non-Harvester kill of a plain villager → **vanilla loot unchanged,
  no Fragment**.
- **D-11:** Harvester recipe — shaped, scythe silhouette in the grid: **blade = 2–3 emeralds
  (≈3)** + **bone haft** + **soul soil** for the necromantic charge. Deliberate choice:
  emerald (the villagers' own currency) becomes the reaping tool. Obtainable at iron age +
  one Nether trip. Economy tone target: **deliberate but not grindy** — the real cost is
  ECON-03's 4 kills per Soul Block, not the tool.

### Soul Fragment & Soul Block

- **D-12:** Soul Fragment — a normal item (drop + crafting ingredient only) with a **subtle
  enchant-style shimmer/foil** so a dropped Fragment is easy to spot after a harvest. No
  light emission. **Sole source = villager harvest** — no mob drops, no trades.
- **D-13:** Soul Block — a **real placeable block** (`BlockItem`). Recipes: `4 Soul Fragment
  → 1 Soul Block` and `1 Soul Block → 4 Soul Fragment`, both **vanilla shapeless, lossless**
  (ECON-03 locks the forward recipe as shapeless). Placed block has **quiet ambient
  presence**: low light level (~7 suggested), occasional soul-wisp particles;
  buildable/stackable. Needs blockstate + model + a loot table that drops itself.
- **D-14:** Creative tab — **one tab** containing every mod item + block (Harvester, Soul
  Fragment, Soul Block, Soul Altar). Registered via `CreativeModeTab` `DeferredRegister` +
  `BuildCreativeModeTabContentsEvent`. Icon + registry name + ordering are Claude's
  discretion (suggest Soul Block icon, tab title "Second Shift").

### Recipe discovery — custom advancement chain

- **D-15:** Recipes are gated behind a **guided custom advancement chain** (not plain
  `has_item` unlocks). Each advancement's description carries the clue for the next step:
  1. **Emerald enters inventory** → advancement (working name "Necromantic Apprentice") +
     toast → **Harvester recipe unlocked**. Emerald can't gate the bootstrap tool behind
     harvesting, so this is the entry point.
  2. **Harvest a villager** (obtain the first Soul Fragment) → advancement → **Soul Block
     recipe unlocked**; description hints "four make something more".
  3. **Craft a Soul Block** → advancement → **Soul Altar recipe unlocked**; description
     hints "set a job-site block on top".
- **D-16:** Advancement trigger for step 2 ("harvest a villager") — Claude's discretion;
  `inventory_changed` on Soul Fragment (simplest, robust) or `player_killed_entity` with a
  villager + Harvester. POL-04 recipe-unlock advancements still fire the normal toast.
- **D-17:** ⚠ **datagen decision revisited for the planner.** STACK.md §12 currently says
  "hand-write JSON, no datagen for milestone 1". This phase adds ~3 items, 1 block, ~4
  recipes (incl. the reverse), ~4 custom advancements, ~3 recipe-advancement files, loot
  tables, blockstates, models, and a lang file. That is near the "~10 registry objects /
  blockstate variants" threshold where STACK.md §12 says to adopt datagen. **The planner
  decides** hand-written vs. `GatherDataEvent` datagen; `en_us.json` stays hand-written
  regardless.

### Carried forward from Phase 1 — mandatory

- **Delete the D-07 `debug_marker`** item (and its future lang/model stubs) when real
  `ModItems` content lands — `registry/ModItems.java` currently exists only for the
  guardrail test.
- **Wire every new `DeferredRegister`** (`ModItems` real content, `ModBlocks`,
  `ModBlockEntities`, `ModCreativeTab`) in the `SecondShift(IEventBus, ModContainer)`
  constructor **in one visible block** (D-08/D-10 from Phase 1) **and add each to
  `ModRegistrySelfCheck`'s `Stream.of(...)`** (D-10). Update the `commonSetup` log in
  `SecondShift.java` if useful.
- **1.21.1 interaction signatures** (PITFALLS §10): `useItemOn` → `ItemInteractionResult`
  (the item-in-hand socket path); `useWithoutItem` → `InteractionResult` (empty hand — a
  no-op this phase: no retrieval, no menu). Always `@Override` so a wrong signature fails to
  compile.
- **One `./gradlew runServer`** this phase — the charged-altar `BlockEntityRenderer` must be
  client-only (`client/` package + `Dist.CLIENT`), never referenced from common code
  (PITFALLS §9).
- **`./gradlew deployToTest`** produces the jar the user tests in the CurseForge "test"
  instance. `pack_format` stays 48; data folders stay singular (`recipe/`, `loot_table/`,
  `advancement/`) — already correct, don't regress.
- Recipes must be **vanilla crafting types** — never a custom `RecipeType` (FEATURES; the
  altar ritual is not a recipe).

### Claude's Discretion

- **datagen vs. hand-written JSON** for models / blockstates / recipes / advancements / loot
  tables (see D-17).
- Harvester **item class** design (plain `Item` with an attack-damage attributes component
  vs. `SwordItem`/`TieredItem` subclass) and **which event hook** implements the instakill
  (`LivingIncomingDamageEvent` / `AttackEntityEvent` / `LivingDamageEvent.Pre`) vs. the drop
  replacement (`LivingDropsEvent` / `LivingDeathEvent`). Disable the vanilla sweep via the
  chosen item design.
- Exact recipe **grid layouts** and quantities within the stated ranges (Harvester 2–3
  emeralds, altar 1–2 emeralds).
- Exact **vanilla sound IDs and particle types** for socket / harvest / ambient FX; exact
  Soul Block **light level** (~7 suggested).
- **Creative tab** icon, registry id, internal item ordering, and title string.
- **BER implementation** details for the embedded Soul Block render (renderer vs. emissive
  model + particle ticker); altar model geometry; exact block hardness/resistance numbers.
- Whether `SoulAltarBlockEntity` NBT carries a format/version int (cheap future-proofing).
- Advancement working names and the exact clue wording (keep the HR/necromancer tone).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope & requirements (in-repo)
- `.planning/ROADMAP.md` — "Phase 2: Economy Items & Soul Altar Block" — goal, the 4 success
  criteria, requirement list. Also the Phase 3 / 5 / 6 entries for what is deliberately
  deferred (menu, binding, ALTAR-06).
- `.planning/REQUIREMENTS.md` — ECON-01, ECON-02, ECON-03, ALTAR-01, ALTAR-07, POL-01,
  POL-03, POL-04 full text; plus ECON-04 / ALTAR-06 (Phase 6) for the deferred boundary.
- `.planning/PROJECT.md` — "Soul Block (BlockItem)", the emerald/necromancer framing, the
  "enhance vanilla with a twist, not make it easy" philosophy, Key Decisions table.

### Architecture & build order
- `.planning/research/ARCHITECTURE.md` — "Recommended Project Structure" (flat `registry/`
  vs. `content/`, `client/` isolation), the "Soul Altar block entity persists exactly one
  thing" section, and **"Build Order" Slices 1–3** (this phase = Slices 1–3; Slice 4, the
  menu harness, is explicitly Phase 3).
- `.planning/research/STACK.md` §1 (`DeferredRegister` creation + register-in-constructor
  rule), §6 (block entity API: `EntityBlock`, `newBlockEntity`, `loadAdditional`/
  `saveAdditional`, `getUpdateTag`, `setChanged`), §7 (recipes — plain vanilla serializers),
  §11 (Harvester item — `Item.Properties#attributes`, `SwordItem.createAttributes`), **§12
  (datagen "worth it?" — the decision D-17 revisits)**.
- `CLAUDE.md` — mirrors the STACK.md stack tables; "What NOT to Use" (no
  `MenuScreens.register`, no Forge-era APIs, no `[[mixins]]`/AT "just in case", no custom
  `RecipeType`); "API Surface" §1 (registration), §6 (block entity), §7 (recipes), §11
  (Harvester); the GSD Workflow Enforcement + Constraints sections for how Claude runs the
  build.

### Pitfalls landing in this phase
- `.planning/research/PITFALLS.md` §10 — `ItemInteractionResult useItemOn(...)` vs.
  `InteractionResult useWithoutItem(...)` in 1.21.1 (**"Phase to address: Soul Altar block
  phase"**).
- `.planning/research/PITFALLS.md` §1 — the unbound-`DeferredRegister` self-check; every new
  register class must be added to the `Stream.of(...)` in `ModRegistrySelfCheck`.
- `.planning/research/PITFALLS.md` §9 — client classes leaking into common code (the BER);
  one `runServer` per phase.
- `.planning/research/PITFALLS.md` "Looks Done But Isn't" checklist — every custom block
  needs blockstate JSON + block model + item model + **loot table**; singular 1.21 data
  folders; `en_us.json` completeness (launch in a non-English locale to catch missing keys);
  `neoforge.mods.toml` at `META-INF/`.
- `.planning/research/PITFALLS.md` "Pitfall-to-Phase Mapping" rows for **"Recipes (smithing
  vs crafting)"** (`SmithingTransformRecipe` needs 3 ingredients — use a crafting-table
  recipe for the Harvester) and **"Assets / lang / loot / pack_format"** (re-checked every
  phase).

### Feature intent
- `.planning/research/FEATURES.md` — the economy-items table (Harvester → 1 guaranteed Soul
  Fragment; 4 Fragments → Soul Block; Soul Altar block), the "table stakes" rows (creative
  tab, complete `en_us.json`, models/textures, recipes visible in JEI, recipe advancements),
  the "Souls as the price of choice" framing, and the Malum scythe precedent.

### Prior phase context
- `.planning/phases/01-skeleton-feedback-loop/01-CONTEXT.md` — **D-07** (debug marker,
  delete this phase), **D-10** (hand-maintained self-check register list — extend it),
  **D-01/D-04** (`deployToTest` is on-demand, fails loud), the `client/` package convention.
- `.planning/phases/01-skeleton-feedback-loop/01-CONTEXT.md` Deferred Ideas — the
  **descriptionId / lang-key resolution self-check** ("belongs where real translated content
  exists — Phase 2 onward"): a candidate to add now, planner's call.

### External (not in-repo)
- `https://docs.neoforged.net/docs/1.21.1/` — versioned NeoForge docs: blockentities,
  resources/client (models/blockstates), resources/server/recipes, resources/server/loot
  tables, `RegisterEvent` / creative tabs.
- `https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle` — the authoritative 1.21.1 MDK
  (already bootstrapped in Phase 1; reference for datagen wiring if D-17 adopts it).

</canonical_refs>

<code_context>
## Existing Code Insights

Phase 1 shipped a minimal but real skeleton. All new Phase 2 code hangs off it.

### Reusable Assets
- `com/cxmxrgo/secondshift/SecondShift.java` — the `@Mod` entrypoint. Its constructor
  `SecondShift(IEventBus modBus, ModContainer container)` is the **single wiring point** for
  every `DeferredRegister`. `commonSetup` (an `FMLCommonSetupEvent` listener) currently just
  logs registered items — extend or leave.
- `com/cxmxrgo/secondshift/registry/ModItems.java` — `DeferredRegister.Items ITEMS` already
  created for `MODID`. Currently holds only `DEBUG_MARKER` (D-07 throwaway — **delete it**).
  Add the real Harvester / Soul Fragment / Soul Block(Item) here.
- `com/cxmxrgo/secondshift/ModRegistrySelfCheck.java` — `@EventBusSubscriber` +
  `FMLLoadCompleteEvent` handler that hard-aborts on any unbound entry. The `Stream.of(...)`
  at line ~43 lists the registers to check — **every new register class must be added here.**
- `com/cxmxrgo/secondshift/client/ClientModBusEvents.java` — the established client-only
  bus-subscriber pattern (`@EventBusSubscriber(..., value = Dist.CLIENT)`). The charged-altar
  BER / renderer registration (`EntityRenderersEvent.RegisterRenderers` or
  `RegisterMenuScreensEvent` sibling) belongs in a class like this, not in common code.
- `src/main/resources/assets/secondshift/lang/en_us.json` — currently `{}`. Must be filled.
- `src/main/resources/pack.mcmeta` — `pack_format` 48; `data/secondshift/{recipe,loot_table,
  advancement}/` singular folders already stubbed with `.gitkeep`.
- `src/main/templates/META-INF/neoforge.mods.toml` — template with `${mod_id}` etc.; no
  changes expected unless a new dependency is declared (none needed).

### Established Patterns
- **Flat `registry/` holders, behavior in `content/`** — `ModBlocks`, `ModBlockEntities`,
  `ModCreativeTab` are pure `DeferredHolder` constants; `SoulAltarBlock` /
  `SoulAltarBlockEntity` / `HarvesterItem` live under `content/`.
- **One visible `.register(modBus)` block** in the `SecondShift` constructor (Phase 1
  D-08/D-10) — structural prevention of the unbound-registry crash.
- **Hand-maintained self-check stream** — no reflection; explicit `Stream.of(REGISTER_A,
  REGISTER_B, ...)`.
- **`client/` package = physical-client-only**; never imported from common.
- `@EventBusSubscriber` `bus` attribute is documentation-only in NeoForge 21.1 (PITFALLS §2)
  — FML derives the bus from the event type; keep the explicit `Bus.` for parity.

### Integration Points
- `SecondShift` constructor — attach `ModItems.ITEMS`, `ModBlocks.BLOCKS`,
  `ModBlockEntities.BLOCK_ENTITIES`, `ModCreativeTab.TABS`; add game-bus event handler
  registration for the Harvester instakill + `LivingDropsEvent` (or use
  `@EventBusSubscriber(bus = GAME)` classes under `content/`/`employee/event/`).
- `ModRegistrySelfCheck` `Stream.of(...)` — add every new register.
- `./gradlew deployToTest` (`gradle.properties` `test_instance_mods_dir`) — unchanged seam
  for in-game verification.
- Game-bus `BuildCreativeModeTabContentsEvent` — populate the creative tab.

</code_context>

<specifics>
## Specific Ideas

- **Eye-of-ender framing** for the Soul Block ↔ altar: it "clicks in" and stays, embedded
  and glowing in the altar like an eye of ender in an end portal frame. One-way, committed.
- **Emerald as the through-line** — the villagers' own trade currency is what you craft both
  the reaping scythe and the binding altar from. Deliberate irony, and it visually links the
  two recipes.
- **Instakill, not a big number** — the Harvester one-shots villagers because it takes the
  soul, not because it hits hard. It should be a *bad* general weapon.
- **The mod enhances vanilla with a twist; it is not meant to make things easy.** This is
  why socketing is one-way and why breaking a charged altar costs you everything with a
  half-heart and a lightning bolt.
- **Discoverability matters to the user** — hence the guided advancement chain with clue
  text. They also want in-world hints (paintings, village clues) — captured as a deferred
  idea, not built here.
- Harvest should feel good every time: soul burst + wisps rising toward you, a satisfying
  vanilla soul sound. Not a giant noisy column (you will harvest several in a row).

</specifics>

<deferred>
## Deferred Ideas

- **Custom paintings + in-village discovery hints** explaining how the mod works — user
  wants this for intuitiveness (paintings with tips/clues, hints placed in village
  structures). This is a **new player-facing discovery/lore capability** not in the Phase 2
  scope or anywhere on the roadmap: needs a custom painting registry + textures + village
  worldgen injection (jigsaw / structure processors). **FLAG FOR ROADMAP** — candidate for
  its own small phase or a bolt-on to Phase 10 (Polish).
- **Harvester right-click "inspect villager"** (preview a villager's profession / biome type
  before committing a soul) — considered, deferred. Revisit if scouting-before-bind proves
  annoying once binding exists (Phase 5+).
- **descriptionId / lang-key resolution self-check** (carried from Phase 1 Deferred Ideas) —
  extend the startup guardrail to assert every registered object's `descriptionId` resolves
  to an `en_us.json` key. Phase 2 is the first phase with real translated content, so it
  could start here; planner's call, otherwise Phase 10.
- **Bespoke `SoundEvent` / `ParticleType` registration** for harvest / socket / ambient FX —
  this phase borrows vanilla soul sounds/particles. Custom audio/particles are Phase 10
  (POL-05) / v1.x (PRES-02).
- **Datagen adoption** — if the planner keeps hand-written JSON for Phase 2, revisit at
  Phase 3–5 when blockstate variants and registry count grow (also flagged in Phase 1).

### Reviewed Todos (not folded)
None — no pending todos matched this phase.

</deferred>

---

*Phase: 2-economy-items-soul-altar-block*
*Context gathered: 2026-09-04*
