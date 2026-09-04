# Roadmap: Second Shift

## Overview

Second Shift reskins vanilla villager trading as necromancy: harvest souls, bind villagers into
named "employees" at a Soul Altar, and hand-pick each employee's profession and trades tier by tier.
The prior draft died on a menu type that was never registered and a build pipeline that could not
detect it, so this roadmap fixes the feedback loop first (Phase 1), builds a zero-GUI economy slice
(Phase 2), then isolates the known failure area as a hard gate — an empty altar screen that opens
under `runClient` before any trade logic exists (Phase 3). From there the employee attachment lands
(Phase 4) as the prerequisite for everything else, the core "choose profession and trades" loop is
delivered (Phase 5), and the independent workstreams follow: traits/death/firing (Phase 6),
progression and the promotion ritual (Phase 7), mod-owned restock (Phase 8), the quarters/happiness
upkeep system (Phase 9), and a final polish/config/invalid-state sweep (Phase 10). Every phase ends
in a jar the user can load in the CurseForge "test" instance and verify in-game.

## Phases

- [x] **Phase 1: Skeleton & Feedback Loop** - Pinned NeoForge build that launches and catches its own registration bugs (completed 2026-09-04)
- [x] **Phase 2: Economy Items & Soul Altar Block** - Harvester, Soul Fragment/Block, and the Soul Altar block exist and behave (completed 2026-09-04)
- [x] **Phase 3: Menu & Screen Harness (HARD GATE)** - Empty "Binding Altar" screen opens under runClient with no crash (completed 2026-09-04)
- [ ] **Phase 4: Employee Attachment & Spawn** - Binding spawns a persistent, named employee villager with synced EmployeeData
- [ ] **Phase 5: Profession Resolution & Trade Picker** - Hand-pick an employee's profession and tier-1 trades from the real vanilla pool
- [ ] **Phase 6: Employee Traits, Death & Firing** - Employees are conversion/breed-immune, recoverable on death, removable only via altar destruction
- [ ] **Phase 7: Progression & Promotion Ritual** - Vanilla XP unlocks tiers; the player picks each tier's trades at the altar, never seeing unchosen trades
- [ ] **Phase 8: Mod-Owned Restock** - Employee trades restock on a POI-independent timer
- [ ] **Phase 9: Quarters & Happiness** - Employees need quarters + food; happiness modulates prices/restock and neglect makes them quit
- [ ] **Phase 10: Polish, Config & Invalid States** - Every surface translated, themed, configurable, and failing gracefully

## Phase Details

### Phase 1: Skeleton & Feedback Loop

**Goal**: A pinned NeoForge 1.21.1 skeleton that builds, launches on both sides, deploys itself into the test instance, and aborts loudly on any unbound registry entry.
**Mode**: mvp
**Depends on**: Nothing (first phase)
**Requirements**: BUILD-01, BUILD-02, BUILD-03, BUILD-04, BUILD-05, POL-09
**Success Criteria** (what must be TRUE):

  1. `./gradlew build` produces a jar; dropped into the CurseForge "test" instance it loads to the main menu with "Second Shift" listed in Mods, alongside owo-lib / accessories / wildcard, no crash.
  2. `./gradlew runClient` reaches the main menu and `./gradlew runServer` reaches "Done", both with the mod loaded.
  3. Temporarily un-registering a `DeferredRegister` makes startup abort with a named list of the unbound entries instead of a later cryptic crash.
  4. Each build lands in the test instance's `mods/` folder replacing the prior jar, with no duplicate-modid failure; `pack.mcmeta` is `pack_format` 48 and data folders use singular 1.21 names.

**Plans**: 2 plans

- [x] 01-01-PLAN.md — Pinned NeoForge 1.21.1 / MDG 2.0.146 skeleton: MDK bootstrap, `@Mod` entrypoint, first real `DeferredRegister` + `debug_marker`, `FMLLoadCompleteEvent` unbound-registry self-check, `runClient`/`runServer` green, `pack.mcmeta` format 48
- [x] 01-02-PLAN.md — On-demand `deployToTest` deploy loop into the CurseForge "test" instance (glob-replace, fail-loud), legacy dashed-jar cleanup, `run/mods` FML-scan check, in-instance load verification

### Phase 2: Economy Items & Soul Altar Block

**Goal**: The soul economy items and the Soul Altar block exist, craft with vanilla recipes, and behave — with zero GUI risk.
**Mode**: mvp
**Depends on**: Phase 1
**Requirements**: ECON-01, ECON-02, ECON-03, ALTAR-01, ALTAR-07, POL-01, POL-03, POL-04
**Success Criteria** (what must be TRUE):

  1. All mod items and the Soul Altar appear in a single creative tab, each with a real (non-missing) texture and model.
  2. Crafting a Harvester, and crafting 4 Soul Fragments into a Soul Block, both work at a vanilla crafting table, show in JEI/EMI, and fire a recipe-unlock toast.
  3. Killing any villager with the Harvester drops exactly 1 Soul Fragment every time; killing one with a sword drops none and normal loot is unchanged.
  4. A placed Soul Altar can be broken and drops itself; it renders as a real block, not a missing-texture cube.

**Plans**: 5 plans

- [x] 02-01-PLAN.md — Wave 0 foundation: all 4 DeferredRegisters (items/blocks/BE type/creative tab) + content skeleton classes, delete debug_marker, one-block wiring, self-check extended to 4 registers + a new descriptionId/lang-key check, complete en_us.json, ECON-02 GameTest scaffold (RED)
- [x] 02-02-PLAN.md — Slice: Harvester reaps villager souls — game-bus LivingDamageEvent.Pre instakill + LivingDropsEvent exactly-1-Fragment swap (turns the ECON-02 GameTests GREEN), Resistance-V spike, Harvester + Soul Fragment models/textures/foil
- [x] 02-03-PLAN.md — Slice: soul economy crafting + discovery chain — Soul Block block assets + loot table, Harvester (shaped) + Soul Block ⇄ 4 Fragment (shapeless) recipes, hand-written advancement chain steps 1-2 gating the Harvester and Soul Block recipes (D-15/D-17)
- [x] 02-04-PLAN.md — Slice: Soul Altar block — useItemOn one-way socket + BE sync, D-04 break behaviour (empty drops self; charged drops nothing + half-heart + cosmetic lightning), altar model/blockstate/recipe + "Soul Mason" advancement (step 3)
- [x] 02-05-PLAN.md — Slice: charged-altar render — client-only BlockEntityRenderer (embedded emissive Soul Block + wisp, no bob) registered from EntityRenderersEvent.RegisterRenderers, plus the phase's mandatory `./gradlew runServer` client-class-leak gate

### Phase 3: Menu & Screen Harness (HARD GATE)

**Goal**: The altar opens a correctly-registered, empty "Binding Altar" screen under `runClient` with no crash — structurally preventing the unbound-`MenuType` failure that killed the prior draft. No trade logic in this phase.
**Mode**: mvp
**Depends on**: Phase 2
**Requirements**: GUI-01, ALTAR-03
**Success Criteria** (what must be TRUE):

  1. `./gradlew runClient` → placing a job-site block on a Soul Altar and inserting a Soul Block opens an empty screen titled "Binding Altar" with no crash.
  2. `./gradlew runServer` loads with no client-class error; the `FMLCommonSetupEvent` guardrail logs a real `secondshift:binding_altar` menu key.
  3. Right-clicking the altar with no job block or no Soul Block does nothing harmful — no crash, screen does not open.
  4. A malformed or out-of-range menu interaction sent by hand is rejected server-side without a crash (`stillValid` re-checks the altar and proximity).

**Plans**: 2 plans

- [x] 03-01-PLAN.md — Menu/screen registration harness: ModMenus registry, BindingAltarMenu (ctors + stillValid + quickMoveStack), SoulSlot + AltarSoulContainer, BindingAltarScreen, RegisterMenuScreensEvent binding, SoulAltarBlockEntity as MenuProvider, D-14/D-15 guardrail extension, GUI texture
- [x] 03-02-PLAN.md — Open-trigger wiring: ProfessionResolver (POI -> profession, no hardcoded list), SoulAltarBlock D-01/D-02/D-04/D-11 interaction logic, D-12 forced-close messaging on stillValid, BindingAltarGameTests (SC4)

### Phase 4: Employee Attachment & Spawn

**Goal**: Completing a (temporary, fixed-profession) bind spawns a persistent, named `minecraft:villager` carrying a serialized and client-synced `EmployeeData` attachment, with zero effect on wild villagers.
**Mode**: mvp
**Depends on**: Phase 3
**Requirements**: EMP-01, EMP-02, EMP-08, EMP-09
**Success Criteria** (what must be TRUE):

  1. Confirming the bind spawns a `minecraft:villager` with an always-visible custom name near the altar, with villager XP >= 1.
  2. `/data get entity <uuid>` shows the `EmployeeData` attachment; after save-quit-reload and after a 300-block round trip, the attachment and name survive.
  3. The employee's attachment data is readable client-side (name tag / GUI), confirming sync fires for entities.
  4. A wild villager in the same world is completely unaffected — trades, profession resets, breeding, and AI all vanilla.

**Plans**: 4 plans

- [ ] 04-01-PLAN.md — EmployeeData record/CODEC/STREAM_CODEC, ModAttachments registration + self-check guardrail (6th register), client-side attachment-sync diagnostic (LIGHT spike verification mechanism)
- [ ] 04-02-PLAN.md — EmployeeManager.bind (fixed-profession spawn, EMP-02 XP floor, D-04 green always-visible name) + EmployeeNames pool + EmployeeGameTests (EMP-01/02/08/09 automated proof)
- [ ] 04-03-PLAN.md — BindEmployeePayload + ServerPayloadHandler (server-derived altar pos, atomic Soul Block slot consume) + temporary Confirm Hire button in BindingAltarScreen
- [ ] 04-04-PLAN.md — Mandatory manual verification checkpoint: persistence round-trip, client-side sync (LIGHT spike empirical answer), Name Tag rename (D-02), wild-villager regression

**Risks**: LIGHT spike — verify `AttachmentType.Builder#sync` actually fires for entities in 21.1.248 (docs contradict the API); fallback is a manual clientbound payload. Add an `EmployeeData` version field before first use.

### Phase 5: Profession Resolution & Trade Picker

**Goal**: The core value — at the altar, funded by harvested souls, the player picks an employee's profession (from the job block) and its tier-1 trades from that profession's real vanilla pool, and the employee spawns offering exactly those trades.
**Mode**: mvp
**Depends on**: Phase 4
**Requirements**: ALTAR-02, ALTAR-04, ALTAR-05, PICK-01, PICK-02, PICK-03, PICK-04, PICK-05, PICK-06, PICK-07, PICK-08, GUI-02, GUI-03
**Success Criteria** (what must be TRUE):

  1. Placing a lectern on the altar and inserting a Soul Block opens "Binding Altar" showing "Librarian" and the real librarian-novice pool as materialized offers with concrete items and prices (including exactly one freshly-rolled enchanted-book offer).
  2. The player edits the pre-filled name, selects exactly 2 offers (or all of them, auto-locked, when the pool is <= 2) and confirms; the Soul Block and the lectern are consumed and a named librarian employee spawns offering exactly those trades at the shown prices.
  3. Trading with the new employee shows only the chosen offers — never vanilla's lazy 2-trade fabrication — and the altar GUI shows the employee's name, profession, tier, and chosen trades.
  4. A job block that maps to no profession, or a profession with an empty tier pool, shows a themed message and never crashes; a modded job-site block resolves to its profession.
  5. Each altar holds exactly one employee — a second bind is refused — and the altar/employee link survives save/load.

**Plans**: TBD
**UI hint**: yes
**Risks**: Spike `ItemListing.getOffer(realVillager, random)` for side effects and null returns across every profession x tier — write it as a GameTest asserting no exception, no null leak, and preview offer == committed offer. Server must re-validate chosen indices and altar proximity against its own candidate list — never trust the client payload. Enforce bind order: `setVillagerData` -> `refreshBrain` -> `setVillagerXp` -> `setOffers` last.

### Phase 6: Employee Traits, Death & Firing

**Goal**: Employees are protected, non-fungible characters — immune to zombie/witch conversion and breeding, kept near their altar, recoverable via the Harvester, drop-recoverable on other deaths, and removable only by destroying the altar.
**Mode**: mvp
**Depends on**: Phase 4 (parallelizable with Phases 5, 7, 8)
**Requirements**: EMP-03, EMP-04, EMP-05, EMP-06, EMP-07, ECON-04, ALTAR-06
**Success Criteria** (what must be TRUE):

  1. A zombie killing an employee never produces a zombie villager; a lightning strike never turns an employee into a witch.
  2. Two employees with a bed and bread nearby for 5+ minutes produce no baby villager.
  3. An employee killed by lava or a mob drops its Soul Block plus slimeballs; an employee killed by the Harvester drops exactly 1 Soul Fragment (clean recovery — no Soul Block, no bonus).
  4. Employees stay within a bounded area around their altar and do not wander off.
  5. Breaking a bound altar consumes (does not drop) the Soul Block and job block, deals half a heart to the breaking player only with no block or environment damage, and ~0.5s later a cosmetic lightning strike (no fire, no collateral) instakills the bound employee.

**Plans**: TBD
**Risks**: LOW-confidence spike — breeding suppression has no verified hook (`BabyEntitySpawnEvent` is dead code for villagers). Budget generously: try precondition-breaking (clear `BREED_TARGET`, no bed/`HOME`, no food) first, `FinalizeSpawnEvent` filtered on `BREEDING` second, a documented targeted AT/Mixin only as a last resort. "Keep employee near altar" also has no pre-researched hook — minor spike; avoid faking a `JOB_SITE` memory. `ResetProfession` immunity (XP >= 1) verified here with a walk-away + relog test.

### Phase 7: Progression & Promotion Ritual

**Goal**: Employees level through normal vanilla trading XP, and the player hand-picks each new tier's trades at the altar — never seeing a trade they did not choose.
**Mode**: mvp
**Depends on**: Phase 5
**Requirements**: PROG-01, PROG-02, PROG-03, PROG-04
**Success Criteria** (what must be TRUE):

  1. Trading with an employee to the Apprentice threshold (and each later threshold) produces an unmissable "ready for promotion" signal — particles above the employee plus a chat/actionbar message.
  2. At no point before the ritual does the employee's trade list gain a trade the player did not choose; vanilla's auto-appended tier trades are reverted.
  3. Right-clicking the altar with a promotable employee nearby opens the picker for the new tier, showing existing trades locked and the new tier's pool as "pick 2".
  4. Confirming installs the chosen trades, retains all prior tiers' trades, and advances the employee's tier.

**Plans**: TBD
**UI hint**: yes
**Risks**: MEDIUM-confidence spike — "revert, don't prevent" for vanilla `updateTrades()`. Verify the throttled revert beats vanilla's append with no per-tick offer thrash, no lost restock state, and no one-tick window where unchosen offers are rendered to the player. Do not hold the mechanical level down. Enforce the same bind order as Phase 5.

### Phase 8: Mod-Owned Restock

**Goal**: Employee trades restock on a mod-owned real-time timer so the mod never feels broken after a trading spree, independent of POI, work schedule, day/night, and dimension.
**Mode**: mvp
**Depends on**: Phase 4 (parallelizable with Phases 5, 6, 7)
**Requirements**: STOCK-01, STOCK-02, STOCK-03
**Success Criteria** (what must be TRUE):

  1. Exhausting an employee's trade and waiting the configured interval unlocks it again, regardless of time of day, dimension, or whether the employee can reach any POI.
  2. The restock interval is exposed as a config value and changing it takes effect.
  3. A wild (non-employee) villager nearby is never touched by the restock logic, and offers are never mutated while the employee is being traded with.
  4. An employee that was unloaded for a long time restocks at most once on reload — no burst.

**Plans**: TBD

### Phase 9: Quarters & Happiness

**Goal**: Employees need quarters and a stocked food chest tied to their altar; a discrete Unhappy/OK/Happy state modulates emerald prices and restock speed, and sustained neglect makes an employee quit.
**Mode**: mvp
**Depends on**: Phase 8, Phase 5
**Requirements**: HAPP-01, HAPP-02, HAPP-03, HAPP-04, HAPP-05, HAPP-06, HAPP-07, STOCK-04
**Success Criteria** (what must be TRUE):

  1. Giving an employee valid quarters (an enclosed space at least 3x3 containing a door) tied to its altar plus a nearby chest stocked with food it can draw from moves it to Happy; removing the door or emptying the chest moves it toward Unhappy.
  2. The altar GUI shows the current happiness tier and its cause.
  3. Happy employees sell below vanilla emerald prices, OK at vanilla, Unhappy above vanilla; Unhappy employees restock slowly or not at all.
  4. An employee left Unhappy for a sustained period quits: it drops its Soul Block, reverts to an ordinary unbound villager, and releases its altar — which can then be re-used for a new bind.

**Plans**: TBD
**UI hint**: yes
**Risks**: Largest net-new chunk with the least research coverage (quarters/structure detection, food-chest access, price + restock modulation, quit-and-revert). Structure/enclosure detection and "food it can draw from" both need a design spike during planning. Quitting must cleanly reverse every bind side effect (attachment removal, XP, custom name, altar link).

### Phase 10: Polish, Config & Invalid States

**Goal**: Every player-facing surface is translated, themed, configurable, and fails gracefully.
**Mode**: mvp
**Depends on**: Phases 2-9
**Requirements**: POL-02, POL-05, POL-06, POL-07, POL-08
**Success Criteria** (what must be TRUE):

  1. Launching with a non-English locale shows no raw translation keys anywhere — items, blocks, menu titles, buttons, tooltips, chat messages, config entries, advancements.
  2. Harvest, bind, and promotion each play a sound and spawn particles.
  3. The Mods menu Config button opens a working screen exposing at least Soul Fragment drop count, restock interval, happiness thresholds, and each trait-immunity toggle; changes persist.
  4. Every invalid state (no job block, no Soul Block, unmapped block, empty pool, pool <= 2, altar with no valid quarters) shows a themed `Component.translatable` message and never crashes.
  5. Employee names and/or the altar GUI surface per-tier HR job titles (e.g. Intern -> Associate -> Senior -> Lead -> Principal).

**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10.
Phases 6 and 8 are parallelizable with the 5 → 7 trade chain once Phase 4 lands.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Skeleton & Feedback Loop | 2/2 | Complete    | 2026-09-04 |
| 2. Economy Items & Soul Altar Block | 5/5 | Complete    | 2026-09-04 |
| 3. Menu & Screen Harness (HARD GATE) | 2/2 | Complete   | 2026-09-04 |
| 4. Employee Attachment & Spawn | 0/4 | Not started | - |
| 5. Profession Resolution & Trade Picker | 0/TBD | Not started | - |
| 6. Employee Traits, Death & Firing | 0/TBD | Not started | - |
| 7. Progression & Promotion Ritual | 0/TBD | Not started | - |
| 8. Mod-Owned Restock | 0/TBD | Not started | - |
| 9. Quarters & Happiness | 0/TBD | Not started | - |
| 10. Polish, Config & Invalid States | 0/TBD | Not started | - |
