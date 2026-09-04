# Spike: preventing employee villagers from breeding

**Question:** can `secondshift:employee`-flagged villagers be stopped from breeding using only
public NeoForge 21.1.248 events/APIs, with no Mixin and no `Villager` subclass?

**Answer: yes.** The CLAUDE.md "GAP" note undersold the position — there IS a clean, public,
event-based hook. It is not the hook the note guessed at (`BabyEntitySpawnEvent`, confirmed dead
end below), it's a different one: **cancel `EntityJoinLevelEvent` for the newborn villager,
correlated to an employee parent by exact position.** NeoForge's own `VillagerMakeLove` patch
already has a code path built for exactly this pattern, and a NeoForge maintainer-fixed bug
(neoforged/NeoForge#1606 / #1793) confirms "cancel the spawn of a villager child during breeding"
is a recognized, supported use of the public event API — just not the event CLAUDE.md guessed.

## Recommendation

**(a) Event-based mitigation, HIGH confidence on mechanism, MEDIUM-HIGH confidence on the
parent-correlation heuristic — recommended primary approach:**

Subscribe to `EntityJoinLevelEvent` (`NeoForge.EVENT_BUS`, both sides but only act on
`!level.isClientSide()`). When the joining entity is a baby `Villager`, scan a small radius
around its spawn position for a `Villager` carrying the `secondshift:employee` attachment. If
found, cancel the event.

```java
@SubscribeEvent
static void onEntityJoin(EntityJoinLevelEvent event) {
    if (event.getLevel().isClientSide()) return;
    if (!(event.getEntity() instanceof Villager child) || !child.isBaby()) return;

    // The child's position is set to the *initiating* parent's exact coordinates by
    // VillagerMakeLove#breed (villager.moveTo(parent.getX(), parent.getY(), parent.getZ(), ...))
    // immediately before addFreshEntityWithPassengers -> this event. The courting partner is
    // within 5.0 distanceSqr (~2.24 blocks) of that parent (VillagerMakeLove#tick guards on
    // distanceToSqr(villager) > 5.0), so a 3-block AABB around the child reliably contains
    // both parents when this join is actually a breeding event.
    AABB nearby = child.getBoundingBox().inflate(3.0);
    boolean employeeInvolved = event.getLevel().getEntitiesOfClass(Villager.class, nearby,
            v -> v != child && v.hasData(ModAttachments.EMPLOYEE.get()))
        .stream().anyMatch(v -> true);

    if (employeeInvolved) {
        event.setCanceled(true);
    }
}
```

This relies on a fix NeoForge already shipped into the 21.1.1.x line: `VillagerMakeLove#breed`
checks `villager.isAddedToLevel()` right after `addFreshEntityWithPassengers` and returns
`Optional.empty()` if the entity wasn't added — which is exactly what happens when
`EntityJoinLevelEvent` is cancelled. The POI/bed-ticket release path (`tryToGiveBirth`'s `else`
branch) then runs correctly, so no bed gets stuck reserved. Both parents' ages are still set to
6000 (adult) before this point, so cancelling doesn't create a tight retry loop — the same
~5-minute cooldown a normal successful birth would impose applies.

**Known limitation:** the correlation is positional, not a direct parent reference (the event API
gives no such reference — see Angle 1). A false positive (blocking an unrelated pair's breeding
because an employee happens to be standing within 3 blocks at the exact tick a different pair
gives birth) is possible in a dense village. Given this is a personal single-player mod with a
small number of employees, this is an acceptable, low-probability edge case — but flag it as a
one-tick, low-stakes false positive (worst case: one wild-villager pair's birth silently fails and
they retry in ~5 minutes), not a crash or corruption risk.

**(c) Practical workaround, not needed as primary but worth stacking as a UX polish:** additionally
erase `MemoryModuleType.BREED_TARGET` from an employee's `Brain` on tick (e.g. via
`LivingEvent.LivingTickEvent` filtered to employee villagers) so an employee never *visibly*
enters the courting animation with a wild villager it initiated toward. This alone is **not
sufficient** as the sole fix (see Angle 3 — a wild villager can independently target an employee
as its own `BREED_TARGET` and drive the courtship from its own side), but combined with the
`EntityJoinLevelEvent` cancel it removes the "employee stands there endlessly courting" visual
glitch for the half of pairings the employee itself initiates.

**(b) is not needed.** No Mixin/AT is required — everything above is public NeoForge event API,
consistent with the project's existing "no Mixin" policy.

---

## Evidence per angle

### 1. Is there any event that fires before/during/after villager breeding AI that can be cancelled?

Confirmed against the actual decompiled 21.1.248 source
(`net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java`, extracted from
`sourcesAndCompiledWithNeoForge_*.jar` in this machine's local Gradle cache — the exact NeoForm
output built for this project):

- `isBreedingPossible()` (gates whether the courting `Behavior` even starts) directly calls
  `villager.canBreed() && optional.get().canBreed()` — a **plain Java method call, no event
  indirection**. `EventHooks.java` (also extracted locally, 59KB, every NeoForge event hook) has
  **zero** matches for "breed" — there is no `EventHooks.canBreed`/`onVillagerBreed`/etc. This
  part of CLAUDE.md's finding is correct: there is no hook at the "can this behavior start" gate.
- However, `VillagerMakeLove#breed()` (the method that actually creates the child) does this:
  ```java
  Villager villager = parent.getBreedOffspring(level, partner);   // fires FinalizeSpawnEvent (see below)
  ...
  level.addFreshEntityWithPassengers(villager);                   // fires EntityJoinLevelEvent
  // Neo: If villager is blocked from spawning (e.g., FinalizeSpawnEvent), then breed should be unsuccessful
  if (!villager.isAddedToLevel()) return Optional.empty();
  ```
  This comment and check are a **NeoForge-authored addition** (source patch, confirmed via
  `neoforged/NeoForge@1.21.1` patch file
  `patches/net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java.patch`, one hunk, fetched
  directly from GitHub) specifically to make cancelling the child's spawn a clean no-op for
  breeding. This is the actual "clean hook" the CLAUDE.md gap was looking for — it exists, just
  one level lower (child-spawn cancellation) than "cancel the courting behavior directly."
- `Villager.getBreedOffspring()` calls `villager.finalizeSpawn(level, ..., MobSpawnType.BREEDING, null)`.
  This looks like a direct, non-eventful call in source — but NeoForge's `FinalizeSpawnEvent`
  javadoc states explicitly: *"In vanilla code, this event is injected by a transformer and not
  via patch, so calls cannot be traced via call hierarchy (it is not source-visible)."* This is a
  global bytecode-level redirect applied to essentially all vanilla `Mob#finalizeSpawn` call sites
  at class-load time (confirmed by NeoForge's own `CoreMods` transformer system, and by
  `EventHooks.finalizeMobSpawn`'s doc: *"Vanilla calls to Mob#finalizeSpawn are replaced with
  calls to this method via coremod... calls to this method will not show in an IDE."*). So
  `getBreedOffspring`'s `finalizeSpawn` call **does** fire `FinalizeSpawnEvent` with
  `MobSpawnType.BREEDING` at runtime, even though the decompiled/patched source shows a direct
  call. Cancelling via `FinalizeSpawnEvent#setSpawnCancelled(true)` (not `setCanceled` — see the
  javadoc distinction below) blocks the spawn the same way `EntityJoinLevelEvent` cancellation
  does.
- `level.addFreshEntityWithPassengers(villager)` → `PersistentEntitySectionManager#addNewEntity`
  (confirmed by direct grep of the extracted source):
  ```java
  if (p_entity instanceof Entity entity && NeoForge.EVENT_BUS.post(
          new EntityJoinLevelEvent(entity, entity.level(), worldGenSpawned)).isCanceled())
      return false;
  ```
  This is the simpler of the two cancellation points, and — critically — it fires **after**
  `villager.moveTo(parent.getX(), parent.getY(), parent.getZ(), 0.0F, 0.0F)` (`VillagerMakeLove`
  line 113, immediately before the `addFreshEntityWithPassengers` call on line 114), so at
  `EntityJoinLevelEvent` time the child's position is byte-for-byte identical to the initiating
  parent's position. This is what makes the proximity-based employee check in the recommendation
  reliable.

**Independent confirmation this is a real, supported technique (not just theoretically
possible):** `neoforged/NeoForge` issue **#1606**, *"[1.21.1] Cancelled villager breeding causes
beds to be wrongly occupied"* — a bug report from a mod developer who was already cancelling
villager breeding via `FinalizeSpawnEvent#setSpawnCancelled`. The fix, **PR #1793** (merged
2025-01-18, backported to the 1.21.1 line in late Jan/early Feb 2025), is exactly the
`if (!villager.isAddedToLevel()) return Optional.empty();` line found in the current 21.1.248
source above — i.e., the fix for this exact bug is what's shipping in the version this project
targets. This both (1) proves other mods already use this technique, and (2) proves the known
POI/bed-leak bug from doing so is already patched in 21.1.248, so the mitigation in this doc
inherits that fix "for free."

**On `BabyEntitySpawnEvent` specifically — CLAUDE.md's claim verified directly, not trusted:**
`BabyEntitySpawnEvent` is only constructed and posted in **one place** in the entire decompiled
21.1.248 source: `Animal#spawnChildFromBreeding` (`net/minecraft/world/entity/animal/Animal.java`,
a NeoForge source patch on that specific class):
```java
public void spawnChildFromBreeding(ServerLevel level, Animal mate) {
    AgeableMob ageablemob = this.getBreedOffspring(level, mate);
    final BabyEntitySpawnEvent event = new BabyEntitySpawnEvent(this, mate, ageablemob);
    ...
}
```
`Villager` does **not** extend `Animal`. Class hierarchy confirmed directly from source:
`Villager extends AbstractVillager` and (checked separately) `AbstractVillager extends
AgeableMob` — a sibling of `Animal`, not a descendant. `Animal extends AgeableMob` too, but
`Villager`'s breeding is driven entirely by the separate `VillagerMakeLove` AI `Behavior` class,
which never calls `spawnChildFromBreeding` and never constructs `BabyEntitySpawnEvent`. **The
CLAUDE.md claim is correct, now verified against source rather than assumed**, and it also
explains *why*: not a docs bug or version regression, but a structural fact of the class
hierarchy — `BabyEntitySpawnEvent` is an `Animal`-only event by construction, and `Villager` was
never in its audience. (`ICancellableEvent`, so it genuinely would work if it fired — javap
confirms `BabyEntitySpawnEvent extends Event implements ICancellableEvent` with the parents/child
accessible — it's just never posted for villagers.)

### 2. Could `LivingConversionEvent` (or a sibling) apply?

No, and this can be ruled out cleanly rather than just asserted. `LivingConversionEvent` models
one entity **transforming into a different entity/type** while conceptually remaining "the same"
individual (villager → zombie villager, villager → witch via lightning) — `EventHooks.canLivingConvert`
is invoked from exactly those two source patches (`Zombie.java.patch`, `Villager.java.patch`,
both re-confirmed present in the local sources jar). Breeding is not a conversion: it creates a
**new**, distinct entity (the child) while the two parents continue to exist unchanged (aside from
an age/cooldown reset). There is no "conversion" framing that fits, and no other
`LivingConversionEvent`-family firing site references breeding anywhere in `EventHooks.java` or
the `npc`/`animal`/`ai.behavior` packages searched. Ruled out with HIGH confidence.

### 3. Is breeding gated through Brain/POI in an interceptable way?

Partially, but not as the primary mechanism — no clean public hook exists at this layer, though
one piece (memory erasure) is a useful supplement:

- `VillagerMakeLove` requires `MemoryModuleType.BREED_TARGET` to be `VALUE_PRESENT` in the
  villager's `Brain` before the behavior's `checkExtraStartConditions` even returns true. This
  memory is populated by a `Sensor` (part of the vanilla sensor/behavior-package pipeline, not
  something NeoForge instruments with an event) that scans nearby villagers for eligible partners.
  There is no `EventHooks` entry for "villager evaluating a breed target" — confirmed by the same
  full-text search of `EventHooks.java` used in Angle 1.
- `Brain#eraseMemory(MemoryModuleType<?>)` is public (used by `VillagerMakeLove#stop` itself:
  `entity.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET)`), so a mod **can** reach in and
  clear this memory every tick for employee-flagged villagers via a `LivingTickEvent` handler —
  no Mixin needed, this is ordinary public API use, not an event hook. But as noted in Angle 5/the
  recommendation, this only stops the employee from being the *initiator*; it does not stop a wild
  villager from independently selecting the employee as *its own* `BREED_TARGET` and driving
  courtship from its side, since the wild villager's sensor and memory are untouched. So this is a
  visual/UX supplement, not a substitute for the spawn-cancel approach.
- `takeVacantBed()` (the POI/bed lookup) uses `ServerLevel#getPoiManager().take(...)` — a plain
  method call, not an event. There is no `PoiManagerTakeEvent` or similar in `EventHooks.java`.
  Denying bed access specifically to employees (Angle 4's suggestion) would require either
  intercepting this call (impossible without Mixin/AT, since `takeVacantBed` is a private method
  on a final-shaped `Behavior` subclass with no override point) or manipulating the POI manager's
  global state so the search never finds any bed for *any* villager near an employee — which is
  both imprecise (collateral-blocks unrelated villagers' breeding near an employee's house) and
  fragile (breaks bed-sleeping/work-site assignment side effects that also route through POI
  state). **Not recommended** — confirmed not viable as a clean public-API path.

### 4. Is `Villager#canBreed()` truly unoverridable without a subclass? Any indirect interception?

Confirmed directly from decompiled source:
```java
// AgeableMob.java
public boolean canBreed() {
    return false;
}
// Villager.java
@Override
public boolean canBreed() {
    return this.foodLevel + this.countFoodPointsInInventory() >= 12 && !this.isSleeping() && this.getAge() == 0;
}
```
Both are `public`, neither is `final`. In principle a subclass could override either. But per this
project's existing (correct) architectural decision, `Villager` is never subclassed — so this
route is out by design, not because the method itself is technically sealed. There is no `Event`
wrapping either call site (`isBreedingPossible()` in `VillagerMakeLove` calls `canBreed()`
directly on both the villager and its `BREED_TARGET` partner, with no `EventHooks` indirection —
re-confirmed by the same grep as Angle 1). Manipulating the three inputs to `Villager#canBreed()`
(`foodLevel`, `isSleeping()`, `getAge()`) to permanently fail the check was considered and
rejected: `foodLevel` is legitimately needed for the mod's other villager-feeding interactions and
forcing it low would be a fragile, easily-defeated (a player could still feed the villager back
above 12) side effect; forcing `isSleeping()` true or age nonzero has larger unwanted behavioral
side effects (a permanently "sleeping" or permanently-aging villager breaks pathing/trading). The
spawn-cancel approach in the recommendation avoids all of this by not needing `canBreed()` to ever
return false — it lets the courting animation play out (mildly odd, but harmless) and only
prevents the actual child from coming into existence.

### 5. Existing open-source mods that solve this without Mixin

- No public mod repository was found that specifically flags-and-blocks breeding for a subset of
  villagers via a data attachment (searched GitHub/web for `canBreed`/`EntityJoinLevelEvent`/
  villager-breeding-prevention mod source; results returned general villager-overhaul mods
  (Villager Overhaul, Create: Better Villager, MORE VILLAGERS) with no visible source snippet
  addressing this specific mechanism, and one breeding-cooldown-tuning mod unrelated to per-entity
  blocking).
- The one directly relevant, code-level result is **neoforged/NeoForge#1606** and its fix
  **#1793** (see Angle 1) — not a third-party mod, but the NeoForge project's own issue tracker
  showing a real developer already cancelling villager breeding via `FinalizeSpawnEvent`, and the
  maintainers treating that as a legitimate use case worth fixing supporting infrastructure for
  (the POI-leak bug), rather than telling the reporter to use Mixin instead. This is strong
  circumstantial confirmation that `FinalizeSpawnEvent`/`EntityJoinLevelEvent` cancellation is the
  maintainer-sanctioned way to block specific villager breeding — even though no single mod's
  source implementing the "which parent is flagged" correlation was found in public search
  results, so the specific proximity-based correlation technique in this doc's recommendation is
  original synthesis, not a copied pattern.

---

## Sources

- Local decompiled/patched NeoForge 21.1.248 source, extracted from
  `C:\Users\user\.gradle\caches\neoformruntime\intermediate_results\sourcesAndCompiledWithNeoForge_cca44f8311ba9c1569e5cd6465f2cd3b8cb86277_output.jar`
  (the exact NeoForm output this project's Gradle build already produced) —
  `net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java`,
  `net/minecraft/world/entity/npc/Villager.java` (`canBreed`, `getBreedOffspring`, `finalizeSpawn`),
  `net/minecraft/world/entity/AgeableMob.java` (`canBreed`),
  `net/minecraft/world/entity/animal/Animal.java` (`spawnChildFromBreeding`, `BabyEntitySpawnEvent` firing site),
  `net/minecraft/world/level/entity/PersistentEntitySectionManager.java` (`EntityJoinLevelEvent` firing site),
  `net/neoforged/neoforge/event/EventHooks.java` (`finalizeMobSpawn`, full-text searched for "breed" — zero hits),
  `net/neoforged/neoforge/event/entity/EntityJoinLevelEvent.java`,
  `net/neoforged/neoforge/event/entity/living/FinalizeSpawnEvent.java`. **HIGH** — this is the
  actual patched Minecraft source NeoForge compiles against for 21.1.248, not documentation.
- `javap -c -p` disassembly of `Villager.getBreedOffspring` from the same jar (bytecode-level
  confirmation of the `finalizeSpawn` call site and its exact invocation, cross-checked against
  the `EventHooks.finalizeMobSpawn` transformer-redirect claim). **HIGH**
- `javap -p` against `neoforge-21.1.248-{universal}.jar` — confirmed `BabyEntitySpawnEvent extends
  Event implements ICancellableEvent`, its `Mob`/`Mob`/`AgeableMob` fields and accessors; confirmed
  `LivingConversionEvent` shape. **HIGH**
- `https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/patches/net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java.patch`
  — fetched directly, single hunk, matches the local decompiled source exactly. **HIGH**
- `https://github.com/neoforged/NeoForge/issues/1606` and
  `https://github.com/neoforged/NeoForge/pull/1793` — fetched directly; issue reports the
  bed-leak bug from cancelling villager breeding via `FinalizeSpawnEvent`, PR fixes it with the
  `isAddedToLevel()` check, merged 2025-01-18, backported to 1.21.1 in early 2025 — well before
  this project's 21.1.248 (a much later 1.21.1 build), so the fix is confirmed present (and was
  independently re-confirmed present by reading the local decompiled source directly, not just
  trusting the backport claim). **HIGH**
- Web search across GitHub/CurseForge for prior art on "block specific villager from breeding,
  NeoForge, no Mixin" — no third-party mod source found implementing this exact pattern; the
  #1606/#1793 exchange is the only concrete code-level precedent. **MEDIUM** (absence-of-evidence
  search, not exhaustive — a mod doing this privately/unpublished would not surface)

### Confidence summary

| Claim | Confidence |
|---|---|
| `BabyEntitySpawnEvent` never fires for villager breeding | **HIGH** — verified by finding its one and only firing site is `Animal#spawnChildFromBreeding`, and `Villager` does not extend `Animal` |
| `LivingConversionEvent` does not apply to breeding | **HIGH** — ruled out by definition (new entity, not a transformation) and confirmed no breeding-related firing site exists |
| `EntityJoinLevelEvent` cancellation stops the villager child from being added, and is safe (POI leak already fixed) | **HIGH** — confirmed by source (`PersistentEntitySectionManager#addNewEntity`) and by the shipped `isAddedToLevel()` fix for #1606/#1793 |
| `FinalizeSpawnEvent` (`MobSpawnType.BREEDING`) also fires for the villager child via `getBreedOffspring`, despite not being source-visible | **HIGH** — confirmed by NeoForge's own javadoc describing the coremod redirect, and independently corroborated by #1606's reporter already using it for this exact purpose |
| The position-based employee-parent correlation at `EntityJoinLevelEvent` time is reliable enough for a personal, low-villager-density world | **MEDIUM-HIGH** — mechanism (exact position copy via `moveTo`) is verified in source; the "how often will an unrelated employee happen to stand within 3 blocks" risk assessment is a judgement call, not something verifiable by static analysis |
| No published mod already implements this exact per-entity breeding block | **MEDIUM** — negative web-search result, not proof of absence |

---

## What this means for Phase 6 planning

**STATE.md currently budgets this as a "LOW confidence" spike. That should be upgraded to HIGH
confidence with a defined implementation, not left as an open risk.** Concretely, for whichever
phase implements the "cannot breed" employee trait:

1. **No Mixin is needed.** The project's "no Mixin" policy holds for this feature too — remove
   this as a candidate exception in CLAUDE.md's Alternatives Considered table (the row currently
   says "Only for the breeding gap, and only after `FinalizeSpawnEvent`/`EntityJoinLevelEvent`
   approaches are proven insufficient" — this spike proves them sufficient).
2. **Implementation is a single `EntityJoinLevelEvent` handler** (see the code sketch above),
   registered like the existing `LivingConversionEvent.Pre` handler for the zombify/lightning
   traits — same file/pattern (`EventHooks`-adjacent game-bus subscriber), same attachment check
   (`villager.hasData(ModAttachments.EMPLOYEE.get())`), same order of magnitude of code.
3. **One thing to verify empirically once implemented** (can't be proven by static source
   reading alone): trigger a breeding attempt in-game between an employee and a wild villager
   (and separately, two employees) and confirm (a) no child appears, (b) the bed POI is correctly
   released afterward (not stuck reserved — this is exactly the bug #1793 fixed, so it should be
   fine, but a 30-second in-game check costs nothing and removes the last bit of uncertainty),
   and (c) a normal wild-villager-only pair nearby is *not* incorrectly blocked by the proximity
   check. Budget this as a normal implementation-phase verification step, not a research risk.
4. **Optional polish, not required for correctness:** add the `MemoryModuleType.BREED_TARGET`
   erasure-on-tick supplement (Angle 3) if the courting-animation-with-no-payoff looks visually
   odd in practice — skip it if it doesn't bother the player, since it adds a tick handler for a
   purely cosmetic gap.

