---
phase: quick/260904-gqp
plan: 01
subsystem: event / harvester-reap
tags: [fx, particles, sound, vanilla-only, polish]
requires:
  - HarvesterEvents.onDeath (guaranteed Soul Fragment path)
provides:
  - "HarvesterEvents.playSoulHarvestFx(ServerLevel, LivingEntity, Player) — layered vanilla soul-harvest death FX"
affects:
  - src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java
tech-stack:
  added: []
  patterns:
    - "Server-side fire-and-forget FX helper: ServerLevel#sendParticles + Level#playSound(null, ...), headless no-op safe"
key-files:
  created: []
  modified:
    - src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java
decisions:
  - "ELDER_GUARDIAN_CURSE eerie tail KEPT (low volume 0.25, SoundSource.HOSTILE) — planner's call, subject to the non-blocking runClient smoke check"
  - "No <api_verification> substitutions needed — every flagged identifier exists in neoforge-21.1.248-sources.jar as expected"
metrics:
  duration: ~8 min
  completed: 2026-09-04
---

# Phase quick/260904-gqp Plan 01: Enhance Villager Soul-Harvest FX Summary

Replaced the thin inline harvest FX (one `SOUL_ESCAPE` + a small `SOUL` scatter + two wisp
puffs) in `HarvesterEvents` with a single layered, vanilla-only `playSoulHarvestFx` helper
wired on the exact reap path — chest `SCULK_SOUL` burst, rising `SOUL` column, `FLASH`,
`REVERSE_PORTAL` stream + arrival burst to the killer, a `SOUL_FIRE_FLAME`/`END_ROD`
vertical pseudo-bolt onto the corpse, and stacked sounds at both the corpse and the killer.

## What Was Built

- **`playSoulHarvestFx(ServerLevel level, LivingEntity corpse, Player killer)`** — one
  private static method, `killer` nullable (Javadoc-documented, null-checked, no
  `@Nullable` import). Anchors computed once (`x`, `z`, `feetY`, `chestY`, `topY` from
  `getBbHeight()`). Emits in order: chest `SCULK_SOUL` burst (count 40) → 8-step rising
  `SOUL` column over ~2 blocks → single `FLASH` → (killer only) 6-step `REVERSE_PORTAL`
  stream via `Vec3#lerp` + 20-count arrival burst → 12-step `SOUL_FIRE_FLAME`+`END_ROD`
  vertical line from `topY+6` down to `topY` → `SCULK_CATALYST_BLOOM` (BLOCKS, pitch 0.7)
  + `SOUL_ESCAPE` (PLAYERS) + `ELDER_GUARDIAN_CURSE` (HOSTILE, vol 0.25) at the corpse →
  (killer only) `RESPAWN_ANCHOR_DEPLETE` (PLAYERS, pitch 1.2) at the killer.
- **Old inline FX block deleted** — the `if (level instanceof ServerLevel serverLevel)`
  body is now just `Player killer = ... ; playSoulHarvestFx(serverLevel, target, killer);`,
  still immediately after `level.addFreshEntity(fragment);`.
- Added `import net.minecraft.world.phys.Vec3;` (used for `lerp`). No other imports
  changed — `ParticleTypes` still used inside the helper.
- Code comment on the pseudo-bolt loop: `// visual-only particle line — deliberately NOT
  a LightningBolt; 02-04 SoulAltarBlock owns the bolt-entity visual and these must read as
  different events.`
- Grep-confirmed: `playSoulHarvestFx` appears once as a method decl; file contains no
  `LightningBolt` / `EntityType.LIGHTNING_BOLT` token.

## API Verification (against build/moddev/artifacts/neoforge-21.1.248-sources.jar)

| Identifier | Result |
|---|---|
| `ParticleTypes.SCULK_SOUL` | present (`SimpleParticleType`) — used |
| `ParticleTypes.SOUL` / `FLASH` / `REVERSE_PORTAL` / `SOUL_FIRE_FLAME` / `END_ROD` | all present — used |
| `SoundEvents.SCULK_CATALYST_BLOOM` | present as bare `SoundEvent` — used directly |
| `SoundEvents.SOUL_ESCAPE` | `Holder.Reference<SoundEvent>` — resolved via the `Holder<SoundEvent>` `playSound` overload (same as pre-existing code) |
| `SoundEvents.RESPAWN_ANCHOR_DEPLETE` | `Holder.Reference<SoundEvent>` — same `Holder<SoundEvent>` overload |
| `SoundEvents.ELDER_GUARDIAN_CURSE` | present as bare `SoundEvent` — tail line KEPT |
| `Vec3#lerp(Vec3, double)` | present (`Vec3.java:230`) — used, no inline fallback needed |
| `LivingEntity#getBbHeight()` | present (already used in this file) — used |
| `Level#playSound(Player, double,double,double, Holder<SoundEvent>, SoundSource, float, float)` | present (`Level.java:489`) |

**No substitutions were required.** Every flagged identifier matched its expected form.

## Deviations from Plan

None affecting code. One reporting note:

- The task constraint expected `runGameTestServer` to report **9** tests; the repo's
  `HarvesterGameTests` currently defines **8** `@GameTest` methods and no other gametest
  class exists. All 8 ran GREEN. Treating the "9" as a stale count.

## Verification

- `./gradlew build --console=plain` → `BUILD SUCCESSFUL` (exit 0).
- `./gradlew runGameTestServer --console=plain` → `BUILD SUCCESSFUL` (exit 0). Exact tally:

  ```
  ========= 8 GAME TESTS COMPLETE IN 598.4 ms ======================
  All 8 required tests passed :)
  ```

  The 4 reap tests (`harvester_kill_villager_drops_one_fragment`,
  `harvester_kill_baby_villager_drops_one_fragment`,
  `harvester_kill_resistance_and_absorption_villager_still_one_shot`,
  `harvester_kill_villager_drops_fragment_with_domobloot_false`) all pass — proving
  `playSoulHarvestFx` does not throw on a headless dedicated server with no client and, in
  most tests, no nearby player. Exclusion tests unaffected.
- `HarvesterEvents.java` is the **only** file touched (`git status`: single `M` entry).
- No `LightningBolt` token in the file.

## Manual Follow-Up (human, NOT blocking)

Run `./gradlew runClient`, hit a villager with the Harvester, and eyeball the effect:
confirm it reads as heavy/distinct from the 02-04 charged-altar lightning, and judge
whether the `ELDER_GUARDIAN_CURSE` tail is too much — if so, delete that single
`level.playSound(..., SoundEvents.ELDER_GUARDIAN_CURSE, ...)` line.

## Known Stubs

None.

## Self-Check: PASSED

- `src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java` — FOUND, contains
  `playSoulHarvestFx` once, no `LightningBolt`.
- Code commit recorded below.
