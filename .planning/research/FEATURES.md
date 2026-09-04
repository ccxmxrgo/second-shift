# Feature Research

**Domain:** Minecraft Java content mod (NeoForge 1.21.1) — villager/trading overhaul with a necromancy theme
**Researched:** 2026-09-04
**Confidence:** MEDIUM-HIGH (vanilla mechanics HIGH; comparable-mod UX MEDIUM; player-expectation claims MEDIUM)

---

## 0. Inherited Vanilla Mechanics (read this first)

Everything below is constrained by how vanilla actually behaves. These facts drive the
table-stakes list, so they are stated up front with confidence levels.

| Fact | Detail | Confidence | Impact on Second Shift |
|------|--------|-----------|------------------------|
| **2 trades per tier** | Each villager level unlocks a **maximum of 2** new trades, drawn randomly *without replacement* from that tier's `ItemListing[]` pool. Max 10 trades total across 5 tiers. | HIGH (Minecraft Wiki, Trading) | The picker is a "**pick 2 of N**" widget, not free-form. |
| **Pool sizes vary 2–5** | Farmer Novice pool = **5** (wheat/potato/carrot/beetroot/bread) → 2 chosen. Librarian pools = 3/3/3/4/2 by tier (wiki lists 67%/67%/67%/50%/100% selection chance, which is exactly 2÷poolSize). | MEDIUM-HIGH (Wiki Farmer/Librarian pages; two independent sources for Farmer) | Picker must degrade gracefully when `pool.size() <= 2` (no meaningful choice — just show and confirm) and handle larger modded pools. |
| **XP thresholds** | Novice 0 → Apprentice 10 → Journeyman 70 → Expert 150 → Master 250. Each trade grants 3–6 XP, +5 bonus on level-up. | HIGH (Minecraft Wiki) | Determines how long the "come back to the altar" loop takes. Novice→Apprentice is ~2–3 trades; Expert→Master is a real grind. |
| **Offers are materialized at roll time** | `VillagerTrades.ItemListing.getOffer(entity, RandomSource)` produces a concrete `MerchantOffer`. Enchanted books, enchanted tools/armour, dyed leather, explorer maps, and most prices are **randomized at that moment**. | HIGH (vanilla API shape; corroborated by Trade Picker's "books always list every enchantment individually" workaround) | **Critical UX decision.** You cannot show "Enchanted Book" as a generic option — you must roll candidates server-side, show the *materialized* offer, and hand back exactly that offer object. |
| **Restocking** | Working at a reachable job-site POI restocks locked trades, **max 2× per Minecraft day**, only during the work schedule (~2000–9000 ticks). Java needs **no bed** for restock. | HIGH (Minecraft Wiki, Trading + Villager) | See "The Restock Question" below — this is the single biggest gameplay landmine. |
| **Villagers never despawn** | "Whether in a village or not, a villager never despawns." | HIGH (Minecraft Wiki, Villager) | Employees are safe across chunk unload / player death / relog. No persistence hack needed. Death by entity cramming (`maxEntityCramming`, default 24) and mob damage is still possible. |
| **Profession is lost until first trade** | Breaking a villager's job-site block resets its profession **unless it has traded at least once** (XP > 0). After one trade the profession is permanently locked. | MEDIUM-HIGH (multiple community sources, well-known behaviour) | A freshly bound level-1 employee with 0 XP is **at risk of losing its profession**. Must be defended against explicitly. |
| **Zombification** | Zombie kill converts a villager with 0% (Easy) / 50% (Normal) / 100% (Hard) chance. Lightning converts a villager to a **witch**, not a zombie. | HIGH (Minecraft Wiki, Villager) | PROJECT's "not converted by lightning" = witch immunity. Both need event cancellation. |
| **Iron golems** | Spawned by 3 panicking villagers or 5 gossiping villagers. | HIGH (Minecraft Wiki, Villager) | Employees are real villagers → they *will* spawn golems in groups. This is a compatibility win, not a bug. Leave it in v1. |
| **Profession ← job-site block is derivable** | `VillagerProfession` is a record carrying `Predicate<Holder<PoiType>> heldJobSite` / `acquirableJobSite`; `PoiType` carries its matching blockstates. Block → PoiType → Profession is resolvable at runtime from registries. | MEDIUM-HIGH (Fabric Yarn 1.21 javadoc + NeoForge profession-registration guides) | Confirms PROJECT's decision to derive professions dynamically — modded professions work for free. |
| **NeoForge config screen is free** | NeoForge 21.x ships `ConfigurationScreen`; register an `IConfigScreenFactory` extension point and the Mods menu gets a working Config button. | HIGH (NeoForged docs, 1.21.1 config page) | Config UI is an S-sized feature, not M. |
| **Patchouli exists for 1.21.1 NeoForge** | `1.21.1-92-neoforge` / `1.21.1-93-neoforge` published on Modrinth. | HIGH (Modrinth version listings) | An in-game guide book is available if wanted — but it's a hard dependency for a personal project. |
| **JEI/EMI pick up vanilla recipe types automatically** | Standard shaped/shapeless crafting recipes appear with zero integration code. Custom recipe types need a JEI plugin; EMI renders JEI plugins. | HIGH (JEI/EMI docs + NeoForge discussion) | Craft recipes = free in JEI. The **altar ritual is not a recipe** — no JEI integration needed or expected in v1. |

### The Restock Question (explicit answer)

**Yes, restocking still applies, and yes, players expect it.** Employees are literal
`minecraft:villager` entities using the vanilla `MerchantOffers` system, so every offer has
`maxUses` and locks when exhausted. "Villager won't restock" is one of the most-searched
villager complaints on the web, and mods that get it wrong get bug reports (e.g. easy-villagers
issue #199, "Trader block doesn't restock").

But vanilla restock requires the villager to **reach a job-site POI during its work schedule**.
A Second Shift employee is bound at the altar — its "job-site block" sits *on top of the altar*,
which may not be a claimable POI (the altar block is between it and the ground), and the employee
may never acquire a `JOB_SITE` brain memory at all. **If you do nothing, employee trades lock
after ~12–16 purchases and never come back. The mod will feel broken.**

Three viable designs, in order of recommendation:

1. **Mod-owned restock timer (recommended for v1).** Restock the employee on a fixed real-time
   interval independent of day/night, work schedule, and POI. This is exactly what Easy Villagers'
   Trader block does ("allows the villager to restock in non-working hours… not dependent on any
   external sources like day/night time or dimension", ~1–3 minutes). It's simple, testable, and
   fits the theme ("your employee doesn't sleep").
2. **Altar-driven restock.** Right-click the altar / feed it a token to restock the bound employee.
   Thematic, gives the altar an ongoing purpose, but adds a chore.
3. **Make the altar's job block a real POI the employee can claim.** Most vanilla-faithful, most
   fragile — depends on POI registration, pathing, and work schedule all lining up.

Whatever you choose, it must be visible: the trade screen should make it obvious that a locked
trade will come back.

---

## 1. Feature Landscape

**Complexity key:** **S** = under half a day · **M** = 1–2 focused days · **L** = multi-day and/or
carries real technical risk.

### Table Stakes (players expect these; missing = feels broken/unfinished)

#### 1a. Core loop (already in PROJECT scope — restated for completeness)

| Feature | Why Expected | Complexity | Dependencies / Notes |
|---------|--------------|------------|----------------------|
| Harvester weapon; villager kill → exactly 1 Soul Fragment, guaranteed | The whole resource economy. Random drops would make the loop feel bad. Precedent: Malum scythes ("kills with a scythe shatter mobs into their component spirits"; unharvested mobs become soulless). | S | `LivingDropsEvent` / `LivingDeathEvent` + weapon item |
| 4 Fragments → Soul Block crafting recipe | Standard crafting is the expected verb for item→item | S | Datagen recipe |
| Soul Altar block; job-site block on top + Soul Block inserted starts binding | The one "ritual" interaction; needs to read as intentional, not accidental | M | Block + BlockEntity + neighbour/`use` handling |
| Binding flow: profession → name → tier-1 trade picks → employee spawns | Core value per PROJECT.md | L | Depends on the GUI stack (below) |
| Trades drawn from the real vanilla pool for that profession/tier | Trades that don't match vanilla balance would break every existing wiki/expectation | M | `VillagerTrades.TRADES` lookup + `getOffer()` materialization |
| Employee = vanilla villager + data attachment | PROJECT constraint | M | `AttachmentType` with serializer **and** `sync` (NeoForge 21.1 supports `StreamCodec`-based sync) |
| Level-up: vanilla trading XP unlocks a tier, altar ritual applies chosen trades | Core value | M | Must **suppress** vanilla's automatic `updateTrades()` on level-up, or the game hands out 2 random trades before the player gets to choose |
| Employee traits: no zombification, no lightning-witch, no breeding | Explicit PROJECT requirement; also protects the player's investment | S | Event cancellation × 3 |
| Employee drops Soul Block + slime on non-Harvester death | Explicit PROJECT requirement; makes losing an employee recoverable rather than punishing | S | `LivingDropsEvent` + damage-source check |

#### 1b. Polish players treat as baseline for any content mod

| Feature | Why Expected | Complexity | Dependencies / Notes |
|---------|--------------|------------|----------------------|
| **Restock solution for employees** | See section above. #1 source of "this mod is broken" reports. | M | Must not touch non-employee villagers |
| **Profession-loss immunity** | A level-1 employee with 0 XP will lose its profession the moment its job-site situation changes. Silently becoming an unemployed villager after you spent 4 souls = rage-quit bug. | S | Force `XP >= 1` on bind, or intercept the profession reset for attached entities. Test explicitly. |
| **Creative mode tab containing every mod item/block** | Universal modding convention; without it items are unfindable in creative | S | `CreativeModeTab` DeferredRegister + `BuildCreativeModeTabContentsEvent` |
| **Complete `en_us.json`** — every item, block, GUI title, button, tooltip, chat message, config entry, advancement | Untranslated `item.secondshift.harvester` strings read as unfinished. Also the cheapest place to put the HR flavour. | S | Datagen `LanguageProvider` |
| **Item/block models + textures** (even placeholder-quality) | Purple-black missing-texture cubes read as broken, not WIP | S | Datagen model providers |
| **Recipes visible in JEI/EMI** | Players will not read a README; they will type "soul block" into JEI | S | Free if you use vanilla crafting recipe types. **Do not** build a custom recipe type for the altar in v1. |
| **Recipe advancements / "recipe unlocked" toast** | Standard datagen output; also the only in-game discovery hint you get for free | S | `RecipeProvider` emits `has_item` advancements automatically |
| **Sounds + particles for harvest, bind, and tier-up** | Rituals with no audio/visual feedback feel like a no-op. Occultism/Malum set the bar here. | S–M | Vanilla soul/soul-fire particles + `SoundEvents.SOUL_ESCAPE`/`ENCHANTMENT_TABLE_USE` are enough for v1; custom `SoundEvent` registration is optional |
| **Config file with a working config screen** | NeoForge gives the screen for free; players expect toggles for drop rates, restock interval, immunities | S | `ModConfigSpec` + `IConfigScreenFactory` |
| **Clear "ready for promotion" signal** | The loop is *trade → return to altar*. If the player can't tell when an employee is promotable, the loop stalls. | S–M | Options (pick 1–2): particles above the employee, altar glow, chat/actionbar message on level-up, badge in the trade screen |
| **Altar GUI shows current employee state** — name, profession, tier, which trades are already chosen | Otherwise the player has to remember their own plan across hours of play | M | Attachment sync to client |
| **Employee visual distinction from vanilla villagers** | Players will build a mixed village. Confusing a bound employee with a regular villager and Harvestering the wrong one is a real failure mode. | S | Minimum viable: always-visible custom name + a subtle soul particle. A custom render layer is v1.x. |
| **Named employee (name entry in the altar GUI)** | PROJECT requirement. Also, precedent: three separate "Villager Names" mods exist purely to name villagers (Serilum's ships a 5000+ name list and appends the profession to the trade-screen name) — naming villagers is a demonstrated player desire. | S | Vanilla `EditBox` widget in the bind screen + a generated default name so the player can just hit Confirm. Applied as `setCustomName` + `setCustomNameVisible(true)`. **A name tag still works too — don't block it.** |
| **Graceful empty/invalid states** | Altar with no job block; altar with job block but no Soul Block; a modded profession whose tier pool is empty; a job block that maps to no profession; a pool of exactly 2 (nothing to pick) | S | Cheap to write, expensive to omit; each one is a crash or a soft-lock |
| **Zero side effects on ordinary villagers** | If the mod changes vanilla villagers, it breaks every other villager mod and the player's existing trading hall | S | Gate every event handler on "has the attachment" |

### Differentiators (where Second Shift actually competes)

**Important honest finding:** "pick your villager's trades from the vanilla pool" is **not** unique.
[Trade Picker](https://modrinth.com/project/Hq1djofF) does exactly that: right-click any villager,
get a searchable list of every trade it could offer at its current level, pick, locked in. It even
defaults every trade to vanilla's minimum price. So the picker is table stakes for *this design*,
not a differentiator. **The differentiator is the cost, the gating, and the fiction around it.**

| Feature | Value Proposition | Complexity | Dependencies / Notes |
|---------|-------------------|------------|----------------------|
| **Souls as the price of choice** | Trade Picker gives trade selection away for free (it's a QoL mod). Second Shift makes it *cost 4 harvested villagers*. That turns trade selection from a cheat into a progression system. This is the strongest idea in the design. | S (it's the framing, not code) | Depends on the Harvester + Soul Block economy already in scope |
| **Multi-tier career planning** | Choosing at bind time what your Master trades *will* be — "I'm building a Mending librarian" — is a planning fantasy no comparable mod offers. Trade Picker only picks at the current level. | M | Either (a) pick tier-1 now and each tier later at the altar (PROJECT's current design, simpler) or (b) show the whole 5-tier career path at bind time as a read-only preview and pick as you go. **Recommend (a) + a read-only "career path" preview panel** — cheap, and it's the whole hook. |
| **No confinement build required** | The stated motivation in PROJECT.md. Trading Post (57M+ downloads) proves the market for "stop babysitting villagers". Second Shift solves it by *design* rather than by adding a remote-trading block. | S | Employees should stay near the altar / not wander far. Verify this actually holds — a wandering employee reintroduces the exact chore you removed. |
| **HR / corporate flavour text** | "Employee #0007 · Grade III · Position: Librarian" costs one lang file and is the entire personality of the mod. Job titles per tier (Intern → Associate → Senior → Lead → Principal mapped onto Novice→Master) is a one-line map. | S | Lang file + tier→title map. Highest flavour-per-byte in the project. |
| **Automatic modded-profession support** | Deriving profession from the POI registry means MoreVillagers etc. work day one with no patch. Almost no villager mod does this. | M | Already a PROJECT decision. Needs defensive handling for modded professions with weird/empty pools. |
| **Named, persistent, non-fungible employees** | Villagers never despawn and can't be bred here, so each employee is a unique character you invested souls in. Combined with the drop-your-Soul-Block-on-death rule, that's genuine attachment. | S | Falls out of table-stakes naming + no-breeding |
| **The Harvester as a retrieval tool, not just a weapon** | Killing your own employee with the Harvester should cleanly recover the soul (recycle a bad hire). Turns "I misclicked the profession" from a disaster into a mechanic. | S | Damage-source discrimination already needed for the drop rule |
| **Employee roster / directory** | An altar (or item) listing all your employees, their tier, and whether they're promotable. Scales the fantasy from 1 employee to a company. | M–L | Needs a persistent world-level registry of employees. **v1.x, not v1.** |

### Anti-Features (deliberately not building)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Free-form / arbitrary trade editor** | "Just let me make 1 emerald → 64 diamonds" | Deletes all balance and all progression; the vanilla pool is the design constraint that makes the mod interesting. Already Out of Scope in PROJECT.md. | Vanilla pool only. The pool *is* the balance. |
| **Custom villager entity type** | Cleaner code, custom model, custom AI | Breaks iron golems, villager entity tags, zombie sieges, raids, and every other villager mod. Already Out of Scope. | `minecraft:villager` + data attachment (PROJECT decision) |
| **Enumerating every enchantment for enchanted-book trades** | Trade Picker does it; it's what everyone wants | It makes guaranteed Mending a 4-soul purchase and collapses the entire enchanting game. It's also the largest UI in the mod (~40 enchantments × levels). | Materialize **one** random enchanted-book offer per candidate slot from the vanilla listing. The player still chooses *whether* to take it — a real decision, not a vending machine. Revisit in v1.x behind a config flag if it feels too punishing. |
| **Trade rerolling / cycling** | Every villager QoL mod has it (Easy Villagers "Cycle Trades" / C key) | It directly contradicts the design. If you can reroll, choosing means nothing and the soul cost means nothing. | Choice replaces rerolling. That's the pitch. |
| **Auto-trading / hopper automation** | Easy Villagers' Auto Trader is popular | Different genre (factory automation), huge surface area, and it destroys the "these are people who work for you" fiction | Out of scope indefinitely |
| **Breeding / population growth** | Natural extension of "run a company" | Already Out of Scope; also would make souls worthless | Hire = harvest. Scarcity is the point. |
| **Employee happiness / upkeep / wages** | Fits the HR framing perfectly | An upkeep system means the mod nags you forever. Every "feed your minions" mechanic in modded MC is universally disliked. | Keep it as flavour text only. Employees are undead — they don't need lunch. That's a joke, not a system. |
| **Multiplayer / economy balancing** | Standard modding hygiene | Already Out of Scope (single-player target). Don't build permission systems, ownership ACLs, or anti-dupe hardening. | Do the *minimum* correct client/server split (menus, packets, attachment sync) because getting that wrong crashes single-player too — but don't design for servers. |
| **Full villager AI overhaul (schedules, guard duty, patrols)** | Guard Villagers proves it's popular | Enormous brain/behaviour surface area; the exact thing PROJECT's architecture constraint says to avoid | Leave vanilla AI intact. Iron golem spawning included. |
| **Custom altar recipe type + JEI plugin** | "My ritual should be in JEI" | A custom `RecipeType` plus a JEI plugin is a multi-day detour to document a 2-step interaction | Document the ritual in a tooltip and (optionally, v1.x) a Patchouli book |
| **Patchouli guide book in v1** | Occultism's Dictionary of Spirits is the gold standard for ritual mods | Adds a hard runtime dependency and a JSON book to a personal mod whose author already knows the recipes | Tooltips + advancement descriptions in v1. Patchouli in v1.x if you ever share it. |
| **Suppressing iron golem spawning from employees** | "My ghoul shouldn't summon a golem" | It's free compatibility and a fun emergent detail | Leave vanilla. Config toggle in v1.x if it ever annoys you. |
| **Villager reputation penalty for harvesting** | Thematically delicious | Vanilla gossip/reputation is fiddly and makes the mod hostile to play | Skip. The Harvester is already a commitment. |

---

## 2. Feature Dependencies

```
[Menu/Screen registration harness]        <-- KNOWN RISK (prior draft crashed here)
    └──required by──> [Soul Altar GUI shell]
                          ├──required by──> [Profession selection screen]
                          ├──required by──> [Name entry (EditBox)]
                          └──required by──> [Trade picker screen]

[Data attachment: serialize + sync]
    └──required by──> [Employee identity: name, profession, tier, chosen trades]
                          ├──required by──> [Altar shows employee state]
                          ├──required by──> [Promotion-ready signal]
                          ├──required by──> [Trait immunities (zombify/lightning/breed)]
                          ├──required by──> [Soul Block drop on death]
                          ├──required by──> [Restock override]
                          └──required by──> [Profession-loss immunity]

[POI registry -> profession derivation]
    └──required by──> [Profession selection screen]
                          └──required by──> [Trade pool lookup]
                                                └──required by──> [Trade picker screen]

[Offer materialization (ItemListing.getOffer)]
    └──required by──> [Trade previews in picker]
    └──required by──> [Applying chosen trades to the employee]

[Suppress vanilla auto-updateTrades on level-up]
    └──required by──> [Tier promotion ritual]   (otherwise vanilla hands out random trades first)

[Harvester + Soul Fragment + Soul Block]
    └──required by──> [Binding ritual]     (the economy gate)

[Restock solution] ──enhances──> [entire mod]   (absence makes everything feel broken)

[Trade rerolling]  ──conflicts──> [Trade picking]     (do not build both)
[Custom entity type] ──conflicts──> [Iron golem / mod compatibility]
[Enchantment enumeration] ──conflicts──> [Souls as the price of choice]
```

### Dependency Notes

- **Everything visual depends on the menu/screen harness.** PROJECT.md records that the prior draft
  died at `RegisterMenuScreensEvent` with an unbound `secondshift:binding_altar` menu type. Build and
  ship a *do-nothing* altar screen that opens, syncs one integer, and closes — before any trade logic
  exists. This is the highest-risk dependency in the project and it gates ~60% of the feature list.
- **Attachment sync is a hard prerequisite for the GUI**, not an optimization. The picker screen runs
  on the client and needs the candidate offers; those are rolled server-side. Either sync via the
  attachment's `StreamCodec` or send them in the menu's opening data (`ContainerData` /
  `IContainerFactory` extra data). Prefer the menu payload for candidates — they're transient.
- **Suppressing vanilla `updateTrades()` must land in the same phase as the promotion ritual.**
  If it doesn't, the vanilla level-up path adds 2 random trades before the player reaches the altar,
  and the player sees trades they never chose. That's the kind of bug that reads as "mod is broken".
- **Profession-loss immunity and restock both hang off the attachment.** Both are cheap once the
  attachment exists and impossible before it.
- **Trade picking and trade rerolling are mutually exclusive design-wise.** Note that Trade Picker's
  own docs warn "don't install alongside another trade-cycling mod… running two can conflict."

---

## 3. MVP Definition

### Launch With (v1) — the shippable "it works and feels finished" set

Core loop (from PROJECT.md):

- [ ] Harvester → guaranteed 1 Soul Fragment on villager kill — *the economy*
- [ ] 4 Fragments → Soul Block recipe — *the gate*
- [ ] Soul Altar block + job-site block on top + Soul Block insert — *the ritual*
- [ ] Menu/Screen registration harness proven in isolation — *the known failure point*
- [ ] Profession derived from the job-site block via POI registry — *no hardcoded list*
- [ ] Name entry with a sensible generated default — *EditBox, S*
- [ ] Tier-1 trade picker: roll candidates from the vanilla pool, show **materialized** offers, pick 2 — *the core value*
- [ ] Employee spawns as `minecraft:villager` + synced data attachment
- [ ] Promotion ritual: vanilla XP unlocks the tier, altar picks the trades, vanilla auto-trades suppressed
- [ ] Traits: no zombification, no lightning-witch, no breeding
- [ ] Soul Block + slime drop on non-Harvester death; clean soul recovery on Harvester death

Polish that is not optional:

- [ ] **Restock solution** — mod-owned timer recommended; without it the mod dies after ~15 trades
- [ ] **Profession-loss immunity** — a 0-XP employee must never silently become unemployed
- [ ] Creative tab with every item and block
- [ ] Complete `en_us.json` (items, blocks, GUI, buttons, chat, config, advancements)
- [ ] Models + textures for all items/blocks (placeholder quality is fine, missing is not)
- [ ] Crafting recipes via vanilla recipe types (→ free JEI/EMI) + recipe advancements
- [ ] Sounds + particles on harvest, bind, and promotion (vanilla soul sounds/particles are enough)
- [ ] Promotion-ready signal the player cannot miss
- [ ] Altar GUI shows the bound employee's name / profession / tier / already-chosen trades
- [ ] Employees visually distinguishable from vanilla villagers (visible custom name minimum)
- [ ] `ModConfigSpec` + `IConfigScreenFactory` with: fragment drop count, restock interval, each immunity toggle
- [ ] Every invalid state handled: no job block, no soul block, unmapped block, empty pool, pool size ≤ 2
- [ ] Zero behaviour change for villagers without the attachment
- [ ] HR flavour: tier→job-title map (Intern/Associate/Senior/Lead/Principal or similar) in the lang file

### Add After Validation (v1.x)

- [ ] **Read-only 5-tier career-path preview** at bind time — *trigger: once tier-by-tier picking feels good and you want the planning fantasy*
- [ ] **Employee roster / directory** — *trigger: once you have 4+ employees and lose track of who's promotable*
- [ ] **Custom render layer for employees** (soul overlay, glowing eyes, HR badge) — *trigger: once name plates aren't enough to tell them apart*
- [ ] Custom registered `SoundEvent`s instead of vanilla borrows — *trigger: when vanilla sounds start feeling generic*
- [ ] Config flag to enumerate enchanted-book options — *trigger: only if random-book rolls feel unfairly punishing in play*
- [ ] Config toggle for iron-golem suppression — *trigger: only if it actually annoys you*
- [ ] Advancement tree for the necromancy progression — *trigger: when the loop is stable enough to have milestones*
- [ ] Altar particle/beam VFX for a bound employee — *trigger: pure polish, after the loop is validated*

### Future Consideration (v2+)

- [ ] Patchouli guide book — *defer: hard dependency, and the author knows the mod*
- [ ] JEI/EMI plugin for the altar ritual — *defer: custom recipe type is a multi-day detour for a 2-step interaction*
- [ ] Employee "departments" / multi-employee altars — *defer: needs the roster first*
- [ ] Deeper HR sim (org chart, performance reviews) — *defer: flavour risk of becoming a chore system*
- [ ] Public release / Modrinth publishing — *explicitly Out of Scope in PROJECT.md*

---

## 4. Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|-----------|---------------------|----------|
| Menu/Screen registration harness (isolated, proven) | HIGH (gates everything) | MEDIUM (L risk-wise) | **P1** |
| Data attachment with serialize + sync | HIGH | MEDIUM | **P1** |
| Harvester → Soul Fragment → Soul Block | HIGH | LOW | **P1** |
| Soul Altar block + bind interaction | HIGH | MEDIUM | **P1** |
| POI → profession derivation | HIGH | MEDIUM | **P1** |
| Trade picker (materialized candidates, pick 2 of N) | HIGH | HIGH | **P1** |
| Promotion ritual + suppress vanilla auto-trades | HIGH | MEDIUM | **P1** |
| **Restock solution** | HIGH | MEDIUM | **P1** |
| **Profession-loss immunity** | HIGH | LOW | **P1** |
| Trait immunities (zombify / lightning / breed) | MEDIUM | LOW | **P1** |
| Soul Block drop on death + Harvester recovery | MEDIUM | LOW | **P1** |
| Name entry + generated default | MEDIUM | LOW | **P1** |
| Creative tab | MEDIUM | LOW | **P1** |
| Complete lang file + HR job titles | MEDIUM | LOW | **P1** |
| Models/textures | MEDIUM | LOW | **P1** |
| Recipes + recipe advancements (JEI free) | MEDIUM | LOW | **P1** |
| Invalid-state handling | MEDIUM | LOW | **P1** |
| Sounds + particles for rituals | MEDIUM | LOW | **P1** |
| Promotion-ready signal | HIGH | LOW | **P1** |
| Altar shows employee state | MEDIUM | MEDIUM | **P1** |
| Employee visual distinction (name plate) | MEDIUM | LOW | **P1** |
| Config + config screen | LOW | LOW | **P2** |
| Career-path preview (read-only, 5 tiers) | HIGH | MEDIUM | **P2** |
| Employee roster / directory | MEDIUM | HIGH | **P2** |
| Custom render layer for employees | MEDIUM | MEDIUM | **P2** |
| Custom SoundEvents | LOW | LOW | **P3** |
| Advancement tree | LOW | LOW | **P3** |
| Patchouli book | LOW | MEDIUM | **P3** |
| JEI plugin for the ritual | LOW | HIGH | **P3** |

---

## 5. Competitor Feature Analysis

| Feature | Trade Picker (Modrinth) | Easy Villagers (henkelmax) | Trading Post (Fuzs) | Guard Villagers | Occultism / Malum | **Second Shift**|
|---------|------------------------|----------------------------|---------------------|-----------------|-------------------|-----------------|
| **Trade selection** | Right-click villager → searchable list of the **full** vanilla pool at current level → pick → locks permanently. Books list **every enchantment individually**. Default = vanilla **minimum** price; config for cost scaling or vanilla randomization. | No selection — **"Cycle Trades" button / C key** rerolls offers until the first trade, then locks | None | None | N/A | **Pick 2 of N materialized candidates from the tier's real pool, at the altar, paid for in souls. No rerolling.** |
| **Cost of choosing** | Free (pure QoL) | Free (rerolls are free) | Free | 3 emeralds (Rally of the Guard addon) | Ritual reagents + sacrifices | **4 Soul Fragments = 4 harvested villagers per employee** |
| **Villager management** | Vanilla villagers in place | Pick villagers up as items (V key); place into single-block workstations (Trader, Auto Trader, Breeder, Incubator, Converter, Iron Farm, Farmer) | One block trades with **all** villagers in a configurable radius (57M+ downloads) | Guards patrol, can be given inventory + patrol points when player has Hero of the Village | N/A | **Employee bound at the altar; no confinement build needed** |
| **Restock** | Vanilla | Trader block restocks on an internal ~1–3 min timer, **independent of day/night or dimension** | Vanilla | N/A | N/A | **Recommend copying Easy Villagers: mod-owned timer, POI-independent** |
| **Naming** | No | No | No | No | No | **EditBox in the bind GUI + generated default.** Precedent: Villager Names (Serilum) ships 5000+ names and appends the profession to the trade-screen name; three separate naming mods exist |
| **Ritual UX** | N/A | N/A | N/A | Crouch + right-click a nitwit/unemployed villager holding a sword or crossbow | Occultism: chalk pentacle + paraphernalia + Golden Sacrificial Bowl; right-click each bowl to place an item, then start. Malum: scythe kills shatter mobs into spirits; unharvested mobs go "soulless" | **Job-site block on the altar + insert Soul Block → GUI opens.** Simpler than Occultism's multiblock, which is right for a personal mod |
| **In-game guide** | Config file / ModMenu | Wiki (external) | Config screen | External | Occultism ships the **Dictionary of Spirits** (in-game, step-by-step) | **Tooltips + advancements in v1; Patchouli deferred** |
| **Config UI** | `config/tradeoptimizer.json` + ModMenu | Extensive in-game config | In-game mod-menu config | Config file | Config file | **NeoForge built-in `ConfigurationScreen` (free)** |

**The read:** Trade Picker already ships the picker mechanic, and Trading Post's 57M downloads prove
the "stop babysitting villagers" itch is huge — but nobody combines them with a *cost*. Second Shift's
defensible position is that it is the only one where choosing your trades is a **progression system**
rather than a convenience toggle, wrapped in a fiction (harvest → bind → employ → promote) that gives
the cost a reason to exist.

---

## 6. Open Questions for Requirements / Roadmap

1. **Does the employee stay put?** The stated motivation is "no confinement builds." If a bound
   employee wanders off, the chore returns. Needs an explicit requirement: leashed to the altar,
   `NoAI`, restricted-area brain memory, or just "employees don't wander" verified in play.
2. **Which restock design?** Recommend the mod-owned timer. Needs to be a decision, not an emergent
   behaviour.
3. **What exactly happens on a pool of size ≤ 2?** Show and auto-confirm, or skip the screen?
   Librarian Master (pool = 2) will hit this.
4. **How many candidates are shown vs picked?** Vanilla is "2 of N". Options: show all N materialized
   (simplest, matches Trade Picker), or show a rolled subset (e.g. 3 of 5) to preserve some RNG.
   Recommend **show all N, pick 2** — matches vanilla's pool exactly and is the cleaner promise.
5. **Enchanted-book handling.** Recommend one materialized random book per pool slot in v1
   (see anti-features). Confirm this in requirements so it doesn't drift.
6. **Are prices vanilla-random or vanilla-minimum?** Trade Picker defaults to minimum. Second Shift
   should probably use **vanilla-random**, since the soul cost is already the balance lever and
   minimum prices on top would be double-dipping.
7. **Can an altar rebind / fire an employee?** Not in PROJECT scope, but "I picked the wrong
   profession" is the most likely early frustration. Harvester-recovery partly covers it.

---

## Sources

**Vanilla mechanics (HIGH confidence):**
- [Trading – Minecraft Wiki](https://minecraft.wiki/w/Trading) — 2 trades per level, random from pool, max 10 trades, XP thresholds 0/10/70/150/250, 3–6 XP per trade +5 on level-up, restock 2×/day, Hero of the Village discounts
- [Villager – Minecraft Wiki](https://minecraft.wiki/w/Villager) — villagers never despawn; restock needs job-site access during work, no bed required in Java; iron golems from 3 panicking / 5 gossiping villagers; zombie conversion 0%/50%/100% by difficulty; lightning → witch
- [Librarian – Minecraft Wiki](https://minecraft.wiki/w/Librarian) — per-tier pool sizes 3/3/3/4/2 with 67%/67%/67%/50%/100% selection chances (MEDIUM: exact trade contents on this page look inconsistent with other sources; the *pool sizes and probabilities* are the load-bearing data and are internally consistent with 2÷poolSize)
- Farmer Novice pool = 5 (wheat/potato/carrot/beetroot/bread), 2 chosen — corroborated across two sources

**Modding platform (HIGH confidence):**
- [Configuration | NeoForged docs (1.21.1)](https://docs.neoforged.net/docs/1.21.1/misc/config/) — `ModConfigSpec`, `IConfigScreenFactory`, built-in `ConfigurationScreen`
- [Data Attachments | NeoForged docs](https://docs.neoforged.net/docs/datastorage/attachments/) — serializer + `sync` with `StreamCodec`
- [Patchouli 1.21.1-93-neoforge on Modrinth](https://modrinth.com/mod/patchouli/version/BIogJv2D)
- [VillagerProfession (Yarn 1.21 javadoc)](https://maven.fabricmc.net/docs/yarn-1.21.11+build.1/net/minecraft/village/VillagerProfession.html) — `Predicate<Holder<PoiType>>` job-site fields (MEDIUM: Yarn mappings, but the record shape matches Mojang mappings)

**Comparable mods (MEDIUM confidence — vendor descriptions and community wikis):**
- [Trade Picker – Modrinth](https://modrinth.com/project/Hq1djofF) — full-pool picker, per-enchantment book listing, min-price default, cost-scaling config
- [Easy Villagers – Modrinth](https://modrinth.com/mod/easy-villagers) / [Easy Villagers wiki](https://www.minecraft-guides.com/mod/easy-villagers/) — Trader block cycle-trades button + C key, POI-independent 1–3 min restock, Auto Trader, Breeder, Converter
- [easy-villagers issue #199 "Trader block doesn't restock"](https://github.com/henkelmax/easy-villagers/issues/199) — evidence that restock bugs are the top complaint class
- [Trading Post – CurseForge](https://www.curseforge.com/minecraft/mc-mods/trading-post) — one block trades with all villagers in a radius; 57.4M+ downloads
- [Guard Villagers – CurseForge](https://www.curseforge.com/minecraft/mc-mods/guard-villagers) / [Rally of the Guard](https://www.curseforge.com/minecraft/mc-mods/rally-of-the-guard-guardvillagers) — crouch+right-click conversion with a sword/crossbow, Hero-of-the-Village-gated inventory GUI, 3-emerald hire cost
- [Villager Names (Serilum) – Modrinth](https://modrinth.com/mod/villager-names-serilum) and [YANDO's Villager Names](https://modrinth.com/mod/villager-names) — 5000+/6000+ name lists, profession appended to the trade-screen name
- [Occultism wiki – Rituals & Pentacles](https://minecraftoccultism.fandom.com/wiki/Category:Rituals) — Golden Sacrificial Bowl, per-item bowls, Dictionary of Spirits in-game guide
- [Malum – CurseForge](https://www.curseforge.com/minecraft/mc-mods/malum) — scythe kills shatter mobs into spirits; unharvested mobs become soulless
- [Necromancy Remake – Modrinth](https://modrinth.com/project/fkk2zkAb) — NeoForge harvest/ritual/minion loop
- [Custom Villager Trades (Forge/NeoForge)](https://www.curseforge.com/minecraft/mc-mods/custom-villager-trades-forge), [VillagerConfig](https://modrinth.com/mod/villagerconfig) (`/vc test villager` opens a randomly generated trade GUI for a profession) — precedent for JSON-driven trade definition and offer preview

---
*Feature research for: NeoForge 1.21.1 necromancy/villager-trading mod (Second Shift)*
*Researched: 2026-09-04*
