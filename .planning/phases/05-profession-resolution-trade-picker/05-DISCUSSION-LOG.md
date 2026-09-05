# Phase 5: Profession Resolution & Trade Picker - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-05
**Phase:** 5-profession-resolution-trade-picker
**Areas discussed:** G-2 scope, Trade selection UX, Naming, Altar occupancy

---

## G-2 scope (job-site block mechanic)

| Option | Description | Selected |
|--------|-------------|----------|
| Include G-2 in Phase 5 | Build the item-socket + hovering render now, alongside real profession resolution. Avoids reworking the same interaction twice; permanently resolves the spawn-overlap bug's root cause. | ✓ |
| Defer G-2 to its own phase | Build against the current "place a real block" mechanic; ProfessionResolver/SoulAltarBlock get touched twice. | |

**User's choice:** Include G-2 in Phase 5 (recommended option).
**Notes:** ROADMAP.md's Phase 5 success criteria still describe placing a real job-site block, which now conflicts with this decision — planner should read "placing a lectern" as "socketing a lectern item."

---

## Trade selection UX

| Option | Description | Selected |
|--------|-------------|----------|
| Click-to-toggle rows | Each candidate offer is a clickable row; clicking toggles selected/unselected, capped at 2. | ✓ |
| Checkboxes beside each row | Explicit checkbox UI per row. | |

**User's choice:** Click-to-toggle rows (recommended option).

---

## Naming

| Option | Description | Selected |
|--------|-------------|----------|
| Fall back to the auto-generated name | If the player clears the field and confirms, silently keep the pre-filled EmployeeNames pool name instead of allowing an empty CustomName. | ✓ |
| Block confirm until non-empty | Disable the Confirm button while the name field is blank. | |

**User's choice:** Fall back to the auto-generated name (recommended option).

---

## Altar occupancy (ALTAR-05)

| Option | Description | Selected |
|--------|-------------|----------|
| Themed message + refuse to open | Right-clicking an occupied altar with bind items shows a themed message and never opens the screen — matches Phase 3's existing no-op pattern. | ✓ |
| Open the screen but disable Confirm | The screen opens normally, but Confirm is disabled with an inline explanation. | |

**User's choice:** Themed message + refuse to open (recommended option).

---

## Follow-up clarification (not a gray area — factual question)

User asked whether the lectern-on-altar test currently spawning random professions was expected. Confirmed: yes, Phase 4's D-03 deliberately locked random-pick-of-3-professions as a temporary placeholder to prove spawn infrastructure without building real profession resolution — this phase (5) is exactly what replaces that with real POI-based resolution.

## Claude's Discretion

- Exact wording of all themed messages (invalid job item, empty tier pool, altar-occupied, etc.).
- GUI-03's happiness-state field this phase (Phase 9 doesn't exist yet — show placeholder or omit).
- Tier-1 candidate list scrolling/pagination if a pool is large.
- Exact BE slot/data shape for the socketed job item.
- Whether a trivial career-path-preview stub naturally falls out of the tier-1 UI work (not a build target, just not forbidden if free).

## Deferred Ideas

- Read-only 5-tier career-path preview panel (FEATURES.md's "Multi-tier career planning" P2/v1.x idea) — deferred, revisit after tier-by-tier picking is validated in play.
- Config-flag toggle for full enchantment enumeration on librarian book trades — out of scope, current design always rolls exactly one enchanted-book offer.
