# Spike: EMP-07 "Employees stay within a bound area around their altar"

**Researched:** 2026-09-04
**Scope:** Pre-emptive research only — no game code written. Answers STATE.md's flagged gap
("EMP-07 'keep employee near altar' has no pre-researched hook") before Phase 6 planning.

## Recommendation (HIGH confidence)

**Use vanilla's built-in `Mob#restrictTo(BlockPos, int)` mechanism. No custom AI, no Mixin, no
access transformer.** This is the exact mechanism vanilla itself uses to keep the Wandering
Trader near its spawn point, and it is fully public, fully inherited by `Villager`, and already
wired into every vanilla wander behavior a villager uses.

Concretely, for Phase 6:

1. **At bind time** (`EmployeeManager.bind`, after `level.addFreshEntity(v)`), call
   `villager.restrictTo(altarPos, RADIUS)`.
2. **On every entity load** (`EntityJoinLevelEvent`, same pattern as `ClientEmployeeSyncDebug`
   and the planned zombify/lightning trait handlers), re-apply
   `villager.restrictTo(employeeData.altarPos(), RADIUS)` for any entity carrying `EmployeeData`.
   This is **required**, not optional — see Finding 2.
3. Do **not** build a periodic re-assertion tick handler (unlike the level-up revert pattern).
   `restrictTo` is genuinely set-and-forget for the lifetime of a loaded entity — see Finding 2.
4. Pick one radius, store it on `EmployeeData` (or derive it from a config value), and use it for
   both the wander restriction and Phase 9's quarters/food-chest search — see Finding 3.

Confidence is HIGH because every claim below is verified against the actual decompiled vanilla
1.21.1 source recompiled with NeoForge 21.1.248 patches (found in this machine's Gradle
NeoFormRuntime cache — see Sources), not just documentation or memory.

---

## Finding 1 — Does vanilla already have a wander-restriction concept, and does `Villager` inherit it cleanly?

**Yes, cleanly, with zero extra work required.** `net.minecraft.world.entity.Mob` (the ancestor
of `PathfinderMob` → `AbstractVillager` → `Villager`) declares, all `public`:

```java
// Mob.java
private BlockPos restrictCenter = BlockPos.ZERO;   // private field, not exposed directly
private float restrictRadius = -1.0F;

public boolean isWithinRestriction() { ... }
public boolean isWithinRestriction(BlockPos pos) { ... }
public void restrictTo(BlockPos pos, int distance) {
    this.restrictCenter = pos;
    this.restrictRadius = (float) distance;
}
public BlockPos getRestrictCenter() { ... }
public float getRestrictRadius() { ... }
public void clearRestriction() { this.restrictRadius = -1.0F; }
public boolean hasRestriction() { return this.restrictRadius != -1.0F; }
```

`Villager` does not override or interfere with any of this. No subclass, AT, or Mixin needed —
this directly matches the project's "no Mixin" / "public API only" policy in CLAUDE.md.

**Does it actually constrain wander AI without extra work?** Yes, for the wander behavior that
matters. Villager movement is entirely Brain-driven (Villager registers **zero** goals in
`GoalSelector` — confirmed by reading `Villager.java` top to bottom, no `goalSelector.addGoal`
calls anywhere). The relevant brain behavior is `VillageBoundRandomStroll`, which every one of
Villager's behavior packages uses for idle/play/panic/pre-raid/raid wandering
(`VillagerGoalPackages.getIdlePackage/getPlayPackage/getPanicPackage/getPreRaidPackage/getRaidPackage`,
all call `VillageBoundRandomStroll.create(...)`).

Tracing the call chain: `VillageBoundRandomStroll` → `LandRandomPos.getPos(...)` →

```java
// LandRandomPos.java
public static Vec3 getPos(PathfinderMob mob, int radius, int yRange, ToDoubleFunction<BlockPos> f) {
    boolean flag = GoalUtils.mobRestricted(mob, radius);   // true once a restriction is set
    return RandomPos.generateRandomPos(() -> {
        BlockPos p = RandomPos.generateRandomDirection(mob.getRandom(), radius, yRange);
        BlockPos p1 = generateRandomPosTowardDirection(mob, radius, flag, p);
        ...
    }, f);
}

public static BlockPos generateRandomPosTowardDirection(PathfinderMob mob, int radius, boolean shortCircuit, BlockPos pos) {
    BlockPos p = RandomPos.generateRandomPosTowardDirection(mob, radius, mob.getRandom(), pos);
    return !GoalUtils.isOutsideLimits(p, mob)
        && !GoalUtils.isRestricted(shortCircuit, mob, p)      // <-- rejects candidate positions outside the restriction
        && !GoalUtils.isNotStable(mob.getNavigation(), p)
        ? p : null;
}
```

And `RandomPos.generateRandomPosTowardDirection` additionally **biases the random offset back
toward the restriction center** whenever `mob.hasRestriction()` is true:

```java
if (mob.hasRestriction() && range > 1) {
    BlockPos c = mob.getRestrictCenter();
    if (mob.getX() > c.getX()) i -= random.nextInt(range / 2); else i += random.nextInt(range / 2);
    if (mob.getZ() > c.getZ()) j -= random.nextInt(range / 2); else j += random.nextInt(range / 2);
}
```

So `restrictTo` doesn't just filter out illegal wander targets — it actively skews every future
random wander target toward the center. This is the same mechanism vanilla itself uses for the
Wandering Trader: `WanderingTraderSpawner.java` line 115: `wanderingtrader.restrictTo(blockpos1, 16)`.

**Caveat (not a blocker):** brain behaviors that walk toward a *specific* memory target —
`SetWalkTargetFromBlockMemory` for `JOB_SITE`/`HOME`/`MEETING_POINT`, `LookAndFollowTradingPlayerSink`
toward a trading player, `AcquirePoi` — do **not** consult `isWithinRestriction`. In practice this
is a non-issue for this mod: the player places the job block and (per Phase 9) the quarters/bed
near the altar, so those POI targets naturally sit inside the restriction radius if the radius is
sized to cover them (Finding 3). It only matters if a player deliberately places a job block or
meeting-point/bell far outside the radius — an edge case, not the "wanders off on its own"
behavior EMP-07 is about.

## Finding 2 — Set-and-forget vs. periodic re-assertion; does it survive save/reload?

**Set once at bind; re-apply once per entity load; no tick handler needed.**

- `restrictCenter`/`restrictRadius` are plain private fields on `Mob` with **no NBT
  serialization**. Confirmed by reading `Mob.addAdditionalSaveData` / `Mob.readAdditionalSaveData`
  in full — neither field appears in either method. **The restriction does NOT survive
  save/reload, chunk unload/reload, or dimension transfer.** This is different from the
  level-up "revert every tick" pattern (STACK.md gap #3) — that pattern exists because vanilla's
  mutation is *continuous and re-triggering*. `restrictTo`'s gap is the opposite: the mutation is
  **not continuous enough** — it's a one-time in-memory flag that vanishes whenever the entity is
  reconstructed from NBT.
- Therefore the correct hook is **`EntityJoinLevelEvent`**, exactly the pattern this project
  already established for `ClientEmployeeSyncDebug` (`src/main/java/com/cxmxrgo/secondshift/client/ClientEmployeeSyncDebug.java`)
  and the planned zombify/lightning trait handlers (CLAUDE.md §10). On that event: if
  `entity instanceof Villager v && v.getData(ModAttachments.EMPLOYEE) is present`, call
  `v.restrictTo(employeeData.altarPos(), radius)`. This event fires for every entity add to a
  level — initial spawn *and* every chunk/dimension reload — so a single handler covers both the
  bind-time case and the reload case if you route bind-time application through the same code path
  (or just also call it directly at the end of `EmployeeManager.bind`, since
  `level.addFreshEntity(v)` fires `EntityJoinLevelEvent` itself — verify in Phase 6 planning
  whether calling `restrictTo` before or after `addFreshEntity` matters; calling it in the
  `EntityJoinLevelEvent` handler alone is simplest and covers every case uniformly).
- **No per-tick or per-N-tick re-assertion is needed.** Once set on the live in-memory entity, the
  field persists for the entity's lifetime in that load — `clearRestriction()` is only called
  by vanilla when a leash is dropped and no leash data remains (`Mob.dropLeash`), which is
  irrelevant here unless a player leashes an employee (worth a defensive note: if a future
  iteration allows leashing employees, dropping the leash will silently clear the restriction —
  re-apply defensively in the `EntityJoinLevelEvent`/leash-drop path, or simply don't worry about
  it for v1 since nothing in REQUIREMENTS.md mentions leashing employees).

## Finding 3 — Conflict with Phase 9's food-chest / quarters mechanic

**Coordinate the radius; don't configure it independently — but keep it as one config value that
Phase 9 also reads, not a second detection system.**

- HAPP-01 requires an enclosed 3×3+ space with a door "tied to its altar"; HAPP-02 requires a
  nearby stocked chest the employee can draw from. Both of these are necessarily **within** the
  area the employee is physically allowed to reach — if the restriction radius is smaller than the
  distance from the altar to the employee's quarters or food chest, the employee will never be
  able to walk to either, and Phase 9's "quarters valid" / "food available" checks will read as
  perpetually broken even when correctly built.
- **Recommendation:** define a single config value (POL-06 already commits to a `ModConfigSpec`
  with tunable values) — e.g. `employeeRestrictionRadius` (blocks, vanilla default precedent:
  Wandering Trader uses 16) — and have both EMP-07's `restrictTo` call **and** Phase 9's quarters/
  chest search radius read from it. This guarantees "the employee can always reach anything HAPP-01
  /HAPP-02 will ever validate" by construction, with no separate coordination logic needed. A
  reasonable default is larger than the minimum 3×3 quarters footprint plus a few blocks of margin
  for a chest and door swing — 12–16 blocks matches vanilla's own Wandering Trader precedent and
  comfortably covers a modest player-built structure without letting the employee roam into open
  world.
- Do **not** invent a second "quarters radius" concept distinct from the restriction radius; that
  would let a player build valid quarters just outside the wander boundary, producing a confusing
  "quarters look right but employee can't reach them" bug. One radius, one config value, two
  consumers (EMP-07's `restrictTo`, Phase 9's structure/chest scan).

## Finding 4 — Edge cases: player moves or breaks the altar

- **The altar itself is not relocatable in this mod's design.** There is no "move a placed block"
  mechanic anywhere in REQUIREMENTS.md/ROADMAP.md — altars are placed once and either persist or
  are destroyed. So "player moves the altar" is not a real scenario to design for.
- **Breaking a bound altar is already fully specified by ALTAR-06**: it consumes the Soul Block and
  job block, damages the breaking player, and — 0.5s later, via a cosmetic lightning strike —
  **instakills the bound employee**. Since the employee is removed as part of that same flow, there
  is no "orphaned restriction center" state to handle; the entity carrying the stale
  `restrictCenter` ceases to exist in the same interaction that invalidated it. No additional hook
  is needed for this edge case beyond what ALTAR-06 already requires Phase 6 to build.
- **The one edge case that *is* real:** the employee attachment (`EmployeeData`, per
  `ARCHITECTURE.md`'s field list) needs to carry the altar's `BlockPos` so the
  `EntityJoinLevelEvent` re-application in Finding 2 has something to call `restrictTo` with,
  independent of the altar block entity (which — per Architectural Pattern 4 — intentionally
  stores nothing about the employee). Confirm `EmployeeData` includes (or gains) the bound altar's
  position; if it currently only lives implicitly via proximity/lookup, Phase 6 planning should add
  an explicit `altarPos` field so the restriction can be re-applied without a level-wide altar scan
  on every entity load.

## Finding 5 — Existing open-source technique to reference

No external mod research was necessary — **vanilla Minecraft's own `WanderingTraderSpawner`** is
the canonical "keep this specific entity near a point" reference implementation, and it is more
authoritative than any third-party mod would be:

```java
// WanderingTraderSpawner.java, line 115
wanderingtrader.restrictTo(blockpos1, 16);
```

It also demonstrates the optional "actively walk back" reinforcement:
`WanderingTrader` additionally adds a `GoalSelector` goal —

```java
this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.35));
```

`MoveTowardsRestrictionGoal` is itself a small, fully public vanilla `Goal`
(`net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal`) whose `canUse()` is simply
`!mob.isWithinRestriction()` and whose `start()` paths the mob back toward
`mob.getRestrictCenter()`. It only requires a `PathfinderMob`, which `Villager` is (via
`AbstractVillager`).

**This is a legitimate optional hardening for Phase 6**, since `Mob.goalSelector` is a `public
final` field and `Villager` never adds any goals to it (confirmed — Villager is 100% brain-driven,
`goalSelector.tick()` still runs every AI step via the inherited `Mob.serverAiStep()`, it's just
empty by default). Adding
`villager.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(villager, 0.35))` alongside
`restrictTo` in the same `EntityJoinLevelEvent` handler would give an *active* walk-back push
(covers being pushed/knocked outside the radius, or a rare brain-target excursion from Finding 1's
caveat) on top of the *passive* wander-filtering `restrictTo` already provides on its own. Given
EMP-07's wording ("stays within a bound area... never wander off") is about passive wandering, this
is a nice-to-have, not required for the success criterion — recommend including it anyway since
it's a two-line addition using a vanilla-provided class with zero new surface area.

---

## Sources

- `C:\Users\user\.gradle\caches\neoformruntime\intermediate_results\sourcesAndCompiledWithNeoForge_cca44f8311ba9c1569e5cd6465f2cd3b8cb86277_output.jar` —
  decompiled Mojang 1.21.1 vanilla source, recompiled against NeoForge 21.1.248's patches, pulled
  directly from this machine's own Gradle NeoFormRuntime build cache (the same toolchain the
  project's `./gradlew build` uses). Read in full: `Mob.java` (fields, `restrictTo`/`isWithinRestriction`/
  `hasRestriction`/`clearRestriction`, `addAdditionalSaveData`/`readAdditionalSaveData`,
  `serverAiStep`/`customServerAiStep`, `goalSelector` field visibility), `PathfinderMob.java`,
  `Villager.java` (full file — no `goalSelector.addGoal` calls, `customServerAiStep`/`tick`
  overrides), `VillagerGoalPackages.java` (every behavior package, all wander behaviors use
  `VillageBoundRandomStroll`), `VillageBoundRandomStroll.java`, `RandomStroll.java`,
  `GoalUtils.java` (`mobRestricted`, `isRestricted`), `LandRandomPos.java`, `DefaultRandomPos.java`,
  `RandomPos.java` (`generateRandomPosTowardDirection` restriction-biasing logic),
  `WanderingTraderSpawner.java` (`restrictTo(pos, 16)` call site), `WanderingTrader.java`
  (`MoveTowardsRestrictionGoal` goal registration), `MoveTowardsRestrictionGoal.java`,
  `ElderGuardian.java` (another vanilla `restrictTo` caller, not detailed above — guards its
  spawner structure). **HIGH** — this is the literal source the project's own build compiles
  against, not documentation or recollection.
- `C:\Users\user\Documents\PROJECTS\necromancy-mod\.planning\research\ARCHITECTURE.md` — employee
  attachment field list, `EntityJoinLevelEvent` pattern precedent, Pattern 4 ("altar BE persists
  nothing about the employee"). **HIGH**
- `C:\Users\user\Documents\PROJECTS\necromancy-mod\src\main\java\com\cxmxrgo\secondshift\client\ClientEmployeeSyncDebug.java` —
  confirmed existing `EntityJoinLevelEvent` usage pattern in this codebase to match. **HIGH**
- `C:\Users\user\Documents\PROJECTS\necromancy-mod\.planning\REQUIREMENTS.md` and `ROADMAP.md` —
  exact EMP-07 wording, Phase 6/9 grouping and requirements, confirmation there is no
  altar-relocation mechanic. **HIGH**
- `C:\Users\user\Documents\PROJECTS\necromancy-mod\CLAUDE.md` — no-Mixin policy, existing trait-hook
  table (STACK.md §10 equivalent), confirms `restrictTo` needs no AT (already public). **HIGH**

### Known gaps

- Not verified in an actual running client/server (this was a source-level, not runtime, spike —
  consistent with "investigation only" scope). Phase 6 planning should still budget a quick
  in-game sanity check (spawn an employee, walk away, confirm it stops following/wandering past the
  radius) as part of its own verification loop, even though the mechanism itself is now
  well-understood rather than unknown.
- Did not investigate third-party open-source NeoForge mods for alternative techniques, since
  vanilla's own `WanderingTraderSpawner`/`Mob#restrictTo` precedent is authoritative, exactly
  matches the "keep this entity near a point" need, and is already the API the project's stack is
  built on — searching further would not have produced a better answer.

---

## What this changes for Phase 6 planning

- STATE.md's flag can be cleared: EMP-07 is no longer a "no pre-researched hook" risk. It has a
  concrete, HIGH-confidence, ~3-line implementation (`restrictTo` call in an `EntityJoinLevelEvent`
  handler, keyed off `EmployeeData`, reusing the pattern already in `ClientEmployeeSyncDebug`).
- Phase 6's plan should add an explicit `altarPos` field to `EmployeeData` if one doesn't already
  exist by the time Phase 6 starts, since the restriction re-application needs it without an
  altar-scan.
- Phase 6 and Phase 9 planning should agree on **one** shared config radius value up front (Finding
  3) rather than Phase 9 inventing its own quarters-search radius independently — worth a one-line
  note in Phase 9's eventual plan pointing back here.
- The optional `MoveTowardsRestrictionGoal` addition (Finding 5) is worth including in Phase 6's
  plan as a cheap two-line hardening, but is not required to satisfy EMP-07's success criterion —
  `restrictTo` alone already prevents the "wanders off" failure mode through vanilla's own wander
  pathfinding.
- No spike/GameTest budget is needed for the mechanism itself during Phase 6 execution — only a
  manual in-game sanity check, same tier of effort as the phase's other already-well-understood
  traits (zombie/witch immunity), not the "budget generously" tier called out for the breeding gap.
