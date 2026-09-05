---
status: awaiting_human_verify
trigger: "Phase 5 (profession-resolution-trade-picker) manual verification checkpoint (Plan 05-07) found 4 real bugs during real-client testing on 'Second Shift' (Minecraft 1.21.1 NeoForge 21.1.248 mod)."
created: 2026-09-05
updated: 2026-09-05
---

## Symptoms

**Expected behavior:** Right-clicking a job-site item (e.g. a Lectern) onto the Soul Altar sockets it (item consumed, hovers/spins above the altar per the G-2 render). Once both sockets (Soul Block + job item) are filled, the Binding Altar screen opens automatically with a correctly laid-out UI (no overlap with the player inventory grid), showing the resolved profession, real trade candidates, and an editable name field. Confirming spawns a named employee with exactly the chosen trades, and right-clicking that employee opens the vanilla trade screen showing those trades.

**Actual behavior (4 distinct issues):**

- **Bug A (UI layout):** The Binding Altar screen's player-inventory slot grid visually overlaps the new trade-candidate list and Confirm button. Reported by user as "the UI is all messed up, with stuff unaligned and whatnot."
- **Bug B (missing refreshBrain):** Confirmed via static read — `EmployeeManager.bind()` in `src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java` never calls `villager.refreshBrain(level)`, contradicting the required `setVillagerData -> refreshBrain -> setVillagerXp -> setOffers(last)` order from PITFALLS.md Pitfall 4 / 05-RESEARCH.md Finding 3. The plan-checker's prior "confirmed present" verdict was checking the PLAN TEXT, not the shipped source — a false positive.
- **Bug C (socket mechanic not engaging for Lectern):** Right-clicking a Lectern onto the altar does "nothing at all" per the user (no sound, no visible change, item not consumed) — the user says they "still have to place the profession block on top of the altar to make it work" (i.e. place it as a real placed block, the old pre-Phase-5 mechanic). Separately, the user reports that later, once an employee spawns, they can see a job item hovering near the altar — and specifically, destroying the real placed Lectern block reveals a hovering item underneath/behind it. This suggests either (a) the socket mechanic actually works silently (no player-facing feedback) and the real placed block was visually obscuring the hover render the whole time, or (b) `ProfessionResolver.fromItem` fails to resolve Lectern specifically (untested via the real interaction path — `BindingAltarGameTests.java` line 214 sets the job item directly via `be.setHeldJobItem(new ItemStack(Blocks.LECTERN.asItem()))`, bypassing `SoulAltarBlock.useItemOn`/`ProfessionResolver.fromItem` entirely; only `Blocks.CARTOGRAPHY_TABLE` is exercised through the real interaction path, at line 51).
- **Bug D (can't open trade screen):** After binding completes and an employee spawns, right-clicking the employee does not open the vanilla trade screen at all. No custom interaction event handler exists anywhere in `src/main/java/` (verified via grep for `PlayerInteractEvent`/`EntityInteract`/`mobInteract`) that could be intercepting this, so the cause must be in the villager's own post-bind state (offers, profession, or something else `EmployeeManager.bind()` sets).

**Error messages:** None reported — all 4 are silent behavioral/visual bugs, not crashes or exceptions.

**Timeline:** First occurrence — this is the very first real-client manual test of Phase 5's newly-built code (Plans 05-01 through 05-06). The full automated GameTest suite (38/38) was green before this test, so these are gaps between GameTest coverage and real interactive/client behavior, not regressions from previously-working code.

**Reproduction:**
1. `./gradlew runClient`.
2. Place a Soul Altar.
3. Right-click a Lectern onto the altar (Bug C: reportedly does nothing).
4. (Workaround used by tester) Place the Lectern as a real block on top of the altar instead, then socket a Soul Block — screen opens (Bug A: layout overlaps inventory grid).
5. Confirm a bind — employee spawns; a hovering job item becomes visible, especially after breaking the real placed Lectern block (Bug C evidence).
6. Right-click the spawned employee — vanilla trade screen does not open (Bug D).

## Current Focus

status: awaiting_human_verify

Round 3 complete. User confirmed Bug C fixed. The 2 remaining round-2 items (Bug A texture
desync, silent selection-rejection) are now fixed and self-verified:
- `./gradlew compileJava` — clean.
- `./gradlew runGameTestServer` — 42/42 (41 prior + 1 new regression test for the
  selection-rejection fix).
- `./gradlew runServer` — clean start, no client-class leak.
- `./gradlew deployToTest` — deployed `secondshift-0.1.0.jar` to the CurseForge test instance.

next_action: Awaiting human re-verification in the real client of (1) the regenerated
binding_altar.png layout (no more visual overlap) and (2) the themed rejection message +
menu-stays-open behavior when confirming with the wrong trade-selection count.

reasoning_checkpoint (Bug C):
  hypothesis: "SoulAltarRenderer.render()'s early-return gates on be.isEmpty() (Soul Block slot
    only), so socketing the job item BEFORE the Soul Block (the reported click order) causes the
    entire render method — including the hovering job-item draw — to be skipped, even though the
    server-side socket succeeded."
  confirming_evidence:
    - "SoulAltarBlockEntity#isEmpty() literally returns `heldSoulBlock.isEmpty()` — confirmed via
      direct file read, not assumption."
    - "A new real-interaction-path GameTest (binding_altar_real_interaction_sockets_lectern_job_item)
      proves SoulAltarBlock.useItemOn + ProfessionResolver.fromItem correctly socket a Lectern item
      server-side — eliminating the resolver-logic hypothesis entirely."
    - "User's own follow-up evidence (hovering item found only after breaking a later, separately
      placed real Lectern block) is consistent with the render having been silently suppressed
      until a Soul Block was later also socketed."
  falsification_test: "If the real-path Lectern GameTest had failed to socket, the root cause would
    have been in ProfessionResolver/useItemOn instead — it passed, ruling that out."
  fix_rationale: "Changed the early-return to `be.isEmpty() && be.isJobItemEmpty()` and made the
    embedded-Soul-Block draw and the wisp conditional on `!be.isEmpty()` independently, so the
    job-item hover renders as soon as the job item is socketed regardless of Soul Block state."
  blind_spots: "Not able to visually confirm the render in a real client from this environment —
    flagged for human re-verification."

reasoning_checkpoint (Bug D):
  hypothesis: "ServerPayloadHandler.validateIndices only enforced an UPPER bound
    (`deduped.size() > 2` -> reject) and never a lower bound, so confirming with 0 or 1 trades
    selected (e.g. the Confirm button's default `new int[0]` if nothing was ever clicked) silently
    produced a villager with an empty/near-empty MerchantOffers list, which then fails vanilla
    Villager#mobInteract's `getOffers().isEmpty()` gate and never opens the trade screen."
  confirming_evidence:
    - "REQUIREMENTS.md PICK-03: 'the player selects exactly 2 (vanilla's per-tier count)' — direct
      requirement text, not inferred."
    - "Villager#mobInteract source (decompiled neoforge-21.1.248-sources.jar): `boolean flag =
      this.getOffers().isEmpty(); ... if (flag) return InteractionResult.CONSUME;` — confirmed via
      direct source read."
    - "validateIndices's old logic path traced by hand: `new int[0]` against a >2-candidate pool
      produces `deduped.size()==0`, which is not `>2`, so it fell through to `return
      List.copyOf(deduped)` — an empty list, unconditionally accepted."
  falsification_test: "If a real confirm always guaranteed exactly 2 selections some other way
    (e.g. client-side validation blocking Confirm), this would not reproduce — but no such
    client-side guard exists (confirmed via BindingAltarScreen read)."
  fix_rationale: "Changed the check from `> 2` to `!= 2`, so 0 or 1 selections against a
    non-auto-locked pool are rejected (payload no-op, sockets untouched, player can retry) instead
    of silently producing a broken employee."
  blind_spots: "The missing `refreshBrain` call (Bug B) was fixed on suspicion of also contributing
    to Bug D, but no direct evidence tied it to the trade-screen-not-opening symptom specifically —
    it's independently required by PITFALLS.md regardless. The validateIndices fix is the
    evidence-backed root cause for Bug D; refreshBrain is best-practice correctness, not a proven
    second cause."
next_action: Deploy via `./gradlew deployToTest` and request human re-verification of Bugs A, C, D
  (visual/interactive confirmation the GameTest suite cannot fully provide).

## Evidence

- timestamp: 2026-09-05T00:00:00Z
  finding: "`BindingAltarGameTests.java` line 214 sets the job item directly via `be.setHeldJobItem(new ItemStack(Blocks.LECTERN.asItem()))` — bypasses `SoulAltarBlock.useItemOn`/`ProfessionResolver.fromItem` entirely. The only GameTest exercising the real interaction path (line 51) uses `Blocks.CARTOGRAPHY_TABLE`, never Lectern. Lectern's real-path resolution has never been GameTest-verified."
- timestamp: 2026-09-05T00:00:01Z
  finding: "`EmployeeManager.bind()` (current shipped source) goes `setVillagerData -> setVillagerXp -> setOffers` with no `refreshBrain` call between `setVillagerData` and `setVillagerXp`, contradicting PITFALLS.md Pitfall 4's required order. Confirmed via direct file read, not assumption."
- timestamp: 2026-09-05T00:00:02Z
  finding: "`BindingAltarMenu`'s constructor places player-inventory slots at y=84/102/120 (3 rows) and y=142 (hotbar) — unchanged from before Phase 5. `BindingAltarScreen` (Plan 05-06) sets `imageHeight=222` and places the `TradeCandidateList` at local y=40 to 130, and the Confirm button at y=134-154 — both ranges overlap the inventory grid's y=84-160 span."
- timestamp: 2026-09-05T00:00:03Z
  finding: "No custom `PlayerInteractEvent`/`EntityInteract`/`mobInteract` handler exists anywhere in `src/main/java/` (grep confirmed) — nothing in this mod intercepts right-click on a villager, so Bug D's cause must be in the spawned villager's own state, not an event handler."
- timestamp: 2026-09-05T00:00:04Z
  finding: "Actual BindingAltarMenu/BindingAltarScreen source read (not summary): inventory slots at y=84/102/120/142 were left completely UNSHIFTED despite the screen's imageHeight growing to 222; the screen only adjusted inventoryLabelY (to 74 — itself inside the candidate-list region, also wrong). Candidate list occupies local y 40-130, Confirm button 134-154, both directly overlapping the unshifted inventory grid (84-160)."
- timestamp: 2026-09-05T00:00:05Z
  finding: "05-UI-SPEC.md's own numbers are internally inconsistent (list 40-130 + button 134-154 + 'shift inventory down by the same 56px insertion offset' cannot all be true simultaneously — 84+56=140 overlaps the button's 134-154 range). Resolved by keeping the existing 200x222 texture asset fixed, and computing a self-consistent layout: list 40-100 (3 rows), button 106-126, inventory shifted by a uniform +56 (row1=140, hotbar=198, bottom-flush with the 222px canvas margin)."
- timestamp: 2026-09-05T00:00:06Z
  finding: "New GameTest `binding_altar_real_interaction_sockets_lectern_job_item` (real `SoulAltarBlock.useItemOn` path, not BE field injection) passes for a Lectern item — proves the socket mechanic and `ProfessionResolver.fromItem` are NOT broken for Lectern. This eliminates the resolver-logic hypothesis for Bug C."
- timestamp: 2026-09-05T00:00:07Z
  finding: "`SoulAltarRenderer.render()` early-returns on `be.isEmpty()` alone (Soul Block slot only, confirmed via `SoulAltarBlockEntity#isEmpty()` source: `return heldSoulBlock.isEmpty();`). Socketing the job item first (before a Soul Block) — the reported real click order — causes the entire render method, including the hovering job-item draw, to be skipped even though the server-side socket succeeded silently. This is Bug C's actual root cause: a feedback/render gate bug, not a resolver bug, exactly matching the 'no visible change' + 'hovering item appeared later' evidence."
- timestamp: 2026-09-05T00:00:08Z
  finding: "`javap`-confirmed `SoundEvents.ITEM_FRAME_ADD_ITEM` is a plain `SoundEvent` (not `Holder<SoundEvent>`) while `SOUL_ESCAPE` is a `Holder$Reference<SoundEvent>` — SoulAltarBlock's differing `.value()` usage between the two branches is correct, not a bug. Eliminated as a hypothesis."
- timestamp: 2026-09-05T00:00:09Z
  finding: "REQUIREMENTS.md PICK-03: 'the player selects exactly 2 (vanilla's per-tier count)'. `ServerPayloadHandler.validateIndices` only enforced `deduped.size() > 2 -> reject`, never a lower bound — `new int[0]` (the Confirm button's default when no row was ever clicked) against a >2-candidate pool produced `deduped.size()==0`, which is not `>2`, so it fell through to `return List.copyOf(deduped)`, an EMPTY list, unconditionally accepted. This produces a villager with `getOffers().isEmpty() == true`."
- timestamp: 2026-09-05T00:00:10Z
  finding: "Decompiled `Villager#mobInteract` (neoforge-21.1.248-sources.jar): `boolean flag = this.getOffers().isEmpty(); ... if (flag) return InteractionResult.CONSUME;` — an empty offers list silently refuses to open the trade screen, matching Bug D's exact symptom with no exception. This is Bug D's actual, evidence-backed root cause."

## Round 3 (2026-09-05) — Bug A texture desync + silent selection-rejection

User confirmed Bug C (socket render) fully FIXED. Investigating the two round-2 leading
hypotheses directly, per the parent task's instructions.

reasoning_checkpoint (Bug A texture desync):
  hypothesis: "binding_altar.png's baked-in slot-frame artwork was never regenerated to match
    the round-2 coordinate fix (soul slot 80,35; inventory rows 140/158/176; hotbar 198; 200x222
    canvas), so the visual frames still show at old/broken positions even though Slot/widget logic
    is correct — a texture/logic desync, not a coordinate math error."
  confirming_evidence:
    - "Decoded the actual shipped binding_altar.png with a pure-Python PNG decoder (zlib/struct,
      no PIL) — direct byte-level inspection, not assumption. Confirmed 200x222/RGBA8, filter type
      0 throughout (matches the project's established generation convention)."
    - "ASCII-rendered the decoded pixel grid: found only 2 baked slot-row blocks total, at y131-136
      (6px tall — too short to be a real 18px slot) and y142-159 (18px, roughly near but not
      exactly matching row1's correct y=140), with NOTHING baked below y160 — the 2nd/3rd
      inventory rows and the hotbar (correct y=158/176/198) have no baked frame at all."
    - "Found a single large dark-recess rectangle baked from y40-129 spanning nearly the full
      width (x8-191) — this overlaps/conflicts with the correct row-block positions and appears to
      be a leftover from an earlier layout iteration, not the current candidate-list-only region
      (y40-100)."
    - "The soul slot's own baked frame (x81-99, y34-53) DOES already match the correct Java
      coordinates (80,35) almost exactly — eliminating the possibility that the whole texture was
      simply never touched; only the inventory-grid portion is desynced."
  falsification_test: "If the baked frame positions had matched the current Java layout exactly
    (soul slot 80,35; 3 rows at 140/158/176; hotbar at 198), this hypothesis would be refuted and
    the 'still overlapping' report would need a different explanation (e.g. a rendering-order bug
    in the screen class itself)."
  fix_rationale: "Regenerated binding_altar.png from scratch with a new Python/zlib/struct script
    (no PIL, matching established convention) that bakes the slot-frame grid at exactly the
    current Java coordinates (soul slot 80,35; rows 140/158/176; hotbar 198, all 18x18) and the
    candidate-list dark recess at the current screen coordinates (x8-192, y40-100), using the same
    color palette already shipped (0xC6C6C6 body, 0x8B8B8B slot fill, 0x555555 borders/outer
    bevel, 0x404040 recess) rather than inventing a new visual style. This directly eliminates the
    positional desync rather than papering over a symptom."
  blind_spots: "Not able to visually confirm the regenerated texture in a real client from this
    environment — verified only programmatically (dimension + pixel-position spot-checks via a
    second decode pass). Flagged for human re-verification."

- timestamp: 2026-09-05T00:00:14Z
  finding: "Decoded shipped binding_altar.png (200x222, RGBA8, all-filter-0 scanlines) byte-for-byte
    via a pure-Python zlib/struct decoder. ASCII-rendered pixel map showed: soul-slot frame baked
    correctly at (80,35)-ish; but the inventory-grid area only had 2 baked row-blocks (one 6px-tall
    broken sliver at y131-136, one 18px block at y142-159) with nothing baked for rows 2/3 or the
    hotbar (correct positions 158/176/198) — direct proof the round-2 coordinate fix's own note
    ('kept the existing texture asset fixed... no asset regeneration needed') was incorrect: the
    texture was never actually in sync with the corrected Java layout."
  timestamp: 2026-09-05T00:00:14Z
- timestamp: 2026-09-05T00:00:15Z
  finding: "Regenerated binding_altar.png via a new stdlib zlib/struct script; re-decoded the
    output and spot-checked pixel values at all 4 authoritative slot regions plus the candidate
    list recess and background body fill — all match the intended color/position exactly (soul
    slot border 0x555555 at (80,35), slot fill 0x8B8B8B at each of 4 grid rows, recess 0x404040 at
    (100,60), body 0xC6C6C6 elsewhere). Dimensions confirmed 200x222 via IHDR re-parse."
  timestamp: 2026-09-05T00:00:15Z

reasoning_checkpoint (silent selection-rejection / "employee never spawns"):
  hypothesis: "ServerPayloadHandler.handleSelectTrades calls sp.closeContainer() unconditionally
    after the atomic access().execute(...) lambda, even when validateIndices rejects the selection
    (wrong count) and the lambda returns early without calling EmployeeManager.bind — silently
    closing the screen with zero player-facing feedback and no employee spawned."
  confirming_evidence:
    - "Direct re-read of the current shipped ServerPayloadHandler.java: `sp.closeContainer();` sits
      as the last statement of handleSelectTrades, OUTSIDE and unconditionally after
      `menu.access().execute(...)` — confirmed by line-by-line reading, not the round-2 summary."
    - "Inside the lambda, `if (selected == null) { return; }` (validateIndices rejection) is the
      only early-return path that fires BEFORE the socket-consuming lines (setHeldSoulBlock/
      setHeldJobItem to EMPTY) — confirmed sockets are genuinely untouched at that point, so
      keeping the menu open on this specific rejection is safe and lossless."
    - "No Component.translatable message of any kind is sent on this path prior to the fix —
      confirmed by grepping the method body for displayClientMessage/translatable calls before the
      unconditional close; none existed."
  falsification_test: "If the Confirm button's client-side logic guaranteed exactly 2 selections
    before ever sending the payload (making a wrong-count server rejection unreachable in normal
    play), this would not explain the reported symptom — but BindingAltarScreen's Confirm handler
    sends whatever candidateList.getSelectedIndices() currently holds (0, 1, or more) with no
    client-side gate, confirmed via direct read of BindingAltarScreen.init()'s button lambda."
  fix_rationale: "Made sp.closeContainer() conditional on NOT having hit the validateIndices
    rejection path (tracked via a boolean[] flag set inside the lambda, since the lambda itself
    can't return a value here) — the menu now stays open and the player gets a themed
    'select_exactly_two' action-bar message on a wrong-count confirm, instead of the screen
    silently vanishing. All other early-return paths (stale send, altar gone, double-confirm race,
    unreachable profession-empty) retain the original close-unconditionally behavior, since sockets
    are consumed or the situation is otherwise terminal/rare on those paths — this is the smallest
    change that fixes the specific reported gap without altering already-tested behavior."
  blind_spots: "The round-2 hypothesis that Bug A's texture desync was making 2 distinct candidate
    rows hard to see (and thus indirectly causing miscounted selections) is plausible but was not
    directly tested — this fix addresses the missing-feedback correctness gap regardless of
    whether mis-selection was the literal cause of the user's 'employee never spawns' report."

- timestamp: 2026-09-05T00:00:16Z
  finding: "Direct re-read of ServerPayloadHandler.handleSelectTrades confirmed: `sp.closeContainer()`
    (now-old line 109) ran unconditionally after `menu.access().execute(...)`, including on the
    `if (selected == null) { return; }` validateIndices-rejection path, with zero player-facing
    feedback sent anywhere in the method before that point."
  timestamp: 2026-09-05T00:00:16Z
- timestamp: 2026-09-05T00:00:17Z
  finding: "Confirmed BindingAltarScreen.init()'s Confirm button lambda sends
    `this.candidateList.getSelectedIndices()` (0, 1, 2+ entries, whatever the player has toggled)
    with no client-side minimum-selection gate — a wrong-count send to the server is fully
    reachable in normal play, not just a malicious/synthetic case."
  timestamp: 2026-09-05T00:00:17Z

## Human Re-Verification (2026-09-05, round 2)

User confirmed: Bug C (socket hover) is FIXED — "Right clicking the altar with the lectern is working now."

Two symptoms remain:
- **Bug A NOT fixed:** "the screen opens, but the UI is all messed up, things are overlapping" — despite the Bug A fix shifting the `BindingAltarMenu` slot Y-coordinates and `BindingAltarScreen`'s widget Y-coordinates to be internally non-overlapping in Java-space (verified by re-reading both files: candidate list 40-100, Confirm button 106-126, inventory grid starts at 140 — no numeric overlap). New leading hypothesis (not yet confirmed): the Bug A fix's own evidence log (timestamp 00:00:05) states it deliberately "kept the existing 200x222 texture asset fixed" and only moved the logical `Slot`/widget coordinates around it. In vanilla `AbstractContainerScreen` convention, the background PNG texture itself bakes in the visual slot-frame graphics (the light/dark inset squares players see) — `Slot` objects only draw the item icon/highlight, not the frame. If `binding_altar.png` was never regenerated to move its baked-in slot-frame artwork to match the new shifted Y-positions, the visual frames would still appear at the OLD (pre-shift) position while the actual interactive slots and rendered items are at the NEW position — a texture/logic desync, not a coordinate math error. This exactly matches "things are overlapping" persisting after a fix that resolved the coordinate math.

- **New symptom, "employee never spawns":** with Bug C now fixed and the real flow reachable, confirming a bind produces no employee at all, with the screen simply closing (silent). Root cause found by re-reading `ServerPayloadHandler.handleSelectTrades` in full: `sp.closeContainer()` (line 109) runs UNCONDITIONALLY after the `menu.access().execute(...)` lambda — including when `validateIndices` returns `null` (wrong selection count) and the lambda's inner logic returns early via `if (selected == null) { return; }` (line 69-71) without ever calling `EmployeeManager.bind`. The Bug D fix's own doc comment claims this case is a "no-op, sockets stay intact, player can retry" — but the container still closes with ZERO player-facing feedback (no themed message, matching this project's own POL-08 convention violated), making a rejected selection indistinguishable from a real bug to the player. Given the Bug A texture desync likely makes it hard to see distinct candidate rows clearly, it's plausible the player is not actually landing exactly 2 selections, and the silent-close-with-no-feedback is turning a recoverable "please select 2" state into an apparent total failure.

## Eliminated

- hypothesis: "Bug C is caused by `ProfessionResolver.fromItem`/`PoiTypes.forState` failing to resolve `Blocks.LECTERN.defaultBlockState()` specifically."
  evidence: "New real-interaction-path GameTest (`binding_altar_real_interaction_sockets_lectern_job_item`) passes — the item sockets correctly via the real `useItemOn` dispatch, identical to `CartographyTable`."
  timestamp: 2026-09-05T00:00:11Z
- hypothesis: "`SoulAltarBlock.useItemOn`'s job-item branch has a sound-event type bug (`SoundEvents.ITEM_FRAME_ADD_ITEM` missing `.value()` unlike the Soul Block branch's `SOUL_ESCAPE.value()`)."
  evidence: "`javap` against the installed NeoForge jar shows `ITEM_FRAME_ADD_ITEM` is a plain `SoundEvent` and `SOUL_ESCAPE` is a `Holder<SoundEvent>` — both usages are already correct for their respective types."
  timestamp: 2026-09-05T00:00:12Z
- hypothesis: "Bug D is caused primarily by the missing `refreshBrain` call (Bug B) altering brain-driven gating in `Villager#mobInteract`."
  evidence: "`Villager#mobInteract`'s gates are `getOffers().isEmpty()`, `isTrading()`, `isSleeping()`, `isBaby()`, `isAlive()` — none are brain-schedule-derived in a way that `refreshBrain`'s absence would flip. The evidence-backed root cause is `ServerPayloadHandler.validateIndices` accepting fewer than 2 selections and producing an empty offers list. `refreshBrain` was still added since it is independently required by PITFALLS.md Pitfall 4 regardless of Bug D."
  timestamp: 2026-09-05T00:00:13Z

## Resolution

root_cause: |
  Bug A: BindingAltarMenu's player-inventory slots (y=84/102/120/142) were never shifted down when
  BindingAltarScreen's imageHeight grew to 222 for the new trade-candidate list (local y 40-130)
  and Confirm button (local y 134-154) — both regions directly overlapped the unshifted inventory
  grid (84-160).
  Bug B: EmployeeManager.bind() never called villager.refreshBrain(level), contradicting the
  required setVillagerData -> refreshBrain -> setVillagerXp -> setOffers(last) order
  (PITFALLS.md Pitfall 4).
  Bug C: SoulAltarRenderer.render() early-returns on be.isEmpty() (Soul Block slot only), so
  socketing the job item before the Soul Block skips the entire render method, including the
  hovering job-item draw, even though the server-side socket succeeded. The socket mechanic and
  ProfessionResolver.fromItem work correctly for Lectern (proven via a new real-interaction-path
  GameTest) — this was a feedback/render gate bug, not a resolver bug.
  Bug D: ServerPayloadHandler.validateIndices enforced only an upper bound on selected trade count
  (`> 2` -> reject) and never a lower bound, silently accepting 0 or 1 selections (e.g. the Confirm
  button's default empty array when no row was clicked) against a >2-candidate pool and returning
  an EMPTY list. This produced a villager with an empty MerchantOffers, which fails vanilla
  Villager#mobInteract's `getOffers().isEmpty()` gate and silently refuses to open the trade
  screen — violating REQUIREMENTS.md PICK-03 ("the player selects exactly 2").
fix: |
  Bug A: BindingAltarMenu — added `INVENTORY_Y_SHIFT = 56` constant, shifted all inventory-row and
  hotbar slot y-coordinates by that amount. BindingAltarScreen — shrunk the candidate list to local
  y 40-100 (3 visible rows), moved the Confirm button to local y 106-126, moved inventoryLabelY to
  130 (10px above the now-shifted row1 at 140), and relocated the "Happiness: N/A" placeholder from
  y=160 (now inside the grid) to the header row (120, 6). All fit within the existing 200x222
  texture asset (no asset regeneration needed) and are bottom-flush with the canvas.
  Bug B: EmployeeManager.bind() — added `villager.refreshBrain(level);` immediately after
  `setVillagerData(...)` and before `setVillagerXp(1)`/`setOffers(...)`.
  Bug C: SoulAltarRenderer.render() — changed the early-return to `be.isEmpty() &&
  be.isJobItemEmpty()`, and made the embedded-Soul-Block draw and the ambient wisp conditional on
  `!be.isEmpty()` independently of the job-item hover draw (which was already gated on
  `!be.isJobItemEmpty()`), so each slot's visual renders as soon as that slot is filled.
  Bug D: ServerPayloadHandler.validateIndices — changed `if (deduped.size() > 2) return null;` to
  `if (deduped.size() != 2) return null;`, enforcing PICK-03's exact-2 requirement for
  non-auto-locked pools. 0/1-selection confirms are now rejected as a no-op (sockets remain
  intact, player can reopen and retry) instead of silently spawning a broken employee.
verification: |
  ./gradlew compileJava — clean build, no errors.
  ./gradlew runGameTestServer — 42/42 tests pass (38 original + 3 round-2 + 1 new round-3:
  reject_wrong_selection_count_keeps_menu_open_and_sockets_intact).
  ./gradlew runServer — starts cleanly, no client-class leak.
  ./gradlew deployToTest — deployed secondshift-0.1.0.jar to the CurseForge test instance.
  User confirmed Bug C fixed in round 2. Round 3's 2 fixes (texture regeneration, selection-
  rejection feedback) are self-verified programmatically (PNG re-decode + pixel spot-checks;
  GameTest) but still require human re-verification of the real-client visual/interactive
  result (see Current Focus).
root_cause: |
  Bug A (round 3 addendum): even after the round-2 coordinate-math fix corrected
  BindingAltarMenu's Slot y-coordinates and BindingAltarScreen's widget y-coordinates,
  binding_altar.png's own baked-in slot-frame artwork was never regenerated to match — a
  vanilla Slot object only draws the item icon, not the frame graphic, which is baked into the
  background PNG. Direct byte-level decode of the shipped 200x222 PNG showed only 2 of the 4
  required inventory-grid row-blocks baked in (one broken 6px-tall sliver, one 18px block
  roughly near but not matching row1), with nothing baked for row2/row3/hotbar at all, plus a
  large stale dark-recess rectangle left over from an earlier layout iteration — a genuine
  texture/logic desync, exactly matching the "still overlapping" report that persisted after
  the coordinate math was already correct.
  Silent selection-rejection (round 3, "employee never spawns"): ServerPayloadHandler
  .handleSelectTrades called sp.closeContainer() unconditionally after the atomic
  access().execute(...) lambda, including when validateIndices rejected the selection (wrong
  count) and the lambda returned early without ever calling EmployeeManager.bind. Confirmed via
  direct re-read of the shipped source: no Component.translatable message existed on this path
  before the unconditional close, and BindingAltarScreen's Confirm button has no client-side
  minimum-selection gate, so a wrong-count send is fully reachable in normal play — turning a
  recoverable "please select 2" state into a silent, unexplained screen-closing failure.
fix: |
  Bug A (round 3 addendum): regenerated binding_altar.png from scratch with a new stdlib
  zlib/struct Python script (matches this project's established no-PIL PNG-generation
  convention) that bakes the slot-frame grid at exactly the current Java layout — soul slot
  (80,35), 3 inventory rows at y=140/158/176, hotbar at y=198 (all 18x18), and the trade-
  candidate-list dark recess at x8-192/y40-100 — using the same color palette already shipped
  (0xC6C6C6 body, 0x8B8B8B slot fill, 0x555555 borders, 0x404040 recess). Verified by decoding
  the regenerated PNG a second time and spot-checking pixel values at all authoritative
  coordinates, not just trusting the generation script's exit code.
  Silent selection-rejection: made sp.closeContainer() conditional on a boolean[] flag set
  only when validateIndices returns null (the exact no-partial-application rejection path,
  which fires before any socket is consumed); on that path the menu now stays open and the
  player receives a new themed action-bar message (lang key
  message.secondshift.altar.select_exactly_two, registered in
  ModRegistrySelfCheck.EXTRA_LANG_KEYS per the project's lang-coverage guardrail) instead of
  the screen silently closing. Every other early-return path in the method (stale/malicious
  send, altar gone, double-confirm race, unreachable profession-empty) retains the original
  close-unconditionally behavior.
files_changed:
  - src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java
  - src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java
  - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java
  - src/main/java/com/cxmxrgo/secondshift/client/render/SoulAltarRenderer.java
  - src/main/java/com/cxmxrgo/secondshift/network/ServerPayloadHandler.java
  - src/main/java/com/cxmxrgo/secondshift/gametest/BindingAltarGameTests.java
  - src/main/java/com/cxmxrgo/secondshift/gametest/ServerPayloadHandlerGameTests.java
  - src/main/java/com/cxmxrgo/secondshift/gametest/EmployeeGameTests.java
  - src/main/resources/assets/secondshift/textures/gui/binding_altar.png (round 3: regenerated)
  - src/main/resources/assets/secondshift/lang/en_us.json (round 3: new
    message.secondshift.altar.select_exactly_two key)
  - src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java (round 3: registered the
    new lang key in EXTRA_LANG_KEYS)
