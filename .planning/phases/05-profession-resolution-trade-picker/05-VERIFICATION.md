---
phase: 05-profession-resolution-trade-picker
verified: 2026-09-08T04:30:00Z
status: gaps_found
score: 8/10 must-haves verified (2 accepted overrides folded into the 8; 1 real gap; 1 item pending human confirmation)
overrides_applied: 3
overrides:
  - must_have: "PICK-03: the picker shows all N candidates for the tier; the player selects exactly 2 (vanilla's per-tier count)"
    reason: "User-requested pivot (rounds 10-15, commits 189b1bd..1825e42) to reuse vanilla's EnchantmentMenu, whose clickMenuButton RPC has no multi-select concept. Replaced with pick-1-immediately from the profession's full highest-tier pool ('career path' picker), confirmed with the user before implementing per 05-07-SUMMARY.md key-decisions. Intent (player sees real candidates, makes a deliberate choice, gets exactly what they picked) is preserved; the literal 'select exactly 2' mechanic is gone by design."
    accepted_by: "cxmxrgo (via prior live session, documented in 05-07-SUMMARY.md)"
    accepted_at: "2026-09-08T03:13:00Z"
  - must_have: "PICK-04: when a tier's pool has <= 2 trades, they are auto-selected and still displayed in the picker"
    reason: "Auto-select/auto-lock is a concept specific to the retired 'pick exactly 2' mechanic. Under the shipped pick-1-immediately scrollable list, every candidate (regardless of pool size) is already visible and individually clickable — there is nothing to auto-select. Superseded by the same round-15 pivot as PICK-03."
    accepted_by: "cxmxrgo (via prior live session, documented in 05-07-SUMMARY.md)"
    accepted_at: "2026-09-08T03:13:00Z"
  - must_have: "PICK-07: the binding GUI has a name field pre-filled with a generated default; the final value is applied as the employee's visible custom name"
    reason: "The player-editable name field was deliberately removed at round 6 after real-client testing found pressing 'E' while typing closed the whole screen (unfocused-EditBox vanilla gotcha). The user supplied a new mockup with no name field. The 'final value applied as custom name' half of this requirement IS still satisfied — EmployeeNames.pickRandom() generates the default name and EmployeeManager.bind() applies it via setCustomName — only the player-facing editable-field UI was cut, and that cut was user-approved."
    accepted_by: "cxmxrgo (round-6 UI redesign, documented in 05-CONTEXT.md D-03 SUPERSEDED note)"
    accepted_at: "2026-09-07T00:00:00Z"
re_verification: null
gaps:
  - truth: "The altar GUI shows the bound employee's name, profession, current tier, already-chosen trades, and happiness state (GUI-03; also half of ROADMAP Success Criterion 3)"
    status: failed
    reason: "Verified against the actual shipped code (SoulAltarBlock.useItemOn / useWithoutItem, BindingAltarScreen, BindingAltarMenu): once be.isEmployeeBound() is true, BOTH interaction paths unconditionally return a themed 'altar.occupied' message and refuse to open any screen at all — there is no code path, anywhere, that opens a menu or renders a view for an already-bound altar. This is not just 'incomplete display' — it is structurally unreachable by the current design (D-04's occupancy refusal happens before any menu construction). While pre-bind the screen's title bar does show the resolved profession name (SoulAltarBlockEntity#getDisplayName, e.g. 'Binding Altar - Librarian'), there is no on-screen name field, tier indicator, chosen-trades list, or happiness placeholder anywhere in the round-15 shipped UI (grepped BindingAltarScreen.java, BindingAltarMenu.java, and the lang files for 'happiness'/'Happiness'/'N/A' — zero matches). Earlier rounds (05-06) DID implement a gold profession label and a static 'Happiness: N/A' placeholder per CONTEXT.md's explicit discretion note, but round 10-15's full rewrite (vanilla EnchantmentMenu reuse) dropped all of that along with the custom Screen class it lived in, and no replacement was built. Unlike the PICK-03/04/07 deviations, 05-07-SUMMARY.md's key-decisions section does NOT mention dropping GUI-03's post-bind display contract — it lists requirements-completed: [ALTAR-02, GUI-03, PICK-05] for this plan, which does not match what the code actually does."
    artifacts:
      - path: "src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java"
        issue: "useItemOn (line ~115-121) and useWithoutItem (line ~182-187) both return CONSUME with only a text message for an occupied altar — no menu, ever, once bound"
      - path: "src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java"
        issue: "No rendering of employee name, tier, chosen-trades, or happiness anywhere in this file; only the vanilla title bar (profession-only) and an empty-pool warning"
      - path: "src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java"
        issue: "getTier()/getProfession() accessors exist and are GameTest-covered, but nothing in the screen ever calls or renders them post-bind; getDisplayedCandidates() is emptied the moment bind succeeds (sockets cleared) so there is no 'already-chosen trades' data left to show even if a view existed"
    missing:
      - "A way to view a bound altar's employee info (name, profession, tier, chosen trades) — either by allowing empty-hand right-click on a bound altar to open a read-only view, or by relocating this to the employee's own entity (e.g. a tooltip/GUI on interacting with the villager directly)"
      - "Explicit decision: is GUI-03's post-bind display scope intentionally deferred to a later phase (e.g. Phase 9's happiness UI, or Phase 7's promotion-ritual altar reopening), or is it an oversight from the round-10-15 rewrite that should be patched into Phase 5 before closing it out?"
human_verification:
  - test: "Open a Binding Altar with a Soul Block + job item (e.g. a Lectern) socketed, confirm the round-15 scrollable career-path list renders visibly (icons, names, baked-in costs), scroll it if the pool has more than 3 entries, and click a row"
    expected: "The list scrolls smoothly, each row shows a real vanilla trade with its cost baked into the name, and clicking a row immediately binds an employee with exactly that trade — screen closes, employee villager appears near the altar with the chosen trade active in its own trade GUI"
    why_human: "05-07-SUMMARY.md explicitly flags this as the one item NOT yet interactively confirmed by the user — only 'boots without crashing' and GameTest pass status are confirmed programmatically. Visual list rendering, scroll feel, row hit-boxes, and real click-to-bind-in-game behavior require a human eye in the actual client, not a GameTest mock player."
---

# Phase 5: Profession Resolution & Trade Picker Verification Report

**Phase Goal:** The core value — at the altar, funded by harvested souls, the player picks an employee's profession (from the job block) and its tier-1 trades from that profession's real vanilla pool, and the employee spawns offering exactly those trades.

**Verified:** 2026-09-08T04:30:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

**Accepted deviation (per verification brief):** the shipped implementation rolls the profession's HIGHEST tier ("career path") instead of tier-1, and grants exactly one trade immediately instead of picking 2 — both are explicit, user-requested pivots documented in 05-07-SUMMARY.md and git history (commits `189b1bd` through `1825e42`). This report treats that pivot as intentional and does not fail it, as instructed. The core mechanic underneath — profession resolved from the socketed job item via the real POI registry, a real vanilla `ItemListing` pool rolled and materialized into concrete `MerchantOffer`s, and an employee spawned with exactly the chosen offer(s) — was independently re-verified against the actual source and GameTest suite below, not taken on the SUMMARY's word.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Profession is resolved from the socketed job-site item via the real POI registry, no hardcoded list | VERIFIED | `ProfessionResolver.fromItem`/`fromPoi` (trade/ProfessionResolver.java) iterates `BuiltInRegistries.VILLAGER_PROFESSION` live via `PoiTypes.forState` — no hardcoded block/profession list. GameTest `profession_resolver_from_item_maps_real_job_site` and `profession_resolver_from_item_ignores_non_poi_block` pass. |
| 2 | Trade candidates are the profession's real vanilla `ItemListing` pool, materialized into concrete `MerchantOffer`s server-side | VERIFIED | `TradePoolCache.rollMaxTierCandidates`/`rollCandidatesForTier` reads `VillagerTrades.TRADES.get(profession)` directly and calls each listing's real `getOffer(throwaway, random)` — no synthetic/hardcoded offers. GameTest `binding_altar_menu_construction_rolls_candidates_and_default_name` and `TradePoolCacheGameTests` pass. |
| 3 | The employee spawns offering exactly the chosen trade(s) — never vanilla's lazy-fabricated 2 random trades | VERIFIED | `EmployeeManager.bind` calls `setVillagerData`→`refreshBrain`→`setVillagerXp`→`setOffers(chosenOffers)` in the documented non-negotiable order (Pitfall 4). `EmployeeGameTests.bound_employee_getoffers_matches_chosen_offers_exactly` (and neighbors) directly asserts `liveOffers.size() == chosenOffers.size()` and per-offer equality. `BindingAltarMenuGameTests.clicking_a_candidate_via_click_menu_button_binds_exactly_one_employee` confirms exactly one employee spawns per pick. |
| 4 | Librarian (and any profession) candidate list includes exactly one freshly-rolled enchanted-book offer, not every enchantment enumerated (PICK-05) | VERIFIED | Each `VillagerTrades.ItemListing` array entry produces at most one `MerchantOffer` per roll (`rollCandidatesForTier`'s one-call-per-listing loop) — an enchanted-book listing entry yields exactly one rolled book, regardless of which tier is rolled. `BindingAltarMenu.resolveDisplayName` additionally resolves the real enchantment name (e.g. "Sharpness III") instead of the generic "Enchanted Book" label. |
| 5 | A job block that maps to no profession, or a profession with an empty tier pool, shows a themed message and never crashes (PICK-08) | VERIFIED | `SoulAltarBlock.useItemOn` rejects an unmapped `BlockItem` with `message.secondshift.altar.not_a_workstation`; `BindingAltarScreen.renderLabels` shows `message.secondshift.altar.empty_pool` when the candidate list is empty; `TradePoolCache.rollCandidatesForTier` returns `List.of()` (never throws) on a missing tier/profession. GameTest `binding_altar_no_job_block_no_menu_no_crash` passes. |
| 6 | Completing a bind consumes the Soul Block and the job-site item into the altar and spawns the employee (ALTAR-04) | VERIFIED | `BindingAltarMenu.attemptBind` sets both `be.setHeldSoulBlock(EMPTY)`/`be.setHeldJobItem(EMPTY)` before calling `EmployeeManager.bind`. GameTest `clicking_a_candidate_via_click_menu_button_binds_exactly_one_employee` asserts `be.isEmpty() && be.isJobItemEmpty()` post-bind. |
| 7 | Each altar holds exactly one employee — a second bind is refused, and the link survives save/load (ALTAR-05) | VERIFIED | `employeeBound` is an atomic guard checked first in both `useItemOn`/`useWithoutItem` (occupied → themed no-op, never opens a menu) and in `attemptBind` (checked again server-side before any mutation). Persisted via `KEY_EMPLOYEE_BOUND` in `saveAdditional`/`loadAdditional`. GameTest `binding_altar_occupied_altar_refuses_to_open`, `soul_altar_be_persists_job_item_and_occupancy`, and `double_pick_does_not_spawn_a_second_employee` all pass. |
| 8 | Client-sent trade selections are re-validated server-side against the server's own candidate list and altar proximity (GUI-02) | VERIFIED | `BindingAltarMenu.clickMenuButton` bounds-checks `id` against `displayedCandidates.size()` (server-side truth, never trusts a client index blindly) and `attemptBind` re-checks `stillValid(sp)` before any mutation. No raw offer/name data crosses the network at all in the shipped design — only an index via vanilla's own `clickMenuButton` RPC — which is a *stronger* trust boundary than the originally-planned `SelectTradesPayload`, not a weaker one. GameTest `picking_an_out_of_range_index_is_a_safe_no_op` confirms an out-of-range index is rejected with zero side effects. |
| 9 | PICK-03/04/07's literal "pick exactly 2" / "auto-lock ≤2" / "editable name field" mechanics | PASSED (override) | See `overrides` in frontmatter — all three are explicit, user-requested pivots (rounds 6 and 10-15) with the underlying intent (real candidates shown, deliberate player choice, employee gets a sensible default name) still satisfied by the shipped pick-1-immediately career-path picker. |
| 10 | The altar GUI shows the bound employee's name, profession, current tier, already-chosen trades, and happiness state (GUI-03; also half of ROADMAP SC3) | ✗ FAILED | See `gaps` in frontmatter. An occupied altar never opens ANY screen (both `useItemOn` and `useWithoutItem` short-circuit to a themed message), so there is no code path that can ever display this information post-bind. Pre-bind, only the profession (via title bar) is shown — no name field, no tier indicator, no chosen-trades list, no happiness placeholder exist anywhere in the round-15 UI. This is a genuine, unaccepted gap — unlike PICK-03/04/07, 05-07-SUMMARY.md does not document dropping this display contract, and its `requirements-completed` list still (incorrectly) claims GUI-03 done. |

**Score:** 8/10 truths verified as originally stated (3 of those via documented override), 1 truth FAILED (GUI-03 post-bind display), 1 item requires human confirmation (see below).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `trade/ProfessionResolver.java` | POI-registry-driven profession resolution, no hardcoded list | ✓ VERIFIED | Live registry iteration, no hardcoded block/profession table |
| `trade/TradePoolCache.java` | Rolls real vanilla `ItemListing` pool into concrete `MerchantOffer`s | ✓ VERIFIED | `rollMaxTierCandidates`/`rollCandidatesForTier`, throwaway-villager pattern per PITFALLS.md |
| `menu/BindingAltarMenu.java` | Server-authoritative candidate list, bind trust boundary, GUI-03 read contract | ✓ VERIFIED (mechanic) / ✗ INCOMPLETE (GUI-03 display never consumed by the screen) | `getProfession()`/`getTier()`/`getDisplayedCandidates()` exist and are GameTest-covered, but `BindingAltarScreen` never renders name/tier/chosen-trades from them |
| `client/screen/BindingAltarScreen.java` | Real UI showing candidates + GUI-03 info | ⚠️ PARTIAL | Candidate list (`TradeCandidateList`) is real and wired; GUI-03's name/tier/chosen-trades/happiness display is absent |
| `client/screen/TradeCandidateList.java` | Scrollable real-vanilla-widget candidate list | ✓ VERIFIED (wiring) / ? UNCERTAIN (visual behavior) | Wired via `ObjectSelectionList`, routes clicks through `handleInventoryButtonClick` correctly by inspection; visual/scroll behavior not yet human-confirmed (see human_verification) |
| `content/blockentity/SoulAltarBlockEntity.java` | Dual-socket state, `employeeBound` persistence, candidate roll caching | ✓ VERIFIED | Persists `employeeBound`/sockets; `candidatesRolled()`/`resetRolledCandidates()` guard against re-roll/stale-empty bugs |
| `content/block/SoulAltarBlock.java` | Socket interaction, occupancy refusal, consume-on-bind | ✓ VERIFIED | `useItemOn`/`useWithoutItem` implement D-04 occupancy refusal and PICK-08 rejection correctly |
| `employee/EmployeeManager.java` | Spawns employee with exactly the chosen offers, correct bind ordering | ✓ VERIFIED | `setVillagerData`→`refreshBrain`→`setVillagerXp`→`setOffers` order matches PITFALLS.md Pattern 2/Pitfall 4 exactly; `EmployeeGameTests` assert exact offer match |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `SoulAltarBlock.useItemOn` | `BindingAltarMenu` (open) | `sp.openMenu` on both-sockets-filled | WIRED | Confirmed by code inspection + `binding_altar_real_interaction_sockets_lectern_job_item` GameTest |
| `BindingAltarMenu` ctor | `ProfessionResolver`/`TradePoolCache` | `resolveDisplayedCandidates` (server-only roll-once) | WIRED | GameTest `binding_altar_menu_construction_rolls_candidates_and_default_name` and `binding_altar_menu_reconstruction_does_not_reroll` |
| `TradeCandidateList` row click | `BindingAltarMenu.clickMenuButton` | `minecraft.gameMode.handleInventoryButtonClick` (vanilla RPC) | WIRED (by inspection) | `BindingAltarScreen.init()` passes `index -> this.minecraft.gameMode.handleInventoryButtonClick(...)` directly as `TradeCandidateList`'s `onSelect` — no dead code between click and RPC call. Not yet confirmed live in a real client (see human_verification). |
| `BindingAltarMenu.clickMenuButton` | `EmployeeManager.bind` | `attemptBind` | WIRED | GameTest-covered end to end (menu click → employee exists with exact chosen offer, `EmployeeGameTests`) |
| `BindingAltarMenu` GUI-03 accessors (`getTier`, `getProfession`, `getDisplayedCandidates`) | `BindingAltarScreen` rendering | *(none)* | **NOT WIRED** | These accessors exist and are unit/GameTest-tested in isolation, but `BindingAltarScreen` never calls them to render name/tier/chosen-trades — this is the GUI-03 gap above |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `TradeCandidateList` rows | `candidates` (`List<ItemStack>`) | `BindingAltarMenu`'s synced candidate slots (`CANDIDATE_SLOT_BASE..`), populated server-side from `TradePoolCache.rollMaxTierCandidates` | Yes — real, rolled `MerchantOffer` results, not static/empty | ✓ FLOWING |
| `BindingAltarScreen` title | Profession name | `SoulAltarBlockEntity.getDisplayName()` → `ProfessionResolver.fromItem` | Yes — resolved live from the actual socketed item | ✓ FLOWING |
| GUI-03's tier/name/chosen-trades display | *(none — never rendered)* | N/A | N/A | ✗ DISCONNECTED (no consumer exists) |

### Behavioral Spot-Checks

Not run as live-client interaction (out of scope for a non-interactive verifier and no running client available); GameTest suite (Step 7c-equivalent for this project's convention) serves as the closest available automated behavioral check and was executed directly (see Probe Execution below).

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| GameTest suite (44 tests, this project's canonical automated behavioral verification) | `./gradlew runGameTestServer --console=plain` | `All 44 required tests passed :)` — exit 0, `BUILD SUCCESSFUL` | ✓ PASS |

This was executed directly in this verification session (not taken from SUMMARY.md's claim) — full raw log confirms `44 tests are now running`, all green bars, `All 44 required tests passed`, then a clean server shutdown with no exceptions.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|--------------|--------|----------|
| ALTAR-02 | 05-01/05-07 | Job-site block sets target profession | ✓ SATISFIED | Intent preserved via item-socket read (`ProfessionResolver.fromItem`); accepted D-01/G-2 rework of the literal "block on top" wording |
| ALTAR-04 | 05-01+ | Bind consumes Soul Block + job item, spawns employee | ✓ SATISFIED | `attemptBind` + `EmployeeManager.bind`, GameTest-verified |
| ALTAR-05 | 05-01+ | One employee per altar, persists across save/load | ✓ SATISFIED | `employeeBound` persisted + guarded, GameTest-verified |
| PICK-01 | 05-01 | POI-registry profession resolution, no hardcoded list | ✓ SATISFIED | `ProfessionResolver` |
| PICK-02 | 05-04+ | Real vanilla `ItemListing` pool materialized server-side | ✓ SATISFIED | `TradePoolCache` |
| PICK-03 | 05-06/05-07 | Show all N candidates, select exactly 2 | ✓ SATISFIED (override) | Pick-1-immediately pivot, user-approved |
| PICK-04 | 05-04/05-06 | Auto-select/-lock pools ≤2 | ✓ SATISFIED (override) | Superseded by round-15 "show everything, click one" design |
| PICK-05 | 05-04+ | Exactly one rolled enchanted-book offer for librarian, not enumerated | ✓ SATISFIED | Verified independent of tier choice |
| PICK-06 | 05-05 | Offers persisted on attachment via `setOffers`, profession set first | ✓ SATISFIED | `EmployeeManager.bind` ordering + `EmployeeGameTests` |
| PICK-07 | 05-06/05-07 | Editable name field pre-filled with default, applied as custom name | ✓ SATISFIED (override, partial) | Field removed (user-approved, E-key bug); default-name generation + application to the employee IS real and verified |
| PICK-08 | 05-01+ | Invalid job item / empty pool → themed message, no crash | ✓ SATISFIED | Verified across both `SoulAltarBlock` and `BindingAltarScreen` |
| GUI-02 | 05-05 | Server-authoritative re-validation of client selections | ✓ SATISFIED | Index bounds-check + `stillValid` re-check; simplified (no name payload at all) rather than weakened |
| GUI-03 | 05-04/05-06/05-07 | Altar GUI shows name/profession/tier/chosen-trades/happiness | ✗ BLOCKED | See gap above — no post-bind display exists in the shipped round-15 UI, and pre-bind only shows profession (via title) |

**Orphaned requirements:** None found — REQUIREMENTS.md's Phase 5 mapping table lists exactly ALTAR-02/04/05, PICK-01 through PICK-08, and GUI-02/03, all of which appear in at least one plan's `requirements:` frontmatter.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| *(none)* | — | Grepped all Phase 5 key files (`BindingAltarMenu.java`, `BindingAltarScreen.java`, `TradeCandidateList.java`, `TradePoolCache.java`, `ProfessionResolver.java`, `EmployeeManager.java`, `SoulAltarBlockEntity.java`, `SoulAltarBlock.java`) for `TODO`/`FIXME`/`XXX`/`HACK`/`PLACEHOLDER`/"not yet implemented"/"coming soon" — zero matches | — | No debt markers found in this phase's code |

One informational note, not an anti-pattern: `SoulAltarBlock.getDrops` contains a deliberately-labeled `>>> D-04 FALLBACK` comment documenting a design toggle (drop vs. destroy the altar on a charged break) — this is intentional design documentation, not a debt marker, and does not reference unresolved work.

### Human Verification Required

#### 1. Round-15 scrollable career-path list — live interactive confirmation

**Test:** Open a Binding Altar with a Soul Block and a job-site item (e.g. a Lectern) both socketed. Confirm the scrollable candidate list renders visibly with icons, names, and baked-in costs; scroll it if the profession's top-tier pool has more than ~3 entries; click a row.
**Expected:** The list scrolls smoothly and legibly inside the enchanting-table-style panel, each row shows a real trade with cost baked into its name, and clicking a row immediately closes the screen and spawns an employee villager with exactly that trade active in its own vanilla trade GUI.
**Why human:** 05-07-SUMMARY.md explicitly documents this as the one item built at the very end of the session and confirmed only to "boot without crashing" and pass all 44 GameTests — never interactively scrolled, read, or clicked by the user. GameTests exercise `clickMenuButton` directly (bypassing real mouse/scroll input), so they cannot confirm the widget's actual on-screen scroll/click behavior.

#### 2. GUI-03 gap — product decision needed, not purely a code fix

**Test:** N/A — this needs a human product decision, not a testable behavior.
**Expected:** A decision on whether "the altar GUI shows the bound employee's name/profession/tier/chosen-trades/happiness" should be (a) patched into Phase 5 now via a read-only reopen path for bound altars, (b) explicitly deferred to a later phase (e.g. Phase 7's promotion-ritual reopening, or Phase 9's happiness UI) with REQUIREMENTS.md/ROADMAP.md amended to say so, or (c) relocated entirely to the employee entity itself (e.g. shown when interacting with the villager, not the altar).
**Why human:** This is a scope/design question the codebase cannot answer for itself — the current code simply has no path to this information post-bind, and the three options above have materially different implementation costs and product feel.

### Gaps Summary

The phase's actual core mechanic — resolve a profession from a real vanilla job-site item, roll that profession's real vanilla trade pool, and spawn an employee with exactly the trades the player picked — is genuinely implemented, wired end-to-end, and covered by a green 44/44 GameTest suite that was independently re-run for this verification (not taken on the SUMMARY's word). The three requirement-wording mismatches the verification brief anticipated (PICK-03/04/07's "pick 2" / "auto-lock" / "editable name field") are legitimate, well-documented, user-approved pivots and are recorded as overrides rather than failures.

However, one requirement was NOT called out in 05-07-SUMMARY.md's deviations section despite also being invalidated by the round 10-15 rewrite: **GUI-03** (and the matching half of ROADMAP Success Criterion 3) requires the altar GUI to show a bound employee's name, profession, tier, chosen trades, and happiness state. The shipped design makes an occupied altar refuse to open any screen at all, and even the pre-bind screen only shows the profession (via the window title) — no name, tier, chosen-trades, or happiness display exists anywhere in the current code. This is a real, verifiable, code-level gap distinct from the accepted PICK-03/04/07 pivots, and 05-07-SUMMARY.md's `requirements-completed: [ALTAR-02, GUI-03, PICK-05]` claim for GUI-03 does not hold up against the actual shipped screen. This should be resolved — either by building a minimal read-only view, or by an explicit, documented amendment to REQUIREMENTS.md/ROADMAP.md deferring it — before Phase 5 is considered fully closed.

---

*Verified: 2026-09-08T04:30:00Z*
*Verifier: Claude (gsd-verifier)*
