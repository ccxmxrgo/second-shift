---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: Ready to execute
stopped_at: Completed 08-01 (Mod-Owned Restock); autonomous overnight run continuing to Phase 9
last_updated: "2026-09-08T05:45:00.000Z"
last_activity: 2026-09-08
progress:
  total_phases: 10
  completed_phases: 8
  total_plans: 23
  completed_plans: 23
  percent: 80
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-04)

**Core value:** Harvest souls → bind a villager at the Soul Altar → hand-pick its profession and its trades, tier by tier. That loop must be reliable and feel good.
**Current focus:** Phase 09 — quarters-happiness (autonomous overnight build in progress)

## Current Position

Phase: 08 (mod-owned-restock) — COMPLETE (fully code-verified, no human_needed items — see 08-VERIFICATION.md)
Next: Phase 09 (Quarters & Happiness) — being built autonomously (2026-09-08 overnight session, user asleep, explicit autonomous-mode request)
Plan: 1 of 1 (Phase 8)
Last activity: 2026-09-08

Progress: [████████░░] 80%

**2026-09-08 note (Phase 6, autonomous session):** an earlier dispatch for this phase accidentally
ran two concurrent agent sessions against the same working tree (one agent silently spawned a
nested background worker instead of doing the work directly, and the orchestrator dispatched a
second independent one when the first appeared to have done nothing). Both detected the collision
and safely aborted with zero corruption — see 06-01-SUMMARY.md "Issues Encountered" for the full
account and the lesson learned (verify whether a "I've launched a background agent" report from a
general-purpose agent is real before assuming nothing happened and redispatching). Phase 6 was
then implemented directly by the orchestrating session itself, in one pass, with no further
concurrency risk.

**2026-09-08 note (autonomous overnight session):** Phase 5's original design (custom Screen, pick-2-then-confirm, editable name field) was completely superseded across 5 live-iteration redesign rounds (10-15) directly requested by the user in real time, landing on: a vanilla-EnchantmentMenu-derived Binding Altar showing a scrollable "career path" list of the profession's HIGHEST tier trades, granting one picked trade immediately. This is a real, user-approved architectural pivot — see git log 189b1bd..1825e42 and 05-07-SUMMARY.md/05-VERIFICATION.md for full detail before assuming any earlier phase document (05-CONTEXT.md, 05-UI-SPEC.md, 05-06-PLAN.md) still describes the shipped UI. The user then invoked `/gsd-autonomous` and went to sleep, asking Claude to keep building phases 6-10 overnight using its own judgment on open design questions, and to research online before designing Phase 7's progression mechanic (see the saved memory note `second-shift-phase7-tier-gated-trade-idea` for that research + recommended direction).

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
- [Phase 05]: EnchantmentScreen's inherited mouseClicked does a hardcoded 3-fixed-row bounding-box check that calls clickMenuButton directly, before any real widget gets a click -- BindingAltarScreen extends AbstractContainerScreen directly instead, once clickMenuButton became genuinely functional (round-15 career-path list)
- [Phase 05]: getType() on AbstractContainerMenu is not final -- overriding it is how a menu whose superclass constructor hardcodes a MenuType (EnchantmentMenu -> MenuType.ENCHANTMENT) can still redirect the open-screen packet to its own registered MenuType, without touching the vanilla type or every real instance of it in the world
- [Phase 06]: Villager#canBreed() requires getAge() == 0; AgeableMob#setAge(int) is public -- holding an employee at a positive age (vanilla's own post-breeding cooldown value, 6000) is a Mixin-free breeding-precondition break that defends both sides of VillagerMakeLove's two-partner canBreed() check
- [Phase 06]: StreamCodec.composite has overloads for exactly 1-6 components in 1.21.1 -- EmployeeData's Phase 6 altarPos field fills the 6th and last slot; Phase 9's happiness/timer fields will need a nested sub-record or a hand-written StreamCodec
- [Phase 06]: ServerLevel#getEntity(UUID) exists for resolving a stored entity id back to a live entity (used by EmployeeFiring's delayed-smite queue)
- [Phase 07]: "Promotable" is derived (villager's vanilla VillagerData.getLevel() > EmployeeData.tier()), never a persisted boolean -- avoids a 7th EmployeeData field past the StreamCodec.composite 6-component ceiling Phase 6 already hit
- [Phase 07]: No clean "villager leveled up" event exists (increaseMerchantCareer/updateTrades are private) -- reverting vanilla's auto-appended trades is done via "revert, don't prevent" on the existing Phase 6 40-tick per-employee periodic check, not a new hook or a Mixin
- [Phase 07]: "Pick 2" at a Promotion Ritual is done with zero new network payloads -- selection toggles client-side only (TradeCandidateList's new toggle mode) and a single Confirm click packs up to 2 chosen indices into one int (idxA*32+idxB) sent through vanilla's existing clickMenuButton RPC, the same one BindingAltarMenu already established
- [Phase 07]: The Binding Altar's bind-time picker now rolls the profession's TIER 1 pool, not the max tier (Phase 5 round-15 shipped max-tier-immediate-grant as an explicit interim stopgap) -- now that Promotion Ritual infrastructure exists, every tier including the max one is earned the same way, generalizing the user's original "grant only at max tier" balancing idea rather than special-casing it
- [Phase 07]: MenuProvider#createMenu runs BEFORE getDisplayName() in ServerPlayer#openMenu (verified via decompiled source) -- SoulAltarBlockEntity's one-shot promotionRitualRequested flag must be reset in getDisplayName(), not createMenu(), or the title packet reads the wrong branch
- [Phase 08]: Villager#restock() is public and has NO internal day/POI/twice-daily gate (that lives entirely in the separate private shouldRestock()/allowedToRestock()) -- calling restock() directly, never shouldRestock(), is what makes the mod-owned timer genuinely independent of vanilla's own restock rhythm
- [Phase 08]: Restock timer is a SEPARATE AttachmentType<Long> (secondshift:restock_timer, no sync -- server-only bookkeeping), not a 7th EmployeeData field, since that record's StreamCodec.composite chain is already full (Phase 6)
- [Phase 08]: "at most one restock on reload, no burst" falls out for free from `if (now - last >= interval) { restock(); last = now; }` -- no explicit catch-up-N-times logic was ever needed
- [Phase 08]: First ModConfig.Type.COMMON value added (restockIntervalTicks, ModConfigSpec/registerConfig) -- generates run/config/secondshift-common.toml, verified loading cleanly in both runGameTestServer and a real runClient boot

### Pending Todos

None yet.

### Blockers/Concerns

- **[Phase 05, non-blocking, pending user]:** The round-15 Binding Altar redesign (scrollable career-path trade list) has only been confirmed to boot without crashing and pass all 44 GameTests. It has NOT been interactively confirmed by the user actually scrolling/clicking it in a live client — please check this when you're back (see 05-VERIFICATION.md Human Verification item 1). If it's already been checked and works, no action needed.
- REQUIREMENTS.md previously stated "52 total"; the enumerated list is actually 60. Roadmap and traceability use 60. Non-blocking; noted for the record.
- 4 research spikes are budgeted into phase planning: attachment entity-sync (Phase 4, LIGHT), ItemListing.getOffer side effects (Phase 5), breeding suppression (Phase 6, LOW confidence), offer re-assertion after level-up (Phase 7, MEDIUM).
- Phase 9 (happiness) is the largest net-new chunk with the least research coverage — quarters/structure detection and food-chest access need a design spike during planning.
- EMP-07 "keep employee near altar" has no pre-researched hook — minor spike in Phase 6.
- **[Phase 07, non-blocking, pending user]:** the promotion-ready signal (particles + action-bar message) and the Promotion Ritual screen's Confirm-button UX have only been verified by GameTest + a clean client boot — not by an interactive playtest of an employee actually leveling up and being promoted in a live client. See 07-VERIFICATION.md Human Verification items.

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
