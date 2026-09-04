---
phase: 02-economy-items-soul-altar-block
plan: 02
subsystem: gameplay
tags: [neoforge, livingdamageevent, livingdropsevent, gametest, damagesource, item-model, foil, java21]

requires:
  - phase: 02-economy-items-soul-altar-block
    provides: "02-01 — HarvesterItem + ModItems.HARVESTER/SOUL_FRAGMENT registry holders; 6 ECON-02 @GameTest methods (3 RED); build.gradle runGameTestServer; complete en_us.json + lang-key self-check"
provides:
  - "event/HarvesterEvents — game-bus LivingDamageEvent.Pre (villager instakill) + LivingDropsEvent (exactly-1-Fragment swap), gated on DamageSource#getWeaponItem() instanceof HarvesterItem AND target instanceof net.minecraft.world.entity.npc.Villager (exact)"
  - "Harvest FX (D-10): vanilla SoundEvents.SOUL_ESCAPE + ParticleTypes.SOUL burst + wisps toward the killer, server-broadcast"
  - "models/item/harvester.json (minecraft:item/handheld) + models/item/soul_fragment.json (minecraft:item/generated)"
  - "textures/item/harvester.png + soul_fragment.png — 16x16 placeholder PNGs (POL-03)"
  - "all 6 ECON-02 GameTests GREEN — the automated ECON-02 verification"
affects: [02-03-recipes-advancements, 02-04-soul-altar-interaction, 06-employees-attachment]

tech-stack:
  added: []
  patterns:
    - "Custom weapon behaviour via game-bus events keyed on DamageSource#getWeaponItem(), never a SwordItem subclass (no sweep)"
    - "Instakill = post-mitigation LivingDamageEvent.Pre#setNewDamage(health + absorption + 1); LivingDropsEvent#getDrops().clear() then add exactly one ItemEntity"
    - "@EventBusSubscriber(modid) self-registering handler class (game bus by default), private ctor, static @SubscribeEvent methods — matches ModRegistrySelfCheck"

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java
    - src/main/resources/assets/secondshift/models/item/harvester.json
    - src/main/resources/assets/secondshift/models/item/soul_fragment.json
    - src/main/resources/assets/secondshift/textures/item/harvester.png
    - src/main/resources/assets/secondshift/textures/item/soul_fragment.png
  modified: []

key-decisions:
  - "Resistance-V + absorption case one-shots with the plain LivingDamageEvent.Pre#setNewDamage override alone — the setHealth(0)+die(source) fallback (RESEARCH Open Question 2) was NOT needed and is not wired"
  - "DamageSource#getWeaponItem() exists on 21.1.248 (A1 resolved YES) — delegates to directEntity.getWeaponItem() = the attacker's main-hand ItemStack; no held-item fallback used"
  - "LivingDamageEvent.Pre exposes getNewDamage()/setNewDamage(float) verbatim (A2 resolved) — post-armor, post-Resistance; absorption is subtracted after the event, hence the '+ absorption' term"
  - "Soul Fragment foil works via the ENCHANTMENT_GLINT_OVERRIDE=true component set in 02-01 — ItemStack#hasFoil() reads it directly on 21.1.248; no ModItems change, no rarity/model fallback"
  - "Harvester item model uses minecraft:item/handheld (holds like a tool); no BlockBench model supplied — placeholder-quality per POL-03"

patterns-established:
  - "Harvester behaviour lives entirely in event/HarvesterEvents; the item class stays a plain marker Item"
  - "isHarvesterKillOfVillager(target, src) single shared gate for both handlers — Phase 6 ECON-04 slots a hasData(EMPLOYEE) branch in here"

requirements-completed: [ECON-01, ECON-02, POL-03]

duration: 12min
completed: 2026-09-04
---

# Phase 2 Plan 02: The Harvester Reaps Villager Souls Summary

**A single Harvester hit on any villager (adult or baby) force-kills it past armor / Resistance V / absorption and drops exactly one Soul Fragment via game-bus LivingDamageEvent.Pre + LivingDropsEvent — all 6 ECON-02 GameTests are GREEN.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-09-04T10:31:00Z
- **Completed:** 2026-09-04T10:36:00Z
- **Tasks:** 2 completed
- **Files created:** 5

## Accomplishments

- `event/HarvesterEvents` turns the ECON-02 mechanic ON: villager instakill + guaranteed single Soul Fragment, gated on the Harvester weapon and an exact `Villager` type check (babies included; wandering trader + zombie villager excluded).
- The 3 RED GameTests from plan 02-01 (`harvester_kill_villager...`, `harvester_kill_baby_villager...`, `harvester_kill_resistance_and_absorption_villager_still_one_shot`) are now GREEN; the 3 exclusion tests stayed GREEN.
- Harvester + Soul Fragment have real item models and placeholder 16x16 textures; the Fragment's enchant-foil is confirmed reachable through the 02-01 component.

## Task Commits

1. **Task 1: HarvesterEvents — villager instakill + exactly-1-Fragment drop** — `a9162ab` (feat)
2. **Task 2: Harvester + Soul Fragment item models, textures, foil** — `762e99c` (feat)

**Plan metadata:** (this commit) (docs: complete plan)

_Task 1 is a `tdd="true"` task but the RED tests already existed (authored in 02-01 Task 3), so this plan only added the GREEN implementation — one `feat` commit, no separate `test` commit._

## Files Created/Modified

- `src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java` - game-bus `LivingDamageEvent.Pre` instakill + `LivingDropsEvent` Fragment swap + vanilla harvest FX
- `src/main/resources/assets/secondshift/models/item/harvester.json` - `minecraft:item/handheld` parent
- `src/main/resources/assets/secondshift/models/item/soul_fragment.json` - `minecraft:item/generated` parent
- `src/main/resources/assets/secondshift/textures/item/harvester.png` - 16x16 placeholder scythe silhouette
- `src/main/resources/assets/secondshift/textures/item/soul_fragment.png` - 16x16 placeholder pale-blue soul shard

## RESEARCH resolutions (required by plan `<output>`)

| Item | Resolution (verified against `neoforge-21.1.248-sources.jar`) |
|------|--------------------------------------------------------------|
| **A1 — `DamageSource#getWeaponItem()`** | **Exists.** `DamageSource.java`: `return this.directEntity != null ? this.directEntity.getWeaponItem() : null;` and `LivingEntity#getWeaponItem()` returns `getMainHandItem()`. The GameTests' `damageSources().playerAttack(mockPlayer)` source therefore reports the mock player's main-hand stack. **No held-item fallback used.** |
| **A2 — `LivingDamageEvent.Pre` method names** | `getNewDamage()` / `setNewDamage(float)` / `getSource()` / `getContainer()` / `getOriginalDamage()` — used verbatim. The Pre javadoc: "armor, and potion modifiers have already been applied … Absorption modifiers are handled after this event." |
| **Open Question 2 — Resistance-V instakill approach** | **Plain `setNewDamage(getHealth() + getAbsorptionAmount() + 1.0F)` is sufficient.** `LivingEntity#actuallyHurt` applies our override post-Resistance; `DamageContainer#setReduction(ABSORPTION, …)` only subtracts up to `getAbsorptionAmount()`, so `health + 1` still lands on health. GameTest `harvester_kill_resistance_and_absorption_villager_still_one_shot` passes. **The `setHealth(0)` + `die(source)` fallback was NOT wired.** |
| **Foil component** | **Works, no fallback.** `ItemStack#hasFoil()` reads `DataComponents.ENCHANTMENT_GLINT_OVERRIDE`; 02-01 sets it `true` on `SOUL_FRAGMENT`. No `ModItems` cross-plan touch. |

## `runGameTestServer` GREEN output

```
> Task :runGameTestServer
========= 6 GAME TESTS COMPLETE IN 447.1 ms ======================
All 6 required tests passed :)
```

`./gradlew build` exits 0. `./gradlew runGameTestServer` exits 0.

## Decisions Made

See frontmatter `key-decisions`. Headline: the instakill needed no fallback beyond the post-mitigation `setNewDamage` one-liner, closing RESEARCH Open Question 2.

## Deviations from Plan

None - plan executed exactly as written.

The plan's Task-1 action step 2 pre-authorised an `AbstractVillager` mention only as a "NOT" contrast; the file avoids the token entirely (javadoc phrases the exclusion in prose) so the plan's `! grep -q "AbstractVillager"` gate passes. Likewise the pre-mitigation hook and `getOffers()` are described in prose, never named, so those negative gates pass. This is wording, not a behavioural deviation.

## Issues Encountered

- First draft of `HarvesterEvents.java` named `AbstractVillager`, `LivingIncomingDamageEvent`, and (implicitly) the pre-mitigation hook in its Javadoc, tripping the plan's negative `grep` gates. Reworded the doc comments to describe the exclusions/anti-patterns in prose without the literal tokens; behaviour unchanged; re-ran GameTests GREEN.

## Known Stubs

- Item textures are 16x16 procedurally-generated placeholders (POL-03 explicitly permits this). A hand-drawn scythe / soul-shard and an optional angled BlockBench Harvester model are polish for Phase 10, not a functional gap.
- No Harvester crafting recipe yet — that is plan 02-03. The item is reachable via `/give` and the creative tab.

## Next Phase Readiness

- ECON-02 is done and automated. 02-03 (recipes + advancement chain) and 02-04 (Soul Altar interaction) are unblocked.
- Phase 6 (ECON-04): `HarvesterEvents.isHarvesterKillOfVillager(...)` is the single gate where the `&& !hasData(EMPLOYEE)` branch slots in — documented in the method Javadoc.
- End-of-phase HUMAN-CHECK still pending: `runClient` visual confirm of models/textures/foil + "no sweep, modest damage on pigs".

## Self-Check: PASSED

- `src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java` — FOUND
- `src/main/resources/assets/secondshift/models/item/harvester.json` — FOUND
- `src/main/resources/assets/secondshift/models/item/soul_fragment.json` — FOUND
- `src/main/resources/assets/secondshift/textures/item/harvester.png` — FOUND (PNG 16x16 RGBA)
- `src/main/resources/assets/secondshift/textures/item/soul_fragment.png` — FOUND (PNG 16x16 RGBA)
- commit `a9162ab` — FOUND
- commit `762e99c` — FOUND

---
*Phase: 02-economy-items-soul-altar-block*
*Completed: 2026-09-04*
