---
phase: 5
slug: profession-resolution-trade-picker
status: draft
nyquist_compliant: true
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
| **Estimated runtime** | ~1-2 seconds per test; the phase adds ~20 new tests across 4 new/extended GameTest classes |

No new test framework needed. Phase 5's automatable surface (profession resolution correctness,
trade materialization, offer-selection validation, second-socket persistence, one-employee-per-altar
enforcement) is well within GameTest's reach — unlike Phase 4, this phase has no behavior that
structurally requires a manual checkpoint mid-phase (no new client-only sync question; the LIGHT
spike is already answered). Only rendering and real vanilla trade-screen contents are genuinely
unautomatable, and are handled by a single end-of-phase manual checkpoint (Plan 05-07).

---

## Sampling Rate

- **After every task commit:** `./gradlew compileJava`; `./gradlew runGameTestServer` for any task touching `ProfessionResolver`, `TradePoolCache`, `SoulAltarBlockEntity`, `BindingAltarMenu`, `ServerPayloadHandler`, or `EmployeeManager.bind`
- **After every plan wave:** `./gradlew runGameTestServer` (full suite) + `./gradlew runServer` (client-class-leak gate)
- **Before `/gsd:verify-work`:** Full GameTest suite green
- **Max feedback latency:** ~5 seconds (GameTest suite runtime)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------|--------------------|--------|
| 05-01 T1 | 05-01 | 1 | ALTAR-05 (BE contract) | compileJava (GameTest coverage lands in T3) | `./gradlew compileJava` | ⬜ pending |
| 05-01 T2 | 05-01 | 1 | ALTAR-02, PICK-01, PICK-08 | compileJava (GameTest coverage lands in T3) | `./gradlew compileJava` | ⬜ pending |
| 05-01 T3 | 05-01 | 1 | ALTAR-02, ALTAR-05, PICK-01 | GameTest | `./gradlew runGameTestServer` | ⬜ pending |
| 05-02 T1 | 05-02 | 1 | PICK-02, PICK-05, PICK-08 | GameTest | `./gradlew runGameTestServer` | ⬜ pending |
| 05-03 T1 | 05-03 | 2 | ALTAR-02 (visual) | compileJava + runServer (client-class-leak) | `./gradlew compileJava && ./gradlew runServer` | ⬜ pending |
| 05-04 T1 | 05-04 | 2 | PICK-02, PICK-04, PICK-07, GUI-03 | compileJava (GameTest coverage lands in T2) | `./gradlew compileJava` | ⬜ pending |
| 05-04 T2 | 05-04 | 2 | PICK-02, PICK-04, PICK-07, PICK-08, GUI-03 | GameTest | `./gradlew runGameTestServer` | ⬜ pending |
| 05-05 T1 | 05-05 | 3 | ALTAR-04, PICK-06 | GameTest | `./gradlew runGameTestServer` | ⬜ pending |
| 05-05 T2 | 05-05 | 3 | ALTAR-04, ALTAR-05, GUI-02 | compileJava (GameTest coverage lands in T3) | `./gradlew compileJava` | ⬜ pending |
| 05-05 T3 | 05-05 | 3 | GUI-02, ALTAR-05, PICK-03, PICK-04 | GameTest | `./gradlew runGameTestServer` | ⬜ pending |
| 05-06 T1 | 05-06 | 4 | PICK-03, PICK-04 (UI) | compileJava | `./gradlew compileJava` | ⬜ pending |
| 05-06 T2 | 05-06 | 4 | PICK-03, PICK-07, PICK-08, GUI-03 | compileJava (manual visual in 05-07) | `./gradlew compileJava` | ⬜ pending |
| 05-06 T3 | 05-06 | 4 | POL-02 (lang guardrail hygiene) | compileJava + runServer (lang self-check) | `./gradlew compileJava && ./gradlew runServer` | ⬜ pending |
| 05-07 T1 | 05-07 | 5 | ALTAR-02, GUI-03, PICK-05 | manual (human-verify checkpoint) | N/A — see 05-07-PLAN.md how-to-verify | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] No dedicated test-framework install needed — `./gradlew runGameTestServer` already exists and covers this phase's automatable surface.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|--------------------|
| The Binding Altar GUI visually shows name/profession/tier/chosen-trades correctly | GUI-03 | Screen rendering/layout is not GameTest-observable | Plan 05-07, step 4 |
| The two altar sockets (Soul Block + job item) render distinctly without visual overlap | ALTAR-02 (G-2) | Rendering is not GameTest-observable | Plan 05-07, step 3 |
| Enchanted-book trade offer looks correct in the real trade screen (not just the picker) | PICK-05 | Vanilla trade-screen rendering is not GameTest-observable | Plan 05-07, step 7 |

*Note: unlike Phase 4, none of these require save/quit/relaunch or chunk-unload — they're
straightforward visual spot-checks, folded into a single end-of-phase blocking checkpoint
(Plan 05-07) rather than scattered non-blocking notes, since all three depend on the full UI
(Plan 05-06) and renderer (Plan 05-03) being complete.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 10s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending — plans finalized 2026-09-05; execution will flip each row to ✅/❌ as tasks complete.
