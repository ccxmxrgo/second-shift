---
phase: 5
slug: profession-resolution-trade-picker
status: draft
nyquist_compliant: false
wave_0_complete: true
created: 2026-09-05
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | NeoForge `GameTestHelper` / `@GameTest` (`net.neoforged.neoforge.gametest`) — same infrastructure proven in Phases 2-4 (`HarvesterGameTests`, `BindingAltarGameTests`, `EmployeeGameTests`) |
| **Config file** | none — GameTests are discovered via `@GameTestHolder(SecondShift.MODID)` class annotation |
| **Quick run command** | `./gradlew runGameTestServer` |
| **Full suite command** | `./gradlew runGameTestServer` (no separate quick/full tier in this project) |
| **Estimated runtime** | ~1-2 seconds per test; 20 existing tests take ~1s total |

No new test framework needed. Phase 5's automatable surface (profession resolution correctness,
trade materialization, offer-selection validation, second-socket persistence, one-employee-per-altar
enforcement) is well within GameTest's reach — unlike Phase 4, this phase has no behavior that
structurally requires a manual checkpoint (no new client-only sync question; the LIGHT spike is
already answered).

---

## Sampling Rate

- **After every task commit:** `./gradlew compileJava`; `./gradlew runGameTestServer` for any task touching `ProfessionResolver`, `TradePoolCache`, `EmployeeManager.bind`, or the socket BE/renderer
- **After every plan wave:** `./gradlew runGameTestServer` (full suite) + `./gradlew runServer` (client-class-leak gate)
- **Before `/gsd:verify-work`:** Full GameTest suite green
- **Max feedback latency:** ~5 seconds (GameTest suite runtime)

---

## Per-Task Verification Map

Filled in by the planner once tasks/waves are finalized — this project's established idiom (proven in
Phases 2-4) is to ship GameTest coverage in the same task as the implementation it verifies, not a
separate pre-implementation RED-only scaffold.

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------|--------------------|--------|
| TBD | TBD | TBD | ALTAR-02, PICK-01 | GameTest | `./gradlew runGameTestServer` | ⬜ pending |
| TBD | TBD | TBD | ALTAR-04, PICK-02..06 | GameTest | `./gradlew runGameTestServer` | ⬜ pending |
| TBD | TBD | TBD | ALTAR-05 | GameTest | `./gradlew runGameTestServer` | ⬜ pending |
| TBD | TBD | TBD | GUI-02 (trust boundary) | GameTest + manual | `./gradlew runGameTestServer` | ⬜ pending |
| TBD | TBD | TBD | GUI-03 | manual (screen contents not GameTest-observable) | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] No dedicated test-framework install needed — `./gradlew runGameTestServer` already exists and covers this phase's automatable surface.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|--------------------|
| The Binding Altar GUI visually shows name/profession/tier/chosen-trades correctly | GUI-03 | Screen rendering/layout is not GameTest-observable | Bind an employee in `runClient`, visually confirm the screen shows correct values |
| The two altar sockets (Soul Block + job item) render distinctly without visual overlap | G-2 | Rendering is not GameTest-observable | Socket both items in `runClient`, visually confirm both hover/spin without clipping into each other |
| Enchanted-book trade offer looks correct in the real trade screen (not just the picker) | PICK-05 | Vanilla trade-screen rendering is not GameTest-observable | Bind a librarian, pick the enchanted-book offer, open vanilla trade UI, confirm it displays correctly |

*Note: unlike Phase 4, none of these require save/quit/relaunch or chunk-unload — they're straightforward visual spot-checks, not a dedicated blocking checkpoint plan. The planner may fold these into a lightweight non-blocking manual-verify task rather than a `checkpoint:human-verify` gate.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 10s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending — planner fills in the Per-Task Verification Map once tasks are finalized.
