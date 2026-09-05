---
phase: 04-employee-attachment-spawn
verified: 2026-09-05T01:05:00Z
status: passed
score: 4/4 must-haves verified
overrides_applied: 0
---

# Phase 4: Employee Attachment & Spawn Verification Report

**Phase Goal:** Completing a (temporary, fixed-profession) bind spawns a persistent, named `minecraft:villager` carrying a serialized and client-synced `EmployeeData` attachment, with zero effect on wild villagers.
**Verified:** 2026-09-05T01:05:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth (roadmap success criteria) | Status | Evidence |
|---|-----------------------------------|--------|----------|
| 1 | Confirming the bind spawns a `minecraft:villager` with an always-visible custom name near the altar, with villager XP >= 1 | VERIFIED | `EmployeeManager.bind` (src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java) sets `villager.setVillagerXp(1)` and `setCustomNameVisible(true)` with a green name. `EmployeeGameTests.bind_sets_villager_xp_at_least_one` and `bind_sets_green_always_visible_name` pass. Independently re-ran `./gradlew runGameTestServer` — 20/20 tests green (confirmed live, not from SUMMARY claim). |
| 2 | `/data get entity <uuid>` shows the `EmployeeData` attachment; survives save-quit-reload and a 300-block round trip | VERIFIED (human-confirmed, checkpoint approved) | `EmployeeData.CODEC` persists via `.serialize(...)` in `ModAttachments.java`. 04-04-SUMMARY.md and 04-VALIDATION.md record the developer's own approved checkpoint (2026-09-05) confirming both round trips against a real client, after CR-01 spawn-overlap fix. Per task instructions, this developer-confirmed manual checkpoint (not a SUMMARY narrative claim) is accepted as evidence for the two structurally GameTest-unreachable behaviors (real save/quit/relaunch, real chunk unload). |
| 3 | The employee's attachment data is readable client-side, confirming sync fires for entities | VERIFIED (human-confirmed, checkpoint approved) | `ClientEmployeeSyncDebug.java` implements a tick-polling client-side read gated on `hasData` before `getData` (correctly avoids the false-negative timing bug found and fixed during this phase). 04-04-SUMMARY.md documents an actual captured log line: `entity=Villager['juninho'/195...] data=EmployeeData[version=1, name=Wendell Timesheet, profession=minecraft:librarian, tier=1, offers=[...]]` — real populated data, not `EmployeeData.EMPTY`. This is the LIGHT spike's empirical resolution, confirmed by the developer's own re-test after the sync-timing fix. |
| 4 | A wild villager in the same world is completely unaffected — trades, profession resets, breeding, AI all vanilla | VERIFIED | `EmployeeGameTests.wild_villager_unaffected_by_bind_in_same_world` asserts `hasData` false, no custom name, `xp == 0` for a wild villager spawned in the same GameTest world as a bind call — passes (confirmed in the independent 20/20 test run above). Developer-confirmed real-client regression check also passed in the 04-04 checkpoint (step 7). `bind` only ever calls `EntityType.VILLAGER.create(level)` to construct a brand-new entity — never looks up or mutates an existing one (verified by reading `EmployeeManager.java` directly). |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `employee/EmployeeData.java` | record + EMPTY + CODEC + STREAM_CODEC | VERIFIED | Read directly — record fields `(version, name, profession, tier, offers)` match STREAM_CODEC composite order exactly. |
| `registry/ModAttachments.java` | `ATTACHMENT_TYPES` DeferredRegister + `EMPLOYEE` holder | VERIFIED | Read directly — registered as 6th `DeferredRegister`, wired in `SecondShift.java` line 49, guarded in `ModRegistrySelfCheck.java` line 78 `Stream.of(...)`. |
| `client/ClientEmployeeSyncDebug.java` | Game-bus, Dist.CLIENT diagnostic | VERIFIED | Read directly — `@EventBusSubscriber(modid=..., value=Dist.CLIENT)`, gates `hasData` before `getData`, uses corrected tick-poll pattern (post sync-timing bugfix). |
| `employee/EmployeeManager.java` | `static Villager bind(ServerLevel, BlockPos)` | VERIFIED | Read directly — exact spawn ordering matches Pattern 2 (profession → xp → offers → name → attachment → addFreshEntity); spawn position uses `altarPos.above(2)` post CR-01 fix. |
| `employee/EmployeeNames.java` | Themed default-name pool | VERIFIED (via GameTest + SUMMARY; not independently re-read line-by-line but referenced and exercised by passing GameTests) | `bind_sets_green_always_visible_name` and other tests exercise `EmployeeNames.pickRandom` transitively; no blank-name failures across 20 tests including a 20-iteration profession-pool test. |
| `gametest/EmployeeGameTests.java` | GameTest coverage EMP-01/02/08/09 + CR-01 regression | VERIFIED | Read directly — 6 methods present, matches SUMMARY claims, independently re-run and passing (20/20). |
| `network/BindEmployeePayload.java` | zero-field C→S CustomPacketPayload | VERIFIED | Read directly — zero-field record, `STREAM_CODEC = StreamCodec.unit(...)`, no position field. |
| `network/ServerPayloadHandler.java` | validate/consume/bind | VERIFIED | Read directly — casts to `ServerPlayer`, checks live `sp.containerMenu instanceof BindingAltarMenu`, re-validates `stillValid`, consumes Soul Block before calling `EmployeeManager.bind` inside the same `access().execute(...)` lambda. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `SecondShift` constructor | `ModAttachments.ATTACHMENT_TYPES.register(modBus)` | explicit register call | WIRED | Confirmed at line 49, 6th register call. |
| `ModRegistrySelfCheck.onLoadComplete` | `ModAttachments.ATTACHMENT_TYPES` | `Stream.of(...)` guardrail | WIRED | Confirmed at line 78 — 6 registers listed including `ModAttachments.ATTACHMENT_TYPES`. |
| `EmployeeManager.bind` | `villager.setData(ModAttachments.EMPLOYEE, ...)` | attachment write after offers | WIRED | Confirmed — `setData` call is the last data/offers/name call before `addFreshEntity`. |
| `BindingAltarScreen` Confirm button | `PacketDistributor.sendToServer(new BindEmployeePayload())` | client→server send | WIRED (per SUMMARY + compileJava evidence; screen file not independently re-read this pass, but network wiring and payload registration verified directly) | `SecondShift.java` registers `registrar.playToServer(...)` per `registerPayloads`. |
| `ServerPayloadHandler.handleBindEmployee` | `EmployeeManager.bind` | `menu.access().execute(...)` after atomic consume | WIRED | Confirmed directly in source — consume happens before `bind` call inside same lambda invocation. |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|--------------|--------|----------|
| EMP-01 | 04-01, 04-02, 04-03 | Employee = `minecraft:villager` + serialized, client-synced `EmployeeData` attachment | SATISFIED | Attachment defined, registered, populated on bind, sync empirically confirmed working (04-04 checkpoint). |
| EMP-02 | 04-02 | Employees spawn with XP >= 1 | SATISFIED | `setVillagerXp(1)` in `EmployeeManager.bind`, GameTest-proven. |
| EMP-08 | 04-02 | Employees visually distinguishable (always-visible custom name) | SATISFIED | Green, always-visible custom name set and GameTest-proven; human-confirmed visually in checkpoint. |
| EMP-09 | 04-02 | Villagers without the attachment behave exactly as vanilla | SATISFIED | `wild_villager_unaffected_by_bind_in_same_world` GameTest passes; `bind` never touches an existing entity (code-reviewed, confirmed by reading `EmployeeManager.java`); human-confirmed in real client (checkpoint step 7). |

**Requirements cross-reference against REQUIREMENTS.md traceability table:** All 4 IDs declared in Phase 4 plans (EMP-01, EMP-02, EMP-08, EMP-09) match exactly the 4 IDs REQUIREMENTS.md's traceability table maps to "Phase 4 | Complete". No orphaned requirements — no other requirement ID in REQUIREMENTS.md is mapped to Phase 4 without a corresponding plan claim.

### Anti-Patterns Found

Scanned all 9 phase-modified source files for `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER`/"not yet implemented"/"coming soon" — zero matches. No debt markers.

### Code Review Findings (04-REVIEW.md, informational — not re-litigated here)

0 Critical, 5 Warnings, 2 Info — all Warnings are non-blocking per this project's code review gate policy (WR-01 Soul-Block-consumed-without-bind edge case, WR-02 missing UX feedback message, WR-03 shared-mutable-singleton latent bug in `EmployeeData.EMPTY`, WR-04 missing self-check lang-key coverage for the throwaway Confirm button, WR-05 always-on debug logging). None of these affect the phase's 4 observable truths — they are quality/robustness gaps in code paths outside the roadmap's success criteria. Recommended as follow-up cleanup, not phase blockers.

### Human Verification Required

None outstanding. The phase's human-verify checkpoint (Plan 04-04) already ran, found 2 real bugs (CR-01, sync-timing), both fixed via quick task 260905-0yg (independently confirmed present in `EmployeeManager.java` and `ClientEmployeeSyncDebug.java` during this verification), and was re-tested and approved by the developer per `04-VALIDATION.md`'s "Checkpoint closed: approved 2026-09-05" line.

### Gaps Summary

None. All 4 roadmap success criteria are verified either directly against source code (goal criteria 1 and 4, GameTest-provable) or via the developer's own approved manual checkpoint for the two criteria (2 and 3) that are structurally outside GameTest's reach (real save/quit/relaunch, real distinct-client sync observation). The full GameTest suite (20/20) was independently re-run during this verification and passed, confirming no regression since the phase's completion. No debt markers, no missing artifacts, no unwired key links.

---

_Verified: 2026-09-05T01:05:00Z_
_Verifier: Claude (gsd-verifier)_
