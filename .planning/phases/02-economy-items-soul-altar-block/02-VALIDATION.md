---
phase: 2
slug: economy-items-soul-altar-block
status: draft
nyquist_compliant: false
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
| 2-00-01 | 00 | 0 | ECON-02 | — | N/A | GameTest | `./gradlew runServer` (namespace `secondshift`) | ❌ W0 | ⬜ pending |
| 2-00-02 | 00 | 0 | POL-01 / ALTAR-01 | — | unbound-register abort still fires | GameTest/guardrail | `ModRegistrySelfCheck` at startup | ❌ W0 | ⬜ pending |
| 2-xx-xx | — | — | ECON-01 | — | N/A | manual (JEI) + build | `./gradlew build` + craft in `runClient` | ✅ (build) | ⬜ pending |
| 2-xx-xx | — | — | ECON-03 | T (dupe) | shapeless lossless both directions | manual (JEI) | craft both directions in `runClient` | ❌ manual | ⬜ pending |
| 2-xx-xx | — | — | ALTAR-01 | T (client BE mutation) | BE writes gated `!level.isClientSide`; persists across relog | manual | place altar, socket, save-quit-reload | ❌ manual | ⬜ pending |
| 2-xx-xx | — | — | ALTAR-07 | — | empty→drops self; charged→no drops, ½-heart, cosmetic lightning | manual + build (loot table present) | break empty + charged altar in `runClient` | ❌ manual | ⬜ pending |
| 2-xx-xx | — | — | POL-03 | — | N/A | manual + non-EN locale launch | no purple cubes; no raw `item.secondshift.*` keys | ❌ manual | ⬜ pending |
| 2-xx-xx | — | — | POL-04 | — | N/A | manual | fresh world: emerald pickup → toast; recipe hidden until gating advancement | ❌ manual | ⬜ pending |
| 2-xx-xx | — | — | — (client leak) | DoS | client classes never imported from common | automated | `./gradlew runServer` reaches "Done", no `NoClassDefFoundError` | ✅ | ⬜ pending |

*The planner refines Task IDs / Plan / Wave columns once plans exist. Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

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
- [ ] Planner decision: is the `descriptionId`/lang-key resolution self-check in scope this phase? If yes, it belongs in Wave 0 alongside the register self-check.

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

- [ ] All tasks have automated verify (`./gradlew build`) or Wave 0 GameTest dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without an automated verify (`build` runs every commit)
- [ ] Wave 0 covers all MISSING references (ECON-02 GameTests, register self-check extension)
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
