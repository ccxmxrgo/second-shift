# Second Shift

## What This Is

Second Shift is a personal Minecraft mod (1.21.1, NeoForge) that reframes villager
trading as necromancy. You play a necromancer running a company of reanimated villager
"employees": instead of building confinement halls to pin down vanilla villagers, you
harvest their souls and bind them into employees whose trades you choose directly. It is
for the mod author's own play, built and iterated on locally.

## Core Value

The bind-an-employee-and-choose-its-trades loop must work and feel good: harvest souls →
craft a Soul Block → bind a villager at the Soul Altar → pick its profession and choose
its trades at each level. If everything else is cut, this loop has to be reliable.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

- [x] Compiles against NeoForge 21.1.248 and loads in the CurseForge "test" instance without crashing — *Validated in Phase 1: Skeleton & Feedback Loop* (BUILD-01…05, POL-09). Also: `runClient`/`runServer` green, an unbound `DeferredRegister` now hard-aborts the launch with a named list, and `./gradlew deployToTest` deploys the jar into the instance on demand.
- [x] The tangible soul economy exists and behaves — *Validated in Phase 2: Economy Items & Soul Altar Block* (ECON-01/02/03, ALTAR-01/07, POL-01/03/04). Harvester (craftable, deliberately weak weapon, loses durability per reap); killing any villager with it drops exactly 1 Soul Fragment every time (6-case GameTest suite, wandering traders / zombie villagers / sword kills excluded); 4 Fragments ⇄ 1 Soul Block; Soul Altar block crafts, places, one-way-sockets a Soul Block (persists across reload, renders embedded + emissive), and breaks per D-04 (empty → drops self with a pickaxe; charged → lost entirely + ½-heart blast + cosmetic lightning). One creative tab, hand-authored assets/recipes/loot, and a 3-step advancement discovery chain gating the recipes. No GUI shipped.

### Active

<!-- Current scope. All hypotheses until shipped and validated. -->

- [ ] Soul Altar block: placing a job-site block on top and inserting a Soul Block begins binding *(altar block, socket, and break behaviour shipped in Phase 2; the binding trigger + job-site detection are still Active)*
- [ ] Binding flow: choose profession (from the job-site block), name the employee, choose level-1 trades
- [ ] Trade choices are drawn from the bound profession's real vanilla trade pool for that tier
- [ ] Employee spawns as a `minecraft:villager` carrying a NeoForge data attachment (no custom entity type)
- [ ] Leveling: trading grants vanilla XP to unlock a tier; player then performs an altar ritual to choose that tier's trades
- [ ] Employee traits: cannot be zombified, not converted by lightning, cannot breed
- [ ] Employee drops its Soul Block (plus slime) when killed by anything other than the player's Harvester
- [ ] Employees stay near their altar (bound area); each altar owns exactly one employee
- [ ] Happiness system: an employee needs quarters (3×3 + door) and a stocked food chest; Unhappy/OK/Happy tiers modulate emerald prices and restock speed, and sustained neglect makes the employee quit (drops its Soul Block, reverts to a wild villager)
- [ ] Firing is only possible by destroying the altar — a player-only ½-heart blast (no block damage), then a cosmetic lightning strike instakills the bound employee; Soul Block and job block are lost
- [ ] Trades restock on a mod-owned timer (vanilla POI restock never reaches an altar-bound employee)

Full requirement list: `.planning/REQUIREMENTS.md` (60 v1 requirements).

### Out of Scope

<!-- Explicit boundaries with reasoning to prevent re-adding. -->

- Custom villager entity type — deliberately using `minecraft:villager` + data attachment so iron golems, villager-targeting mobs, entity tags, and other villager mods keep working
- Free-form / arbitrary trade editor — trades come from vanilla profession pools, not a custom trade builder
- Public release (Modrinth / CurseForge publishing) — personal project; no release pipeline
- Employee breeding or population growth — employees are bound, not bred
- Reusing any `second-shift` draft code or its jar — full rebuild from scratch
- Multiplayer/server hardening beyond what vanilla client/server sync requires — single-player is the target

## Context

- **Rebuild from scratch.** A prior draft (`Documents/second-shift-m1/second-shift/`) implemented
  the same concept. An early build crashed on client init with `NullPointerException: Trying to
  access unbound value: ResourceKey[minecraft:menu / secondshift:binding_altar]` during
  `RegisterMenuScreensEvent` — the `MenuType` was never actually added to the registry. Research
  corrected the "nothing worked" framing: a *later* draft build (the `second-shift-0.1.0.jar` that
  was in the test instance) did load and reach gameplay, but was never validated end to end. The
  old source tree and jar have been deleted; the rebuild-from-scratch decision stands.
- **Known risk area:** the menu/screen registration + client↔server sync. All three research docs
  independently demand a do-nothing altar screen that opens under `runClient` as a hard gate
  *before* any trade logic. The failure that killed the draft was a feedback-loop failure
  (`build` / `runData` / jar-copy could not detect an unregistered menu), not a knowledge gap.
- **Other verified landmines (see `.planning/research/`):** `@EventBusSubscriber(bus=…)` is
  ignored in NeoForge 21.1; vanilla `updateTrades()` appends 2 random trades on level-up exactly
  when the player should be choosing; `setVillagerData` nulls offers on profession change;
  `ResetProfession` reverts a 0-XP employee to unemployed; `BabyEntitySpawnEvent` never fires for
  villager breeding (breeding suppression needs a spike); 1.21.1 uses `ItemInteractionResult`.
- **Target environment:** Minecraft 1.21.1, NeoForge 21.1.248, Java 21 (Temurin). Test target is
  the CurseForge instance "test" at `C:\Users\user\curseforge\minecraft\Instances\test\`
  (also has owo-lib, accessories, wildcard installed).
- **Worth carrying over from the draft's design:** derive block → villager profession from the
  point-of-interest registry at runtime (no hardcoded profession list) so modded professions work
  automatically.
- **Motivation:** vanilla villager trading requires tedious confinement builds to keep villagers
  still and safe; this mod replaces that chore with a themed necromancer fantasy.

## Constraints

- **Tech stack**: Minecraft 1.21.1 + NeoForge 21.1.248, Java 21 — must match the CurseForge "test" instance exactly
- **Build ownership**: Claude sets up Gradle (NeoForge MDK + wrapper) and runs `./gradlew build` after every change; the user only launches the game to test. Each iteration's deliverable is a working jar copied to `C:\Users\user\curseforge\minecraft\Instances\test\mods\`
- **Feedback loop**: on failure, Claude reads `crash-reports\` and `logs\latest.log` from the test instance
- **Architecture**: employees are `minecraft:villager` + NeoForge data attachment; behavior lives in event handlers and the attachment, not in a subclass — Why: preserves compatibility with vanilla and modded villager systems
- **Distribution**: personal use only; MIT license retained, no publishing

## Key Decisions

<!-- Decisions that constrain future work. -->

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Full rebuild; delete the old `second-shift` draft and jar | Prior draft never verified, GUI crashed on load; cleaner to restart | — Pending |
| Keep name "Second Shift", mod id `secondshift`, package `com.cxmxrgo.secondshift`, MIT | Continuity with the concept; id/package are sensible defaults | — Pending |
| Employees = vanilla villager + NeoForge data attachment (no custom entity) | Maximize compatibility; avoid brittle subclassing | — Pending |
| Trades sourced from vanilla profession pools, not a custom editor | Keeps scope focused; reuses existing trade balance | — Pending |
| Leveling = vanilla trading XP unlocks a tier, altar ritual applies chosen trades | Blends familiar progression with the necromancer theme | — Pending |
| Claude owns the Gradle build; user only tests in-game | User wants to focus on mechanics and feel, not the toolchain | — Pending |
| Include a happiness/upkeep system (quarters + food chest → price + restock modifiers → quitting) | User wants the HR-sim depth; research flags "feed your minions" upkeep as commonly disliked in modded MC — accepted risk, revisit after playtesting | ⚠️ Revisit |
| Firing an employee only via altar destruction (½-heart player blast + cosmetic smite kills the employee, Soul Block + job block lost) | Makes hiring a real commitment; no in-GUI undo keeps the soul cost meaningful | — Pending |
| Menu/screen harness is a hard gate: empty altar screen must open in `runClient` before any trade logic | Every research doc calls this out; the prior draft died on an unregistered menu that the build could not catch | — Pending |
| Toolchain: ModDevGradle 2.0.146 + Gradle 9.2.1 wrapper, Parchment 2024.11.17, from the official 1.21.1 MDK | Research-verified against live Maven metadata; right choice for a single-version solo mod | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-09-04 — Phase 2 (Economy Items & Soul Altar Block) complete: Harvester soul-reap (guaranteed 1 Fragment, GameTest-covered), Fragment⇄Block crafting, Soul Altar block with one-way socket + persistence + emissive charged render + D-04 break, one creative tab, hand-authored assets/recipes, 3-step advancement discovery chain. Human UAT passed 12/12; one deferred polish item (G-1: harvest FX impact).*
