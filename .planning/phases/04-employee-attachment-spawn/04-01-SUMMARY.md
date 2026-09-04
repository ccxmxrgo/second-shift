---
phase: 04-employee-attachment-spawn
plan: 01
subsystem: attachments
tags: [neoforge-attachments, codec, streamcodec, villager, data-attachment]

# Dependency graph
requires:
  - phase: 03-menu-screen-harness
    provides: registration + register-in-constructor + ModRegistrySelfCheck guardrail pattern (D-08/D-09/D-10)
provides:
  - "EmployeeData record (version, name, profession, tier, offers) with CODEC + STREAM_CODEC"
  - "ModAttachments.ATTACHMENT_TYPES / ModAttachments.EMPLOYEE registered as the mod's 6th DeferredRegister"
  - "ClientEmployeeSyncDebug — Dist.CLIENT EntityJoinLevelEvent diagnostic answering the phase's LIGHT spike"
affects: [04-02-employee-spawn, 04-03-network-bind-trigger]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "NeoForge data attachment registration: DeferredRegister<AttachmentType<?>> via NeoForgeRegistries.ATTACHMENT_TYPES, .register(name, () -> AttachmentType.builder(...).serialize(CODEC).sync(STREAM_CODEC).build())"
    - "DeferredRegister<AttachmentType<?>>.register(...) returns DeferredHolder<AttachmentType<?>, AttachmentType<T>> (explicit typed holder, matching the project's existing 5-registry convention — never raw var)"
    - "Game-bus event diagnostics live in their own Dist.CLIENT @EventBusSubscriber class, separate from the mod-bus ClientModBusEvents"

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeData.java
    - src/main/java/com/cxmxrgo/secondshift/registry/ModAttachments.java
    - src/main/java/com/cxmxrgo/secondshift/client/ClientEmployeeSyncDebug.java
  modified:
    - src/main/java/com/cxmxrgo/secondshift/SecondShift.java
    - src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java

key-decisions:
  - "ModAttachments.EMPLOYEE typed as DeferredHolder<AttachmentType<?>, AttachmentType<EmployeeData>> (compiler-inferred return of DeferredRegister<AttachmentType<?>>.register(...)), not the Supplier<AttachmentType<EmployeeData>> shape sketched in the plan's interfaces block — verified via javap against the actual DeferredRegister.register(String, Supplier) signature in neoforge-21.1.248-merged.jar"
  - "ClientEmployeeSyncDebug is a standalone Dist.CLIENT class (not folded into ClientModBusEvents) because EntityJoinLevelEvent is a game-bus event while ClientModBusEvents only handles mod-bus events"

patterns-established:
  - "Attachment registries follow the same register-in-constructor + ModRegistrySelfCheck Stream.of(...) guardrail + manual-detach verification discipline as items/blocks/block-entities/creative-tab/menus"

requirements-completed: [EMP-01]

# Metrics
duration: 25min
completed: 2026-09-04
---

# Phase 4 Plan 1: Employee Attachment Contract & Registration Summary

**EmployeeData attachment record (codec + stream-codec backed) registered as the mod's 6th DeferredRegister, plus a client-side diagnostic to observe whether AttachmentType#sync fires for entity holders in NeoForge 21.1.248.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-09-04T19:56:00Z
- **Completed:** 2026-09-04T20:21:30Z
- **Tasks:** 3
- **Files modified:** 5 (3 created, 2 modified)

## Accomplishments
- `EmployeeData` is a fully specified record (`version, name, profession, tier, offers`) with `EMPTY`, `CODEC` (persistence), and `STREAM_CODEC` (sync) — field order verified to match the STREAM_CODEC composite argument order exactly
- `ModAttachments.EMPLOYEE` registered under `secondshift:employee`, wired into `SecondShift`'s constructor as the 6th `.register(modBus)` call, and guarded by `ModRegistrySelfCheck`'s unbound-registry check as the 6th `Stream.of(...)` entry
- Manual-detach hard-abort (D-14) proven live: commenting out `ModAttachments.ATTACHMENT_TYPES.register(modBus)` and running `./gradlew runServer` produced `IllegalStateException: Unbound registry entries: [secondshift:employee]`; the line was restored and a clean re-run confirmed `Done (0.312s)! For help, type "help"` with no exceptions
- `ClientEmployeeSyncDebug` added — a `Dist.CLIENT`, game-bus `EntityJoinLevelEvent` handler that gates `hasData` before `getData` (T-4-03) on `Villager` entities and logs `EmployeeData` at INFO once a real bind exists in Plan 04-02/04-03; confirmed present with no `NoClassDefFoundError`/class-leak under `./gradlew runServer`

## Task Commits

Each task was committed atomically:

1. **Task 1: EmployeeData record contract** - `5b838f9` (feat)
2. **Task 2: ModAttachments registry + wiring + self-check guardrail (6th register)** - `0dabd18` (feat)
3. **Task 3: Client-side EmployeeData sync diagnostic (LIGHT spike verification mechanism)** - `420aade` (feat)

**Plan metadata:** pending (docs: complete plan)

## Files Created/Modified
- `src/main/java/com/cxmxrgo/secondshift/employee/EmployeeData.java` - record + EMPTY + CODEC + STREAM_CODEC, the shared employee data contract
- `src/main/java/com/cxmxrgo/secondshift/registry/ModAttachments.java` - `ATTACHMENT_TYPES` DeferredRegister + `EMPLOYEE` holder
- `src/main/java/com/cxmxrgo/secondshift/SecondShift.java` - added `ModAttachments.ATTACHMENT_TYPES.register(modBus)` as the 6th register-in-constructor line
- `src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java` - added `ModAttachments.ATTACHMENT_TYPES` as the 6th entry in the unbound-registry `Stream.of(...)` guardrail
- `src/main/java/com/cxmxrgo/secondshift/client/ClientEmployeeSyncDebug.java` - Dist.CLIENT `EntityJoinLevelEvent` diagnostic for the LIGHT spike

## Decisions Made
- Verified `DeferredRegister<AttachmentType<?>>.register(...)`'s actual compile-time return type via `javap` against the project's own `neoforge-21.1.248-merged.jar` rather than trusting the plan's illustrative `Supplier<AttachmentType<T>>` sketch — used the compiler-correct `DeferredHolder<AttachmentType<?>, AttachmentType<EmployeeData>>` to match this project's existing typed-holder convention.
- Verified `MerchantOffers.CODEC` / `MerchantOffers.STREAM_CODEC` exist as public static fields in 1.21.1 via `javap` before relying on them in `EmployeeData`.

## Deviations from Plan

None - plan executed exactly as written (interfaces block's illustrative type sketch was clarified against the actual compiled API per its own instruction to "verify the actual declared type").

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `EmployeeData` and `ModAttachments.EMPLOYEE` are now available for Plan 04-02 (employee spawn logic) and Plan 04-03 (network bind trigger) to consume directly.
- The LIGHT spike's diagnostic mechanism (`ClientEmployeeSyncDebug`) is in place but has nothing to observe yet — its log line will only fire once a real attachment bind exists (Plan 04-02/04-03), which is when the phase's open question (does `.sync(STREAM_CODEC)` deliver attachment data to entity clients in 21.1.248) gets its empirical answer.
- No blockers.

---
*Phase: 04-employee-attachment-spawn*
*Completed: 2026-09-04*
