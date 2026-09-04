# Phase 3: Menu & Screen Harness (HARD GATE) - Context

**Gathered:** 2026-09-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Register the altar's `MenuType` + `Screen` **correctly** and prove an empty "Binding Altar"
screen opens under `runClient` with no crash — structurally killing the unbound-`MenuType`
failure that crashed the prior draft (`Trying to access unbound value: ResourceKey[minecraft:menu
/ secondshift:binding_altar]`). This is the HARD GATE before any trade/binding logic.

**Requirements:** GUI-01, ALTAR-03.

**In scope:**
- `registry/ModMenus` — 5th `DeferredRegister` (`MenuType<BindingAltarMenu>`), wired into the one
  `SecondShift` constructor block and the `ModRegistrySelfCheck` guardrail.
- `menu/BindingAltarMenu extends AbstractContainerMenu` (common, both sides) — player inventory
  + the altar's **one** slot (the socketed Soul Block), no other slots, no trade/employee data.
- `client/screen/BindingAltarScreen extends AbstractContainerScreen<BindingAltarMenu>` (client
  only) — vanilla dialog chrome, player inventory, the one slot, title "Binding Altar". Nothing else.
- `client/ClientModBusEvents` (already `@EventBusSubscriber(bus=MOD, Dist.CLIENT)`) — add the
  `RegisterMenuScreensEvent` → `BindingAltarScreen::new` binding.
- `SoulAltarBlockEntity implements MenuProvider`; server opens via
  `serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(pos))`.
- Open trigger wired on `SoulAltarBlock` (`useItemOn` socket-path + `useWithoutItem` reopen-path).
- Runtime **PoiType → VillagerProfession resolver** (research §9) — built here, used ONLY as a
  yes/no "is the block on top a real job site" gate. The resolved profession is not stored or used.
- `stillValid(Player)` re-checks the altar block + proximity (SC4).
- Themed `Component.translatable` messages (POL-08) for every invalid state reachable this phase.
- `en_us.json` keys for the GUI title + all new POL-08 messages.
- A server-side GameTest for menu-open + `stillValid` rejection (SC4), if cheap.

**Out of scope (Phases 4–9):**
- Any trade pool, `MerchantOffer` list, trade picker, `MenuType` open-data beyond the block pos.
- Storing/using the resolved profession for binding (ALTAR-02 — Phase 5).
- Consuming the Soul Block / job block, spawning the employee, `EmployeeData` (ALTAR-04, Phase 4–5).
- `ContainerData` / payload sync of employee name/profession/tier/happiness (GUI-02, GUI-03 — Phase 5).
- Name-entry widget, career-path preview (PICK-07, ROST-01 — Phase 5+).
- Any `CustomPacketPayload` for client→server selections (Phase 5).
- POL-08's later cases: empty trade pool, altar with no valid quarters (Phases 5 / 9).

</domain>

<decisions>
## Implementation Decisions

### Open trigger & Soul Block handling
- **D-01:** Right-click **with a Soul Block in hand** on an altar that is (a) empty and (b) has a
  profession-mapped job block directly above → **socket the Soul Block into the BE AND open the
  Binding Altar screen in the same click** (one action; matches SC1 "inserting a Soul Block …
  opens"). The socket is real, persisted BE state — this extends Phase 2's existing one-way
  `useItemOn` socket, it does not replace it.
- **D-02:** Empty-hand right-click (`useWithoutItem`) **re-opens** the screen for an altar that is
  already charged AND has a profession-mapped job block above.
- **D-03:** No consumption / no binding this phase. The socketed Soul Block stays in the BE
  (still one-way, no retrieval — same as Phase 2). Phase 5's bind is what actually consumes it.
- **D-04:** Right-click with a Soul Block on an **already-charged** altar just opens the screen
  (no second socket, no error).

### The empty screen
- **D-05:** Real `AbstractContainerMenu` → `MenuType` → `AbstractContainerScreen` plumbing — NOT a
  bare `Screen`. The hard gate exists to prove the container-menu path works.
- **D-06:** `BindingAltarScreen` renders: vanilla dialog chrome, the player inventory, and the
  altar's **one slot** showing the socketed Soul Block. The slot is display-only this phase —
  the player cannot take from or place into it (`Slot#mayPickup`/`mayPlace` → false, or a
  read-only slot subclass). Nothing else on the screen.
- **D-07:** Title = `Component.translatable("container.secondshift.binding_altar")` → "Binding Altar"
  (exact string is fixed by SC1). HR-necromancer tone allowed in the messages, not the title.

### "Job block on top" gate
- **D-08:** Phase 3 builds the runtime resolver (research §9 — iterate the villager-profession
  registry, match on the profession's job-site POI / `PoiType`; **no hardcoded block list**).
  `PoiTypes.forState(level.getBlockState(altarPos.above()))` → `PoiType` → profession-or-nothing.
- **D-09:** The gate is **"resolver returns a profession"**. The resolved `VillagerProfession` is
  NOT stored on the BE and NOT used for anything this phase — only the boolean. Phase 5 wires the
  real ALTAR-02 target-profession behaviour on top of the same resolver.
- **D-10:** Resolver lives in a shared **common** util (research calls it `ProfessionResolver`),
  reused by `SoulAltarBlock` now and Phase 5 later.

### Invalid-state feedback (POL-08, reachable-now subset)
- **D-11:** Themed `Component.translatable` action-bar/chat messages now for: no job block on top;
  block on top is not a job site (POI present but unmapped, or no POI); no Soul Block in hand and
  the altar is empty. Wording: HR-necromancer tone (consistent with the advancement clue chain).
- **D-12:** On forced close — `stillValid` fails because the altar was broken, the job block was
  removed, or the player moved out of range — the screen closes **and** the player gets a themed
  message naming the reason.
- **D-13:** Deferred POL-08 cases (empty trade pool, no valid quarters) are explicitly NOT in
  Phase 3.

### Registration guardrail (locked by SC2 — not a discussion choice)
- **D-14:** Extend `ModRegistrySelfCheck`'s `Stream.of(ModItems.ITEMS, ModBlocks.BLOCKS,
  ModBlockEntities.BLOCK_ENTITIES, ModCreativeTab.TABS)` to include `ModMenus.MENUS` (4 → 5
  registers). A deliberate detach of `ModMenus` must hard-abort startup naming
  `secondshift:binding_altar`.
- **D-15:** SC2 says the guardrail logs the menu key from `FMLCommonSetupEvent`; Phase 1's
  guardrail currently throws from `FMLLoadCompleteEvent`. **Research/planning must reconcile** —
  keep one event, ensure the menu key is asserted bound and logged. Do not silently add a second
  parallel guardrail path.

### Server-authoritative open (SC4)
- **D-16:** `BindingAltarMenu` has the two standard ctors: client
  `(int, Inventory, RegistryFriendlyByteBuf)` reading the block pos, server
  `(int, Inventory, SoulAltarBlockEntity)` (or `ContainerLevelAccess`). `stillValid` =
  `AbstractContainerMenu.stillValid(ContainerLevelAccess.create(level, pos), player,
  ModBlocks.SOUL_ALTAR.get())` plus the vanilla ~8-block distance check. No custom payload.

### Claude's Discretion
- Exact GUI texture / background dimensions and the 1-slot position (UI-SPEC or planner's call —
  the screen is nearly empty, so this is low-stakes; `/gsd:ui-phase 3` optional).
- Precise message strings (tone is fixed: HR-necromancer).
- Whether the read-only slot is a `Slot` subclass or flag overrides.
- Whether to add the SC4 GameTest as a new method in `HarvesterGameTests` or a new
  `BindingAltarGameTests` class.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Menu / Screen architecture (the load-bearing split)
- `.planning/research/ARCHITECTURE.md` — the `menu/` (common) vs `client/screen/` (client) split,
  the full class table (`menu/BindingAltarMenu`, `client/screen/BindingAltarScreen`,
  `registry/ModMenus`, `client/ClientModBusEvents`), and the "common code never imports `client/`;
  open via `serverPlayer.openMenu(MenuProvider, extraDataWriter)`" rule. **MUST read.**
- `.planning/research/STACK.md` §2 — the exact prior-draft crash cause (`MenuType` never added to
  the registry / register not attached) that this phase exists to prevent.
- `.planning/research/PITFALLS.md` — menu/screen pitfalls, `Dist.CLIENT` class-isolation, and the
  "Looks Done But Isn't" checklist.
- `./CLAUDE.md` §2 "Menu + Screen — the known failure point" and "What NOT to Use"
  (NO `MenuScreens.register` from `FMLClientSetupEvent`; use `RegisterMenuScreensEvent` on the mod
  bus, `Dist.CLIENT`; no `net.minecraftforge.*` / Forge-era APIs; client→server payloads use
  `RegisterPayloadHandlersEvent` + `StreamCodec` — but no payload is needed this phase).

### POI → profession resolver
- `./CLAUDE.md` §9 "POI → profession, at runtime, no hardcoded list".
- `.planning/research/ARCHITECTURE.md` (the `ProfessionResolver` entry in the class table).

### Existing altar code this phase extends
- `.planning/phases/02-economy-items-soul-altar-block/02-04-SUMMARY.md` — current `SoulAltarBlock`
  (`useItemOn` one-way socket, `useWithoutItem` → PASS, `playerWillDestroy`/`getDrops`,
  `brokenWhileCharged` flag) and `SoulAltarBlockEntity` (`heldSoulBlock`, accessors,
  `getUpdateTag`/`getUpdatePacket`).
- `.planning/phases/02-economy-items-soul-altar-block/02-05-SUMMARY.md` — `client/ClientModBusEvents`
  already exists as a `bus=MOD, Dist.CLIENT` subscriber (it hosts the BER registration).
- `.planning/phases/01-skeleton-feedback-loop/01-01-SUMMARY.md` — the `ModRegistrySelfCheck`
  event pattern (direct-throw, not `enqueueWork`) that D-14/D-15 extend.

### Phase contract
- `.planning/ROADMAP.md` "Phase 3: Menu & Screen Harness (HARD GATE)" — goal + the 4 success
  criteria (the actual contract).
- `.planning/REQUIREMENTS.md` — GUI-01, ALTAR-03 (this phase); GUI-02, ALTAR-02/04 (Phase 5, the
  boundary).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java` —
  `heldSoulBlock` field + `isEmpty()/getHeldSoulBlock()/setHeldSoulBlock()` + client sync are
  already there. Add `implements MenuProvider` (`getDisplayName()` + `createMenu(int, Inventory,
  Player)`). The one menu slot reads/writes this field.
- `src/main/java/com/cxmxrgo/secondshift/client/ClientModBusEvents.java` — already
  `@EventBusSubscriber(modid=MODID, bus=MOD, value=Dist.CLIENT)`. Add one
  `@SubscribeEvent static void onRegisterScreens(RegisterMenuScreensEvent e)`.
- `src/main/java/com/cxmxrgo/secondshift/registry/Mod{Items,Blocks,BlockEntities,CreativeTab}.java`
  — the `DeferredRegister` holder pattern to copy for `ModMenus` (use
  `DeferredRegister<MenuType<?>>` / `IMenuTypeExtension.create` for the buffer-aware factory).
- `src/main/java/com/cxmxrgo/secondshift/gametest/HarvesterGameTests.java` — `@GameTestHolder`
  pattern + `gameTestServer` run already wired; a menu-open / `stillValid` test can slot in here.

### Established Patterns
- **One visible register block** in `SecondShift` constructor — `ModMenus.MENUS.register(modBus)`
  joins it (Phase 1 D + Phase 2 pattern).
- **`ModRegistrySelfCheck` `Stream.of(...)`** hard-aborts on any unbound register — 4 → 5 (D-14).
- **`@Override` on every altar interaction method** so a wrong 1.21.1 signature fails to compile
  (Phase 2 rule — `useItemOn` returns `ItemInteractionResult`, `useWithoutItem` returns
  `InteractionResult`).
- **`Dist.CLIENT` class isolation** proven by the phase-2 `runServer` client-class-leak gate —
  the same gate applies here (screen + widgets must never load server-side).
- **Hand-written `en_us.json`, no datagen** (D-17 from Phase 2) — add the GUI + POL-08 keys directly.

### Integration Points
- `SoulAltarBlock.useItemOn` — the socket path gains "if job block maps to a profession, also
  `player.openMenu(be)` on the server after socketing".
- `SoulAltarBlock.useWithoutItem` — currently returns PASS; becomes the reopen path.
- `SecondShift` constructor + `ModRegistrySelfCheck` — new 5th register.
- `en_us.json` — `container.secondshift.binding_altar` + `message.secondshift.*` POL-08 keys.

</code_context>

<specifics>
## Specific Ideas

- The screen should feel like a real dialog (vanilla chrome + inventory + the one slot), not a
  placeholder box — the user wants the harness to look finished, not stubbed.
- The user deliberately chose the thorough options: build the real POI→profession resolver now
  (gate-only use) and land the full reachable-now POL-08 message set, accepting a bigger Phase 3
  in exchange for less Phase 5 rework and a discovery flow that feels complete.
- HR-necromancer tone for all player-facing messages, consistent with the Phase 2 advancement
  clue chain.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. The user's "thorough" picks (profession resolver,
full POL-08) were folded into Phase 3 scope, not deferred.

</deferred>

---

*Phase: 3-menu-screen-harness-hard-gate*
*Context gathered: 2026-09-04*
