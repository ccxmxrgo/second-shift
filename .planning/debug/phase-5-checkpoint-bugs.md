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

All 4 bugs fixed and verified via `./gradlew compileJava`, `./gradlew runGameTestServer` (41/41,
38 original + 3 new regression tests), and `./gradlew runServer` (clean start, no client-class
leak). Awaiting human re-verification of Bugs A, C, D in the real client (GameTest cannot fully
confirm visual/interactive behavior) before archiving.

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
  ./gradlew runGameTestServer — 41/41 tests pass (38 original + 3 new: a real-interaction-path
  Lectern socket test for Bug C, a validateIndices fewer-than-two regression test and a bound-
  employee mobInteract-gate regression test for Bug D).
  ./gradlew runServer — starts cleanly, no client-class leak.
  Bugs A, C, and D still require human re-verification in the real client (visual layout,
  hover render, and trade-screen-opening are not fully provable via GameTest alone).
files_changed:
  - src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java
  - src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java
  - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java
  - src/main/java/com/cxmxrgo/secondshift/client/render/SoulAltarRenderer.java
  - src/main/java/com/cxmxrgo/secondshift/network/ServerPayloadHandler.java
  - src/main/java/com/cxmxrgo/secondshift/gametest/BindingAltarGameTests.java
  - src/main/java/com/cxmxrgo/secondshift/gametest/ServerPayloadHandlerGameTests.java
  - src/main/java/com/cxmxrgo/secondshift/gametest/EmployeeGameTests.java
