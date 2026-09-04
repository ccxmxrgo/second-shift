# Phase 4: Employee Attachment & Spawn - Context

**Gathered:** 2026-09-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Completing a **temporary, fixed-profession** bind spawns a persistent, named `minecraft:villager`
carrying a serialized and client-synced `EmployeeData` attachment, with zero effect on wild
villagers. This phase proves the entity/attachment/sync/traits infrastructure — it does NOT
implement the real profession-resolution or trade-picker flow (Phase 5+).

**Requirements:** EMP-01, EMP-02, EMP-08, EMP-09.

**In scope:**
- `employee/EmployeeData` — record + `CODEC` + `STREAM_CODEC` + a version field (name, profession
  id, tier=1, offers, timers as needed). This is the entity's entire identity.
- `registry/ModAttachments` — `DeferredRegister<AttachmentType<?>>`, `EMPLOYEE` attachment via
  `AttachmentType.builder(...).serialize(EmployeeData.CODEC).sync(EmployeeData.STREAM_CODEC)`.
  Registered in the one-block constructor wiring; covered by `ModRegistrySelfCheck`.
- `employee/EmployeeManager.bind(...)` — real, permanent infra: spawns
  `EntityType.VILLAGER.create(level)` directly above the altar, sets a randomly-chosen fixed
  profession + level 1 + `villagerXp >= 1`, default vanilla level-1 offers for that profession,
  a generated default name (green, always-visible), attaches `EmployeeData`, `addFreshEntity`.
- A **temporary Confirm button** added to Phase 3's `BindingAltarScreen` (touches shipped Phase 3
  code) that triggers the bind server-side via a minimal payload (no name field needed per D-01 —
  see below). This is the throwaway part; `EmployeeManager.bind(...)` itself is not.
- LIGHT spike: verify `AttachmentType.Builder#sync` actually fires for entities in 21.1.248 (docs
  contradict the API per research/STACK.md §4's "Important correction"); fallback = manual
  clientbound payload if it doesn't.
- `EMP-09` guardrail: every employee-only code path gates on `hasData(ModAttachments.EMPLOYEE)` —
  a plain wild villager must be completely unaffected (no traits, no name, no attachment).

**Out of scope (Phase 5+):**
- Real profession resolution from the job-site block (ALTAR-02) — this phase's profession is
  randomly picked from a small fixed set, not resolved from what's socketed.
- Consuming the Soul Block / job-site block on bind (ALTAR-04).
- One-employee-per-altar enforcement / the altar↔employee link (ALTAR-05).
- The real trade picker, name-entry field, career-path preview (GUI-02/03, PICK-*, ROST-01).
- Employee traits beyond what's needed for EMP-08/09 this phase — no-zombify/no-lightning/no-breed
  (EMP-03/04/05) and death-drop behavior (EMP-06) are separate requirements mapped to later phases;
  do not implement them here unless a plan explicitly needs a stub to avoid a wild-villager
  regression.
- Soul-wisp ambient particle (FEATURES.md's "v1 minimum" visual) — explicitly deferred.

</domain>

<decisions>
## Implementation Decisions

### Employee naming
- **D-01:** No custom text-input UI this phase. The employee spawns with an **auto-generated
  default name** (Claude's discretion on the generation scheme — recommend a small curated pool
  fitting the "Second Shift" HR conceit, e.g. generic office-worker names, picked randomly per
  bind; a simple "Employee #N" counter is an acceptable fallback if a themed pool feels like scope
  creep). The temporary Confirm button therefore needs no payload fields beyond triggering the
  bind (simpler than the original research note's "SelectTradesPayload, name only" — no name is
  sent from the client this phase).
- **D-02:** The name **must remain changeable via a vanilla Name Tag** after spawn — do not
  override `Villager`'s interact-with-name-tag handling. Add an acceptance criterion: using a
  Name Tag on a freshly-bound employee renames it exactly like a vanilla villager.

### Profession
- **D-03:** No single hardcoded profession. On each bind, `EmployeeManager.bind(...)` **randomly
  picks one of 3 fixed professions**: `FARMER`, `LIBRARIAN`, and a third — Claude's discretion,
  recommend `CLERIC` for trade-pool variety and because it fits the "reanimation/soul" theme
  reasonably well. All three get their normal vanilla level-1 offer pool (not a hardcoded trade).
  This is still "fixed-profession, no picker" per the ROADMAP phase name — the player makes no
  choice, the game rolls one of the 3.

### Visual distinction (EMP-08)
- **D-04:** Custom name only — **no particle this phase** (the FEATURES.md soul-wisp suggestion is
  explicitly deferred). The name must render in **green** (`ChatFormatting.GREEN` or equivalent)
  and always-visible (`setCustomNameVisible(true)`), so an employee reads as visually distinct
  from a wild villager at a glance even without hovering.

### Spawn position
- **D-05:** Spawn directly `pos.above()` the altar (not a search for the nearest free adjacent
  block). Simple and deterministic; matches the socket's visual language (the employee rises from
  where the Soul Block was socketed). If `pos.above()` is provably obstructed in practice (e.g. by
  the job-site block still sitting there), that's an edge case for the planner/executor to note,
  not a reason to add adjacent-block search logic this phase.

### Claude's Discretion
- Exact default-name generation scheme (themed pool vs. counter — see D-01).
- Third profession choice beyond Farmer/Librarian (recommend Cleric — see D-03).
- Exact green color code / `ChatFormatting` constant used for the name.
- Whether the temporary Confirm button lives inline in `BindingAltarScreen` or needs a tiny new
  widget class — implementation detail for the planner.
- How `EmployeeData`'s version field and CODEC/STREAM_CODEC shape are structured, beyond
  containing name + profession + tier=1 + offers (per research/ARCHITECTURE.md's design).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Employee/attachment architecture (already fully designed)
- `.planning/research/ARCHITECTURE.md` — the `employee/` package design (`EmployeeData`,
  `EmployeeManager`, event handler split), "Slice 5 — Attachment + employee spawn (fixed
  profession, no picker)" (~line 386), and the exact `bind(...)` pseudocode (~line 284):
  `EntityType.VILLAGER.create(level)` → `setVillagerData` → `setOffers` → `setVillagerXp(1)` →
  `setCustomName`/`setCustomNameVisible(true)` → `setData(EMPLOYEE, new EmployeeData(...))` →
  `addFreshEntity`. **MUST read.**
- `.planning/research/STACK.md` §4 — `AttachmentType`/`Builder` verified against the 21.1.248
  universal jar, including the "Important correction to the published docs" on `.sync(...)` — the
  LIGHT spike this phase must resolve empirically.
- `.planning/research/FEATURES.md` — "Profession is lost until first trade" (why `villagerXp >= 1`
  is non-negotiable), "Employee visual distinction from vanilla villagers" (the deferred soul-wisp
  note), villager-never-despawns / iron-golem-compatibility notes.
- `.planning/research/SUMMARY.md` "Phase 4: Employee attachment + spawn (fixed profession, no
  picker)" — the original phase-boundary note this CONTEXT.md refines (adjusted per D-01/D-03).
- `.planning/research/PITFALLS.md` — event-bus assignment habit (`@EventBusSubscriber` — grep
  `debug.log` for the "Subscribing ... to the game/mod event bus" line), logging-per-tick
  anti-pattern.

### Existing shipped code this phase extends
- `.planning/phases/03-menu-screen-harness-hard-gate/03-01-SUMMARY.md`,
  `03-02-SUMMARY.md` — `BindingAltarScreen`/`BindingAltarMenu`/`SoulAltarBlock` as they exist now;
  the temporary Confirm button is added to this screen, not a new one.
- `./CLAUDE.md` §4 "Data attachments" (attachment API table) and §10 "Employee traits — the event
  hooks that make them possible" (for later phases' context — not all needed this phase, see Out
  of Scope).

### Phase contract
- `.planning/ROADMAP.md` "Phase 4: Employee Attachment & Spawn" — goal + 4 success criteria +
  the LIGHT spike risk note.
- `.planning/REQUIREMENTS.md` — EMP-01, EMP-02, EMP-08, EMP-09 (this phase); EMP-03/04/05/06/07,
  ALTAR-02/04/05, GUI-02/03, PICK-* (later phases, the boundary).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `registry/Mod{Items,Blocks,BlockEntities,CreativeTab,Menus}.java` — the `DeferredRegister`
  holder pattern to copy for `ModAttachments`.
- `content/block/SoulAltarBlock.java` / `menu/BindingAltarMenu.java` — where the temporary Confirm
  interaction plugs in; `SoulAltarBlockEntity` already exposes `getBlockPos()`-adjacent state for
  computing the spawn position.
- `ModRegistrySelfCheck.java` — extend `Stream.of(...)` to include `ModAttachments.ATTACHMENT_TYPES`
  (6th register).

### Established Patterns
- One visible register-in-constructor block in `SecondShift.java`.
- `Dist.CLIENT` isolation already proven twice (Phase 2 renderer, Phase 3 screen) — this phase's
  work is almost entirely common-side (attachment, entity spawn), low risk here.
- Hand-written `en_us.json`, no datagen.

### Integration Points
- `BindingAltarScreen`/`BindingAltarMenu` gain the temporary Confirm control.
- `SecondShift` constructor + `ModRegistrySelfCheck` gain the `ModAttachments` register.
- New `employee/` package (per ARCHITECTURE.md) houses `EmployeeData` + `EmployeeManager`.

</code_context>

<specifics>
## Specific Ideas

- Employee name should feel like the "Second Shift" HR conceit if a themed pool is used (office-
  worker names), not purely mechanical — but this is polish, not a requirement.
- Random-pick-of-3 professions is deliberate: gives the fixed-profession spawn some variety and a
  taste of the real game before Phase 5's real resolver exists, without building a picker.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. The soul-wisp particle (FEATURES.md) and real
name-entry UI were both explicitly deferred to later phases per D-01/D-04 above, not lost.

</deferred>

---

*Phase: 4-employee-attachment-spawn*
*Context gathered: 2026-09-04*
