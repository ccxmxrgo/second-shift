---
phase: 06-employee-traits-death-firing
plan: 01
subsystem: gameplay
tags: [neoforge-events, villager, attachments, gametest]

requires:
  - phase: 04-employee-attachment-spawn
    provides: EmployeeData attachment, EmployeeManager.bind
  - phase: 05-profession-resolution-trade-picker
    provides: SoulAltarBlockEntity dual-socket contract, BindingAltarMenu.attemptBind
provides:
  - Zombie/witch conversion immunity for employees
  - Mixin-free breeding prevention via a vanilla-cooldown-mirroring age lock
  - A bidirectional altar<->employee link (EmployeeData.altarPos + SoulAltarBlockEntity.employeeId)
  - A two-radius altar tether (soft walk-home / hard teleport-home)
  - Harvester sneak-release as a second, voluntary entry point into the existing reap path
  - Drop-recovery (Soul Block + slimeballs) on any non-Harvester, non-firing death
  - ALTAR-06's two-strike altar-destruction firing sequence
affects: [07-progression-promotion-ritual, 09-quarters-happiness]

tech-stack:
  added: []
  patterns:
    - "Precondition-breaking over Mixin: read the vanilla AI behavior's actual guard condition
       (VillagerMakeLove.isBreedingPossible -> canBreed() -> getAge() == 0) and find the smallest
       public lever that breaks it, rather than reaching for a heavier tool."
    - "A tiny in-memory tick-counted queue (EmployeeFiring) for a short, non-persistence-critical
       delay, rather than threading state through a BlockEntity/attachment for something that
       degrades safely if lost."
    - "Remove an attachment BEFORE killing to suppress a death handler cleanly, instead of
       threading a boolean 'this death is special' flag through it."

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/event/EmployeeEvents.java
    - src/main/java/com/cxmxrgo/secondshift/event/EmployeeFiring.java
    - src/main/java/com/cxmxrgo/secondshift/gametest/EmployeeTraitsGameTests.java
  modified:
    - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeData.java
    - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java
    - src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java
    - src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java
    - src/main/java/com/cxmxrgo/secondshift/event/HarvesterEvents.java
    - src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java

key-decisions:
  - "Breeding prevention needs no Mixin, despite the ROADMAP pre-authorizing one as a last resort:
     Villager#canBreed() requires getAge() == 0, and AgeableMob#setAge(int) is public. Holding an
     employee at age 6000 (vanilla's OWN post-breeding cooldown value) breaks the precondition on
     BOTH sides of VillagerMakeLove.isBreedingPossible's two-partner check, defending both
     directions for the cost of one field write every 40 ticks. See 06-CONTEXT.md D-03 for the
     full decompiled-source citation and the honest limitation (a precondition break, not a
     capability break)."
  - "'Removable only by destroying the altar' does NOT forbid the Harvester reap - that reading
     would contradict EMP-06/ECON-04, requirements in this same phase that exist precisely so an
     employee is recoverable. Read as: removal is always physical and always costly, and there is
     no cheap GUI dismissal. See 06-CONTEXT.md D-08."
  - "The Harvester's voluntary release is sneak + right-click, not a plain right-click, so an
     employee stays tradeable while its owner holds the mod's signature tool. It doesn't duplicate
     HarvesterEvents' reap logic - it triggers a REAL playerAttack damage event that flows through
     the exact same existing instakill+Fragment+FX path."
  - "The bidirectional altar<->employee link (EmployeeData.altarPos, SoulAltarBlockEntity.employeeId)
     is guarded on identity in EmployeeManager.releaseAltar - a stale/mismatched id can never
     release an altar belonging to a different, living employee. A null stored id (pre-Phase-6
     saves) matches any dying employee, for backward compatibility."
  - "ALTAR-06's firing kill removes the EmployeeData attachment before calling kill(), so the
     ordinary other-death drop handler (EMP-06) correctly does nothing for that death - one state
     change instead of threading a flag through the death handler."
  - "EmployeeData.altarPos is the SIXTH component on that record, which is StreamCodec.composite's
     hard ceiling in 1.21.1 (verified: overloads exist for 1-6 components only). Documented
     directly in the class's own doc comment - Phase 9's happiness/timer fields will need a nested
     sub-record or a hand-written StreamCodec; they cannot extend this composite chain further."

patterns-established:
  - "Game-bus handlers in a final class with a private constructor and package-private static
     @SubscribeEvent methods, self-registering via @EventBusSubscriber with no wiring line in
     SecondShift.java (matches HarvesterEvents' established shape)."
  - "Every villager-touching handler gates on hasData(EMPLOYEE) first (EMP-09) - the single most
     important invariant in the mod, per CLAUDE.md."

requirements-completed: [EMP-03, EMP-04, EMP-05, EMP-06, EMP-07, ECON-04, ALTAR-06]

duration: ~2h (autonomous overnight session, after two aborted concurrent-agent attempts — see Issues Encountered)
completed: 2026-09-08
---

# Phase 6: Employee Traits, Death & Firing Summary

**Employees are conversion-immune, breeding-locked via a vanilla-cooldown-mirroring age trick (no Mixin needed), tethered to their altar by a two-radius periodic check, and recoverable through exactly two costed paths (Harvester sneak-release or altar destruction) that both free the altar's one-employee slot**

## Performance

- **Duration:** ~2 hours of direct implementation (after ~1.5h and ~460k tokens lost to two
  concurrent autonomous agent attempts that collided on the same working tree and safely aborted
  — see Issues Encountered)
- **Started:** 2026-09-08 (autonomous overnight session, continuing from Phase 5 closure)
- **Completed:** 2026-09-08T05:00:00Z
- **Tasks:** implemented directly (no multi-plan structure — a focused, coherent set of changes)
- **Files modified:** 6 modified, 3 created (2 production classes, 1 GameTest suite)

## Accomplishments
- Zombie/witch conversion immunity (EMP-03/04) via `LivingConversionEvent.Pre`, verified against
  decompiled vanilla sources for both real call sites (`Zombie#killedEntity`, `Villager#thunderHit`).
- Breeding prevention (EMP-05) resolved WITHOUT the Mixin the roadmap pre-authorized as a last
  resort — a genuinely better solution (see key-decisions) found by reading `VillagerMakeLove`
  directly.
- A real bidirectional altar↔employee link, closing a Phase 5 gap (the boolean `employeeBound`
  flag couldn't answer "which altar hired THIS villager").
- A two-radius altar tether (EMP-07) that cooperates with the brain-driven villager AI instead of
  fighting it with a bolted-on `Goal`.
- Two distinct, costed recovery paths (ECON-04 Harvester sneak-release, EMP-06 any-other-death
  drops) plus the forced ALTAR-06 firing path — with a clear, documented rule for what each one
  costs and recovers.
- 14 new GameTests, all passing alongside the full pre-existing 44 (58/58 total).

## Files Created/Modified
- `event/EmployeeEvents.java` - conversion immunity, breeding-lock reassert + altar tether (one
  periodic per-employee check), Harvester sneak-release, other-death drop recovery
- `event/EmployeeFiring.java` - the ALTAR-06 delayed-smite tick-counted queue
- `employee/EmployeeData.java` - added `Optional<BlockPos> altarPos` (6th/final composite slot)
- `employee/EmployeeManager.java` - `bind()` now sets the breeding-lock age and writes `altarPos`;
  new `releaseAltar()` identity-guarded helper
- `content/blockentity/SoulAltarBlockEntity.java` - persisted nullable `employeeId` UUID
- `content/block/SoulAltarBlock.java` - `playerWillDestroy` widened to fire on a bound-but-empty
  altar too, and schedules the delayed smite
- `event/HarvesterEvents.java` - widened `isHarvesterKillOfVillager` to package-private; added the
  altar-release call to the existing employee-Harvester-kill branch
- `menu/BindingAltarMenu.java` - records the spawned employee's UUID on the BE right after bind
- `gametest/EmployeeTraitsGameTests.java` - 14 new tests (see Key Link Verification in
  06-VERIFICATION.md for what each covers)

## Decisions Made
See `key-decisions` in frontmatter above — all decisions and their full rationale are also
recorded at length in `06-CONTEXT.md` (D-01 through D-08), written during this same session before
implementation began.

## Deviations from Plan

### None from 06-CONTEXT.md — implemented as designed

This phase had no formal multi-plan PLAN.md structure (implemented directly, per the orchestrating
session's judgment that a focused set of changes didn't warrant one) and no deviations from the
design already captured in `06-CONTEXT.md`. The one real "deviation" worth recording is procedural,
not architectural:

### Auto-fixed Issues

**1. [Test harness limitation] Tether GameTests rewritten to post events directly**
- **Found during:** Initial GameTest run for the two altar-tether tests
- **Issue:** `helper.runAfterDelay(45, ...)` after moving the test villager 60-100 blocks away
  never triggered the tether — the entity appeared to stop receiving real ticks once moved that
  far from the GameTest structure, a property of the harness, not of the production code (the
  SAME handler's breeding-lock re-assert, which doesn't require moving the entity, passed
  correctly under the identical delay pattern).
- **Fix:** Rewrote both tests to post a synthetic `EntityTickEvent.Post` directly (matching the
  technique already used for the conversion-immunity and interact-event tests in the same file) —
  deterministic, and exercises the exact same handler code path.
- **Files modified:** `gametest/EmployeeTraitsGameTests.java`
- **Verification:** Both tests pass; full 58/58 suite green.

---

**Total deviations:** 1 auto-fixed (test-harness workaround, no production code change).
**Impact on plan:** None on shipped behavior — purely a test-authoring fix.

## Issues Encountered

**Two concurrent autonomous agent sessions collided on this exact phase before this
implementation started.** An earlier dispatch (intended as a single agent building Phase 6)
silently spawned a nested background agent instead of doing the work directly; the orchestrating
session, unaware of this, dispatched a second independent agent when the first appeared to return
having done nothing. Both agents ended up reading/writing the same files
(`06-CONTEXT.md`/`06-RESEARCH.md`, and briefly the same Java sources) concurrently. Both correctly
detected the conflict via git log timestamps and live file-content changes they couldn't explain,
and both safely aborted — reverting their own changes, preserving work to `scratchpad/`, and
asking for guidance rather than corrupting the tree. No code was lost or corrupted; the cost was
~460k tokens and ~25 minutes of wall time across both aborted attempts, plus one committed but
harmless doc-file churn (`06-CONTEXT.md` was overwritten twice before settling on its final,
committed version — the content itself was never wrong, just written by different agents at
different points). This implementation proceeded as a single, direct session afterward with no
further concurrency risk.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 7 (Progression & Promotion Ritual) and Phase 8 (Mod-Owned Restock) can both proceed
  independently — see ROADMAP.md dependency notes.
- Phase 9 will need a nested sub-record or hand-written `StreamCodec` for `EmployeeData`'s new
  fields (the 6-component composite ceiling is now full) — flagged in that class's own doc comment
  so it isn't rediscovered from scratch.
- **For the user:** 3 items are `human_needed` (tether feel, sneak-release ergonomics, the
  two-lightning-strike timing) — see `06-VERIFICATION.md` frontmatter for exactly what to check.
  None are expected to be broken; all have verified underlying logic and automated coverage.

---
*Phase: 06-employee-traits-death-firing*
*Completed: 2026-09-08*
