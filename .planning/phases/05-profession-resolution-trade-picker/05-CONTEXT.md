# Phase 5: Profession Resolution & Trade Picker - Context

**Gathered:** 2026-09-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace Phase 4's fixed-profession placeholder (random pick of 3 professions, default vanilla
level-1 offers, no player choice) with the real core value: the job-site block placed on the altar
determines a real vanilla profession (via the POI registry, no hardcoded list), the player sees that
profession's real tier-1 trade pool as materialized `MerchantOffer`s, picks exactly 2 (or all, when
the pool is ≤ 2), edits the pre-filled name, and confirms — spawning an employee with exactly those
trades. This phase also replaces the "place a real block on top" mechanic (Phases 2-4) with an
item-socket + hovering render mechanic (the locked G-2 decision from Phase 3's UAT), since the two
are the same interaction surface and G-2's fix also permanently resolves Phase 4's spawn-position
workaround (`altarPos.above(2)`).

**Requirements:** ALTAR-02, ALTAR-04, ALTAR-05, PICK-01 through PICK-08, GUI-02, GUI-03.

**In scope:**
- G-2 rework: right-click a job-site block (as an item) onto the altar → consumed into a second
  BE-stored slot → custom `BlockEntityRenderer` draws it hovering + slowly spinning above the altar
  (same technique as the existing charged Soul Block socket / enchanting-table floating book).
  Reworks `ProfessionResolver.fromAbove`, `SoulAltarBlock.useItemOn`/`useWithoutItem`,
  `BindingAltarMenu.stillValid`, and `BindingAltarGameTests`'s block-placement tests.
- `ProfessionResolver` reading the socketed item's block state → real profession, no hardcoded list
  (PICK-01). A job item that maps to no profession → themed message, no crash (PICK-08).
- `TradePoolCache`/`TradePoolHandler`: roll tier-1 candidates from the profession's real vanilla
  `ItemListing` pool via a throwaway (not-added-to-level) villager, materialize into concrete
  `MerchantOffer`s server-side (PICK-02). Empty pool → themed message, no crash (PICK-08).
- Real `BindingAltarScreen` UI: candidate offer list (click-to-toggle rows, max 2 selected — D-02),
  editable name field pre-filled with Phase 4's `EmployeeNames` default (D-03), Confirm button
  replaces Phase 4's throwaway one.
- `SelectTradesPayload` (or equivalent) carrying chosen candidate indices + name — **not** raw
  `MerchantOffer` data. Server re-validates indices against its own materialized candidate list and
  re-checks altar/menu proximity before acting (GUI-02) — same trust-boundary discipline as Phase
  4's `BindEmployeePayload`.
- Bind-order discipline (PITFALLS.md Pitfall 4): `setVillagerData` → `refreshBrain` → `setVillagerXp`
  → `setOffers(chosenOffers)` **last** → persist chosen offers into the attachment.
- Auto-select + lock when a tier's pool has ≤ 2 trades, still displayed in the picker (PICK-04).
- Librarian tier pools include exactly one freshly-rolled enchanted-book offer, not every enchantment
  enumerated separately (PICK-05) — matches FEATURES.md's explicit anti-Trade-Picker design choice.
- One-employee-per-altar enforcement (ALTAR-05): themed message + refuse to open the screen at all
  when the altar is already occupied (D-04) — same no-op pattern as Phase 3's no-job/no-soul-block
  cases, altar↔employee link persists across save/load.
- Consuming the Soul Block AND the job-site item into the altar on successful bind (ALTAR-04).
- `BindingAltarMenu`/GUI shows name, profession, tier, chosen trades (GUI-03) — the happiness-state
  portion of GUI-03 is a forward reference to Phase 9 (not built yet); show a static "N/A" or omit
  that field entirely this phase (Claude's discretion, see below).

**Out of scope (later phases):**
- The happiness system itself (Phase 9) — GUI-03's happiness-state display has nothing real to show
  yet.
- Leveling / promotion ritual, tier 2-5 trade picking (Phase 7).
- Employee traits (no-zombify already shipped pre-Phase-4-scope note: verify — traits are Phase 6).
- Read-only 5-tier career-path preview panel (FEATURES.md's P2/v1.x idea) — explicitly deferred,
  see `<deferred>`.
- Restock timer (Phase 8).

</domain>

<decisions>
## Implementation Decisions

### G-2 scope (job-site block mechanic)
- **D-01:** Phase 5 includes building G-2's item-socket + hovering-render rework, not just real
  profession resolution against the current "place a real block" mechanic. Rationale: the two are
  the same interaction surface (what determines profession), and G-2's fix permanently resolves the
  root cause of Phase 4's CR-01 spawn-overlap bug (the interim `altarPos.above(2)` fix can be
  revisited once the job item is a non-collidable socketed render rather than a real block occupying
  `altarPos.above()`).

### Trade selection UX
- **D-02:** Candidate offers are click-to-toggle rows (not separate checkboxes) — click a row to
  select/deselect, capped at 2 selected at once. Matches vanilla trade-screen row interaction style,
  no new widget type needed beyond what a list-of-rows requires.

### Naming
- **D-03 (SUPERSEDED 2026-09-07, round-6 UI redesign):** Originally locked as an editable name
  field. Superseded after real-client testing surfaced a vanilla-gotcha bug (pressing "E" while
  typing closed the whole screen, since a focused `EditBox` doesn't consume plain alphanumeric keys
  in `keyPressed` — those fall through to the container-close keybind check) and the user provided
  a new mockup with no name field at all. **New decision:** name editing is removed this phase
  entirely — the employee always gets its `EmployeeNames`-pool-generated default name. This also
  eliminates the E-key bug at the root (no focusable text field exists in the screen anymore).
  `SelectTradesPayload` still carries a name field for wire-format stability, but the client always
  sends `""`; `ServerPayloadHandler.sanitizeName`'s existing fallback-to-default behavior applies
  the default name transparently, so no network-payload shape change was needed.

### Screen layout (D-05, ADDED 2026-09-07, round-6 UI redesign)
- **D-05:** Full layout redesign per a user-provided mockup, replacing Plan 05-06's original
  layout. Top to bottom: a full-width Confirm Hire button at the very top; the Soul Block and
  profession-item sockets side by side below it (both now real display-only `SoulSlot`s — the
  profession item is visible as an icon in the GUI for the first time, via a new
  `AltarJobItemContainer` mirroring `AltarSoulContainer`'s exact pattern); the scrollable
  trade-candidate list to the right of the two sockets at the same height (3 visible rows); the
  profession name (gold) and the Phase-9 Happiness placeholder in the narrow column directly under
  the two sockets; then the standard player inventory grid, repositioned to clear the new content
  above it. Canvas resized to 176x172 (down from the round-3 fix's 200x222 — the new layout is more
  compact). Texture regenerated to match.

### Altar occupancy (ALTAR-05)
- **D-04:** Right-clicking an already-occupied altar with bind items (job item + Soul Block) shows a
  themed message (e.g. "This altar already has an employee") and never opens the Binding Altar
  screen — matches the existing Phase 3 no-op pattern for missing-job-block/missing-Soul-Block cases.
  Do not open the screen with a disabled Confirm button.

### Claude's Discretion
- Exact wording of all themed messages (invalid job item, empty tier pool, altar-occupied, etc.).
- GUI-03's happiness-state field this phase: show a static placeholder/omit it, since the happiness
  system doesn't exist until Phase 9 — planner's call on the least-effort correct approach.
- Whether tier-1 candidate list rendering needs scrolling/pagination if a profession's pool is large
  — implementation detail.
- Exact BE slot/data shape for the socketed job item (mirrors the existing Soul Block socket
  pattern — see code_context below).
- Career-path preview panel: NOT built this phase (see deferred), but if a trivial read-only stub
  naturally falls out of the tier-1 UI work, no objection — just don't scope-expand to build it
  properly.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### G-2 redesign (locked decision, full rationale)
- `.planning/phases/03-menu-screen-harness-hard-gate/03-HUMAN-UAT.md` §G-2 — the visual problem
  (full-size job block on a narrow pedestal looks broken) and the locked replacement design.
- `.planning/STATE.md` "Deferred Items" table, G-2 row — implementation notes and the explicit note
  that this reworks Phase 3's shipped code, not purely additive.
- `.planning/phases/04-employee-attachment-spawn/04-REVIEW.md` CR-01 (via
  `.planning/REVIEW-phases-1-4.md`) — the spawn-overlap bug G-2 permanently resolves.

### Trade picker / profession resolution design (already fully designed)
- `.planning/research/ARCHITECTURE.md` Slice 6 (Profession resolution, ~line 394), Slice 7 (Trade
  pool cache + candidate materialization, ~line 401), Slice 8 (Trade picker interaction + apply,
  ~line 410) — exact class/method breakdown (`TradePoolCache.rollCandidates`, `TradePickerWidget`,
  `NameEntryWidget`, `SelectTradesPayload`). **MUST read.**
- `.planning/research/FEATURES.md` — "Offers are materialized at roll time" (why you can't show
  generic "Enchanted Book," must roll+show the real materialized offer), "Multi-tier career
  planning" (why tier-1-now is the chosen design, not a full 5-tier picker), "Enumerating every
  enchantment" (why librarians get exactly ONE rolled enchanted-book offer, not all enchantments
  listed) — directly informs PICK-03/04/05.
- `.planning/research/PITFALLS.md` Pitfall 4 (order-of-operations: `setVillagerData` before
  `setOffers`, or offers silently discarded) and Pitfall 3 (`getOffers()` lazy-fabricates 2 random
  trades on first call — never call it before installing chosen offers).
- `.planning/research/STACK.md` §8 — `ItemListing.getOffer` needs an `Entity`; use a throwaway
  villager not added to the level; the LIGHT spike this phase must resolve empirically (does
  `getOffer` have side effects or return null for treasure-map-style listings across every
  profession × tier?).

### Existing shipped code this phase extends
- `.planning/phases/04-employee-attachment-spawn/04-01-SUMMARY.md` through `04-04-SUMMARY.md` —
  `EmployeeData`/`EmployeeManager`/`ModAttachments`/the network trust-boundary pattern as they exist
  now.
- `.planning/phases/03-menu-screen-harness-hard-gate/03-01-SUMMARY.md`, `03-02-SUMMARY.md` —
  `BindingAltarScreen`/`BindingAltarMenu`/`SoulAltarBlock`/`ProfessionResolver` as they exist now.
- `./CLAUDE.md` §4 (Data attachments), §9 (POI → profession, no hardcoded list) — canonical API
  patterns already established.

### Phase contract
- `.planning/ROADMAP.md` "Phase 5: Profession Resolution & Trade Picker" — goal + 5 success criteria
  + risks (note: success criterion 1's wording, "placing a lectern on the altar," predates the G-2
  decision above — read it as "socketing a lectern item," not literally placing the block).
- `.planning/REQUIREMENTS.md` — ALTAR-02/04/05, PICK-01 through PICK-08, GUI-02/03 (this phase);
  GUI-03's happiness-state clause forward-references Phase 9.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SoulAltarBlockEntity`'s existing charged-Soul-Block socket (BE-stored `ItemStack` field +
  `BlockEntityRenderer` hovering/spinning render) is the direct template for the new job-item socket
  slot — same `renderSingleBlock`-based technique.
- `EmployeeNames.java` (Phase 4) — the default-name generator to pre-fill the new name field.
- `network/BindEmployeePayload.java` + `network/ServerPayloadHandler.java` (Phase 4) — the
  zero-trust-in-client-data pattern to extend for `SelectTradesPayload` (indices + name, re-validated
  server-side against the menu's own candidate list).
- `employee/EmployeeManager.bind` (Phase 4) — extend rather than replace; the spawn-ordering
  discipline it already follows (villager data → xp → name → attachment → addFreshEntity) must now
  also incorporate the offer-installation order from PITFALLS.md Pitfall 4.

### Established Patterns
- Themed no-op messaging for invalid interaction states (Phase 3's no-job/no-soul-block pattern) —
  reuse for altar-occupied (D-04) and invalid-job-item/empty-pool (PICK-08) cases.
- Server-authoritative re-validation of all client-supplied menu state (Phase 4 T-4-01/T-4-02) — the
  same discipline applies to trade-index selection and the name string (GUI-02).
- `ModRegistrySelfCheck`'s `EXTRA_LANG_KEYS` guardrail must be extended for every new
  `Component.translatable` key this phase introduces (Phase 4's REVIEW.md WR-04 flagged this
  guardrail already fell behind once in Phase 3→4; don't repeat it here).

### Integration Points
- `SoulAltarBlockEntity` gains a second socket slot (job item) alongside the existing Soul Block
  slot.
- `BindingAltarMenu`/`BindingAltarScreen` gain the real candidate-list + name-field + toggle-select
  UI, replacing Phase 4's single throwaway Confirm button.
- `EmployeeManager.bind` gains a `List<MerchantOffer> chosenOffers` parameter (or equivalent),
  replacing Phase 4's hardcoded default-tier1-offers roll.

</code_context>

<specifics>
## Specific Ideas

- The player should be able to look at a lectern, place it on the altar (as an item, per G-2), open
  the screen, and see something recognizably "Librarian, here are 5 real trades" — not an abstracted
  generic list.
- Confirmed via discussion: the mod author personally tested Phase 4's placeholder (random
  profession pick) and understands it's a known, temporary stand-in — Phase 5 is what makes the
  profession match the actual job-site item.

</specifics>

<deferred>
## Deferred Ideas

- **Read-only 5-tier career-path preview panel** (FEATURES.md's "Multi-tier career planning" P2/v1.x
  idea) — showing all 5 tiers' worth of possible trades at bind time. Explicitly deferred; current
  design is tier-1-now, tier-by-tier-later-at-the-altar (simpler, matches FEATURES.md's
  recommendation (a)). Revisit once tier-by-tier picking feels good in actual play.
- Config-flag toggle for full enchantment enumeration on librarian book trades (FEATURES.md v1.x
  idea) — out of scope, current design always rolls exactly one enchanted-book offer.

</deferred>

---

*Phase: 5-profession-resolution-trade-picker*
*Context gathered: 2026-09-05*
