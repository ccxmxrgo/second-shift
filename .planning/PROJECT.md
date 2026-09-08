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
- [x] The Binding Altar screen opens correctly and safely, with zero trade logic — *Validated in Phase 3: Menu & Screen Harness (HARD GATE)* (GUI-01, ALTAR-03). `secondshift:binding_altar` `MenuType`/`AbstractContainerMenu`/`AbstractContainerScreen` registered and proven under `runClient`/`runServer` (the exact unbound-`MenuType` crash class that killed the prior draft is now structurally prevented, guardrail-covered, and deliberately reproduced+restored). Runtime `ProfessionResolver` (POI→profession, no hardcoded list) gates the open trigger; right-click with a Soul Block + a real job-site block on top sockets it and opens the screen in one action; no-job/no-block right-clicks are harmless themed no-ops; `stillValid` rejects a broken/moved-away session and force-closes with a themed message (14/14 GameTests green). Code review found and fixed one real gap: the display-only slot's invariant is now enforced at the BE/Container layer, not just the UI layer. Human UAT passed 3/3.
- [x] Employee spawns as a `minecraft:villager` carrying a NeoForge data attachment (no custom entity type) — *Validated in Phase 4: Employee Attachment & Spawn* (EMP-01, EMP-02, EMP-08, EMP-09). `EmployeeData` (name/profession/tier/offers, versioned CODEC/STREAM_CODEC) attaches via `ModAttachments.EMPLOYEE`; `EmployeeManager.bind` spawns a real `minecraft:villager` with `villagerXp >= 1`, a random one of 3 fixed professions, and an always-visible green name — confirmed persisting across a save/quit/relaunch and a 300-block chunk-unload round trip, confirmed client-synced (the LIGHT spike: `AttachmentType.Builder#sync` empirically proven to deliver attachment data to the client in NeoForge 21.1.248), confirmed renameable via a vanilla Name Tag, and confirmed zero effect on a separate wild villager in the same world. One real bug (spawn position overlapping the job-site block) found and fixed during manual verification. 20/20 GameTests green.

- [x] Soul Altar item-socket binding flow: pick a profession from a socketed job item, choose a real vanilla-pool trade in a scrollable "career path" picker (reusing vanilla's own `EnchantmentMenu`/`EnchantmentScreen`), and bind — *Validated in Phase 5: Profession Resolution & Trade Picker* (amended across 5 live-iteration redesign rounds from the original plan — see 05-VERIFICATION.md for the full shipped-vs-planned account). GUI-03's "show a bound employee's status back at the altar" was explicitly descoped, not shipped.
- [x] Employee traits: immune to zombie/witch conversion, cannot breed (Mixin-free precondition break), tethered to its altar, recoverable via a Harvester sneak-release or any other death's drop-recovery, removable only by destroying the altar — *Validated in Phase 6: Employee Traits, Death & Firing* (EMP-03…07, ECON-04, ALTAR-06).
- [x] Leveling: trading grants real vanilla XP to unlock a tier; vanilla's auto-appended trades are reverted within one periodic check; the player performs a Promotion Ritual at the altar (reusing the same "career path" picker pattern) to choose each new tier's trades — *Validated in Phase 7: Progression & Promotion Ritual* (PROG-01…04).
- [x] Trades restock on a mod-owned real-time timer, independent of POI/work-schedule/day-night/dimension, and configurable — *Validated in Phase 8: Mod-Owned Restock* (STOCK-01…03).
- [x] Happiness system: an employee needs quarters (a real enclosed 3×3+ room with a door, bounded-flood-fill detected) and a stocked food chest; a gradual Unhappy/OK/Happy meter modulates emerald prices (vanilla's own hero-of-the-village mechanism) and restock speed, and sustained neglect makes the employee quit (drops its Soul Block, reverts to a wild villager, frees its altar) — *Validated in Phase 9: Quarters & Happiness* (HAPP-01…07, STOCK-04).
- [x] Every player-facing surface translated, themed, configurable (a real Mods-menu Config screen), and failing gracefully; HR job titles on every employee's name — *Validated in Phase 10: Polish, Config & Invalid States* (POL-02/05/06/07/08).

Full requirement list: `.planning/REQUIREMENTS.md` (60 v1 requirements — all 60/60 complete as of 2026-09-08).

### Active

<!-- Current scope. All hypotheses until shipped and validated. -->

None — v1.0 milestone complete. Remaining work is exclusively the `human_needed` playtest backlog
recorded in each phase's `VERIFICATION.md` (Phases 6, 7, 9, 10) — real-play "does it feel right"
checks with verified underlying logic, not open implementation work.

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
*Last updated: 2026-09-08 — v1.0 milestone COMPLETE: all 10 phases and all 60/60 v1 requirements
shipped. Phases 5-10 (Profession Resolution & Trade Picker through Polish, Config & Invalid
States) built in one `/gsd-autonomous` overnight session while the user slept — see each phase's
own SUMMARY.md/VERIFICATION.md for full detail, and STATE.md's Decisions log for every
cross-phase technical call made along the way. The bind-an-employee-and-choose-its-trades core
loop is complete end to end: harvest → bind → pick trades → level via real vanilla XP → promote
→ maintain quarters/happiness → restock/fire/quit, all configurable and themed. Remaining work is
exclusively the accumulated human_needed playtest backlog (Phases 6, 7, 9, 10) — real-play "does
it feel right" checks, not open implementation.*
