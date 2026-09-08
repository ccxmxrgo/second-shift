# Phase 6: Employee Traits, Death & Firing - Context

**Gathered:** 2026-09-08
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 5 delivered the bind: an altar with both sockets filled spawns a named employee with
hand-picked trades. What it did not deliver is any of the *character* of an employee — right now a
bound employee is an ordinary villager that happens to carry an attachment. It can be zombified, hit
by lightning and turned into a witch, bred into a population, and it will wander off across the world
like any villager. If it dies, the four Soul Fragments and the job block that paid for it evaporate
with zero recourse, and its altar is left permanently occupied by a corpse that no longer exists.

Phase 6 makes an employee a protected, non-fungible, *recoverable* character, and gives the altar a
real bidirectional link to the entity it hired.

**Requirements:** EMP-03, EMP-04, EMP-05, EMP-06, EMP-07, ECON-04, ALTAR-06.

**In scope:**
- Zombie-conversion and lightning/witch-conversion immunity via `LivingConversionEvent.Pre`, gated
  strictly on the `EmployeeData` attachment (EMP-03, EMP-04).
- Breeding prevention for employees, by precondition-breaking only — no Mixin (EMP-05).
- A bidirectional altar↔employee link: an `altarPos` on `EmployeeData` and an `employeeId` UUID on
  `SoulAltarBlockEntity`, both persisted. Phase 5 shipped only a one-bit `employeeBound` flag, which
  is not enough to *release* an altar when its employee dies.
- A "kept near their altar" tether (EMP-07).
- Harvester recovery of your own employee: exactly 1 Soul Fragment, and the altar's slot is freed
  (ECON-04).
- Drop-recovery on every other death: Soul Block + slimeballs, and the altar's slot is freed
  (EMP-06).
- Altar destruction as the forced-removal path: consumed materials, half a heart to the breaker
  only, a delayed cosmetic lightning strike that instakills the employee (ALTAR-06).

**Out of scope (later phases):**
- Quitting from sustained unhappiness (HAPP-06, Phase 9) — that is a *third* removal path with its
  own semantics (revert to a wild villager rather than die). Phase 6 deliberately builds the
  altar-release primitive that HAPP-06 will reuse, but does not build quitting.
- Themed `Component.translatable` messaging for death/firing events (POL-08, Phase 10). Phase 6 adds
  no new lang keys, so the `ModRegistrySelfCheck` `EXTRA_LANG_KEYS` guardrail needs no extension
  this phase.
- Config toggles for each trait immunity (POL-06, Phase 10) — the immunities are unconditional now.
- Restock, promotion, happiness (Phases 7-9).

</domain>

<decisions>
## Implementation Decisions

### D-01: Bidirectional altar↔employee link (the enabling prerequisite)

Phase 5's `SoulAltarBlockEntity.employeeBound` is a boolean. Every Phase 6 death path needs to answer
"which altar hired *this* villager?" and "is the villager this altar hired still alive?", which a
boolean cannot answer. Therefore:

- `EmployeeData` gains a sixth field, `Optional<BlockPos> altarPos`.
- `SoulAltarBlockEntity` gains a persisted, nullable `UUID employeeId`, alongside the existing
  `employeeBound` flag (kept, so pre-Phase-6 saves still read as occupied).

**Rationale for `Optional<BlockPos>` rather than a required field:** `EmployeeData.EMPTY` is the
attachment's default value and has no altar; an `Optional` models that honestly instead of inventing
a sentinel position.

**A hard constraint discovered during research:** `StreamCodec.composite` in 1.21.1 has overloads for
1 through **6** components and no more. `EmployeeData` had 5. `altarPos` takes the sixth and last
slot. Any future field on `EmployeeData` (happiness, timers — Phase 9) **cannot** be added by
extending the existing composite chain; it will need a nested sub-record or a hand-written
`StreamCodec`. This is written down here because Phase 9 will hit it.

**Release semantics (`EmployeeManager.releaseAltar`):** clearing an altar is guarded on identity —
the altar is only released if its stored `employeeId` matches the dying employee, or if it is `null`
(a pre-Phase-6 save that has an `employeeBound` flag but no recorded UUID). This means a stale
`altarPos` on some other altar's employee can never free an altar that legitimately belongs to a
different, living employee.

**No dimension key is stored.** An employee has no way to change dimension under its own power
(villagers do not use portals), and every altar-side operation re-validates that the block at
`altarPos` is actually a Soul Altar before acting. A player who hauls an employee through a portal in
a boat gets a silently detached employee rather than a cross-dimension bug — an acceptable,
non-destructive failure mode for a single-player personal mod.

### D-02: Conversion immunity (EMP-03, EMP-04)

Cancel `LivingConversionEvent.Pre` whenever the converting entity carries the `EmployeeData`
attachment. Verified against the decompiled 1.21.1 + NeoForge sources: **both** relevant vanilla call
sites are gated on `EventHooks.canLivingConvert`, which is exactly what this event backs —
`Zombie#killedEntity` for villager→zombie-villager and `Villager#thunderHit` for villager→witch.

Deliberately **not** filtered by `getOutcome()`. An employee should be immune to *every* conversion,
including any a future NeoForge version or another mod routes through the same hook. The single
`hasData` gate keeps wild villagers untouched (EMP-09).

Note: a zombie killing an employee on Easy difficulty never attempts conversion at all — vanilla
simply kills the villager. That path is covered by EMP-06's drop recovery, not by this immunity.

### D-03: Breeding prevention (EMP-05) — the phase's one real research risk, resolved

`CLAUDE.md` documents this as a **GAP**: `BabyEntitySpawnEvent` is never fired for villager breeding,
and `Villager#canBreed()` is public but unoverridable without the forbidden entity subclass. The
roadmap budgeted a LOW-confidence spike here and pre-authorized a Mixin as a last resort. **No Mixin
was needed.** Reading `VillagerMakeLove` directly produced a clean precondition break:

```java
private boolean isBreedingPossible(Villager villager) {
    ...
    return BehaviorUtils.targetIsValid(...) && villager.canBreed() && optional.get().canBreed();
}
```

`isBreedingPossible` requires `canBreed()` on **both** partners, and is re-checked in
`checkExtraStartConditions` *and* `canStillUse` — so breaking one side of a pair is sufficient to
prevent the pair from ever breeding, in either direction, at start and mid-behavior.

And `Villager#canBreed()` is:

```java
return this.foodLevel + this.countFoodPointsInInventory() >= 12 && !this.isSleeping() && this.getAge() == 0;
```

Of those three terms, `getAge() == 0` is the one reachable from a mod: `AgeableMob#setAge(int)` is
public. A **positive** age on an adult is vanilla's own post-breeding cooldown state — it is exactly
what `VillagerMakeLove.breed` writes onto both parents (`setAge(6000)`) after a successful birth. It
decrements by one per tick in `AgeableMob#aiStep` and has no other effect on an adult: no rendering
change, no AI change, no trade effect. (`isBaby()` is `age < 0`, so a positive age is never mistaken
for a baby.)

**Decision:** employees are held at a positive age. `EmployeeManager.bind` sets `setAge(6000)` at
spawn, and the periodic employee tick re-asserts it to 6000 whenever it decays below 1200. The
re-assert floor exists because age ticks down; 1200 ticks (60s) of headroom against a 2-second
re-assert interval is an enormous margin.

**The honest limitation, stated plainly:** this is a *precondition* break, not a *capability* break.
It relies on vanilla's `VillagerMakeLove.isBreedingPossible` continuing to require `canBreed()`, and
on nothing else setting an employee's age back to 0 within the re-assert window. It does not stop a
mod that spawns baby villagers by some other route, and it does not stop `/data` edits. For the
stated requirement — "two employees with a bed and bread nearby for 5+ minutes produce no baby
villager" — it is airtight, and it is strictly better than the alternatives considered
(`FinalizeSpawnEvent` filtered on `MobSpawnType.BREEDING` would have suppressed **all** villager
breeding world-wide, violating EMP-09; a Mixin was explicitly a last resort).

Rejected alternative: erasing the `BREED_TARGET` memory each tick. It works, but only defends the
employee's own brain — a wild villager holding `BREED_TARGET` pointing at an employee runs its own
copy of the behavior. The age lever defends both directions because of the two-sided `canBreed()`
check, and costs one field write every 40 ticks instead of a memory erase every tick.

### D-04: "Kept near their altar" (EMP-07) — a two-radius tether, not an AI goal

Villagers are brain-driven, not goal-selector-driven; bolting a `Goal` onto a `Villager` fights the
brain rather than cooperating with it, and the roadmap explicitly warns against faking a `JOB_SITE`
memory. Instead: a periodic check on `EntityTickEvent.Post`, gated on the attachment, running every
40 ticks (2 seconds) using the entity's own `tickCount` as the phase offset — so the cost is
per-employee, not a world-wide entity sweep, and employees naturally self-stagger.

Two radii, both measured horizontally-and-vertically from the altar block:

| Radius | Distance | Behavior |
|---|---|---|
| Soft | **24 blocks** | `getNavigation().moveTo(altar, 0.6)` — the employee walks itself home. |
| Hard | **48 blocks** | Teleport to the altar's spawn position, the same `altarPos.above(2)` the bind used. |

**Why 24/48:** 24 blocks is roughly a comfortable "shop floor" — bigger than any reasonable altar
room, small enough that an employee never disappears over a hill. It is also outside the 16-block
default entity tracking range, so a player standing at the altar never sees the nudge fire for an
employee they can see. 48 is double that and matches vanilla's own villager POI search radius, so an
employee that has gone genuinely feral (fell in a river, got pathed into a cave) is recovered rather
than lost. Both are constants in one place, trivially tunable after playtesting.

**Tether suspension:** the tether does not fire while the employee `isPassenger()` (boat, minecart)
or `isLeashed()`. Moving an employee deliberately is a player action, not wandering; fighting the
player's boat would be obnoxious and is not what EMP-07 asks for. Unleashing or disembarking beyond
the hard radius brings it straight home on the next check.

**Altar validation:** the tether is skipped entirely unless the block at `altarPos` is still a Soul
Altar. This is what makes the missing dimension key (D-01) safe, and it means a detached employee
just behaves like a normal villager rather than teleporting to a nonsense coordinate.

### D-05: Harvester recovery (ECON-04) — the *voluntary* release path

Phase 2's `HarvesterEvents` already instakills any `Villager` hit with the Harvester and drops
exactly 1 Soul Fragment. An employee is a `Villager`, so **ECON-04's yield is already correct today**
and needs no change: 1 Fragment, no Soul Block, no bonus. The only thing missing is the altar link,
which did not exist. So the employee case differs from the wild-villager case in exactly one way —
it also releases the altar — as required.

**On the "right-click to let go" framing.** The roadmap's own success criterion is worded as a kill
("an employee killed by the Harvester drops exactly 1 Soul Fragment"), and a plain right-click with
the Harvester in hand must keep opening the vanilla trade screen — a release bound to plain
right-click would make an employee untradeable while its owner holds the mod's signature tool. The
release is therefore **sneak + right-click with the Harvester**, which is a no-op in vanilla and
cannot shadow trading. It does not duplicate the reap logic: it applies a real
`playerAttack` damage source, which `DamageSource#getWeaponItem()` resolves to the held Harvester, so
the existing Phase 2 instakill + 1-Fragment + FX path runs verbatim. One mechanic, two ergonomic
entry points, one yield.

### D-06: Drop-recovery on every other death (EMP-06)

An employee killed by anything that is not the Harvester and not altar-destruction drops **1 Soul
Block + 2 slimeballs**, and its altar is released.

Spawned from `LivingDeathEvent` via `addFreshEntity`, not from `LivingDropsEvent` — the same
reasoning Phase 2 wrote down for the Fragment guarantee: `LivingDropsEvent` is entirely suppressed by
`doMobLoot=false` and by `shouldDropLoot()`, and losing a four-Fragment employee to a game rule would
be a genuinely bad outcome. Slimeball count is a fixed 2 rather than a random range, so the GameTest
can assert an exact number and so the recovery value is predictable.

**Why the whole Soul Block comes back here but only a Fragment comes back from the Harvester:** the
Harvester reap is a *choice* made at leisure, so it costs 3 of the 4 Fragments — deciding to let an
employee go should sting. Dying to a creeper is not a choice; making bad luck cost the same as a
decision would just be punishing. This inversion (accidents refund more than deliberate release) is
intentional, and it is also what makes altar-destruction — which refunds *nothing* — read as the
harshest of the three.

### D-07: Altar destruction (ALTAR-06) — the *forced* removal path

Extends Phase 2's existing D-04 charged-break handler rather than adding a parallel one. The
condition widens from "the altar holds a Soul Block" to "the altar holds a Soul Block **or** has a
bound employee", because on a bound altar the Soul Block was already consumed at bind time — which
means, without this change, breaking a bound altar today is completely free.

On breaking a bound altar: both sockets are cleared, the altar block and everything in it is
suppressed from dropping (the existing `brokenWhileCharged` mechanism), the breaking player takes
exactly 1.0F of armour-bypassing magic damage — half a heart, no block damage, no environment damage,
no explosion — and a visual-only `LightningBolt` fires at the altar.

**10 ticks (0.5 s) later**, a second visual-only `LightningBolt` strikes the bound employee and kills
it. The delay is real, not cosmetic sugar: it is what makes the two strikes read as *cause and
effect* rather than one event. It is implemented as a tiny tick-counted queue drained on
`ServerTickEvent.Post`, keyed on the employee's UUID and dimension, and it degrades safely — if the
employee is gone, unloaded, or already dead when the timer fires, the entry is simply dropped.

**The firing kill must not drop a Soul Block.** Rather than threading a "this death is a firing" flag
through the death handler, the smite **removes the `EmployeeData` attachment before killing**. The
villager dies as an ordinary villager, so D-06's handler correctly does nothing, and the altar link
is already gone. One state change instead of two, and no possibility of the flag and the attachment
disagreeing.

### D-08: What "removable only by destroying the altar" actually gates

This is the phase's one genuine interpretive question, since Harvester-recovery obviously also
removes an employee. The resolution:

**Altar destruction is the *forced* removal path. Harvester reap is the *consensual* one. There is no
third path, and in particular there is no "fire" button anywhere in a GUI.**

The requirement's intent is recorded in PROJECT.md's Key Decisions table: *"Makes hiring a real
commitment; no in-GUI undo keeps the soul cost meaningful."* The thing being ruled out is a cheap,
costless, at-a-distance dismissal. Both surviving paths honour that:

| Path | Costs | Recovers | Requires |
|---|---|---|---|
| Harvester reap | the altar's contents stay spent; 3 of 4 Fragments lost | 1 Soul Fragment | walking up to the employee and killing it yourself |
| Altar destruction | the altar block, the Soul Block, the job block, ½ heart | nothing | destroying the altar |
| *(a GUI fire button)* | — | — | **does not exist and will not be built** |

Read literally as "the only way to get rid of an employee is to break its altar", the requirement
would forbid the Harvester reap — but that reading directly contradicts EMP-06/ECON-04, which are
requirements in this same phase and which exist precisely so an employee is recoverable. The
consistent reading is the one above: *removal is always physical and always costly*. Altar
destruction is the path that works when you cannot reach the employee, or do not want its soul back.

**Both paths free the altar's one-employee slot**, and so does dying to a creeper. An altar whose
employee is dead must be re-bindable; the alternative — an altar permanently occupied by an entity
that no longer exists — is a soft-lock, and no reading of ALTAR-05 ("each altar is bound to exactly
one employee") is served by enforcing a link to a corpse.

### Claude's Discretion (exercised)

- Tether radii (24/48) and the 40-tick check interval — chosen above, single-constant tunable.
- Slimeball count on death recovery — fixed at 2.
- Breeding-lock age (6000) and re-assert floor (1200) — chosen to mirror vanilla's own cooldown.
- Firing delay — 10 ticks, matching ALTAR-06's "~0.5s".
- No new lang keys / no player-facing messages this phase; themed messaging is POL-08 (Phase 10).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase contract
- `.planning/ROADMAP.md` "Phase 6: Employee Traits, Death & Firing" — goal, 5 success criteria, and
  the risks block (which pre-authorised a Mixin for breeding; D-03 above did not need it).
- `.planning/REQUIREMENTS.md` — EMP-03/04/05/06/07, ECON-04, ALTAR-06.
- `./CLAUDE.md` §10 "Employee traits — the event hooks that make them possible" — the hook table,
  including the breeding GAP row that D-03 closes.

### Existing shipped code this phase extends
- `src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java` — the Phase 2 reap. Its
  `isHarvesterKillOfVillager` predicate becomes the discriminator for D-06's inverted branch; its own
  javadoc already anticipates this ("Phase 6 (ECON-04): a separate branch keyed on `hasData`").
- `src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java` — `playerWillDestroy` /
  `getDrops` / `onRemove`, the Phase 2 D-04 charged-break behaviour ALTAR-06 widens. Its javadoc
  explicitly defers ALTAR-06 to this phase.
- `src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java` — the
  dual-socket + `employeeBound` contract from 05-01.
- `src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java` — the non-negotiable bind
  ordering (`setVillagerData` → `refreshBrain` → `setVillagerXp` → `setOffers` → attachment →
  `addFreshEntity`). The breeding-lock `setAge` must not disturb it.
- `src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java` `attemptBind` — the sole caller
  that marks an altar occupied; it is where the employee UUID must now be recorded.

### Verified API surface (see 06-RESEARCH.md)
- `VillagerMakeLove` / `Villager#canBreed` / `Villager#thunderHit` / `Zombie#killedEntity` —
  decompiled, quoted, and cited in `06-RESEARCH.md`.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `HarvesterEvents.isHarvesterKillOfVillager` — already exactly the predicate D-06 needs to invert;
  it only needs widening from `private` to package-private (both classes live in `…secondshift.event`).
- `SoulAltarBlockEntity.markBrokenWhileCharged()` / `wasBrokenWhileCharged()` + `SoulAltarBlock.getDrops`
  — the existing drop-suppression mechanism ALTAR-06 reuses verbatim; no new suppression path needed.
- `SoulAltarBlock.playerWillDestroy`'s visual-only `LightningBolt` (`setVisualOnly(true)`) — exactly
  the "no fire, no collateral" bolt ALTAR-06 asks for, reused for the delayed employee strike.

### Established Patterns
- Game-bus handlers in a `@EventBusSubscriber(modid = SecondShift.MODID)` final class with a private
  constructor and package-private `static` `@SubscribeEvent` methods, self-registering with no wiring
  line in `SecondShift` (`HarvesterEvents` is the template).
- Every villager-touching handler gates on `hasData(ModAttachments.EMPLOYEE.get())` first, so wild
  villagers are untouched (EMP-09) — this is the single most important invariant in the mod.
- Persistence: hand-rolled `saveAdditional`/`loadAdditional` with a `DATA_VERSION` int and
  `tag.contains(KEY)` guards so older saves load with sensible defaults.
- GameTests: `@GameTestHolder(SecondShift.MODID)` + `@PrefixGameTestTemplate(false)`,
  `@GameTest(template = "empty")`, `helper.assertTrue/assertFalse/succeed()`, no raw JUnit.

### Integration Points
- `EmployeeData` record + `CODEC` + `STREAM_CODEC` gain a sixth component (composite is now full).
- `EmployeeManager.bind` writes `altarPos` into the attachment and sets the breeding-lock age.
- `BindingAltarMenu.attemptBind` records the spawned villager's UUID on the block entity.
- `SoulAltarBlock.playerWillDestroy` widens its trigger condition and schedules the smite.
- `HarvesterEvents` — one predicate visibility change only; the reap itself is untouched.

</code_context>

<specifics>
## Specific Ideas

- The two lightning strikes when an altar is destroyed should read as one sentence: the altar
  detonates, half a second passes, and *then* the employee is struck. Firing someone should feel
  deliberate and slightly awful, not like a particle effect.
- An employee that dies to a creeper leaving a Soul Block on the floor is the moment the mod stops
  feeling like it can waste your time.

</specifics>

<deferred>
## Deferred Ideas

- **Per-trait config toggles** (POL-06, Phase 10) — the immunities are currently unconditional.
- **Themed messages on death / firing** (POL-08, Phase 10) — no new lang keys this phase.
- **Quitting from unhappiness** (HAPP-06, Phase 9) — a third removal path with different semantics
  (revert to a wild villager rather than die). It will reuse `EmployeeManager.releaseAltar` and the
  attachment-removal idiom the smite establishes.
- **Employee-specific death sound / particles** (POL-05, Phase 10).
- **A `EmployeeData` field for happiness/timers** (Phase 9) — blocked by the 6-component
  `StreamCodec.composite` ceiling documented in D-01; will need a nested record or a hand-written
  `StreamCodec`.

</deferred>

---

*Phase: 06-employee-traits-death-firing*
*Context gathered: 2026-09-08*
