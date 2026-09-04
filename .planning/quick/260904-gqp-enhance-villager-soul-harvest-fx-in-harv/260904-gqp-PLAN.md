---
phase: quick/260904-gqp
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java
autonomous: true
requirements: [G-1]
user_setup: []

must_haves:
  truths:
    - "On a lethal Harvester hit, a SCULK_SOUL burst + a rising SOUL column + a single FLASH particle appear at the dying villager's chest"
    - "A visible particle stream travels from the villager to the killing player and ends in a REVERSE_PORTAL burst at the player's chest"
    - "A vertical soul-colored particle line (SOUL_FIRE_FLAME + END_ROD) descends onto the villager, with NO LightningBolt entity spawned"
    - "Layered sounds play: SCULK_CATALYST_BLOOM (pitched down) + SOUL_ESCAPE at the corpse, RESPAWN_ANCHOR_DEPLETE at the player"
    - "The FX fires on the exact same code path as the guaranteed Soul Fragment spawn (LivingDeathEvent onDeath), so it cannot desync from the actual reap"
    - "All existing HarvesterGameTests stay GREEN — the FX never throws on a headless dedicated server (no client, no nearby players)"
  artifacts:
    - path: "src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java"
      provides: "One private helper playSoulHarvestFx(ServerLevel, LivingEntity, Player) called from onDeath immediately after addFreshEntity; old inline FX block removed"
      contains: "playSoulHarvestFx"
  key_links:
    - from: "HarvesterEvents.onDeath"
      to: "playSoulHarvestFx"
      via: "direct call inside the `if (level instanceof ServerLevel serverLevel)` block, right after level.addFreshEntity(fragment)"
      pattern: "playSoulHarvestFx\\("
    - from: "playSoulHarvestFx"
      to: "ServerLevel#sendParticles / Level#playSound(null, ...)"
      via: "server-side particle broadcast + positional sound"
      pattern: "sendParticles|playSound\\(null"
---

<objective>
Enhance the villager soul-harvest death FX in `HarvesterEvents` — the death-moment visual/audio polish that closes deferred item G-1 from the Phase 2 UAT. The reap mechanic (instakill + guaranteed Soul Fragment) is already correct and GameTest-covered; only the cosmetic burst is in scope.

Replace the current thin inline FX (a single `SOUL_ESCAPE` sound + small `SOUL` particle scatter) with a layered vanilla-only effect: a chest burst, a rising soul column, a flash, a soul stream to the killer, a soul-colored pseudo-bolt, and stacked sounds at both the corpse and the killer.

Purpose: the reap should feel like something significant just happened.
Output: one clearly-named private helper in `HarvesterEvents.java`, wired on the same path as the Fragment drop.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@CLAUDE.md
@src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java
@src/main/java/com/cxmxrgo/secondshift/gametest/HarvesterGameTests.java
@.planning/phases/02-economy-items-soul-altar-block/02-02-SUMMARY.md
@.planning/phases/02-economy-items-soul-altar-block/02-REVIEW-FIX.md
@.planning/phases/02-economy-items-soul-altar-block/02-04-SUMMARY.md

<interfaces>
<!-- Current HarvesterEvents shape the executor edits. Extracted from the file. -->

`onDeath(LivingDeathEvent event)` — game-bus handler. After the guard
`isHarvesterKillOfVillager(target, event.getSource())` it:
  1. spawns exactly one Soul Fragment `ItemEntity` via `level.addFreshEntity(fragment)`
  2. THEN runs the inline FX block: `if (level instanceof ServerLevel serverLevel) { ... }`
     containing `serverLevel.playSound(null, x,y,z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 1.0F)`,
     a `serverLevel.sendParticles(ParticleTypes.SOUL, ...)` burst, and
     `if (event.getSource().getEntity() instanceof Player killer) { ... }` wisps toward the killer.

`ServerLevel#sendParticles(ParticleOptions type, double x, double y, double z,
    int count, double xDist, double yDist, double zDist, double speed)` — already
used in this file (confirmed compiles on 21.1.248).

`Level#playSound(@Nullable Player except, double x, double y, double z,
    SoundEvent sound, SoundSource source, float volume, float pitch)` — already
used in this file. A `Holder<SoundEvent>` overload also exists.

Already-imported in the file: `ParticleTypes`, `ServerLevel`, `SoundEvents`,
`SoundSource`, `Player`, `Level`, `LivingEntity`, `Villager`.
</interfaces>

<constraints>
- **Vanilla particles/sounds ONLY.** No custom `ParticleType` / `SoundEvent` registration (that is Phase 10 / POL-05). Do NOT touch `build.gradle`, any registry class, or `en_us.json`.
- **Server-side emission only.** Particles via `ServerLevel#sendParticles(...)` (broadcasts to all nearby players). Sounds via `Level#playSound(null, x, y, z, ...)`. No client-only classes, no `Minecraft.getInstance()`, no `LevelRenderer`.
- **FX must fire on the same path as the Fragment drop.** Keep it inside `onDeath`, inside the `ServerLevel` block, immediately after `level.addFreshEntity(fragment)`. It must not move to `onDrops` or `onDamagePre` and must not be gated on anything the Fragment spawn is not gated on.
- **Do NOT spawn a real `LightningBolt`** (or `EntityType.LIGHTNING_BOLT`). The charged-altar break in `SoulAltarBlock#playerWillDestroy` (02-04) already owns a visual-only lightning bolt; this effect must read as a clearly different event. The "bolt" here is a vertical particle line only.
- **Headless-safe.** GameTests run on a dedicated server with no client and (in most tests) no player near `SPAWN`. Every particle/sound call must be a safe no-op in that situation — `sendParticles` and `playSound(null, ...)` already are; just do not introduce anything that assumes a client or a receiver.
- **One helper.** All new FX code lives in a single private static method with a self-documenting name (e.g. `playSoulHarvestFx`). The old inline FX block is deleted, not left alongside.
- **Sound-only or particle-only partial failure must not crash a dedicated server** — no asserts, no throws in the helper; it is pure fire-and-forget emission.
</constraints>

<api_verification>
Before writing the helper, the executor MUST verify these identifiers against the
decompiled `build/moddev/artifacts/neoforge-21.1.248-sources.jar` (extract / open it;
this is the exact binary the test instance loads). Adjust names to match reality and
note any substitution in the SUMMARY.

| Identifier | Expected | If absent / different |
|---|---|---|
| `ParticleTypes.SCULK_SOUL` | particle spawned by a sculk catalyst bloom | fall back to `ParticleTypes.SOUL` at higher count |
| `ParticleTypes.SOUL` | already used in this file — confirmed | n/a |
| `ParticleTypes.FLASH` | firework-flash white pop | fall back to `ParticleTypes.END_ROD` single |
| `ParticleTypes.REVERSE_PORTAL` | upward-drifting portal particle | fall back to `ParticleTypes.SOUL` |
| `ParticleTypes.SOUL_FIRE_FLAME` | blue flame | fall back to `ParticleTypes.SOUL` |
| `ParticleTypes.END_ROD` | white drifting spark | fall back to `ParticleTypes.FIREWORK` |
| `SoundEvents.SCULK_CATALYST_BLOOM` | `SoundEvent` | fall back to `SoundEvents.SCULK_CATALYST_BREAK` or `SoundEvents.WARDEN_HEARTBEAT` |
| `SoundEvents.SOUL_ESCAPE` | already used in this file — confirmed | n/a |
| `SoundEvents.RESPAWN_ANCHOR_DEPLETE` | may be `Holder.Reference<SoundEvent>`, not a bare `SoundEvent` | use the `Holder<SoundEvent>` `playSound` overload, or call `.value()` |
| `SoundEvents.ELDER_GUARDIAN_CURSE` | `SoundEvent` (optional tail) | drop the tail line if unavailable |
| `Vec3#lerp(Vec3 to, double delta)` | linear interpolation for the soul-stream points | compute the lerp inline: `from.add(to.subtract(from).scale(t))` |
| `LivingEntity#getBbHeight()` | bounding-box height for chest / top offsets | use `getEyeHeight()` or a `1.8` literal |
</api_verification>

<tasks>

<task type="auto">
  <name>Task 1: Replace inline harvest FX with a layered playSoulHarvestFx helper</name>
  <files>src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java</files>
  <action>
Run the `<api_verification>` checks first; keep a note of any identifier you had to substitute (goes in the SUMMARY).

**1. Add the helper.** Create one private static method:
`private static void playSoulHarvestFx(ServerLevel level, LivingEntity corpse, Player killer)`
`killer` may be null. No throws, no asserts — pure emission. Add imports for `net.minecraft.world.phys.Vec3` (only if you use `Vec3#lerp`; otherwise skip). Do NOT add a `@Nullable` import — just document the nullable param in the Javadoc and null-check it.

Compute anchors once: `x = corpse.getX()`, `z = corpse.getZ()`, `feetY = corpse.getY()`, `chestY = corpse.getY() + corpse.getBbHeight() * 0.5`, `topY = corpse.getY() + corpse.getBbHeight()`.

Emit, in order:
- **Chest burst** — one `sendParticles` of the sculk-soul particle, count ~40, at `(x, chestY, z)`, spread `(0.3, 0.4, 0.3)`, speed `0.02`.
- **Rising SOUL column** — loop 8 steps `i = 0..7`; `y = feetY + (i / 7.0) * 2.0`; each step `sendParticles(ParticleTypes.SOUL, x, y, z, 2, 0.05, 0.02, 0.05, 0.01)` (small upward drift). Column rises ~2 blocks.
- **Single FLASH** — `sendParticles(flash, x, chestY, z, 1, 0.0, 0.0, 0.0, 0.0)`.
- **Soul stream to killer** — only if `killer != null`. `from = (x, chestY, z)`, `to = (killer.getX(), killer.getY() + killer.getBbHeight() * 0.5, killer.getZ())`. Loop `j = 1..6`; `t = j / 6.0`; lerp a point between `from` and `to`; `sendParticles(reversePortal, px, py, pz, 3, 0.05, 0.05, 0.05, 0.02)`. Then a burst at `to`: `sendParticles(reversePortal, to.x, to.y, to.z, 20, 0.2, 0.3, 0.2, 0.05)`.
- **Soul-colored pseudo-bolt** — vertical line from `topY + 6.0` down to `topY`. Loop 12 steps `k = 0..11`; `y = (topY + 6.0) - (k / 11.0) * 6.0`; at each step emit BOTH `sendParticles(soulFireFlame, x, y, z, 1, 0.02, 0.0, 0.02, 0.0)` and `sendParticles(endRod, x, y, z, 1, 0.02, 0.0, 0.02, 0.0)`. Add a code comment: `// visual-only particle line — deliberately NOT a LightningBolt; 02-04 SoulAltarBlock owns the bolt-entity visual and these must read as different events.`
- **Sounds at the corpse** — `level.playSound(null, x, chestY, z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 1.0F, 0.7F)` then `level.playSound(null, x, chestY, z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 1.0F)`. Optional eerie tail (planner's call: include it, low volume): `level.playSound(null, x, chestY, z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.25F, 1.0F)` — drop this line if the api check fails.
- **Sound at the killer** — only if `killer != null`: `level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), <RESPAWN_ANCHOR_DEPLETE>, SoundSource.PLAYERS, 0.7F, 1.2F)` (use the `Holder<SoundEvent>` overload or `.value()` per the api check).

**2. Wire it and delete the old FX.** In `onDeath`, replace the entire existing inline block (the `if (level instanceof ServerLevel serverLevel) { ... }` that plays `SOUL_ESCAPE` + the `SOUL` burst + the killer wisps) with:
```
if (level instanceof ServerLevel serverLevel) {
    Player killer = event.getSource().getEntity() instanceof Player p ? p : null;
    playSoulHarvestFx(serverLevel, target, killer);
}
```
Keep it immediately after `level.addFreshEntity(fragment);` so it is on the exact reap path. Update the `onDeath` Javadoc line that mentions "harvest FX" to point at the new helper. Remove now-unused imports only if they are genuinely unused (`ParticleTypes` is still used inside the helper).

**3. Confirm no `LightningBolt` reference** was introduced anywhere in the file.
  </action>
  <verify>
    <automated>./gradlew build --console=plain</automated>
    <automated>./gradlew runGameTestServer --console=plain</automated>
  </verify>
  <done>
`./gradlew build --console=plain` exits 0. `./gradlew runGameTestServer --console=plain` exits 0 with every existing HarvesterGameTests method GREEN (the reap tests `harvester_kill_villager_drops_one_fragment`, `harvester_kill_baby_villager_drops_one_fragment`, `harvester_kill_resistance_and_absorption_villager_still_one_shot`, `harvester_kill_villager_drops_fragment_with_domobloot_false` all still pass — proving the helper does not throw headless). `HarvesterEvents.java` contains exactly one FX helper, the old inline FX block is gone, `onDeath` calls the helper right after `addFreshEntity`, and the file contains no `LightningBolt` / `EntityType.LIGHTNING_BOLT` token.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| (none new) | This change adds no input parsing, no network payload, no package install, no persisted state. It is server-side cosmetic emission on an already-authorized code path (a confirmed Harvester kill). |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-gqp-01 | Denial of Service | `playSoulHarvestFx` particle/sound volume per kill | accept | Fixed, small particle budget (~40 + ~16 + ~12 + ~38 + ~24 ≈ 130 particles) and ≤5 sound events per reap event; reaps are player-gated melee actions, not automatable at rates that matter. No loops unbounded by a constant. |
| T-gqp-02 | Tampering | vanilla `ParticleTypes` / `SoundEvents` identifiers | mitigate | `<api_verification>` step forces each identifier to be checked against the decompiled `neoforge-21.1.248-sources.jar` before use; documented fallbacks prevent a guessed symbol from shipping. |
</threat_model>

<verification>
- `./gradlew build --console=plain` green.
- `./gradlew runGameTestServer --console=plain` green — all existing HarvesterGameTests pass unchanged (this is the headless "FX never throws on a dedicated server" gate).
- Grep check: `HarvesterEvents.java` contains `playSoulHarvestFx` once as a method decl, and contains no `LightningBolt`.
- No changes to any file other than `HarvesterEvents.java`.
</verification>

<success_criteria>
- One layered, vanilla-only soul-harvest FX helper on the same reap path as the Soul Fragment drop.
- Chest SCULK_SOUL burst + rising SOUL column + FLASH at the villager; REVERSE_PORTAL stream + burst to the killer; SOUL_FIRE_FLAME/END_ROD vertical line onto the villager; layered sounds at corpse and killer.
- No real `LightningBolt` — visually distinct from the 02-04 charged-altar break.
- All existing GameTests GREEN; build green.
- **Manual smoke (human check, NOT blocking):** after the plan completes, the user runs `./gradlew runClient`, hits a villager with the Harvester, and eyeballs the effect — confirms it reads as heavy/distinct and the `ELDER_GUARDIAN_CURSE` tail is not too much (drop that one line if it is).
</success_criteria>

<output>
Create `.planning/quick/260904-gqp-enhance-villager-soul-harvest-fx-in-harv/260904-gqp-SUMMARY.md` when done.

Record in the SUMMARY:
- Any `<api_verification>` identifier that had to be substituted, and to what.
- Whether the `ELDER_GUARDIAN_CURSE` tail was kept.
- The `runGameTestServer` GREEN tail output.
- Confirmation that `HarvesterEvents.java` was the only file touched.
</output>
