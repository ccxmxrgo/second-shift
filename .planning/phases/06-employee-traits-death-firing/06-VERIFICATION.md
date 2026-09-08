---
phase: 06-employee-traits-death-firing
verified: 2026-09-08T05:00:00Z
status: human_needed
score: 5/5 success criteria code-verified; 3 items pending human playtest
human_verification:
  - test: "Kill an employee with the Harvester (a plain hit, not sneak) — should still work exactly as before, dropping exactly 1 Soul Fragment and freeing the altar."
    expected: "1 Soul Fragment drops, the altar can be re-bound afterward."
    why_human: "Covered by GameTest via a direct hurt() call with a HarvesterItem-resolving DamageSource, but never exercised through a REAL held-item melee swing in a live client."
  - test: "Sneak + right-click a bound employee while holding the Harvester (the new voluntary release gesture)."
    expected: "The employee dies instantly, drops exactly 1 Soul Fragment, and does NOT open the trade screen. A plain (non-sneak) right-click with the Harvester in hand must still open the trade screen normally."
    why_human: "GameTest posts a synthetic PlayerInteractEvent.EntityInteract directly — never exercised via a real sneak+click in a live client. This is the phase's one genuinely new player-facing interaction."
  - test: "Walk an employee's altar's owner away, let the employee wander (or push it) more than ~24 blocks from its altar, and watch what happens; then push it past ~48 blocks."
    expected: "Past 24 blocks it should visibly start walking back toward the altar on its own; past 48 blocks it should teleport home instead."
    why_human: "GameTest posts a synthetic EntityTickEvent.Post directly (real chunk-distance ticking in the test harness doesn't work at the radii involved — see 06-01-SUMMARY.md Issues Encountered). The underlying logic is verified, but the FEEL (does 24/48 blocks feel right, does the walk-home look natural) needs a human's judgment call, and the constants are trivially tunable if not."
  - test: "Break a bound altar and watch the two-lightning-strike sequence."
    expected: "Lightning + thunder at the altar immediately, half a heart of damage to you, then ~0.5s later a second lightning strike on the employee that kills it (no drops from that second death)."
    why_human: "GameTest verifies the delay and no-drop behavior mechanically (via TICK-based assertions), but the actual VISUAL/AUDIO timing and whether it reads as 'cause and effect' the way 06-CONTEXT.md intends is a feel judgment only a human can make."
---

# Phase 6: Employee Traits, Death & Firing Verification Report

**Phase Goal:** Employees are protected, non-fungible characters — immune to zombie/witch conversion and breeding, kept near their altar, recoverable via the Harvester, drop-recoverable on other deaths, and removable only by destroying the altar.

**Verified:** 2026-09-08T05:00:00Z (autonomous overnight session, self-verified — no separate verifier agent dispatched for this phase, given the session's earlier lesson about concurrent-agent collisions; verification performed directly by the same session that implemented the phase, with the same rigor: independent GameTest re-run, not taken on faith)

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A zombie killing an employee never produces a zombie villager; lightning never turns one into a witch | ✓ VERIFIED | `EmployeeEvents.onConversionPre` cancels `LivingConversionEvent.Pre` whenever `hasData(EMPLOYEE)`, unconditionally (not filtered by outcome type). GameTests `employee_is_immune_to_zombie_conversion`, `employee_is_immune_to_witch_conversion`, and `wild_villager_conversion_is_untouched` (EMP-09 regression) all pass. |
| 2 | Two employees with a bed and food nearby for 5+ minutes produce no baby villager | ✓ VERIFIED (mechanism) / pending human feel-check on the real 5-minute timescale | `Villager#canBreed()` requires `getAge() == 0`; employees are held at `EmployeeManager.BREEDING_LOCK_AGE` (6000, vanilla's own post-breed cooldown value) and re-asserted whenever it decays below 1200 via `EmployeeEvents.onEntityTick`'s 40-tick periodic check. Since `VillagerMakeLove.isBreedingPossible` requires `canBreed()` on BOTH partners, breaking one side prevents breeding in either direction. GameTests `employee_spawns_with_breeding_lock_age_and_cannot_breed` and `breeding_lock_is_reasserted_after_decaying_below_the_floor` pass. |
| 3 | Non-Harvester death drops Soul Block + slimeballs; Harvester death drops exactly 1 Fragment | ✓ VERIFIED | `EmployeeEvents.onOtherDeath` (gated on NOT `HarvesterEvents.isHarvesterKillOfVillager`) drops 1 Soul Block + 2 slimeballs via `LivingDeathEvent` (not `LivingDropsEvent`, matching the existing Fragment-guarantee pattern's rationale). `HarvesterEvents.onDeath`'s existing 1-Fragment drop is unchanged; it now additionally calls `EmployeeManager.releaseAltar`. GameTests `non_harvester_death_drops_soul_block_and_slimeballs_and_releases_altar`, `wild_villager_death_never_drops_recovery_items` (EMP-09), and `sneak_harvester_click_releases_the_employee` (which also confirms the 1-Fragment yield via the new voluntary path) all pass. |
| 4 | Employees stay within a bounded area around their altar | ✓ VERIFIED (logic) / human_needed (feel) | Two-radius tether (24 soft walk-home / 48 hard teleport-home) in `EmployeeEvents.onEntityTick`, skipped while passenger/leashed or when the recorded altar position no longer holds a real Soul Altar. GameTests `employee_far_past_the_hard_radius_is_teleported_home` and `tether_is_skipped_when_the_altar_is_gone` pass (via directly-posted `EntityTickEvent.Post` — see Issues Encountered below for why `runAfterDelay` at the real radii didn't work in this harness). |
| 5 | Breaking a bound altar consumes its contents, hurts the player, and fires the employee 0.5s later | ✓ VERIFIED | `SoulAltarBlock.playerWillDestroy` widened to also trigger when `be.isEmployeeBound()`, schedules `EmployeeFiring.schedule(...)` (10-tick delayed queue drained on `ServerTickEvent.Post`), and synchronously releases the altar link before the delay even starts. `EmployeeFiring.fire` removes the `EmployeeData` attachment BEFORE killing so EMP-06's drop handler correctly does nothing for this death. GameTests `destroying_a_bound_altar_schedules_a_delayed_smite_that_drops_nothing` and `empty_altar_break_does_not_schedule_a_smite` pass. |

**Score:** 5/5 success criteria have a verified, working code path and automated coverage. 3 of the 5 additionally carry a human_needed item for the "does it feel right" dimension GameTest cannot judge (see frontmatter).

### Required Artifacts

| Artifact | Expected | Status |
|----------|----------|--------|
| `employee/EmployeeData.java` | 6th field `Optional<BlockPos> altarPos` (the record's half of the bidirectional link) | ✓ VERIFIED — `StreamCodec.composite`'s 6-component ceiling hit exactly as 06-CONTEXT.md predicted; documented in the class's own doc comment for Phase 9 |
| `employee/EmployeeManager.java` | `bind()` writes `altarPos` + breeding-lock age; new `releaseAltar()` identity-guarded helper | ✓ VERIFIED |
| `content/blockentity/SoulAltarBlockEntity.java` | Persisted nullable `employeeId` UUID (the BE's half of the link) | ✓ VERIFIED — `hasUUID`/`getUUID`/`putUUID` guard keeps pre-Phase-6 saves loading with `employeeId = null` |
| `content/block/SoulAltarBlock.java` | `playerWillDestroy` widened + smite scheduling | ✓ VERIFIED |
| `event/EmployeeEvents.java` (new) | Conversion immunity, breeding-lock reassert, altar tether, sneak-release, other-death drops | ✓ VERIFIED |
| `event/EmployeeFiring.java` (new) | Delayed smite queue | ✓ VERIFIED |
| `event/HarvesterEvents.java` | Widened `isHarvesterKillOfVillager` visibility + altar-release on employee reap | ✓ VERIFIED |
| `menu/BindingAltarMenu.java` | Records the spawned employee's UUID on the BE at bind time | ✓ VERIFIED |

### Key Link Verification

| From | To | Via | Status |
|------|-----|-----|--------|
| `EmployeeManager.bind` | `SoulAltarBlockEntity` | `BindingAltarMenu.attemptBind` sets `employeeId` right after `bind()` returns | WIRED |
| `HarvesterEvents.onDeath` (employee branch) | `EmployeeManager.releaseAltar` | direct call, guarded on `hasData(EMPLOYEE)` | WIRED |
| `EmployeeEvents.onOtherDeath` | `EmployeeManager.releaseAltar` | direct call, guarded on NOT `isHarvesterKillOfVillager` | WIRED |
| `SoulAltarBlock.playerWillDestroy` | `EmployeeFiring.schedule` | direct call when `isEmployeeBound()` and `getEmployeeId() != null` | WIRED |
| `EmployeeFiring.fire` | (suppresses) `EmployeeEvents.onOtherDeath` | removes `EmployeeData` attachment before `kill()`, so `hasData` check short-circuits | WIRED |

## Issues Encountered

**GameTest harness distance limitation (not a production bug):** the first version of the tether tests used `helper.runAfterDelay(45, ...)` after moving the test villager 60-100 blocks from the GameTest structure, expecting real world ticks to trigger the tether naturally. This never worked — moving the entity that far apparently took it out of the harness's own loaded/ticking region (`EntityTickEvent.Post` never fired for it), which is a property of the GameTest environment, not of `EmployeeEvents.onEntityTick`'s actual logic. Root-caused by observing that the SAME handler's breeding-lock re-assert (which doesn't require moving the entity anywhere) passed correctly under the identical `runAfterDelay` pattern. Fixed by posting `EntityTickEvent.Post` directly (the same technique already used successfully for conversion-immunity and interact-event tests in this same file) instead of waiting on real ticking — deterministic, and exercises the exact same handler code.

**Two independent Phase 6 build attempts collided and were both safely aborted before this implementation started** (see STATE.md and git history around this timestamp for the full account) — an earlier autonomous dispatch accidentally ran two concurrent agent sessions against the same working tree. Both detected the conflict and backed out cleanly with zero corruption; their 06-CONTEXT.md (used as the design basis for this implementation) and one genuinely valuable technical finding (the `setAge()`-based breeding-lock, cheaper and more effective than the memory-erase approach either session initially considered) were preserved. No wasted code shipped — only wasted compute on the aborted attempts.

## Next Phase Readiness

- Phase 7 (Progression & Promotion Ritual) and Phase 8 (Mod-Owned Restock) can both proceed — neither depends on anything Phase 6-specific beyond the `EmployeeData`/altar-link shape now established.
- Phase 9 (Quarters & Happiness) will need a nested sub-record or hand-written `StreamCodec` for its new fields, since `EmployeeData`'s `StreamCodec.composite` chain is now at its 6-component ceiling (documented in the class's own doc comment).
- **For the user:** please playtest the 3 human_needed items above when you're back — none are expected to be broken (all have verified underlying logic), but the tether radii, the sneak-release gesture, and the two-lightning-strike timing are all genuinely new interactions/feel that only a human can judge.

---
*Verified: 2026-09-08T05:00:00Z*
*Verifier: Claude (self-verified, autonomous session)*
