# Phase 3: Menu & Screen Harness (HARD GATE) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-04
**Phase:** 3-menu-screen-harness-hard-gate
**Areas discussed:** Open trigger + Soul Block, "Empty" screen contents, "Job block on top" gate, Invalid-state feedback

---

## Open trigger + Soul Block

| Option | Description | Selected |
|--------|-------------|----------|
| Socket + open in one action | Right-click WITH a Soul Block + job block on top: socket into the altar AND open the screen same click. Empty-hand right-click re-opens a charged+job-block altar. | ✓ |
| Socket, then empty-hand opens | Keep Phase 2 as-is (right-click sockets one-way); add empty-hand right-click to open when charged + job block present. | |
| Open only, no consume yet | Right-click just opens the screen; Soul Block stays in hand until the Phase 5 bind consumes it; Phase 3 touches no BE state. | |

**User's choice:** Socket + open in one action
**Notes:** The socket stays one-way / no retrieval (Phase 2 behaviour); Phase 5's bind is what actually consumes it. Re-opening an already-charged altar (with or without a Soul Block in hand) just opens, no error, no double-socket.

---

## "Empty" screen contents

| Option | Description | Selected |
|--------|-------------|----------|
| Container screen + 1 slot | AbstractContainerScreen: vanilla chrome + player inventory + the altar's one slot showing the socketed Soul Block. Nothing else. | ✓ |
| Container screen, 0 mod slots | Standard chrome + inventory, no mod slot — blank content area. | |
| Bare titled panel | Plain Screen (not AbstractContainerScreen), titled box, no inventory/slots. | |

**User's choice:** Container screen + 1 slot
**Notes:** The slot is display-only this phase (no take/place). User wants the harness to look like a finished dialog, not a stub. Real AbstractContainerMenu → MenuType → Screen plumbing is the point of the hard gate.

---

## "Job block on top" gate

| Option | Description | Selected |
|--------|-------------|----------|
| Backs a PoiType | Any block whose state has an associated PoiType — real "job site" definition, no profession resolution. | |
| Any non-air block | Any non-air, non-replaceable block; defer all validation to Phase 5. | |
| Maps to a profession | Only a block whose PoiType resolves to a real VillagerProfession. Builds the runtime resolver now. | ✓ |

**User's choice:** Maps to a profession
**Notes:** Confirmed in a follow-up: Phase 3 builds the runtime PoiType→VillagerProfession resolver (research §9, no hardcoded list) and uses it ONLY as a yes/no gate. The resolved profession is not stored or used — Phase 5 wires ALTAR-02 target-profession behaviour on the same resolver. Chosen deliberately for less Phase 5 rework and a discovery flow that feels complete.

---

## Invalid-state feedback

| Option | Description | Selected |
|--------|-------------|----------|
| Silent no-op now | All SC3 cases silent; stillValid closes silently; POL-08 messages deferred. | |
| Themed messages for common cases | Action-bar messages for no-job-block / no-Soul-Block / wrong-block; silent close. | |
| Full POL-08 set now | All themed messages including an on-close reason when the screen is force-closed. | ✓ |

**User's choice:** Full POL-08 set now
**Notes:** Confirmed in a follow-up: scoped to invalid states reachable in Phase 3 (no job block, no Soul Block, unmapped block-on-top, altar broken / job block removed / walked-away on-close). POL-08's later cases (empty trade pool, no valid quarters) stay deferred to Phases 5 / 9. HR-necromancer tone.

---

## Claude's Discretion

- GUI texture / background dimensions and the 1-slot position (nearly-empty screen; `/gsd:ui-phase 3` optional).
- Precise message strings (tone fixed: HR-necromancer).
- Read-only slot implementation (Slot subclass vs flag overrides).
- SC4 GameTest location (new method in HarvesterGameTests vs a new BindingAltarGameTests class).
- Reconciling SC2's `FMLCommonSetupEvent` wording with Phase 1's `FMLLoadCompleteEvent` guardrail (flagged for research/planning — keep one event, assert the menu key bound).

## Deferred Ideas

None — discussion stayed within phase scope. The user's "thorough" picks (profession resolver, full POL-08) were folded into Phase 3 scope rather than deferred.
