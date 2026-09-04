---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: Ready to execute
stopped_at: Phase 2 context gathered
last_updated: "2026-09-04T09:26:23.703Z"
last_activity: 2026-09-04
progress:
  total_phases: 10
  completed_phases: 1
  total_plans: 7
  completed_plans: 3
  percent: 10
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-04)

**Core value:** Harvest souls → bind a villager at the Soul Altar → hand-pick its profession and its trades, tier by tier. That loop must be reliable and feel good.
**Current focus:** Phase 02 — economy-items-soul-altar-block

## Current Position

Phase: 02 (economy-items-soul-altar-block) — EXECUTING
Next: Phase 2 (Economy Items & Soul Altar Block) — discuss or plan
Plan: 2 of 5
Last activity: 2026-09-04

Progress: [█░░░░░░░░░] 10%

## Performance Metrics

**Velocity:**

- Total plans completed: 2
- Average duration: -
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 2 | - | - |

**Recent Trend:**

- Last 5 plans: -
- Trend: -

*Updated after each plan completion*
| Phase 01 P01 | 55 min | 2 tasks | 13 files |
| Phase 01 P02 | 8 min | 2 tasks | 5 files |
| Phase 02 P01 | 41 min | 3 tasks | 14 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Relevant to current work:

- Full rebuild from scratch; old draft source + jar deleted.
- Menu/screen harness is a HARD GATE (Phase 3): empty altar screen must open under `runClient` before any trade logic.
- Toolchain pinned: NeoForge 21.1.248 / ModDevGradle 2.0.146 / Gradle 9.2.1 wrapper / Parchment 2024.11.17, from the official 1.21.1 MDK.
- Employees = `minecraft:villager` + `EmployeeData` attachment; behaviour lives in game-bus handlers guarded by `hasData(EMPLOYEE)`, never a subclass.
- Happiness/upkeep system is IN scope but flagged "revisit after playtesting" (Phase 9).
- [Phase 01]: Self-check throws directly from the FMLLoadCompleteEvent handler (not via enqueueWork) so an unbound register is a fatal hard abort, not a soft broken-mod state — enqueueWork swallows the exception; verified on runClient + runServer
- [Phase 01]: runData is not a self-check surface on MDG 2.0.146 / NeoForge 21.1.248 (no FMLCommonSetupEvent / FMLLoadCompleteEvent); guardrail proven on runClient + runServer
- [Phase 01]: MDG 2.0.146 writes dev run logs to run/logs/latest.log + run/logs/debug.log (not runs/<name>/logs/)
- [Phase 01]: D-12 resolved YES: FML scans run/mods/ (FMLPaths MODSDIR) — a jar dropped there loads with deps enforced; later phases can use it for dev-parity mods
- [Phase 01]: deployToTest is config-cache-safe (providers captured at config time, doLast uses Files+File not project) and absent from the build task graph
- [Phase ?]: Phase 2 lang-key self-check reads the mod jar's own en_us.json off the classpath (not net.minecraft.locale.Language) so it runs identically on client and dedicated server
- [Phase ?]: 1.21.1 has no Item.Properties#enchantable / DataComponents.ENCHANTABLE (both 1.21.2+); durability items are table-enchantable via Item#isEnchantable, override getEnchantmentValue for enchant quality

### Pending Todos

None yet.

### Blockers/Concerns

- REQUIREMENTS.md previously stated "52 total"; the enumerated list is actually 60. Roadmap and traceability use 60. Non-blocking; noted for the record.
- 4 research spikes are budgeted into phase planning: attachment entity-sync (Phase 4, LIGHT), ItemListing.getOffer side effects (Phase 5), breeding suppression (Phase 6, LOW confidence), offer re-assertion after level-up (Phase 7, MEDIUM).
- Phase 9 (happiness) is the largest net-new chunk with the least research coverage — quarters/structure detection and food-chest access need a design spike during planning.
- EMP-07 "keep employee near altar" has no pre-researched hook — minor spike in Phase 6.

## Deferred Items

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| *(none)* | | | |

## Session Continuity

Last session: 2026-09-04T09:26:17.783Z
Stopped at: Phase 2 context gathered
Resume file: None
