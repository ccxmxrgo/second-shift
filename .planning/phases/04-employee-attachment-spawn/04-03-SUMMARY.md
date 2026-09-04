---
phase: 04-employee-attachment-spawn
plan: 03
subsystem: networking
tags: [neoforge-networking, custompacketpayload, streamcodec, villager, minecraft-1.21.1]

# Dependency graph
requires:
  - phase: 04-02
    provides: "EmployeeManager.bind(ServerLevel, BlockPos) -> Villager — the phase's permanent core spawn method"
provides:
  - "BindEmployeePayload — zero-field C->S CustomPacketPayload (D-01), registered via RegisterPayloadHandlersEvent"
  - "ServerPayloadHandler.handleBindEmployee — server-side validate/consume/bind trigger"
  - "BindingAltarMenu.access() — server-side ContainerLevelAccess accessor for position re-derivation"
  - "BindingAltarScreen Confirm Hire button — throwaway UI trigger for the permanent bind plumbing"
affects: [phase-05-trade-selection]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "RegisterPayloadHandlersEvent wired as a second modBus.addListener alongside commonSetup in SecondShift's constructor (one visible wiring point, matches D-08/D-10 discipline)"
    - "Zero-field client->server payload (D-01) as the standard shape for triggers with no client-trustworthy state — position/state always re-derived server-side from sp.containerMenu, never from payload fields"
    - "Atomic consume-then-mutate inside the same access().execute(BiConsumer) lambda invocation as the follow-on business-logic call, preventing double-fire from rapid duplicate client sends"

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/network/BindEmployeePayload.java
    - src/main/java/com/cxmxrgo/secondshift/network/ServerPayloadHandler.java
  modified:
    - src/main/java/com/cxmxrgo/secondshift/SecondShift.java
    - src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java
    - src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java
    - src/main/resources/assets/secondshift/lang/en_us.json

key-decisions:
  - "ServerPayloadHandler made public (not package-private as the plan's illustrative sketch implied) because SecondShift.java (root package) needs a compile-time method reference to it across packages — plan's 'controller role, final class, private ctor' shape retained, just with public visibility on the class and method"

patterns-established:
  - "Bind trigger validate-before-mutate: cast IPayloadContext#player() to ServerPlayer -> re-check sp.containerMenu instanceof the expected menu type -> re-run stillValid -> execute mutation inside access().execute(...) with the atomic consume immediately before the business-logic call"

requirements-completed: [EMP-01]

# Metrics
duration: 20min
completed: 2026-09-04
---

# Phase 4 Plan 3: Network Bind Trigger Summary

**A zero-field BindEmployeePayload wired through RegisterPayloadHandlersEvent triggers ServerPayloadHandler.handleBindEmployee, which re-derives the altar position exclusively from the sender's live BindingAltarMenu, atomically consumes the socketed Soul Block, and calls EmployeeManager.bind exactly once per successful click of a new throwaway "Confirm Hire" button.**

## Performance

- **Duration:** 20 min
- **Started:** 2026-09-04T19:20:00Z
- **Completed:** 2026-09-04T19:40:28Z
- **Tasks:** 3
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments
- `BindEmployeePayload` — a zero-field `CustomPacketPayload` record (D-01) — registered for the play-to-server direction via `RegisterPayloadHandlersEvent` (`registrar("1")`), wired as a second `modBus.addListener` alongside the existing `commonSetup` listener in `SecondShift`'s constructor
- `ServerPayloadHandler.handleBindEmployee` implements the full validate-before-mutate chain mirroring `SoulAltarBlock.useItemOn`: casts `IPayloadContext#player()` to `ServerPlayer`, re-checks the player's live `sp.containerMenu instanceof BindingAltarMenu`, re-runs `stillValid`, then inside `menu.access().execute(...)` atomically clears the Soul Block slot (`setHeldSoulBlock(EMPTY)` + `setChanged()` + `sendBlockUpdated`) strictly before calling `EmployeeManager.bind(serverLevel, pos)`, and closes the container on success
- `BindingAltarMenu.access()` — the one-line accessor the handler needs to re-derive the altar position purely from server-side menu state, never from any client-supplied data (T-4-01)
- `BindingAltarScreen` gained a throwaway "Confirm Hire" button (D-01) at `(leftPos+8, topPos+60)` — clears both the Soul Slot (y 35-51) and the player inventory grid (y 84+) — that sends `PacketDistributor.sendToServer(new BindEmployeePayload())`
- Full verification: `./gradlew compileJava` clean after every task, `./gradlew build` clean, `./gradlew runServer` boots to `Done (0.313s)!` with no `PayloadRegistrar` crash and no client-class leak, `./gradlew runGameTestServer` still reports 19/19 required tests passed (no regressions from the menu/handler changes), and `./gradlew deployToTest` copied the built jar into the CurseForge "test" instance for the user's manual `runClient` verification of the full altar -> screen -> Confirm -> spawn loop

## Task Commits

Each task was committed atomically:

1. **Task 1: BindEmployeePayload contract + payload registration** - `c2bad55` (feat)
2. **Task 2: ServerPayloadHandler — validate, atomically consume, bind** - `28ffcff` (feat)
3. **Task 3: Temporary Confirm button in BindingAltarScreen** - `46176bb` (feat)

**Plan metadata:** pending (docs: complete plan)

## Files Created/Modified
- `src/main/java/com/cxmxrgo/secondshift/network/BindEmployeePayload.java` - zero-field C->S `CustomPacketPayload` record (D-01)
- `src/main/java/com/cxmxrgo/secondshift/network/ServerPayloadHandler.java` - `handleBindEmployee` validate/atomically-consume/bind
- `src/main/java/com/cxmxrgo/secondshift/SecondShift.java` - added `registerPayloads` listener registering the play-to-server handler
- `src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java` - added `access()` accessor (the ONLY change to this file)
- `src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java` - throwaway "Confirm Hire" button wired to send the payload
- `src/main/resources/assets/secondshift/lang/en_us.json` - added `gui.secondshift.binding_altar.confirm` lang key

## Decisions Made
- `ServerPayloadHandler` is `public final class` with a `public static` handler method, not package-private as the plan's illustrative sketch might read — required because `SecondShift.java` lives in the root `com.cxmxrgo.secondshift` package and needs a compile-time method reference (`ServerPayloadHandler::handleBindEmployee`) across the package boundary. The "controller role, final class, private ctor" shape the plan specified is preserved exactly; only the class/method visibility modifier changed from the plan's ambiguous default.

## Deviations from Plan

None - plan executed exactly as written. The only adjustment (public vs package-private visibility on `ServerPayloadHandler`) was a compile-correctness necessity inherent to the plan's own cross-package method-reference requirement, not a behavioral or architectural change — documented above under Decisions Made rather than as a Rule-1/2/3 deviation since no bug or missing functionality was involved.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required. The built jar has been deployed to `C:\Users\user\curseforge\minecraft\Instances\test\mods\` via `./gradlew deployToTest`; the user can launch the CurseForge "test" instance to manually verify the full altar -> screen -> Confirm Hire -> spawn loop (place altar, place job block, insert Soul Block, open the Binding Altar screen, click Confirm Hire, observe a named villager spawn above the altar and the screen close).

## Next Phase Readiness
- The end-to-end "insert Soul Block, click Confirm, employee spawns" loop is now reachable in-game, not just from GameTests — Phase 4's success criteria are observable under `runClient`.
- Both flagged threats (T-4-01 client-position spoofing, T-4-02 rapid-click double-bind) are mitigated exactly per the RESEARCH.md Security Domain table: the payload carries zero fields and the handler never reads a position from it; the Soul Block slot's empty-check and consume happen atomically in the same lambda invocation as the bind call.
- The Confirm Hire button is explicitly throwaway (D-01) — Phase 5 replaces it with a real trade/profession picker, but the `BindEmployeePayload` -> `ServerPayloadHandler` -> `EmployeeManager.bind` plumbing and the "menu re-derives position, never trusts client state" pattern are permanent and should be reused/extended, not rebuilt.
- No blockers. Manual `runClient` verification is the one remaining step and is the user's to perform (deployed jar ready).

---
*Phase: 04-employee-attachment-spawn*
*Completed: 2026-09-04*
