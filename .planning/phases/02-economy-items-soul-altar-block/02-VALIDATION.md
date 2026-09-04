---
phase: 2
slug: economy-items-soul-altar-block
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-09-04
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `02-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | NeoForge GameTest (bundled; `neoforge.enabledGameTestNamespaces=secondshift` already set on the `client`/`server` run configs). No JUnit in the project. |
| **Config file** | none — GameTests are `@GameTest`-annotated methods discovered by namespace. The standalone `gameTestServer` run was removed in Phase 1 (crashes with zero registered gametests); re-add only if GameTests are written. |
| **Quick run command** | `./gradlew build` (compile + JSON/resource load validation) |
| **Full suite command** | `./gradlew build` then `./gradlew runServer` (client-class-leak gate) + `./gradlew runClient` smoke |
| **Estimated runtime** | ~60–120 s for `build`; `runClient`/`runServer` ~1–2 min to reach "Done" |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew build`
- **After every plan wave:** Run `./gradlew runClient` smoke (tab + one recipe + place/break altar) **and** `./gradlew runServer` (client-leak gate)
- **Before `/gsd:verify-work`:** `./gradlew build` green; full manual checklist via `./gradlew deployToTest` + real launch in the CurseForge "test" instance, **plus** one launch in a non-English locale (POL-03 raw-key check)
- **Max feedback latency:** ~120 seconds (`./gradlew build`)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 2-01-01 | 01 | 0 | ECON-02, ALTAR-01, POL-01 | — | content skeletons + item/block holders compile; `DEBUG_MARKER` gone; no `extends SwordItem` | build | `./gradlew compileJava` + grep gates | ❌ W0 | ⬜ pending |
| 2-01-02 | 01 | 0 | POL-01, ALTAR-01, POL-03 | T-02-02, T-02-03 | all 4 registers attached + in `ModRegistrySelfCheck`; every `descriptionId` resolves in `en_us.json`; deliberate detach / missing-key aborts startup | build + runClient + runServer | `./gradlew build` + `run-until.sh runClient` + `run-until.sh runServer` | ❌ W0 | ⬜ pending |
| 2-01-03 | 01 | 0 | ECON-02 | — | ECON-02 GameTest suite exists, discovered under `secondshift`, currently RED | GameTest | `./gradlew runGameTestServer` (asserts RED) | ❌ W0 | ⬜ pending |
| 2-02-01 | 02 | 1 | ECON-02 | T-02-04, T-02-05, T-02-06 | instakill gated on `HarvesterItem` + exact `npc.Villager`, server-side only; `getDrops().clear()` + exactly 1 Fragment | GameTest | `./gradlew runGameTestServer` (all 6 GREEN) | ❌ W0→W1 | ⬜ pending |
| 2-02-02 | 02 | 1 | ECON-01, POL-03 | — | Harvester + Soul Fragment real model + texture; Fragment foil | build + manual | `./gradlew build` + `runClient` visual | ✅ (build) | ⬜ pending |
| 2-03-01 | 03 | 2 | POL-03 | — | Soul Block blockstate + block model + item model + drops-self loot table (4-file completeness) | build + manual | `./gradlew build` + JSON parse | ✅ (build) | ⬜ pending |
| 2-03-02 | 03 | 2 | ECON-01, ECON-03 | T-02-07 | shaped Harvester; shapeless 4→1 and 1→4 lossless; `result.id` shape | build | `./gradlew build` + recipe assertion script | ✅ (build) | ⬜ pending |
| 2-03-03 | 03 | 2 | POL-04 | T-02-08, T-02-09 | steps 1&2 advancements grant recipes via `rewards.recipes` (forward + reverse Soul Block on `first_harvest`); no auto `advancement/recipes/` | build + manual | `./gradlew build` + advancement assertion script | ✅ (build) | ⬜ pending |
| 2-04-01 | 04 | 3 | ALTAR-01 | T-02-10, T-02-11, T-02-12 | `useItemOn`→`ItemInteractionResult`; BE mutated only `!isClientSide`; `setChanged()` + `sendBlockUpdated`; one-way, no `Capability` | build + manual (relog) | `./gradlew build` + `@Override`/signature grep | ✅ (build) | ⬜ pending |
| 2-04-02 | 04 | 3 | ALTAR-07 | T-02-13, T-02-14 | empty → drops self; charged → `getDrops` empty + `magic()` 1.0F + `setVisualOnly` lightning; fallback is a one-line branch | build + manual | `./gradlew build` + loot/grep asserts | ✅ (build) | ⬜ pending |
| 2-04-03 | 04 | 3 | POL-03, POL-04, ALTAR-01 | — | altar non-full-cube `elements` model + blockstate + shaped emerald recipe + "Soul Mason" advancement gates the recipe | build + manual | `./gradlew build` + recipe/advancement assertion script | ✅ (build) | ⬜ pending |
| 2-05-01 | 05 | 4 | POL-03 | T-02-16 | client-only BER; charged render emissive `FULL_BRIGHT` + no bob; empty renders nothing; no client ref from `content/`/`event/`/`registry/` | build + manual (reload + chunk) | `./gradlew build` + leak grep | ✅ (build) | ⬜ pending |
| 2-05-02 | 05 | 4 | — (client leak) | T-02-15 | `runServer` reaches "Done", no `NoClassDefFoundError` / `net/minecraft/client` | automated | `run-until.sh runServer` + `run-until.sh runClient` | ✅ | ⬜ pending |

*Task IDs are `2-<plan>-<task>`. Threat refs → each plan's `<threat_model>` STRIDE register. Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test`-equivalent GameTest class (e.g. `content/HarvesterGameTests.java`) with `@GameTest` methods for **ECON-02**:
  - villager + Harvester → exactly 1 `secondshift:soul_fragment` `ItemEntity`, nothing else
  - baby villager → same
  - Resistance V + absorption villager → still one-shot
  - `WanderingTrader` → survives one hit, no Fragment
  - `ZombieVillager` → survives one hit, no Fragment
  - sword kill of a villager → vanilla loot, no Fragment
  - Register via `@GameTestHolder(SecondShift.MODID)` / `RegisterGameTestsEvent`
- [ ] Re-add the `gameTestServer` run to `build.gradle` **only if** GameTests are written, or run via `./gradlew runServer` with the namespace enabled
- [ ] Extend `ModRegistrySelfCheck` `Stream.of(...)` with `ModBlocks.BLOCKS`, `ModBlockEntities.BLOCK_ENTITIES`, `ModCreativeTab.TABS` (code task, but it is the guardrail infrastructure)
- [x] Planner decision: **YES** — the `descriptionId`/lang-key resolution self-check is in scope this phase; implemented in `02-01` Task 2 (Wave 0) alongside the register self-check, verified clean on both `runClient` and `runServer` (with a documented `Dist.CLIENT` fallback if `Language` is unpopulated server-side).

*Fallback if the planner judges GameTests as over-investment: the manual checklist below run via `deployToTest` (the established Phase 1 loop). Recommendation from research: write the ECON-02 GameTests — the instakill is the one genuinely custom mechanic and its edge cases are exactly what a deterministic test protects.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Harvester craftable + visible in JEI/EMI | ECON-01 | recipe-book / JEI rendering is client UI | craft at a table in `runClient`; confirm recipe shows in JEI/EMI if installed |
| Harvester ~3–4 dmg, no sweep | ECON-01 | combat feel + sweep visual | hit a line of 3 pigs in `runClient`; only the struck pig is damaged; ~2 hits to kill a pig |
| 4 Fragment ⇄ 1 Soul Block, lossless | ECON-03 | crafting-grid interaction | craft both directions in `runClient` |
| Altar block + BE; socket persists across relog | ALTAR-01 | world save/load round-trip | socket a Soul Block, save-quit-reload, BE still holds it, render still charged |
| Empty altar breaks → drops itself | ALTAR-07 | block-break drop behavior | break an empty altar in `runClient` → altar item drops |
| Charged altar breaks → no drops, ½-heart, cosmetic lightning | ALTAR-07 / D-04 | visual + damage side effects | break a charged altar → no items, player loses 1 hp, lightning flash + thunder, no fire/collateral |
| One creative tab, all 4 entries | POL-01 | creative menu is client UI | open creative menu in `runClient`, "Second Shift" tab has Harvester, Soul Fragment, Soul Block, Soul Altar |
| No missing models/textures/lang keys | POL-03 | visual inspection | no purple cubes; launch in a non-English locale, no raw `item.secondshift.*` / `block.secondshift.*` keys |
| Recipe-unlock toasts + gated discovery chain | POL-04 / D-15 | toast + recipe-book state | fresh survival world: emerald pickup → "Necromantic Apprentice" + "recipe unlocked" toast; `secondshift:harvester` absent from recipe book until emerald; `soul_block` absent until first Fragment; `soul_altar` absent until a Soul Block crafted |

---

## Validation Sign-Off

- [x] All tasks have automated verify (`./gradlew build`) or Wave 0 GameTest dependencies
- [x] Sampling continuity: no 3 consecutive tasks without an automated verify (`build` runs every commit)
- [x] Wave 0 covers all MISSING references (ECON-02 GameTests, register self-check extension)
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-09-04
