# Phase 4: Employee Attachment & Spawn - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-04
**Phase:** 4-employee-attachment-spawn
**Areas discussed:** Name input, Profession, Visual distinction, Spawn position

---

## Name input

| Option | Description | Selected |
|--------|-------------|----------|
| Real text field | A minimal EditBox in the Confirm screen, sent via a SelectTradesPayload (name only). | |
| Generated default name | No text input this phase — auto-generate a name. | ✓ (modified) |

**User's choice:** Generated default name, changeable with name tag
**Notes:** User explicitly added that the generated name must remain renameable via the vanilla Name Tag item afterward — captured as D-02 (an acceptance criterion: name-tag renaming must not be broken by any custom interact override).

---

## Profession

| Option | Description | Selected |
|--------|-------------|----------|
| Farmer | Research's suggested example. | ✓ (modified) |
| Different profession | Pick another. | |
| None / unemployed | Tests spawn+attachment+traits in isolation. | |

**User's choice:** "Make 3 professional, librarian, farmer and another random one"
**Notes:** Interpreted as: randomly pick one of 3 fixed professions (Farmer, Librarian, + a third) on each bind, rather than a single hardcoded profession. Claude's discretion applied for the third profession — recommended Cleric — captured as D-03.

---

## Visual distinction

| Option | Description | Selected |
|--------|-------------|----------|
| Name only | Always-visible custom name tag satisfies EMP-08 literally. | ✓ (modified) |
| Name + subtle soul particle | Add an ambient particle so employees read as different immediately. | |

**User's choice:** Name only, colored green
**Notes:** No particle. Custom name must render in green, always visible — captured as D-04.

---

## Spawn position

| Option | Description | Selected |
|--------|-------------|----------|
| Directly above the altar | pos.above() — simple, deterministic. | ✓ |
| Nearest free adjacent block | Handles pos.above() being obstructed. | |

**User's choice:** Directly above the altar (Recommended)
**Notes:** No adjacent-block search logic this phase.

---

## Claude's Discretion

- Default-name generation scheme (themed office-worker pool vs. counter).
- Third profession choice (recommended Cleric).
- Exact green color/ChatFormatting constant.
- Confirm-button widget implementation detail (inline vs. new widget class).
- EmployeeData CODEC/STREAM_CODEC exact field shape beyond name/profession/tier/offers.

## Deferred Ideas

None — soul-wisp particle and real name-entry UI are explicitly deferred to later phases, not out-of-scope ideas that were dropped.
