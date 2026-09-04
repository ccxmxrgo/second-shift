---
phase: 03-menu-screen-harness-hard-gate
plan: 01
subsystem: menu-screen-harness
tags: [neoforge, menu, screen, registration, hard-gate, gui-01]
requires:
  - phase: 02-economy-items-soul-altar-block
    provides: "SoulAltarBlockEntity (heldSoulBlock field + accessors), ModBlockEntities/ModBlocks registry patterns, ClientModBusEvents/ModRegistrySelfCheck/SecondShift extension points"
provides:
  - "registry/ModMenus.java — DeferredRegister<MenuType<?>> + BINDING_ALTAR holder via IMenuTypeExtension.create(BindingAltarMenu::new)"
  - "menu/BindingAltarMenu.java — AbstractContainerMenu, 3 ctors (client buffer, convenience BlockPos, core ContainerLevelAccess), stillValid, quickMoveStack, 1 SoulSlot + 36 player-inventory slots"
  - "menu/SoulSlot.java — display-only Slot subclass (mayPickup/mayPlace -> false)"
  - "menu/AltarSoulContainer.java — 1-slot Container wrapper re-resolving SoulAltarBlockEntity per call"
  - "client/screen/BindingAltarScreen.java — AbstractContainerScreen<BindingAltarMenu>, 176x166, blits binding_altar.png"
  - "SoulAltarBlockEntity implements MenuProvider — getDisplayName/createMenu"
  - "textures/gui/binding_altar.png — 256x256 procedurally-generated placeholder panel"
affects: [03-02-interaction-wiring]
tech-stack:
  added: []
  patterns:
    - "Register-in-constructor: ModMenus.MENUS.register(modBus) added to SecondShift's one visible register block — the exact line the prior draft omitted"
    - "IMenuTypeExtension.create(BindingAltarMenu::new) buffer-aware MenuType factory (reads BlockPos out of the open-screen buffer)"
    - "Dedicated Container wrapper (AltarSoulContainer) instead of SimpleContainer, keeping SoulAltarBlockEntity as sole source of truth"
    - "Client-class isolation: BindingAltarScreen lives only in client/screen/, referenced only from ClientModBusEvents.onRegisterScreens"
    - "SC2/D-15 reconciliation: hard-abort stays in ModRegistrySelfCheck's FMLLoadCompleteEvent handler; SecondShift.commonSetup adds an informational log line naming the resolved menu key"
key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/registry/ModMenus.java
    - src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java
    - src/main/java/com/cxmxrgo/secondshift/menu/SoulSlot.java
    - src/main/java/com/cxmxrgo/secondshift/menu/AltarSoulContainer.java
    - src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java
    - src/main/resources/assets/secondshift/textures/gui/binding_altar.png
  modified:
    - src/main/java/com/cxmxrgo/secondshift/client/ClientModBusEvents.java
    - src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java
    - src/main/java/com/cxmxrgo/secondshift/SecondShift.java
    - src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java
    - src/main/resources/assets/secondshift/lang/en_us.json
key-decisions:
  - "AltarSoulContainer re-resolves the BlockEntity from level/pos on every call rather than caching a reference, per RESEARCH Open Question 1 option (b) — safe across chunk/BE reloads on both sides"
  - "Player-inventory slot loop written directly as two for loops (no named vanilla helper found/used), per RESEARCH Open Question 2 resolution"
  - "GUI texture generated with a dependency-free Python script (stdlib zlib/struct) instead of PIL, since PIL was not installed in this environment — output verified 256x256 RGBA via direct PNG header parse"
duration: ~35min
completed: 2026-09-04
---

# Phase 3 Plan 01: Menu & Screen Registration Harness Summary

**Registered `secondshift:binding_altar` as a real `MenuType` -> `AbstractContainerMenu` -> `AbstractContainerScreen` chain end to end and structurally killed the prior draft's exact crash by putting `ModMenus.MENUS.register(modBus)` in the one visible register block, verified live under both `runClient` and `runServer`.**

## Performance

- **Duration:** ~35 min
- **Tasks:** 3 completed
- **Files created:** 6 (5 modified)

## Accomplishments

- `ModMenus` is a 5th `DeferredRegister<MenuType<?>>` following the exact `ModBlockEntities` shape, with `BINDING_ALTAR` built via `IMenuTypeExtension.create(BindingAltarMenu::new)`.
- `BindingAltarMenu extends AbstractContainerMenu` with all three required constructors (client buffer, convenience `BlockPos`, core `ContainerLevelAccess`), server-authoritative `stillValid` (D-16, delegating to the vanilla static helper against `ModBlocks.SOUL_ALTAR`), a no-op `quickMoveStack` (D-06), the one display-only `SoulSlot` at UI-SPEC coordinates (80, 35), and the full 36-slot player inventory.
- `SoulSlot` and `AltarSoulContainer` implement the read-only mod slot (D-06) — the container wrapper re-resolves the block entity on every call so it never goes stale across chunk/BE reload.
- `BindingAltarScreen extends AbstractContainerScreen<BindingAltarMenu>` (176x166), blitting a new procedurally-generated 256x256 `binding_altar.png` texture with a readable dark-panel body, bevel border, a soul-cyan glow ring around the recessed mod slot, and the full inventory slot-recess grid, matching the UI-SPEC contract. Lives only in `client/screen/`, bound via `RegisterMenuScreensEvent` in `ClientModBusEvents`.
- `SoulAltarBlockEntity implements MenuProvider` (`getDisplayName`/`createMenu`), making it a valid `serverPlayer.openMenu(...)` target — no interaction trigger is wired yet (Plan 02 / ALTAR-03).
- `SecondShift`'s constructor now registers `ModMenus.MENUS` on the mod bus (the exact line the prior draft omitted) and `commonSetup` logs the resolved menu key (SC2/D-15, informational only). `ModRegistrySelfCheck`'s `Stream.of(...)` guardrail was extended 4 -> 5 registers to include `ModMenus.MENUS` (D-14).
- `container.secondshift.binding_altar` = "Binding Altar" added to `en_us.json`, matching SC1's exact title requirement.

## Task Commits

1. **Task 1: Menu contracts — ModMenus, BindingAltarMenu, SoulSlot, AltarSoulContainer** — `9e4bf49` (feat)
2. **Task 2: BindingAltarScreen + RegisterMenuScreensEvent binding + GUI texture** — `193ec1e` (feat)
3. **Task 3: MenuProvider wiring, register-in-constructor, SC2 guardrail extension, lang key** — `0192514` (feat)

## Verification Performed

- `./gradlew build` green after every task.
- `./gradlew runServer`: clean load, `Done (0.316s)! For help, type "help"`, log line `[SecondShift] menu registered: secondshift:binding_altar` present (SC2 satisfied).
- `./gradlew runClient`: reached `RegisterMenuScreensEvent` cleanly — log line `[SecondShift] registered BindingAltarScreen for secondshift:binding_altar` present, followed by `[SecondShift] client setup ok` with no exceptions. This is the exact dispatch point the prior draft crashed at (`Trying to access unbound value: ResourceKey[minecraft:menu / secondshift:binding_altar]`); it is now clean.
- **D-14 manual verification:** temporarily commented out `ModMenus.MENUS.register(modBus)` in `SecondShift`'s constructor, rebuilt, and re-ran `runServer`. Result: a hard `NullPointerException: Trying to access unbound value: ResourceKey[minecraft:menu / secondshift:binding_altar]` was raised from `SecondShift.commonSetup`'s new `BuiltInRegistries.MENU.getKey(ModMenus.BINDING_ALTAR.get())` log-line call (which itself resolves the unbound holder and throws), logged by FML's `DeferredWorkQueue` as a mod error, and the dedicated server subsequently failed to start (`Failed to start the minecraft server FATAL`). This confirms detaching `ModMenus.MENUS` hard-aborts startup while naming `secondshift:binding_altar`, satisfying the plan's D-14 truth — though the exact crash surfaced via the `commonSetup` log line (which runs at `FMLCommonSetupEvent`, before `ModRegistrySelfCheck`'s `FMLLoadCompleteEvent` guardrail even gets a chance to run its own "Unbound registry entries" list), rather than via `ModRegistrySelfCheck`'s message text verbatim. See "Deviations" below. Restored the register line, rebuilt, and re-ran `runServer` — confirmed clean again (`menu registered: secondshift:binding_altar` present).

## Decisions & Deviations

### Auto-fixed / discretionary items (no plan deviation, within Claude's discretion per plan)

- **AltarSoulContainer** implemented as a dedicated `Container` (RESEARCH Open Question 1, option b) rather than `SoulAltarBlockEntity implements Container` — matches the plan's explicit instruction.
- **Player-inventory slots** written as two direct `for` loops (RESEARCH Open Question 2 resolution) — no named vanilla helper was assumed or searched for at implementation time; this is standard, well-established boilerplate.
- **GUI texture generation:** the plan suggested a "short Python/PIL script." PIL (Pillow) is not installed in this environment (`ModuleNotFoundError: No module named 'PIL'`). Generated the 256x256 RGBA PNG using only Python's standard library (`struct` + `zlib`, manual IHDR/IDAT/IEND chunk construction) instead of installing a new package — this stays within Rule 3's package-manager-install exclusion (no install attempted at all, so no legitimacy-verification checkpoint was needed) and produces an equivalent real, readable placeholder panel matching every UI-SPEC pixel/color requirement (verified `file` reports "PNG image data, 256 x 256, 8-bit/color RGBA, non-interlaced").

### Noteworthy observation (documented, not a deviation)

- The D-14 manual-verification crash path is a raw `NullPointerException` surfaced through the `commonSetup` log line's own `.get()` call on the unbound `DeferredHolder`, rather than through `ModRegistrySelfCheck`'s cleaner "Unbound registry entries: [...]" message. This happens because `FMLCommonSetupEvent` (where the new informational log line lives, per D-15) fires *before* `FMLLoadCompleteEvent` (where the guardrail's `Stream.of(...)` check lives) in the mod lifecycle — so when `ModMenus.MENUS` is detached, the log line itself is the first code to touch the unbound holder and it throws first. The net effect (hard abort naming `secondshift:binding_altar`, server never starts) is unchanged and still satisfies the plan's stated truth. No code change was made in response to this — it is the correct behavior per the plan's Pattern 1/D-15 design; both failure paths would still name the key and both would still hard-abort. Flagging here for visibility since the exact message text differs from the plan's `<done>` wording ("Unbound registry entries" list) in this crash scenario specifically.

**No Rule 4 (architectural) deviations.** No auth gates encountered. No package installs attempted.

## Known Stubs

None. The screen is intentionally near-empty per D-06/UI-SPEC (vanilla chrome + player inventory + one display-only slot + title) — this is the locked design contract for this phase, not a stub. No block-interaction trigger exists yet to actually open the screen in normal play; that is explicitly Plan 02's scope (ALTAR-03), not a gap in this plan.

## Threat Flags

None. All new surface (the one display-only `SoulSlot`, `stillValid`'s block/distance re-check) was already covered by the plan's `<threat_model>` (T-03-01, T-03-02) — no new trust boundary or unmitigated surface was introduced beyond what the plan anticipated.

## Next Plan Readiness

Plan 02 (interaction wiring, `SoulAltarBlock#useItemOn`/`useWithoutItem`, `ProfessionResolver`, POL-08 messaging, D-12 forced-close) is unblocked: `BindingAltarMenu`, `SoulAltarBlockEntity implements MenuProvider`, and `ModMenus.BINDING_ALTAR` are all in place and proven live under both `runClient` and `runServer`.

## Self-Check: PASSED
