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

(None yet — ship to validate)

### Active

<!-- Current scope. All hypotheses until shipped and validated. -->

- [ ] Harvester tool: killing a villager with it drops exactly 1 Soul Fragment, guaranteed
- [ ] 4 Soul Fragments craft into 1 Soul Block
- [ ] Soul Altar block: placing a job-site block on top and inserting a Soul Block begins binding
- [ ] Binding flow: choose profession (from the job-site block), name the employee, choose level-1 trades
- [ ] Trade choices are drawn from the bound profession's real vanilla trade pool for that tier
- [ ] Employee spawns as a `minecraft:villager` carrying a NeoForge data attachment (no custom entity type)
- [ ] Leveling: trading grants vanilla XP to unlock a tier; player then performs an altar ritual to choose that tier's trades
- [ ] Employee traits: cannot be zombified, not converted by lightning, cannot breed
- [ ] Employee drops its Soul Block (plus slime) when killed by anything other than the player's Harvester
- [ ] Compiles against NeoForge 21.1.248 and loads in the CurseForge "test" instance without crashing

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
  the same concept but was never verified. Its compiled jar (`second-shift-0.1.0.jar`, still in
  the test instance) crashes on client init with a `NullPointerException: Trying to access
  unbound value: ResourceKey[minecraft:menu / secondshift:binding_altar]` during
  `RegisterMenuScreensEvent` — the custom GUI (MenuType/Screen registration) was the failure
  point ("nothing worked"). All old-mod code and jars are being deleted.
- **Known risk area:** the trade-picker GUI. Menu/screen registration and client↔server sync
  must be built incrementally and tested in isolation before layering mechanics on top.
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
*Last updated: 2026-09-04 after initialization*
