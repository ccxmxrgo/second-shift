---
phase: 3
slug: menu-screen-harness-hard-gate
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-09-04
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | NeoForge GameTest (`net.minecraft.gametest.framework`, `net.neoforged.neoforge.gametest`) — already in use (`HarvesterGameTests.java`) |
| **Config file** | `build.gradle` `runs.gameTestServer` (already present, confirmed in Phase 2) |
| **Quick run command** | `./gradlew build` (compile check) |
| **Full suite command** | `./gradlew runGameTestServer` |
| **Estimated runtime** | ~10 min (GameTest suite) + `runClient`/`runServer` manual passes ~15 min each |

---

## Sampling Rate

- **After every task commit:** `./gradlew build` + targeted manual `runClient` spot-check when menu/screen code changed
- **After every plan wave:** `./gradlew runGameTestServer` (full GameTest suite) + `./gradlew runServer` (client-class-leak gate, per Phase 2's established habit)
- **Before `/gsd:verify-work`:** `runClient` visual pass (SC1, SC3) + `runServer` clean load (SC2) + `runGameTestServer` green (SC4) all required
- **Max feedback latency:** ~10 min (GameTest suite is the slowest automated gate)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|--------------------|-------------|--------|
| 03-01-* | 01 | 0 | GUI-01 / SC2 | T-03-* | Unbound `binding_altar` menu hard-aborts startup | automated | `./gradlew runServer` + log-grep for guardrail line | ✅ existing `ModRegistrySelfCheck` pattern, extend `Stream.of(...)` | ⬜ pending |
| 03-01-* | 01 | 0 | ALTAR-03 / GUI-01 | T-03-* | No job block / no Soul Block → no crash, no menu | GameTest | `./gradlew runGameTestServer` | ❌ Wave 0 — new test method | ⬜ pending |
| 03-01-* | 01 | 0 | SC4 | T-03-* | Malformed/out-of-range interaction rejected; `stillValid` false after altar break or player move | GameTest | `./gradlew runGameTestServer` | ❌ Wave 0 — new test method(s) | ⬜ pending |
| 03-01-* | 01 | 0 | ALTAR-03 (job gate) | T-03-* | `ProfessionResolver` returns a profession for a real job-site block, empty for a non-POI block | GameTest | `./gradlew runGameTestServer` | ❌ Wave 0 — new test method | ⬜ pending |
| 03-01-* | 01 | 0 | GUI-01 / SC1 | — | Menu opens under `runClient`, titled "Binding Altar", no crash | manual (human-verify — screen rendering can't be asserted headless) | `./gradlew runClient` | N/A — visual check | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

*(Task IDs are placeholders — the planner assigns final `{phase}-{plan}-{task}` IDs. This map will be refined once PLAN.md exists.)*

---

## Wave 0 Requirements

- [ ] `BindingAltarGameTests.java` (or new methods on `HarvesterGameTests.java` — planner's discretion per CONTEXT.md) covering:
  - Open menu server-side via `helper.makeMockServerPlayerInLevel()` + a manually-constructed `BindingAltarMenu`; assert non-null menu instance and `stillValid(player) == true` while altar + job block + proximity are all valid.
  - Break the altar block (or the job block above it) mid-test; assert `stillValid(player) == false`.
  - Move the mock player far away (`player.teleportTo(...)`, > 8 blocks); assert `stillValid(player) == false`.
  - No job block / non-job-site block above the altar + right-click with Soul Block in hand (`helper.useBlock(pos, player)`) → assert no exception, no menu opened, altar remains unsocketed.
- [ ] `ProfessionResolver` GameTest: place a real vanilla job-site block (e.g. `Blocks.CARTOGRAPHY_TABLE`) above the altar, assert the resolver returns `VillagerProfession.CARTOGRAPHER`; place a non-POI block (e.g. `Blocks.STONE`), assert `Optional.empty()`.

*No Wave 0 gap for GUI-01/SC1/SC2/SC3 rendering/boot checks — those reuse the pre-existing manual `runClient`/`runServer` loop from Phase 1/2 and require no new test infrastructure.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|--------------------|
| Screen opens, titled "Binding Altar", renders chrome + inventory + the one display-only slot | GUI-01 / SC1 | Screen rendering can't be asserted headless | `./gradlew runClient` → place a real job-site block on a Soul Altar, right-click with a Soul Block in hand → screen opens with the correct title and layout |
| Right-click with no job block / no Soul Block does nothing harmful | ALTAR-03 / SC3 | Covered by GameTest for the no-crash assertion, but the "feels like nothing happened" UX (no error toast bleed, no visual glitch) is a manual spot-check | `./gradlew runClient` → right-click a bare altar empty-handed, then with a Soul Block but no job block above; confirm no crash and no screen |
| POL-08 themed messages read correctly on the action bar | D-11/D-12 | Message wording/tone and action-bar placement are visual/UX | `./gradlew runClient` → trigger each of the 6 reachable-now invalid states, confirm the message text and delivery channel |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 10 min
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
