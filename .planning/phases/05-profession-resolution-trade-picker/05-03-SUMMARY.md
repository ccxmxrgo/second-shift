---
phase: 05-profession-resolution-trade-picker
plan: 03
subsystem: rendering
tags: [neoforge, minecraft-1.21.1, blockentityrenderer, client-render]

# Dependency graph
requires:
  - phase: 05-profession-resolution-trade-picker (Plan 01)
    provides: "SoulAltarBlockEntity.isJobItemEmpty()/getHeldJobItem() — the dual-socket item-socket contract this render task reads from"
provides:
  - "SoulAltarRenderer now draws the socketed job item hovering and slowly spinning above the altar, independent of the embedded Soul Block render"
affects: [05-04, 05-05, 05-07, employee-binding-flow]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Second independent pushPose/popPose render block appended after an existing BlockEntityRenderer render block, never nested inside it, to keep two simultaneous socket visuals from sharing transform state"

key-files:
  created: []
  modified:
    - src/main/java/com/cxmxrgo/secondshift/client/render/SoulAltarRenderer.java

key-decisions:
  - "Hovering job-item render uses the render method's own packedLight parameter (ambient-lit) rather than LightTexture.FULL_BRIGHT, deliberately distinguishing it from the emissive embedded Soul Block per UI-SPEC's render-coordination contract"

patterns-established: []

requirements-completed: [ALTAR-02]

# Metrics
duration: 10min
completed: 2026-09-05
---

# Phase 5 Plan 3: Job-Item Socket Render Summary

**Added a hovering, slowly spinning render of the socketed job-site block above the Soul Altar, using the same `renderSingleBlock` technique as the existing embedded Soul Block but ambient-lit and visually distinct, closing out G-2's render half.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-09-05T02:20:00Z (approx.)
- **Completed:** 2026-09-05T02:30:00Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- `SoulAltarRenderer` now renders both altar sockets simultaneously and distinctly: the embedded Soul Block stays exactly as before (recessed, `FULL_BRIGHT`, no rotation), and a new block renders hovering at `HOVER_Y = 1.35D`, scaled to `0.4F`, spinning at `1.0F` degrees per tick, lit at the render method's own `packedLight` (ambient, not emissive).
- The job-item render only fires when `!be.isJobItemEmpty() && be.getHeldJobItem().getItem() instanceof BlockItem` — a plain no-op when the job socket is empty, matching the existing `be.isEmpty()` early-return pattern for the Soul Block socket.
- The two socket renders use fully independent `pose.pushPose()`/`pose.popPose()` pairs (verified via `pushPose` occurring exactly twice in the file) with no shared transform-stack state, satisfying the plan's "never share a transform stack" truth.

## Task Commits

Each task was committed atomically:

1. **Task 1: Hovering + spinning job-item render** - `698240d` (feat)

**Plan metadata:** (this commit)

## Files Created/Modified
- `src/main/java/com/cxmxrgo/secondshift/client/render/SoulAltarRenderer.java` - added `HOVER_Y`/`HOVER_SCALE`/`SPIN_DEG_PER_TICK` constants and an independent hover+spin render block for the socketed job item, appended after the existing embedded Soul Block render

## Decisions Made
- Used the render method's own `packedLight` for the job-item render (not `LightTexture.FULL_BRIGHT`) — the job item is a normal held block, not an emissive artifact, so it should read differently from the charged Soul Block per the UI-SPEC's render-coordination contract. Plan-specified, not a deviation.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `SoulAltarRenderer` now fully implements both halves of G-2's socket-render contract; the "full-size block on a narrow pedestal looks broken" UAT gap is visually resolved (deferred to 05-07 for manual confirmation per 05-VALIDATION.md — rendering is not GameTest-observable).
- No blockers for 05-04/05-05 (candidate offers, naming, trade selection) — this plan touched only client render code.
- `./gradlew compileJava` succeeds; `./gradlew runServer` starts clean with no client-class-leak error, confirming `SoulAltarRenderer`'s new `BlockItem`/`Axis` imports stay within the existing `client/render/` package boundary.

---
*Phase: 05-profession-resolution-trade-picker*
*Completed: 2026-09-05*

## Self-Check: PASSED

All modified files and the task commit hash verified present.
