---
phase: 08-mod-owned-restock
plan: 01
subsystem: gameplay
tags: [neoforge-config, villager, attachments, gametest]

requires:
  - phase: 04-employee-attachment-spawn
    provides: EmployeeManager.bind, ModAttachments pattern
  - phase: 06-employee-traits-death-firing
    provides: EmployeeEvents periodic 40-tick per-employee check
provides:
  - A mod-owned, real-time, POI/day-night/dimension-independent restock timer per employee
  - ModConfig.Type.COMMON with a configurable restockIntervalTicks value
  - A second, unsynced attachment (secondshift:restock_timer) alongside EmployeeData
affects: [09-quarters-happiness]

tech-stack:
  added:
    - "NeoForge ModConfigSpec / ModContainer#registerConfig (first config value this mod has)"
  patterns:
    - "Call a vanilla entity's own public mechanism (Villager#restock()) directly instead of its
       gated public/private wrapper (shouldRestock()/allowedToRestock()) when the goal is
       specifically to bypass that gating."
    - "A second, purpose-specific attachment for bookkeeping a single already-full record can't
       hold, rather than restructuring the record or adding a sync payload it doesn't need."

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/config/ModConfig.java
    - src/main/java/com/cxmxrgo/secondshift/gametest/RestockGameTests.java
  modified:
    - src/main/java/com/cxmxrgo/secondshift/SecondShift.java
    - src/main/java/com/cxmxrgo/secondshift/registry/ModAttachments.java
    - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java
    - src/main/java/com/cxmxrgo/secondshift/event/EmployeeEvents.java

key-decisions:
  - "Villager#restock() is public with no internal gate (verified against decompiled source);
     shouldRestock()/allowedToRestock() (the day/POI/twice-daily gate) are private and only ever
     called from the brain's work-schedule behavior, never from restock() itself. Calling
     restock() directly and never touching shouldRestock() is what makes the mod's restock
     genuinely independent of POI, work schedule, day/night, and dimension — see 08-CONTEXT.md
     D-01."
  - "The restock timer is a second AttachmentType<Long> (secondshift:restock_timer, no sync),
     not a 7th EmployeeData field — that record's StreamCodec.composite chain is already at its
     documented 6-component ceiling (Phase 6). See 08-CONTEXT.md D-02."
  - "Success criterion 4 ('restocks at most once on reload, no burst') falls out for free from
     the comparison's own shape: `if (now - last >= interval) { restock(); last = now; }` always
     restocks exactly once regardless of how large the elapsed gap is. See 08-CONTEXT.md D-04."
  - "The interval is the mod's first ModConfig.Type.COMMON value — server-authoritative logic
     with no per-world override need, so COMMON (not SERVER) is the simplest correct scope. See
     08-CONTEXT.md D-05."

patterns-established: []

requirements-completed: [STOCK-01, STOCK-02, STOCK-03]

duration: ~40min (autonomous overnight session, continuing directly from Phase 7)
completed: 2026-09-08
---

# Phase 8: Mod-Owned Restock Summary

**Employee trades restock on a real, mod-owned tick timer — calling `Villager#restock()` directly
and never touching vanilla's own POI/work-schedule/day-count gate — so an employee never feels
stuck out of stock regardless of time of day, dimension, or workstation access; the interval is a
configurable value; and a long-unloaded employee restocks exactly once on its next check, never in
a burst.**

## Performance

- **Duration:** ~40 minutes of direct implementation — the smallest and most self-contained phase
  so far, building directly on Phase 6's already-established periodic per-employee tick.
- **Started:** 2026-09-08, continuing the `/gsd-autonomous` overnight session immediately after
  Phase 7's commit/push.
- **Completed:** 2026-09-08T05:45:00Z.
- **Tasks:** implemented directly (no formal multi-plan PLAN.md — a small, focused change).
- **Files modified:** 4 modified, 2 created.

## Accomplishments

- STOCK-01: employees restock on a real-time timer that ignores vanilla's day/night cycle, POI
  reachability, work schedule, and dimension entirely — `Villager#restock()` is called directly,
  never `shouldRestock()`.
- STOCK-02: `restockIntervalTicks` is a live `ModConfig.Type.COMMON` value (default 12000 ticks /
  10 real minutes), generating `run/config/secondshift-common.toml`.
- STOCK-03: gated on the same `hasData(EMPLOYEE)` check every other Phase 6/7 handler uses — a
  wild villager is never touched, verified by a dedicated GameTest.
- Success criterion 4 (no burst after a long unload) verified with a 500x-interval simulated gap.
- 5 new GameTests, all passing alongside the full pre-existing 67 (72/72 total).
- Verified crash-free with a real `runClient` boot: `secondshift-common.toml` generates correctly
  and loads without error, resource reload and texture-atlas loading complete with no new crash
  report.

## Files Created/Modified

- `config/ModConfig.java` (new) — the mod's first config spec: `restockIntervalTicks`
  (200-480000 range, default 12000).
- `gametest/RestockGameTests.java` (new) — 5 tests: restock-on-elapsed, no-restock-before-elapsed,
  no-burst-after-long-gap, live config-value wiring, wild-villager immunity (STOCK-03 regression).
- `SecondShift.java` — `container.registerConfig(Type.COMMON, ModConfig.SPEC)`.
- `registry/ModAttachments.java` — new `RESTOCK_TIMER` attachment (`AttachmentType<Long>`, no
  sync).
- `employee/EmployeeManager.java` — `bind()` initializes the restock timer to the current game
  time.
- `event/EmployeeEvents.java` — the restock check added to the existing 40-tick periodic block.

## Decisions Made

See `key-decisions` in frontmatter above — full rationale for all of them is in `08-CONTEXT.md`
D-01 through D-06, written before implementation began this same session.

## Deviations from Plan

None. This phase had no formal multi-plan PLAN.md structure (implemented directly, matching
Phases 6 and 7's precedent) and no deviations from the design captured in `08-CONTEXT.md`.

## Issues Encountered

None. This was the most self-contained phase of the autonomous session — a single new attachment,
a single config value, and one small addition to an already-proven periodic-check pattern.

**No concurrent-agent dispatch this phase**, continuing Phase 6's lesson: implemented directly by
the orchestrating session throughout.

## User Setup Required

None automatically — but the user CAN now tune `restockIntervalTicks` in
`run/config/secondshift-common.toml` (or the CurseForge test instance's equivalent config file)
if the default 10-real-minute cadence doesn't feel right in play.

## Next Phase Readiness

- Phase 9 (Quarters & Happiness) depends on this phase (STOCK-04, "restock is slowed/paused while
  Unhappy", explicitly extends the restock check this phase just built) and on Phase 5. Both
  prerequisites are now satisfied.
- No `human_needed` items this phase — every success criterion has direct, deterministic GameTest
  coverage (the "real-time timer" claim is tested by manipulating the tracked timer value itself,
  which is equivalent to waiting real ticks for this mod's purposes) plus a clean client boot with
  the generated config file confirmed correct.

---
*Phase: 08-mod-owned-restock*
*Completed: 2026-09-08*
