# Requirements: Second Shift

**Defined:** 2026-09-04
**Core Value:** Harvest souls → bind a villager at the Soul Altar → hand-pick its profession and its trades, tier by tier. That loop must be reliable and feel good.

## v1 Requirements

Requirements for the first shippable version. Each maps to exactly one roadmap phase.

### Build & Feedback Loop

- [x] **BUILD-01**: Mod compiles against NeoForge 21.1.248 / Minecraft 1.21.1 (Java 21) via the Gradle wrapper; `./gradlew build` produces a loadable jar
- [x] **BUILD-02**: `./gradlew runClient` reaches the main menu with the mod loaded; `./gradlew runServer` starts clean
- [x] **BUILD-03**: A startup self-check aborts loading with a named list of any unbound DeferredRegister entry (guards the registration bug that killed the prior draft)
- [x] **BUILD-04**: The built jar loads in the CurseForge "test" instance alongside owo-lib / accessories / wildcard without crashing
- [x] **BUILD-05**: Each build is deployed into the test instance's `mods/` folder (replacing the previous build) so the user can test in-game

### Economy

- [x] **ECON-01**: The Harvester is a craftable tool/weapon
- [x] **ECON-02**: Killing any villager with the Harvester drops exactly 1 Soul Fragment, guaranteed; no other vanilla drops are changed
- [x] **ECON-03**: 4 Soul Fragments craft into 1 Soul Block (vanilla shapeless recipe)
- [ ] **ECON-04**: Killing your own employee with the Harvester yields 1 Soul Fragment only (no Soul Block, no bonus)

### Altar & Binding

- [x] **ALTAR-01**: The Soul Altar is a craftable block backed by a block entity
- [ ] **ALTAR-02**: Placing a vanilla job-site block on top of the altar sets the target profession for binding
- [x] **ALTAR-03**: Inserting a Soul Block into an altar that has a job block on top opens the binding GUI
- [ ] **ALTAR-04**: Completing a bind consumes the Soul Block and the job-site block into the altar and spawns the employee
- [ ] **ALTAR-05**: Each altar is bound to exactly one employee and persists that link across save/load
- [ ] **ALTAR-06**: Destroying a bound altar consumes (does not drop) the Soul Block and job block, deals 1 damage to the breaking player only with no block or environment damage, and 0.5s later a cosmetic lightning strike (visual + thunder, no fire, no collateral damage) instakills the bound employee
- [x] **ALTAR-07**: An unbound altar breaks normally and drops itself

### Employee

- [x] **EMP-01**: An employee is a `minecraft:villager` carrying a serialized, client-synced `EmployeeData` attachment (name, profession, tier, chosen offers, happiness, timers) — no custom entity type
- [ ] **EMP-02**: Employees spawn with villager XP ≥ 1 so vanilla never resets their profession to unemployed
- [ ] **EMP-03**: Employees cannot be converted into zombie villagers
- [ ] **EMP-04**: Employees cannot be converted by lightning into a witch
- [ ] **EMP-05**: Employees cannot breed and never produce baby villagers
- [ ] **EMP-06**: An employee killed by anything other than the Harvester or altar-destruction drops its Soul Block plus slimeballs
- [ ] **EMP-07**: Employees stay within a bound area around their altar and never wander off
- [ ] **EMP-08**: Employees are visually distinguishable from wild villagers (always-visible custom name at minimum)
- [ ] **EMP-09**: Villagers without the `EmployeeData` attachment behave exactly as vanilla — zero side effects

### Quarters & Happiness

- [ ] **HAPP-01**: An employee needs "quarters" tied to its altar: an enclosed space of at least 3×3 containing a door
- [ ] **HAPP-02**: An employee needs access to a nearby chest stocked with food it can draw from
- [ ] **HAPP-03**: Employee happiness is a discrete state — Unhappy / OK / Happy — derived from quarters validity and food availability
- [ ] **HAPP-04**: Happy employees sell at reduced emerald prices; OK employees at vanilla prices; Unhappy employees above vanilla prices
- [ ] **HAPP-05**: Unhappy employees restock slowly or not at all
- [ ] **HAPP-06**: An employee left Unhappy for a sustained period quits: it drops its Soul Block, reverts to an ordinary unbound villager, and releases its altar
- [ ] **HAPP-07**: Current happiness state and its cause are shown in the altar GUI

### Trades & Picker

- [ ] **PICK-01**: The target profession is derived from the job-site block via the POI registry at runtime — no hardcoded list; modded professions supported
- [ ] **PICK-02**: Trade candidates for a tier are the profession's real vanilla `ItemListing` pool for that tier, materialized into concrete `MerchantOffer`s server-side
- [ ] **PICK-03**: The picker shows all N candidates for the tier; the player selects exactly 2 (vanilla's per-tier count)
- [ ] **PICK-04**: When a tier's pool has ≤ 2 trades, they are auto-selected and still displayed in the picker
- [ ] **PICK-05**: For librarian employees, each tier's candidate list includes exactly one freshly-rolled enchanted-book trade; enchantments are not separately enumerated
- [ ] **PICK-06**: Chosen trades are persisted as concrete offers on the attachment and installed via `setOffers`; profession is always set before offers
- [ ] **PICK-07**: The binding GUI has a name field pre-filled with a generated default; the final value is applied as the employee's visible custom name
- [ ] **PICK-08**: A job block that maps to no profession, or a profession with an empty tier pool, is handled with a themed message and no crash

### Progression

- [ ] **PROG-01**: Employees earn vanilla trading XP normally; their tier advances at vanilla thresholds (0 / 10 / 70 / 150 / 250)
- [ ] **PROG-02**: Vanilla's automatic trade generation on level-up is suppressed/reverted — the player never sees trades they did not choose
- [ ] **PROG-03**: Reaching a new tier produces an unmissable "ready for promotion" signal
- [ ] **PROG-04**: Right-clicking the altar while a promotable employee is bound opens the picker for the new tier; confirming installs the chosen trades

### Restock

- [ ] **STOCK-01**: Employees restock trades on a mod-owned real-time timer, independent of POI, work schedule, day/night, and dimension
- [ ] **STOCK-02**: The restock interval is configurable
- [ ] **STOCK-03**: Restock logic never touches villagers without the attachment
- [ ] **STOCK-04**: Restock is slowed or paused while the employee is Unhappy

### GUI / Menu

- [x] **GUI-01**: The altar `MenuType` + `Screen` are registered correctly and an (initially empty) altar screen opens under `runClient` without crashing — proven before any trade logic is built
- [ ] **GUI-02**: The menu is server-authoritative; client-sent selections (trade indices, name) are re-validated server-side against the server's own candidate list and altar proximity
- [ ] **GUI-03**: The altar GUI shows the bound employee's name, profession, current tier, already-chosen trades, and happiness state

### Polish

- [x] **POL-01**: A creative mode tab contains every mod item and block
- [ ] **POL-02**: Complete `en_us.json` covering every item, block, GUI title, button, tooltip, chat message, config entry, and advancement
- [x] **POL-03**: Every item and block has a model and texture (placeholder quality acceptable; missing is not)
- [x] **POL-04**: Crafting recipes use vanilla recipe types (appear in JEI/EMI automatically) and emit recipe-unlock advancements
- [ ] **POL-05**: Harvest, bind, and promotion each have sound + particle feedback
- [ ] **POL-06**: A `ModConfigSpec` + config screen exposes at least: Soul Fragment drop count, restock interval, happiness thresholds, and each trait-immunity toggle
- [ ] **POL-07**: HR flavour — per-tier job titles (e.g. Intern → Associate → Senior → Lead → Principal) surfaced in the employee name and/or altar GUI
- [ ] **POL-08**: Every invalid state has a themed `Component.translatable` message: no job block, no Soul Block, unmapped job block, empty pool, altar with no valid quarters
- [x] **POL-09**: `pack.mcmeta` uses `pack_format` 48; datapack files use the singular 1.21 folder names (`recipe/`, `loot_table/`, `advancement/`)

## v2 Requirements

Deferred to a future release. Tracked, not in the current roadmap.

### Planning & Roster

- **ROST-01**: Read-only 5-tier career-path preview shown at bind time
- **ROST-02**: Employee roster / directory listing every employee, tier, and promotable status

### Presentation

- **PRES-01**: Custom render layer for employees (soul overlay / HR badge)
- **PRES-02**: Custom registered `SoundEvent`s in place of vanilla borrows
- **PRES-03**: Advancement tree for the necromancy progression

### Documentation

- **DOC-01**: Patchouli in-game guide book
- **DOC-02**: JEI/EMI plugin describing the altar ritual

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Free-form / arbitrary trade editor | The vanilla pool is the balance constraint that makes the mod interesting |
| Custom villager entity type | Breaks iron golems, entity tags, raids, and other villager mods; attachment approach preserves all of it |
| Enumerating every enchantment for book trades | Makes guaranteed Mending a 4-soul purchase; collapses enchanting and defeats the soul cost |
| Trade rerolling / cycling | Directly contradicts choose-and-commit; if you can reroll, choosing and the soul cost mean nothing |
| Auto-trading / hopper automation | Different genre (factory automation); destroys the "these are people who work for you" fiction |
| Employee wages / upkeep beyond the happiness system | Happiness is the one upkeep mechanic; anything more becomes a nag system |
| Public release / Modrinth / CurseForge publishing | Personal project; no release pipeline |
| Multiplayer / server hardening (ownership ACLs, anti-dupe) | Single-player target; do the minimum-correct client/server split only |
| Full villager AI overhaul (schedules, guard duty, patrols) | Enormous brain/behaviour surface; the architecture constraint says hook vanilla, don't fork it |
| Suppressing iron-golem spawning from employees | Free compatibility and a fun emergent detail; leave vanilla |

## Traceability

Which phases cover which requirements. Populated during roadmap creation (2026-09-04).

| Requirement | Phase | Status |
|-------------|-------|--------|
| BUILD-01 | Phase 1 | Complete |
| BUILD-02 | Phase 1 | Complete |
| BUILD-03 | Phase 1 | Complete |
| BUILD-04 | Phase 1 | Complete |
| BUILD-05 | Phase 1 | Complete |
| POL-09 | Phase 1 | Complete |
| ECON-01 | Phase 2 | Complete |
| ECON-02 | Phase 2 | Complete |
| ECON-03 | Phase 2 | Complete |
| ALTAR-01 | Phase 2 | Complete |
| ALTAR-07 | Phase 2 | Complete |
| POL-01 | Phase 2 | Complete |
| POL-03 | Phase 2 | Complete |
| POL-04 | Phase 2 | Complete |
| GUI-01 | Phase 3 | Complete |
| ALTAR-03 | Phase 3 | Complete |
| EMP-01 | Phase 4 | Complete |
| EMP-02 | Phase 4 | Pending |
| EMP-08 | Phase 4 | Pending |
| EMP-09 | Phase 4 | Pending |
| ALTAR-02 | Phase 5 | Pending |
| ALTAR-04 | Phase 5 | Pending |
| ALTAR-05 | Phase 5 | Pending |
| PICK-01 | Phase 5 | Pending |
| PICK-02 | Phase 5 | Pending |
| PICK-03 | Phase 5 | Pending |
| PICK-04 | Phase 5 | Pending |
| PICK-05 | Phase 5 | Pending |
| PICK-06 | Phase 5 | Pending |
| PICK-07 | Phase 5 | Pending |
| PICK-08 | Phase 5 | Pending |
| GUI-02 | Phase 5 | Pending |
| GUI-03 | Phase 5 | Pending |
| EMP-03 | Phase 6 | Pending |
| EMP-04 | Phase 6 | Pending |
| EMP-05 | Phase 6 | Pending |
| EMP-06 | Phase 6 | Pending |
| EMP-07 | Phase 6 | Pending |
| ECON-04 | Phase 6 | Pending |
| ALTAR-06 | Phase 6 | Pending |
| PROG-01 | Phase 7 | Pending |
| PROG-02 | Phase 7 | Pending |
| PROG-03 | Phase 7 | Pending |
| PROG-04 | Phase 7 | Pending |
| STOCK-01 | Phase 8 | Pending |
| STOCK-02 | Phase 8 | Pending |
| STOCK-03 | Phase 8 | Pending |
| HAPP-01 | Phase 9 | Pending |
| HAPP-02 | Phase 9 | Pending |
| HAPP-03 | Phase 9 | Pending |
| HAPP-04 | Phase 9 | Pending |
| HAPP-05 | Phase 9 | Pending |
| HAPP-06 | Phase 9 | Pending |
| HAPP-07 | Phase 9 | Pending |
| STOCK-04 | Phase 9 | Pending |
| POL-02 | Phase 10 | Pending |
| POL-05 | Phase 10 | Pending |
| POL-06 | Phase 10 | Pending |
| POL-07 | Phase 10 | Pending |
| POL-08 | Phase 10 | Pending |

**Coverage:**

- v1 requirements: 60 total (the earlier "52" summary count was stale — the enumerated list above has always had 60)
- Mapped to phases: 60
- Unmapped: 0 ✓

---
*Requirements defined: 2026-09-04*
*Last updated: 2026-09-04 after roadmap creation — traceability populated, coverage 60/60*
