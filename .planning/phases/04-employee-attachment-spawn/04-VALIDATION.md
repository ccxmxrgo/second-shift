---
phase: 4
slug: employee-attachment-spawn
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-09-04
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | NeoForge `GameTestHelper` / `@GameTest` (`net.neoforged.neoforge.gametest`) — already proven in this codebase (`HarvesterGameTests`, `BindingAltarGameTests`) |
| **Config file** | none — GameTests are discovered via `@GameTestHolder(SecondShift.MODID)` class annotation |
| **Quick run command** | `./gradlew runGameTestServer` |
| **Full suite command** | `./gradlew runGameTestServer` (this project has no separate quick/full tier) |
| **Estimated runtime** | ~5-10 seconds (14 existing + 5 new = 19 tests) |

No new test framework needed — existing infrastructure (proven in Phase 2/3) covers this phase's
automatable behaviors. Two roadmap success criteria (persistence round-trip, client-side sync) and
one acceptance criterion (D-02 name-tag rename) are structurally outside GameTest's reach and are
covered by Plan 04-04's mandatory manual verification checkpoint instead.

---

## Sampling Rate

- **After every task commit:** `./gradlew compileJava` (fast compile-correctness check); `./gradlew runGameTestServer` for any task touching `EmployeeManager`/`ModAttachments`/spawn logic (Plan 04-02 Task 2, which writes `EmployeeManager.bind` and `EmployeeGameTests` together and verifies with the full GameTest suite)
- **After every plan wave:** `./gradlew runGameTestServer` (full suite) + `./gradlew runServer` (client-class-leak gate)
- **Before `/gsd:verify-work`:** Full GameTest suite green (19/19) AND Plan 04-04's manual checkpoint approved
- **Max feedback latency:** ~10 seconds (GameTest suite runtime)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 1 | EMP-01 | — | N/A | compile | `./gradlew compileJava` | ❌ W0 (created this task) | ⬜ pending |
| 04-01-02 | 01 | 1 | EMP-01 | T-4-03 (indirect) | 6th register hard-aborts if unbound | integration | `./gradlew runServer` (+ manual D-14 detach proof) | ❌ W0 (created this task) | ⬜ pending |
| 04-01-03 | 01 | 1 | EMP-01 | T-4-03 | `hasData` gated before any `getData` read | compile + manual (log inspection deferred to 04-04) | `./gradlew runServer` (class-leak) | ❌ W0 (created this task) | ⬜ pending |
| 04-02-01 | 02 | 2 | EMP-01 (support) | — | N/A | compile | `./gradlew compileJava` | ❌ W0 (created this task) | ⬜ pending |
| 04-02-02 | 02 | 2 | EMP-01, EMP-02, EMP-08, EMP-09 | T-4-04 | never mutates an existing entity, only constructs new; wild-villager regression proven per test | GameTest | `./gradlew runGameTestServer` (full suite — task writes `EmployeeManager.bind` and `EmployeeGameTests.java` together, single end-of-task verify; see revision note below) | ❌ W0 (created this task) | ⬜ pending |
| 04-03-01 | 03 | 3 | EMP-01 (trigger) | T-4-01 (payload has zero fields) | no client-position field exists to spoof | compile | `./gradlew compileJava` | ❌ W0 (created this task) | ⬜ pending |
| 04-03-02 | 03 | 3 | EMP-01 (trigger) | T-4-01, T-4-02, T-4-03 | server re-derives pos from menu; atomic slot consume before bind | compile (behavior proven manually in 04-04) | `./gradlew compileJava` | ❌ W0 (created this task) | ⬜ pending |
| 04-03-03 | 03 | 3 | EMP-01 (trigger) | — | N/A (client UI only) | compile + manual | `./gradlew compileJava` | ❌ W0 (created this task) | ⬜ pending |
| 04-04-01 | 04 | 4 | EMP-01, EMP-02, EMP-08, EMP-09 | T-4-01, T-4-02 (real-world proof) | full loop confirmed in real client | manual (human-check) | N/A — checkpoint | N/A | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Revision note (post plan-checker review):** 04-02's original Task 2 ("EmployeeManager.bind")
and Task 3 ("EmployeeGameTests") were merged into a single Task 2 (`04-02-02`) so the task's
`<verify>` never invokes a `--tests` filter against a not-yet-created test class. The row above
now covers both the implementation and its GameTest proof as one Nyquist sample point; there is
no longer a separate `04-02-03` row.

---

## Wave 0 Requirements

- [x] No dedicated test-framework install needed — `./gradlew runGameTestServer` already exists and covers this phase's automatable surface (proven in Phase 2/3).
- [ ] `gametest/EmployeeGameTests.java` — created in Plan 04-02 Task 2 (merged with `EmployeeManager.bind`'s implementation, per the revision note above), covering EMP-01/EMP-02/EMP-08/EMP-09 spawn-correctness assertions. Not yet on disk at planning time — this IS this phase's Wave 0 deliverable, produced inline with the implementation task it verifies (matches this codebase's established Phase 2 idiom of shipping GameTest coverage in the same or an adjacent task, not a separate pre-implementation RED-only plan).

No separate Wave 0 plan is needed: this phase's implementation task (Plan 04-02 Task 2) ships
its GameTest coverage together with the implementation, following the project's established
pattern (Phase 2's `HarvesterGameTests` was written as a RED scaffold in 02-01 and turned GREEN in
02-02; this phase's `EmployeeGameTests` is written and turned GREEN together in a single 04-02 task
since there is no separate scaffold-then-implement split for a brand-new method with no
pre-existing partial implementation).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|--------------------|
| `EmployeeData` survives save/quit/relaunch | EMP-01 (persistence) | GameTest runs in a synthetic single-tick-or-few-tick harness with no real save/quit/relaunch cycle | Plan 04-04 checkpoint, steps 1-2 + 4 |
| `EmployeeData` survives a 300-block chunk-unload round trip | EMP-01 (persistence) | GameTest structures do not exercise real chunk unload/reload at that distance | Plan 04-04 checkpoint, step 3 |
| Client-side read of the synced attachment matches the server value (the LIGHT spike proof) | EMP-01 (sync) | GameTest runs server-side only; no real distinct client process to observe sync | Plan 04-04 checkpoint, step 5 (reads the `ClientEmployeeSyncDebug` log line from Plan 04-01 Task 3) |
| Name actually renders green in the real client HUD | EMP-08 (visual) | Rendering is not GameTest-observable | Plan 04-04 checkpoint, implicit in step 1 (visually confirm on spawn) |
| A vanilla Name Tag renames a freshly-bound employee exactly like a wild villager | EMP-08 / D-02 | `Villager`'s vanilla name-tag interaction path is not simulated by any existing GameTest helper in this codebase, and this phase deliberately does not add any name-tag-specific code to test against | Plan 04-04 checkpoint, step 6 |
| A separate wild villager is visibly/behaviorally unaffected in a real running world | EMP-09 (real-world regression) | Complements the automated `wild_villager_unaffected_by_bind_in_same_world` GameTest (04-02) with a real-client visual/behavioral confirmation | Plan 04-04 checkpoint, step 7 |
| Rapid double-Confirm-click does not double-spawn | T-4-02 (security) | Requires two real network round-trips against a live server tick boundary, not reproducible identically in a single-threaded GameTest invocation | Manual: click "Confirm Hire" twice in rapid succession during Plan 04-04's session; confirm exactly one employee spawned |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (Plan 04-04's single task uses `<human-check>` per its checkpoint type, which is the documented exception for `checkpoint:human-verify` tasks)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (04-01's 3 tasks and 04-02's 2 tasks are each automated; 04-03's 3 tasks are automated at compile-level with behavior proof deferred to the single manual checkpoint in 04-04, which is its own plan, not 3+ consecutive manual tasks)
- [x] Wave 0 covers all MISSING references (`EmployeeGameTests.java` is the one MISSING file, and it is created within the phase's own Wave 2 plan, per the note above)
- [x] No watch-mode flags anywhere in this document
- [x] Feedback latency < 10s for automated checks; manual checkpoint latency is developer-paced (acceptable per Nyquist rules for `checkpoint:human-verify` gates)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-09-04 (planner self-certification against the Nyquist checklist above; final developer sign-off happens at Plan 04-04's checkpoint during execution). Revised 2026-09-04 to merge 04-02 Tasks 2/3 per plan-checker blocker finding.

**Checkpoint closed: approved 2026-09-05.** All 7 manual verification steps passed on re-test after two bugs (CR-01 spawn-overlap, sync-diagnostic timing) were found and fixed via quick task 260905-0yg. The LIGHT spike is resolved: entity attachment sync confirmed working in NeoForge 21.1.248. See `04-04-SUMMARY.md` for full detail.
</content>
