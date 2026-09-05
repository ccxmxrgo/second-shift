---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: Ready to execute
stopped_at: Completed 05-05-PLAN.md
last_updated: "2026-09-05T04:48:06.246Z"
last_activity: 2026-09-05
progress:
  total_phases: 10
  completed_phases: 4
  total_plans: 20
  completed_plans: 19
  percent: 40
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-04)

**Core value:** Harvest souls → bind a villager at the Soul Altar → hand-pick its profession and its trades, tier by tier. That loop must be reliable and feel good.
**Current focus:** Phase 05 — profession-resolution-trade-picker

## Current Position

Phase: 05 (profession-resolution-trade-picker) — EXECUTING
Next: Phase 4 (Employee Attachment & Spawn) — discuss or plan
Plan: 7 of 7
Last activity: 2026-09-05

Progress: [███░░░░░░░] 30%

## Performance Metrics

**Velocity:**

- Total plans completed: 11
- Average duration: -
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 2 | - | - |
| 02 | 5 | - | - |
| 04 | 4 | - | - |

**Recent Trend:**

- Last 5 plans: -
- Trend: -

*Updated after each plan completion*
| Phase 01 P01 | 55 min | 2 tasks | 13 files |
| Phase 01 P02 | 8 min | 2 tasks | 5 files |
| Phase 02 P01 | 41 min | 3 tasks | 14 files |
| Phase 02 P02 | 12min | 2 tasks | 5 files |
| Phase 02 P03 | 12min | 3 tasks | 10 files |
| Phase 02 P04 | 25min | 3 tasks | 10 files |
| Phase 02 P05 | 9min | 2 tasks | 2 files |
| Phase 03 P01 | 35min | 3 tasks | 6 files |
| Phase 03 P02 | 25min | 3 tasks | 5 files |
| Phase 04 P01 | 25min | 3 tasks | 5 files |
| Phase 04 P02 | 5min | 2 tasks | 3 files |
| Phase 04 P03 | 20min | 3 tasks | 6 files |
| Phase 05 P01 | 35min | 3 tasks | 6 files |
| Phase 05 P02 | 15min | - tasks | - files |
| Phase 05 P03 | 10min | 1 tasks | 1 files |
| Phase 05 P04 | 20min | 2 tasks | 2 files |
| Phase 05 P05 | 30min | 3 tasks | 8 files |
| Phase 05 P06 | 40min | 3 tasks | 5 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Relevant to current work:

- Full rebuild from scratch; old draft source + jar deleted.
- Menu/screen harness is a HARD GATE (Phase 3): empty altar screen must open under `runClient` before any trade logic.
- Toolchain pinned: NeoForge 21.1.248 / ModDevGradle 2.0.146 / Gradle 9.2.1 wrapper / Parchment 2024.11.17, from the official 1.21.1 MDK.
- Employees = `minecraft:villager` + `EmployeeData` attachment; behaviour lives in game-bus handlers guarded by `hasData(EMPLOYEE)`, never a subclass.
- Happiness/upkeep system is IN scope but flagged "revisit after playtesting" (Phase 9).
- [Phase 01]: Self-check throws directly from the FMLLoadCompleteEvent handler (not via enqueueWork) so an unbound register is a fatal hard abort, not a soft broken-mod state — enqueueWork swallows the exception; verified on runClient + runServer
- [Phase 01]: runData is not a self-check surface on MDG 2.0.146 / NeoForge 21.1.248 (no FMLCommonSetupEvent / FMLLoadCompleteEvent); guardrail proven on runClient + runServer
- [Phase 01]: MDG 2.0.146 writes dev run logs to run/logs/latest.log + run/logs/debug.log (not runs/<name>/logs/)
- [Phase 01]: D-12 resolved YES: FML scans run/mods/ (FMLPaths MODSDIR) — a jar dropped there loads with deps enforced; later phases can use it for dev-parity mods
- [Phase 01]: deployToTest is config-cache-safe (providers captured at config time, doLast uses Files+File not project) and absent from the build task graph
- [Phase ?]: Phase 2 lang-key self-check reads the mod jar's own en_us.json off the classpath (not net.minecraft.locale.Language) so it runs identically on client and dedicated server
- [Phase ?]: 1.21.1 has no Item.Properties#enchantable / DataComponents.ENCHANTABLE (both 1.21.2+); durability items are table-enchantable via Item#isEnchantable, override getEnchantmentValue for enchant quality
- [Phase ?]: [Phase 02]: Villager Harvester instakill needs only LivingDamageEvent.Pre#setNewDamage(health+absorption+1) — Resistance V + absorption still one-shots, no setHealth(0)/die fallback (closes 02-RESEARCH Open Question 2)
- [Phase ?]: [Phase 02]: DamageSource#getWeaponItem() exists on NeoForge 21.1.248 (delegates to directEntity main-hand item) — custom-weapon event gates key on it; no held-item fallback needed
- [Phase ?]: [Phase 02]: Hand-written 1.21.1 datapack JSON proven end-to-end (recipes/advancements/loot/blockstate/models); recipe result is {id,count}, recipes stay gated with no advancement/recipes/*.json — granted only via advancement rewards.recipes
- [Phase 02]: [Phase 02]: Soul Altar break (D-04) implemented as written — a charged altar drops NOTHING (not the Soul Block, not the altar block); one-line fallback flip marked in SoulAltarBlock#getDrops
- [Phase 02]: [Phase 02]: 'Soul Mason' advancement uses minecraft:recipe_crafted (recipe_id secondshift:soul_block), not inventory_changed — matches D-15 step 3 'craft a Soul Block'
- [Phase 02]: [Phase 02]: getDrops break-time suppression uses a transient BlockEntity flag (set in playerWillDestroy, read via LootContextParams.BLOCK_ENTITY) — never a Block-singleton field
- [Phase 02]: [Phase 02]: Charged Soul Altar render is a client-only BlockEntityRenderer registered from EntityRenderersEvent.RegisterRenderers; embedded Soul Block drawn via BlockRenderDispatcher#renderSingleBlock at LightTexture.FULL_BRIGHT (no render-type trick), wisp throttled by a per-frame RandomSource roll
- [Phase 02]: [Phase 02]: mandatory ./gradlew runServer client-class-leak gate PASSED — SoulAltarRenderer never class-loads on the dedicated server; Phase 2 complete
- [Phase 03]: AltarSoulContainer re-resolves the BE from level/pos per call (no caching) to stay correct across chunk/BE reloads on both sides
- [Phase 03]: GUI texture generated via stdlib zlib/struct (PIL not installed) instead of installing a new package
- [Phase ?]: Phase 3: ProfessionResolver.heldJobSite() chosen over acquirableJobSite() — identical for vanilla, matches vanilla ResetProfession semantics
- [Phase ?]: Phase 3: useWithoutItem is the single reopen gate for D-02 and D-04 (fallthrough via PASS_TO_DEFAULT_BLOCK_INTERACTION) — no duplicated profession-gate logic
- [Phase 03 UAT]: LOCKED for Phase 5 (ALTAR-02) — job-site block on top of the altar looks visually broken (full-size vanilla block on the narrow pedestal top). Replace "place a real block on top" with an item-socket mechanic: right-click the job-site block onto the altar → consumed into a second BE slot → custom BlockEntityRenderer draws it hovering + slowly spinning above the altar (enchanting-table-book technique, same renderSingleBlock approach as the charged Soul Block). Reworks ProfessionResolver.fromAbove, SoulAltarBlock.useItemOn/useWithoutItem, BindingAltarMenu.stillValid, and BindingAltarGameTests's block-placement tests — not purely additive to Phase 5. See 03-HUMAN-UAT.md Gaps G-2.
- [Phase 04]: ModAttachments.EMPLOYEE typed as DeferredHolder<AttachmentType<?>, AttachmentType<EmployeeData>> (compiler-verified via javap), not the plan sketch's Supplier<AttachmentType<T>> shape
- [Phase 04]: ClientEmployeeSyncDebug is a standalone Dist.CLIENT class (not folded into ClientModBusEvents) because EntityJoinLevelEvent is a game-bus event
- [Phase 04]: EmployeeManager.bind + EmployeeGameTests: spawn ordering (profession -> xp -> offers -> name -> attachment -> addFreshEntity) is the canonical shape every later re-bind/respawn path must match — Non-negotiable per RESEARCH.md Pattern 2 (setVillagerData nulls offers on profession change)
- [Phase 04]: ServerPayloadHandler made public (class + method) so SecondShift.java (root package) can take a cross-package method reference for RegisterPayloadHandlersEvent registration — Compile-correctness necessity, not a behavioral change; controller-role shape (final class, private ctor) preserved
- [Phase ?]: SoundEvents.ITEM_FRAME_ADD_ITEM is a plain SoundEvent (not a Holder) in this mapped API surface — call directly, no .get()/.value()
- [Phase ?]: GameTest coverage for an already-bound altar sets up state via direct BE setters, not two real socket interactions — completing both sockets via useItemOn triggers a real openMenu packet the GameTest mock player cannot receive
- [Phase ?]: List.of()/List.copyOf() immutable lists throw NPE from contains(null) by JDK design - use Stream#anyMatch(Objects::isNull) for null-leak checks instead
- [Phase ?]: [Phase 05 P03]: Hovering job-item render uses the render method's own packedLight (ambient) rather than LightTexture.FULL_BRIGHT, deliberately distinguishing it from the emissive embedded Soul Block
- [Phase 05]: GameTest fully-socketed-altar setup sets heldJobItem/heldSoulBlock directly on the BE, continuing the 05-01 pattern that avoids the real openMenu packet a GameTest mock player cannot receive
- [Phase 05]: MerchantOffer has no equals() override; roll-once content-comparison test compares ItemStack.matches on result/costA/costB plus maxUses/xp
- [Phase ?]: employeeBound is set true only after EmployeeManager.bind returns successfully, inside the same atomic access().execute lambda, closing the double-confirm race and soft-lock risk
- [Phase ?]: validateIndices/sanitizeName made public (not package-private) on ServerPayloadHandler so the cross-package GameTest suite can exercise the trust-boundary logic directly
- [Phase ?]: sanitizeName strips only the literal section-sign character and control characters, not full 2-char vanilla formatting codes -- verified empirically via GameTest against the plan's literal regex spec
- [Phase ?]: MerchantOffer.getCostB() returns ItemStack (not Optional<ItemCost>) -- use ItemStack#isEmpty() for the costB presence check
- [Phase ?]: AbstractSelectionList's scrollbar-position hook on NeoForge 21.1.248 is getScrollbarPosition(), not scrollBarX()
- [Phase ?]: AbstractContainerScreen: renderBg/widgets render before the leftPos/topPos pose translate (absolute coords); renderLabels renders after (relative coords) -- verified via javap bytecode disassembly

### Pending Todos

None yet.

### Blockers/Concerns

- REQUIREMENTS.md previously stated "52 total"; the enumerated list is actually 60. Roadmap and traceability use 60. Non-blocking; noted for the record.
- 4 research spikes are budgeted into phase planning: attachment entity-sync (Phase 4, LIGHT), ItemListing.getOffer side effects (Phase 5), breeding suppression (Phase 6, LOW confidence), offer re-assertion after level-up (Phase 7, MEDIUM).
- Phase 9 (happiness) is the largest net-new chunk with the least research coverage — quarters/structure detection and food-chest access need a design spike during planning.
- EMP-07 "keep employee near altar" has no pre-researched hook — minor spike in Phase 6.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260904-gqp | Enhance villager soul-harvest FX in HarvesterEvents (closes deferred G-1) — layered SCULK_SOUL/SOUL/FLASH + REVERSE_PORTAL stream to killer + SOUL_FIRE_FLAME/END_ROD pseudo-bolt + stacked sounds | 2026-09-04 | 28f0ca9 | [260904-gqp-enhance-villager-soul-harvest-fx-in-harv](./quick/260904-gqp-enhance-villager-soul-harvest-fx-in-harv/) |
| 260905-0yg | Fix CR-01 (bound villager spawn overlaps job-site block — now spawns at altarPos.above(2)) and the client-side EmployeeData sync diagnostic's structurally-always-false synchronous hasData check (now a bounded 20-tick ClientTickEvent.Post poll) | 2026-09-05 | c7aec01 | [260905-0yg-fix-cr-01-spawn-position-overlap-and-cli](./quick/260905-0yg-fix-cr-01-spawn-position-overlap-and-cli/) |

## Deferred Items

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| Polish | G-1: villager soul-harvest FX wants more visual impact. First pass shipped as quick task 260904-gqp (vanilla particles/sounds). Bespoke homing soul-wisp particle + custom sound sting still deferred to Phase 10 (POL-05). | Partially addressed | Phase 2 UAT (2026-09-04) |
| Design (locked) | G-2: job-site block on top of the altar looks visually broken (full block on a narrow pedestal). Locked replacement for Phase 5 (ALTAR-02): item-socket mechanic + hovering/spinning BER render, matching the Soul Block socket pattern. Reworks Phase 3's ProfessionResolver/SoulAltarBlock/BindingAltarMenu/BindingAltarGameTests — build when Phase 5 is planned, not purely additive. | Locked for Phase 5 | Phase 3 UAT (2026-09-04) |

## Session Continuity

Last session: 2026-09-05T04:47:19.617Z
Stopped at: Completed 05-05-PLAN.md
Resume file: None
