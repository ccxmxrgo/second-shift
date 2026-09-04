---
phase: 03-menu-screen-harness-hard-gate
verified: 2026-09-04T15:45:00Z
human_verified: 2026-09-04T18:00:00Z
status: passed
score: 4/4 must-haves verified; human UAT passed 3/3 (see 03-HUMAN-UAT.md)
human_uat_note: "All 3 human verification items passed. One non-blocking design gap logged (G-2): the job-site block placed on top of the altar looks visually broken given the current pedestal shape. Locked decision: replace with an item-socket + floating/spinning render mechanic in Phase 5 (ALTAR-02) — see 03-HUMAN-UAT.md Gaps and STATE.md Deferred Items."
overrides_applied: 0
human_verification:
  - test: "Place a real job-site block (e.g. cartography table) on top of a Soul Altar, right-click it holding a Soul Block, confirm the 'Binding Altar' screen visually opens with a legible title and a correctly-rendered panel/texture."
    expected: "Screen opens showing the 'Binding Altar' title, the procedurally-generated 176x166 panel texture, one visibly recessed slot showing the socketed Soul Block, and the player inventory grid — no visual glitches."
    why_human: "Screen rendering, texture legibility, and title placement cannot be asserted headless; the GameTest suite and gradle log markers only prove the registration/logic chain, not visual correctness (human_verify_mode: end-of-phase, per phase instructions)."
  - test: "Right-click a bare altar empty-handed, and right-click an altar with a Soul Block in hand but no/wrong job block above it. Confirm the themed action-bar message text displays legibly and no crash/screen occurs in both cases."
    expected: "No screen opens, no crash, and the themed POL-08 action-bar message (e.g. 'Nothing to bind...' / 'That block posts no job...') is visible and readable in the action bar."
    why_human: "Message wording/tone/action-bar placement is a UX/visual check; GameTest asserts no-crash/no-socket only, not the rendered message."
  - test: "While the Binding Altar screen is open, break the altar block (or the job block above it), or walk more than ~8 blocks away, and confirm the screen force-closes and the themed closed-reason message appears legibly."
    expected: "Screen closes automatically and the correct themed message (altar_gone / job_gone / too_far) appears on the action bar exactly once."
    why_human: "Visual confirmation of the client-side screen actually closing and the message rendering; GameTest only proves `stillValid` flips to false server-side, not the client-visible close+message UX."
---

# Phase 3: Menu, Screen & Harness Hard Gate Verification Report

**Phase Goal:** The altar opens a correctly-registered, empty "Binding Altar" screen under `runClient` with no crash — structurally preventing the unbound-MenuType failure that killed the prior draft. No trade logic in this phase.
**Verified:** 2026-09-04T15:45:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `runClient` → placing a job-site block + inserting a Soul Block opens an empty "Binding Altar" screen, no crash | ✓ VERIFIED (plumbing) / human_needed (visual) | `SoulAltarBlock.useItemOn` gates on `ProfessionResolver.fromAbove` then sockets + calls `sp.openMenu(be, ...)` (src/main/java/.../content/block/SoulAltarBlock.java:101-133). Independently re-ran `./gradlew runClient`: log shows `registered BindingAltarScreen for secondshift:binding_altar` → `menu registered: secondshift:binding_altar` → `client setup ok`, no exceptions. `en_us.json` has `container.secondshift.binding_altar` = "Binding Altar" exactly. GameTest `binding_altar_menu_open_stillvalid_true` proves the menu opens/validates server-side. Actual visual screen-opening on right-click was not exercised by this verifier (requires a live game session) — routed to human verification. |
| 2 | `runServer` loads with no client-class error; guardrail logs a real `secondshift:binding_altar` menu key | ✓ VERIFIED | Independently re-ran `./gradlew runServer`: log line `[SecondShift] menu registered: secondshift:binding_altar` present, followed by `Done (0.344s)! For help, type "help"`, no exceptions, no client-class references (BindingAltarScreen lives only in `client/screen/`, referenced only from `ClientModBusEvents`, a `Dist.CLIENT`-scoped `@EventBusSubscriber`). |
| 3 | Right-clicking with no job block or no Soul Block does nothing harmful — no crash, screen does not open | ✓ VERIFIED | `SoulAltarBlock.useItemOn`/`useWithoutItem` both gate on `ProfessionResolver.fromAbove(...).isEmpty()` and `be.isEmpty()` before any `openMenu` call, sending a themed message and returning early (src/main/java/.../content/block/SoulAltarBlock.java:112-117, 147-161). GameTest `binding_altar_no_job_block_no_menu_no_crash` independently re-run and green: altar remains unsocketed, no exception. |
| 4 | A malformed/out-of-range menu interaction is rejected server-side without a crash (`stillValid` re-checks altar + proximity) | ✓ VERIFIED | `BindingAltarMenu.stillValid` delegates to `AbstractContainerMenu.stillValid(access, player, SOUL_ALTAR)` every tick and additionally re-derives job-block presence via `ProfessionResolver.fromAbove` for message selection (src/main/java/.../menu/BindingAltarMenu.java:69-86). GameTests `binding_altar_stillvalid_false_after_altar_break` and `binding_altar_stillvalid_false_when_far` independently re-run and green. Vanilla `AbstractContainerMenu#clicked` bounds-checks slot indices; the one custom `SoulSlot` is display-only (`mayPickup`/`mayPlace` → false), so no bespoke out-of-range handling was needed and none was claimed beyond this. |

**Score:** 4/4 truths verified (plumbing/logic level). Visual/UX confirmation of SC1/SC3's rendered outcome is deferred to human verification per `human_verify_mode: end-of-phase`.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `registry/ModMenus.java` | `DeferredRegister<MenuType<?>>` + `BINDING_ALTAR` via `IMenuTypeExtension.create` | ✓ VERIFIED | Matches exactly; registered on mod bus in `SecondShift` constructor and included in `ModRegistrySelfCheck`'s guardrail `Stream.of(...)`. |
| `menu/BindingAltarMenu.java` | 3 ctors, `stillValid`, `quickMoveStack` | ✓ VERIFIED | All three ctors present, `stillValid` re-checks block + proximity + sends forced-close message once (`forcedCloseMessageSent` guard), `quickMoveStack` returns `ItemStack.EMPTY`. |
| `menu/SoulSlot.java` | display-only slot | ✓ VERIFIED | `mayPickup`/`mayPlace` both `false`. |
| `menu/AltarSoulContainer.java` | 1-slot Container wrapper | ✓ VERIFIED | Re-resolves BE per call, all Container methods implemented correctly. |
| `client/screen/BindingAltarScreen.java` | `AbstractContainerScreen<BindingAltarMenu>`, 176x166, blits texture | ✓ VERIFIED | Matches; lives only in `client/screen/` package. |
| `textures/gui/binding_altar.png` | 256x256 GUI texture | ✓ VERIFIED (existence/dims) — visual quality is human-check | File exists; dimension claim not independently re-verified via `file` command in this pass but was verified in 03-01-SUMMARY and unaffected by 03-02's changes. |
| `content/blockentity/SoulAltarBlockEntity.java implements MenuProvider` | `getDisplayName`/`createMenu` | ✓ VERIFIED | Confirmed via grep; both methods present, `@Override`. |
| `trade/ProfessionResolver.java` | PoiType → VillagerProfession, no hardcoded block list | ✓ VERIFIED | Registry-only lookups (`PoiTypes.forState` + `BuiltInRegistries.VILLAGER_PROFESSION` scan), no `Block`/`ResourceLocation` literals. |
| `gametest/BindingAltarGameTests.java` | SC4 + ALTAR-03 gate GameTests | ✓ VERIFIED | 6 `@GameTest` methods present, all independently re-run green. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `ClientModBusEvents` | `BindingAltarScreen` | `RegisterMenuScreensEvent#register(ModMenus.BINDING_ALTAR.get(), BindingAltarScreen::new)` | ✓ WIRED | Confirmed in source; log line confirms it fires at runtime. |
| `SecondShift` | `ModMenus` | `ModMenus.MENUS.register(modBus)` | ✓ WIRED | Present in constructor, in the one visible register block. |
| `ModRegistrySelfCheck` | `ModMenus` | `Stream.of(..., ModMenus.MENUS)` unbound scan | ✓ WIRED | Confirmed 5-register `Stream.of(...)` includes `ModMenus.MENUS`. |
| `SoulAltarBlockEntity` | `BindingAltarMenu` | `createMenu(...)` returns `new BindingAltarMenu(...)` | ✓ WIRED | Confirmed. |
| `SoulAltarBlock` | `ProfessionResolver` | `ProfessionResolver.fromAbove(level, pos)` gate | ✓ WIRED | Confirmed in both `useItemOn` and `useWithoutItem`, gate runs before mutation. |
| `SoulAltarBlock` | `SoulAltarBlockEntity` | `serverPlayer.openMenu(be, buf -> buf.writeBlockPos(pos))` | ✓ WIRED | Confirmed in both methods on their respective success paths. |
| `BindingAltarMenu` | `player.displayClientMessage` | `stillValid()` forced-close messaging | ✓ WIRED | Confirmed, `ContainerLevelAccess#evaluate` used to select the reason key. |

### Data-Flow Trace (Level 4)

Not applicable in the traditional sense (no dynamic list/DB-backed UI this phase — the screen is intentionally near-empty by design, D-06). The one data path (socketed Soul Block → `AltarSoulContainer.getItem` → `SoulSlot` render) resolves through the live `SoulAltarBlockEntity` on every call rather than a cached/static value — confirmed by code read, not a stub.

### Behavioral Spot-Checks / Live Gradle Runs

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full compile | `./gradlew build` | BUILD SUCCESSFUL, no output/errors | ✓ PASS |
| GameTest suite | `./gradlew runGameTestServer` (independently re-run) | `All 14 required tests passed :)` — includes `[SecondShift] menu registered: secondshift:binding_altar` | ✓ PASS |
| Client boot | `./gradlew runClient` (independently re-run) | `registered BindingAltarScreen for secondshift:binding_altar` → `menu registered: secondshift:binding_altar` → `client setup ok`, no exceptions | ✓ PASS |
| Server boot | `./gradlew runServer` (independently re-run) | `menu registered: secondshift:binding_altar` → `Done (0.344s)!`, no exceptions | ✓ PASS |
| D-14 unbound-menu hard-abort | Not independently re-run (destructive/manual edit-rebuild-revert cycle) | SUMMARY 03-01 documents the crash reproduction narrative in detail (raw NPE from the `commonSetup` log line, server fails to start, key named) — plausible and consistent with the surrounding code (the `BuiltInRegistries.MENU.getKey(...)` call in `commonSetup` would indeed throw on an unbound `DeferredHolder`, and `ModRegistrySelfCheck`'s guardrail independently covers the same registry via `Stream.of(...)` regardless of ordering) | not independently re-run — accepted on code-plausibility grounds, see note below |

**Note on D-14 spot-check:** This verifier did not comment out `ModMenus.MENUS.register(modBus)` and re-run `runServer` to reproduce the crash byte-for-byte, since that requires a destructive edit/rebuild/revert cycle. The claim was accepted based on: (a) reading `SecondShift.commonSetup`'s `BuiltInRegistries.MENU.getKey(ModMenus.BINDING_ALTAR.get())` call, which unconditionally calls `.get()` on the `DeferredHolder` and would throw on an unbound holder; (b) `ModRegistrySelfCheck`'s guardrail independently includes `ModMenus.MENUS` in its `Stream.of(...)` unbound-scan, which is the structural mechanism the phase goal requires regardless of which code path surfaces the failure first. This is a lower-confidence, code-review-only confirmation rather than a live-reproduced one — noted for transparency, not treated as a gap since the underlying structural fix (`ModMenus.MENUS.register(modBus)` present, guardrail covers it) is independently verified.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|--------------|--------|----------|
| GUI-01 | 03-01, 03-02 | Altar `MenuType` + `Screen` registered correctly; empty screen opens under `runClient` without crashing | ✓ SATISFIED | `ModMenus`/`BindingAltarMenu`/`BindingAltarScreen` chain verified end-to-end; `runClient` confirmed clean live. Visual open confirmation deferred to human verification. |
| ALTAR-03 | 03-02 | Inserting a Soul Block into an altar with a job block on top opens the binding GUI | ✓ SATISFIED | `SoulAltarBlock.useItemOn` wired with `ProfessionResolver` gate + `openMenu` call; GameTest suite proves the underlying logic paths. |

Both requirement IDs from PLAN frontmatter (`GUI-01`, `ALTAR-03`) are present in REQUIREMENTS.md and both are marked `[x]` there — consistent with the evidence gathered independently in this verification. No orphaned requirements found for Phase 3 in REQUIREMENTS.md's phase-mapping table.

### Anti-Patterns Found

None found. Scanned all files modified/created in this phase (`ModMenus`, `BindingAltarMenu`, `SoulSlot`, `AltarSoulContainer`, `BindingAltarScreen`, `ClientModBusEvents`, `SoulAltarBlockEntity`, `SecondShift`, `ModRegistrySelfCheck`, `ProfessionResolver`, `SoulAltarBlock`, `BindingAltarGameTests`) for `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER`/empty-return stubs. No debt markers present. One `>>> D-04 FALLBACK` comment in `SoulAltarBlock.getDrops` is a documented, intentional design toggle (not a stub or debt marker) explaining how to flip a deliberate loot-drop behavior — it is explanatory, not an outstanding task.

### Human Verification Required

See frontmatter `human_verification` list above — 3 items, all covering the visual/UX rendering of the screen, message text, and forced-close behavior, none of which can be asserted headless. This matches `03-VALIDATION.md`'s own "Manual-Only Verifications" table (screen title/layout, no-crash "feel", POL-08 message wording), and is consistent with `human_verify_mode: end-of-phase` for this phase.

### Gaps Summary

No gaps found. Every observable truth, artifact, and key link resolved to VERIFIED against the live codebase and independently re-run Gradle tasks (`build`, `runGameTestServer` 14/14, `runClient`, `runServer`), not merely against SUMMARY.md's narrative. The one lower-confidence item (D-14 crash reproduction) was accepted on code-review grounds rather than live-reproduced, and is noted transparently above but does not block the phase — the structural fix it describes (menu registered in the one visible constructor block + covered by the guardrail) is independently confirmed regardless.

Status is `human_needed` rather than `passed` solely because the phase's own `human_verify_mode: end-of-phase` setting requires the visual rendering (screen appearance, message legibility, forced-close UX) to be confirmed by a human — this is expected per the phase's stated verification mode, not a sign of an implementation gap.

---

_Verified: 2026-09-04T15:45:00Z_
_Verifier: Claude (gsd-verifier)_
