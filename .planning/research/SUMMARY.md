# Project Research Summary

**Project:** Second Shift
**Domain:** Minecraft content mod (NeoForge 1.21.1) - villager/trading overhaul with a necromancy theme; solo, single-player
**Researched:** 2026-09-04
**Confidence:** HIGH - toolchain and API surface were binary-verified against the exact neoforge-21.1.248 jars and the 1.21.1 client jar on this machine, and the prior crash was reproduced from the real crash report plus logs.

## Executive Summary

Second Shift is a personal NeoForge 1.21.1 mod that reskins villager trading as necromancy: harvest villager souls, bind them into named "employee" villagers at a Soul Altar, and hand-pick each employee's trades from that profession's real vanilla trade pool, tier by tier. The build-it-right approach is well understood and fully supported by public NeoForge APIs - no Mixin, no Access Transformers, no custom entity type. An employee is a plain minecraft:villager carrying one AttachmentType (EmployeeData) record; every behavioural difference (no zombification, no lightning-witch, no breeding, custom drops, mod-owned restock, chosen-trade enforcement) is a game-bus event handler guarded by villager.hasData(EMPLOYEE). Custom GUI is a standard MenuType + AbstractContainerMenu + AbstractContainerScreen, opened server-side via ServerPlayer.openMenu with candidate offers passed in the open-data buffer, and a single client-to-server payload carrying the player selections.

The dominant risk is not villager logic - it is the feedback loop. The prior draft shipped a MenuType that was never actually added to the registry (MENUS.register(modBus) missing, or holder class never loaded), which surfaced only as a client-init NPE at RegisterMenuScreensEvent ("Trying to access unbound value: ...secondshift:binding_altar"). "gradlew build" compiles it, "gradlew runData" never touches it, and the only test loop was hand-copying a jar into a CurseForge instance. The roadmap must fix the loop first: "gradlew runClient" as the default iteration loop, one "gradlew runServer" per phase for dist-safety, plus a startup isBound() self-check over every DeferredRegister. Do a do-nothing menu vertical slice that opens under runClient before any mechanic exists - STACK, ARCHITECTURE, and PITFALLS all independently call this out as a hard gate.

The second risk cluster is vanilla villager internals, all mapped and mitigated but two needing verification spikes: (1) breeding suppression - BabyEntitySpawnEvent is dead code for villagers, there is no clean hook, confidence LOW; (2) re-asserting chosen offers after a vanilla level-up - updateTrades() appends two random trades and increaseMerchantCareer()/shouldIncreaseLevel() are private, so the strategy is "let the level rise, revert the offers list every throttled tick from the attachment", confidence MEDIUM. Both must be budgeted as real work, not one-liners. Other verified landmines with known fixes: setVillagerData() nulls offers on profession change; ResetProfession reverts a fresh 0-XP employee to unemployed (fix: set villagerXp to at least 1 at bind); AbstractVillager.getOffers() throws on the client and lazily fabricates two trades server-side (never call it - build MerchantOffers and setOffers()); the @EventBusSubscriber "bus" attribute is ignored by FML 4.0.43 (bus is chosen by the event type); 1.21.1 uses ItemInteractionResult useItemOn versus InteractionResult useWithoutItem (1.21.2+ tutorials are wrong).

**Correction to PROJECT.md:** "nothing worked" overstates it. The crash report is an early build. The jar currently in the test instance is a later build that loads fine and reaches gameplay - it was simply never validated end-to-end. Treat the prior work as "first GUI attempt crashed on load; later builds unverified", not "uniformly broken". The rebuild-from-scratch decision still stands.

## Key Findings

### Recommended Stack

Pin everything to the test instance exactly: Minecraft 1.21.1 + NeoForge 21.1.248 + Temurin JDK 21, built with ModDevGradle 2.0.146 on the Gradle 9.2.1 wrapper, bootstrapped from the official MDK-1.21.1-ModDevGradle template (the wrapper jar cannot be authored as text). Compile against neo_version=21.1.248 but declare a runtime range of [21.1,) so a patch rollback does not brick the jar. Parchment 2024.11.17 for readable MC sources (dev-only). Delete the stale second-shift-0.1.0.jar from the test instance before the first run. Full detail in STACK.md.

**Core technologies:**
- NeoForge 21.1.248 + MC 1.21.1 - hard constraint, must match the test instance; every API in STACK.md was javap-verified against these exact jars
- ModDevGradle 2.0.146 / Gradle 9.2.1 - official 1.21.1 MDK pairing; simpler buildscript than NeoGradle, right for a single-version solo mod
- Java 21 (Temurin, already installed) - Mojang ships Java 21 for 1.21.x
- No supporting libraries - no Mixin, no Access Transformers, no JEI/Curios/owo-lib deps; every needed behaviour is reachable via public NeoForge events and APIs (verified)

**Key API surface (all binary-verified):** DeferredRegister for menus/blocks/items/block-entities/attachments/components; IMenuTypeExtension.create + RegisterMenuScreensEvent; PayloadRegistrar + CustomPacketPayload + StreamCodec; AttachmentType.builder().serialize(CODEC).sync(STREAM_CODEC); MerchantOffer/MerchantOffers CODEC and STREAM_CODEC (do not hand-roll offer serialization); PoiTypes.forState + BuiltInRegistries.VILLAGER_PROFESSION for runtime profession derivation; LivingConversionEvent.Pre for conversion immunity; vanilla JSON recipes only (no custom RecipeType).

### Expected Features

Full landscape in FEATURES.md. The picker mechanic itself is not novel (Trade Picker already does full-pool selection). The differentiator is cost + gating + fiction: choosing trades costs four harvested villagers and is a progression system, not a QoL toggle, wrapped in HR/corporate flavour text (Intern to Principal tier titles - highest flavour-per-byte in the project).

**Must have (table stakes / v1):**
- Harvester gives a guaranteed 1 Soul Fragment; 4 Fragments craft into 1 Soul Block (vanilla shapeless recipe)
- Soul Altar block + job-site block on top + Soul Block insert opens the binding GUI
- Menu/Screen harness proven in isolation under runClient - the known failure point
- Profession derived from the job-site block via the POI registry (no hardcoded list - modded professions free)
- Name entry (EditBox) with a generated default; trade picker is "pick 2 of N" from materialized offers (roll server-side, show the concrete offer, persist the concrete MerchantOffer)
- Employee is a minecraft:villager + synced attachment; spawn with villagerXp at least 1
- Promotion ritual: vanilla XP unlocks the tier, the altar picks the trades, vanilla auto-appended trades are reverted
- Traits: no zombification, no lightning-witch, no breeding; Soul Block (+slime) drop on non-Harvester death, clean recovery on Harvester death
- Restock solution (mod-owned POI-independent timer - without it the mod feels broken after ~15 trades); must not touch non-employee villagers
- Profession-loss immunity (a 0-XP employee must never silently go unemployed)
- Polish treated as baseline: creative tab, complete en_us.json, models/textures, recipe advancements, ritual sounds/particles, a "ready for promotion" signal, altar GUI shows employee state, visible custom name, config + config screen, every invalid state handled, zero side-effects on wild villagers

**Should have (v1.x, after validation):**
- Read-only 5-tier career-path preview at bind time (the planning fantasy)
- Employee roster / directory
- Custom render layer for employees (soul overlay / HR badge)

**Defer (v2+):** Patchouli guide book, JEI plugin for the ritual, employee "departments", deeper HR sim, any public release.

**Explicit anti-features:** free-form trade editor, custom entity type, enchantment enumeration for book trades, trade rerolling/cycling (contradicts the whole design), auto-trading, breeding, employee upkeep/wages, villager AI overhaul, suppressing iron-golem spawning.

### Architecture Approach

Single Gradle module, single source set. Client-only code isolated by package (client/) plus annotation (@EventBusSubscriber value = Dist.CLIENT), never a source-set split. Three layers: client (screens/widgets, RegisterMenuScreensEvent), common (menu, registry holders, payloads, block/BE/item, employee domain, trade cache), and vanilla/NeoForge systems that are hooked, never forked. Full structure and step-by-step data flows in ARCHITECTURE.md.

**Major components:**
1. SecondShift (@Mod) - the single wiring point: attaches every DeferredRegister to the mod bus in one visible block (the anti-crash measure), registers payloads and config
2. registry/Mod* - flat package of pure DeferredHolder constants, class-loaded from the constructor
3. employee/ - the domain core: EmployeeData (record + CODEC + STREAM_CODEC = the entire employee identity), EmployeeManager (bind/promote/rebuildOffers/isEmployee), four game-bus handlers (traits, death-drops, level-up + offer-revert, restock)
4. trade/ - TradePoolCache (populated from VillagerTradesEvent to capture modded trades), rollCandidates (materialize offers), ProfessionResolver (POI blockstate to profession)
5. menu/BindingAltarMenu (common, server-authoritative candidate list) + client/screen/BindingAltarScreen (mirror built from the open-data buffer) + network/SelectTradesPayload (client to server: indices + name, re-validated server-side)
6. content/ - SoulAltarBlock (EntityBlock), SoulAltarBlockEntity (persists only the inserted Soul Block ItemStack, nothing employee-related), HarvesterItem

**Key patterns:** attachment-as-identity + handlers-as-behaviour; revert-don't-prevent for the vanilla level-up; server-authoritative menu with client mirror from open-data; recompute-over-persist for the altar.

### Critical Pitfalls

Top items from PITFALLS.md (12 total, plus tech-debt, UX, and "looks done but isn't" checklists):

1. Unbound MenuType / DeferredRegister (the prior crash) - .register(modBus) missing, or the holder class referenced only from client code so its static init runs after RegisterEvent. Silent at compile and at server startup; only runClient catches it. Avoid: register every DeferredRegister in the @Mod constructor as one block; add an FMLLoadCompleteEvent self-check that throws a named list of unbound entries; keep forge.logging.markers=REGISTRIES on.
2. The @EventBusSubscriber "bus" attribute is ignored in NeoForge 21.1 - FML 4.0.43 AutomaticEventSubscriber reads only "value" (Dist) and "modid"; the bus is chosen by whether each @SubscribeEvent method event type implements IModBusEvent. Do not reason about "bus" on the annotation; grep debug.log for the "Subscribing @EventBusSubscriber class ... to the game/mod event bus" line each launch. Bus choice is still manual for DeferredRegister.register (mod bus) and hand-registered listeners.
3. AbstractVillager.getOffers() throws on the client and fabricates two random trades on first server call (lazy updateTrades()). Never call it. Build a MerchantOffers and install with setOffers(...); send offers to the client explicitly via MerchantOffers.STREAM_CODEC.
4. Villager.setVillagerData() nulls offers when the profession changes - re-arms the lazy 2-trade path. Enforce order: setVillagerData, then refreshBrain, then setVillagerXp, then setOffers LAST, then persist to the attachment. VillagerData.setProfession/setLevel return a new record - reassign.
5. ResetProfession reverts a fresh employee to unemployed when JOB_SITE memory is absent AND villagerXp == 0 AND level <= 1 (and profession not none/nitwit). This mod deliberately spawns employees with no job site. Fix: set villagerXp to at least 1 at bind (breaks the cheapest clause, semantically honest). Do not rely on a per-tick watchdog as the primary fix; keep it only as a detector.
6. BabyEntitySpawnEvent does not fire for villager breeding - villagers breed via the VillagerMakeLove brain behaviour. Break the preconditions instead (clear BREED_TARGET, no bed/HOME, no food in inventory); optionally cancel FinalizeSpawnEvent for a nearby baby villager (but NeoForge issue 1606: cancelling leaves beds marked occupied). Confidence LOW - dedicated spike.
7. Level-up appends two random trades via updateTrades() (called from increaseMerchantCareer()); restock does NOT re-roll trades. shouldIncreaseLevel()/increaseProfessionLevelOnUpdate are private. Treat the villager live MerchantOffers as a projection of the attachment; reconcile by full replacement (not index-pruning) after any level-up detected via a throttled EntityTickEvent.Post watching getVillagerData().getLevel(). Confidence MEDIUM - dedicated spike.
8. ItemListing.getOffer is nullable, random, and needs a real server-side entity (treasure maps, biome-typed emeralds, and some books return null; map listings cast trader.level() to ServerLevel). Roll candidates once against the real villager, store them, commit by index - never re-roll on confirm. Modded professions may be absent from VillagerTrades.TRADES entirely (trades added via event) - handle as an empty candidate list, not a crash.
9. Client classes / Minecraft.getInstance() leaking into common code - invisible in single-player (shared JVM). This project only tests single-player, so this entire bug class is invisible to the default loop. Mitigate with one gradlew runServer per phase and strict client/ package discipline.
10. 1.21.1 uses ItemInteractionResult useItemOn versus InteractionResult useWithoutItem - 1.21.2 unified these; nearly all tutorials and training data are wrong for 1.21.1. The altar needs both (insert Soul Block is useItemOn, open menu empty-handed is useWithoutItem). Always @Override.
11. Attachments: persistence, sync, and copy are three separate opt-ins - no .serialize(CODEC) means silent loss on save/load; .sync exists in 21.1.248 (verified) but the 1.21.1 docs page is stale and says otherwise; sync resends the whole value (keep EmployeeData small); getData() on a wild villager creates the attachment - always guard with hasData(). Add an EmployeeData version field before first use.
12. Trusting client-sent selections - bounds-check indices against the server-stored candidate array; picksRemaining authoritative server-side; stillValid() must actually re-check altar proximity and BE, never return true.

Also from the checklists: pack.mcmeta needs pack_format 48 (draft shipped 34); 1.21 data folders are singular (data/ns/recipe/, loot_table/, advancement/); every custom block needs blockstate + block model + item model + loot table; en_us.json must include menu titles, creative-tab title, and death messages.

## Implications for Roadmap

ARCHITECTURE.md already defines a 13-slice dependency-ordered build sequence, each slice testable in-game. The suggested phases group those slices. Slice 4 (menu harness) is a hard gate - nothing proceeds until it opens under runClient without crashing.

### Phase 1: Skeleton + feedback loop
**Rationale:** The prior draft failed because the build pipeline could not detect a broken registration. Fix the loop before writing any feature. (ARCHITECTURE Slice 0; PITFALLS Phase 1.)
**Delivers:** MDK bootstrap pinned to 21.1.248 / MDG 2.0.146 / Gradle 9.2.1; @Mod class; neoforge.mods.toml in src/main/templates/; empty en_us.json; pack.mcmeta format 48; runClient reaches main menu and runServer starts; scripted stale-jar delete + copy; isBound() startup self-check scaffold; the client/ package rule; the debug.log bus-assignment grep habit.
**Addresses:** "compiles against 21.1.248 and loads without crashing".
**Avoids:** Pitfalls 1, 2, 9 and toolchain/jar-collision debt.

### Phase 2: Items, Harvester, Soul Altar block
**Rationale:** Zero GUI risk; unblocks the economy and the altar. (Slices 1-3.)
**Delivers:** Harvester / Soul Fragment / Soul Block items + creative tab + models/textures; vanilla JSON recipes + advancements; DeathDropHandler for the guaranteed 1-Fragment Harvester kill; SoulAltarBlock (EntityBlock) + SoulAltarBlockEntity (1-slot, save/load + getUpdateTag/getUpdatePacket); Soul Block insert via useItemOn, break drops the slot.
**Implements:** content/, registry/ModItems/ModBlocks/ModBlockEntities.
**Avoids:** Pitfall 10 (ItemInteractionResult - both interaction methods), the asset/lang/loot/pack_format checklist, the custom-RecipeType anti-pattern.

### Phase 3: Menu/Screen harness - HARD GATE
**Rationale:** The single highest-risk area; isolate it with an empty screen before any trade logic. All three research files independently demand this. (Slice 4.)
**Delivers:** ModMenus + MENUS.register(modBus); BindingAltarMenu (both constructors, quickMoveStack, a real stillValid); client/ClientModBusEvents with RegisterMenuScreensEvent to an empty BindingAltarScreen; altar useWithoutItem to serverPlayer.openMenu(SimpleMenuProvider, writeBlockPos); FMLCommonSetupEvent guardrail log. Empty screen opens under runClient with no crash; runServer clean.
**Avoids:** Pitfalls 1 (the prior crash), 9, 12 (the menu trust boundary is established here).
**Research flag:** NO deeper research - STACK.md section 2 has the exact verified pattern. This phase is execution discipline, not investigation.

### Phase 4: Employee attachment + spawn (fixed profession, no picker)
**Rationale:** The attachment is the entire employee identity and a hard prerequisite for the GUI and every trait. (Slice 5.)
**Delivers:** ModAttachments; EmployeeData record + CODEC + STREAM_CODEC + .sync + version field; EmployeeManager.bind with a hardcoded profession and default offers; a temporary Confirm button sending SelectTradesPayload (name only) so the server spawns a named villager with villagerXp at least 1 and the attachment.
**Avoids:** Pitfalls 3, 4, 11 (write the server-only offers helper here; never call getOffers()); establishes the bind order.
**Research flag:** LIGHT - verify empirically whether AttachmentType.Builder.sync actually fires for entities in 21.1.248 (STACK gap 2; API exists, 1.21.1 docs contradict it). Fallback is a manual clientbound payload. Budget half a day.

### Phase 5: Profession resolution + trade pool + trade picker
**Rationale:** The core value. Builds on the attachment and the proven harness. (Slices 6-8.)
**Delivers:** ProfessionResolver (POI blockstate to profession, filter none/nitwit); TradePoolHandler (VillagerTradesEvent to TradePoolCache); rollCandidates materializing offers against a real server villager; TradePickerWidget (toggle, enforce max 2) + NameEntryWidget; SelectTradesPayload carries indices + name; the server validates indices against its own candidate list, sanitizes the name, builds MerchantOffers, and consumes the Soul Block; pool-size 2-or-less auto-select-and-lock path.
**Addresses:** "trade choices from the real vanilla pool", binding flow, name entry.
**Avoids:** Pitfalls 8 (roll-once-store-reuse, null-check, modded-profession empty pool), 12 (server-side index validation).
**Research flag:** SPIKE inside the phase - confirm ItemListing.getOffer(realVillager, random) has no side effects and handles nulls across every profession by every tier (STACK section 8). Write a GameTest or loop asserting no exception, no null leak, preview offer equals committed offer.

### Phase 6: Employee traits
**Rationale:** Cheap once the attachment exists; parallelizable with Phase 5. (Slice 9.)
**Delivers:** TraitHandlers cancel LivingConversionEvent.Pre (one handler covers zombie and lightning-witch); DeathDropHandler extended so employee death drops the Soul Block plus slime and Harvester death is a clean recovery; breeding suppression.
**Avoids:** Pitfalls 5 (ResetProfession - the villagerXp-at-least-1 fix lives here too, with the walk-away and relog acceptance test), 6 (breeding).
**Research flag:** SPIKE - breeding suppression has no verified hook (BabyEntitySpawnEvent is dead for villagers), confidence LOW. Budget real time: try precondition-breaking (BREED_TARGET, bed, food) first, FinalizeSpawnEvent second, a targeted AT or Mixin only as a documented last resort.

### Phase 7: Level-up detection + offer revert + promotion ritual
**Rationale:** Highest-risk slice after the harness; needs the picker (Phase 5) and profession resolution. (Slice 10.)
**Delivers:** LevelUpHandler (throttled EntityTickEvent.Post, hasData guard) reverts vanilla-appended offers to EmployeeData.chosenOffers by full replacement; detects mechanical level above tier to set pendingLevel plus notify plus particles; BindingAltarMenu gains MODE_PROMOTE plus target UUID; altar right-click scans nearby employees with pendingLevel above tier; apply merges offers and sets tier to pendingLevel.
**Addresses:** the leveling requirement.
**Avoids:** Pitfalls 7 (revert-don't-prevent; no index-pruning; do not hold the level down - anti-pattern 5), 4 (bind order again).
**Research flag:** SPIKE - confirm the revert beats vanilla append without per-tick offer thrash or lost restock state, and that the one-tick window where unchosen offers exist is never rendered (STACK gap 3, confidence MEDIUM).

### Phase 8: Restock (mod-owned timer)
**Rationale:** Independent workstream once the attachment exists; without it the mod feels broken after ~15 trades. (Slice 11.)
**Delivers:** RestockHandler (server tick, staggered by tickCount): per loaded employee, if gameTime minus lastRestockTick is at least configInterval then restock offers and update the anchor; clamp catch-up for long-unloaded employees to one restock. Config value for the interval.
**Avoids:** the FEATURES "Restock Question" (the number-one "mod is broken" complaint class); must not touch non-employee villagers; do not mutate offers while villager.isTrading().

### Phase 9: Polish + config + invalid states
**Rationale:** Mostly parallel; attaches to whichever feature each item targets. (Slice 12.)
**Delivers:** sounds/particles on harvest/bind/promote; complete en_us.json + HR tier titles (Intern to Principal); ModConfigSpec + IConfigScreenFactory (fragment drop count, restock interval, each immunity toggle); altar GUI shows bound-employee state; promotion-ready signal finalized; every invalid state (no job block / no soul block / unmapped block / empty pool / pool 2-or-less) with a themed Component.translatable message; recipe advancements; always-visible employee name.
**Avoids:** UX pitfalls (untranslated keys, silent failures) and the "looks done but isn't" checklist.

### Phase Ordering Rationale

- Loop before features. Phase 1 exists purely because the prior draft pipeline could not catch its own bug. runClient + one runServer per phase + the isBound() self-check are non-negotiable infrastructure.
- Strict chain: 1, then 2, then 3 (gate), then 4, then 5, then 7. Phase 3 gates roughly 60 percent of the feature list. Phase 7 needs both Phase 5 and profession resolution.
- Parallel once Phase 4 lands: traits (6), restock (8), and the trade chain (5 then 7) are independent workstreams. A solo dev still does them in sequence but can reorder 6 and 8 freely.
- Zero-GUI-risk work first (Phase 2) so there is a working, testable mod before touching the known failure area.
- Pitfall clustering: registration and dist pitfalls (1, 2, 9) all land in Phases 1-3; villager-internals pitfalls (3-8) cluster in Phases 4-7 where the triggering code is written.

### Research Flags

Phases needing a spike budgeted during planning:
- Phase 4: AttachmentType.Builder.sync behaviour for entities in 21.1.248 - LIGHT verification, about half a day, clear fallback.
- Phase 6: breeding suppression - LOW confidence, no known-good approach, budget generously.
- Phase 7: offer re-assertion after vanilla level-up - MEDIUM confidence; the revert strategy is sound but unproven against timing and thrash.
- Phase 5: ItemListing.getOffer side effects and null handling across all professions - a contained spike, write it as a test.

Phases with standard, fully-documented patterns (skip --research-phase):
- Phase 1 - STACK.md is a complete bootstrap spec.
- Phase 2 - vanilla blocks/items/recipes, no novelty.
- Phase 3 - STACK.md section 2 has the exact verified registration and screen pattern; the risk is discipline, not knowledge.
- Phases 8 and 9 - ModConfigSpec, IConfigScreenFactory, lang and particles are boilerplate.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Every version read from live Maven metadata or the official MDK; every API signature javap-verified against the exact neoforge-21.1.248 jars and the 1.21.1 client jar on this machine |
| Features | MEDIUM-HIGH | Vanilla mechanics HIGH (Minecraft Wiki, corroborated); comparable-mod UX and player-expectation claims MEDIUM (vendor descriptions, community wikis) |
| Architecture | MEDIUM-HIGH | HIGH for structure and the common/client split (NeoForge docs + binary-verified API); MEDIUM for the level-up interception shape and the promotion-ritual UX (an open design question) |
| Pitfalls | HIGH | Most claims verified by decompiling the exact installed artifacts plus the prior draft jar, crash report, and runtime logs; issue-tracker items MEDIUM |

**Overall confidence:** HIGH - with three explicitly flagged verification spikes (breeding LOW, level-up offer revert MEDIUM, attachment entity-sync LIGHT).

### Gaps to Address

- Breeding suppression (Phase 6): no verified hook. Plan a spike; accept that a targeted AT or Mixin may be the pragmatic answer despite the STACK.md "no Mixin" default - document the exception if taken.
- Offer re-assertion after level-up (Phase 7): "revert every throttled tick" is unproven. Validate no offer thrash, no lost restock state, no player-visible unchosen trades. Fallback: a tighter throttle or event-driven detection.
- AttachmentType.sync for entities (Phase 4): the API exists but the 1.21.1 docs contradict it. Verify empirically; fallback is a manual clientbound payload (already in the design as a contingency).
- Whether VillagerTradesEvent is the only aggregation point for modded trades: no other was found, but a negative was not exhaustively proven. Low impact - cache from the event and accept.
- Open design questions for requirements (FEATURES.md section 6): does a bound employee stay put (the whole motivation collapses if not)? which restock design? pool 2-or-less behaviour (show and auto-confirm, or skip)? show-all-N versus rolled-subset in the picker? enchanted-book handling (recommend one materialized random book per slot)? vanilla-random versus vanilla-minimum prices (recommend random - the soul cost is the balance lever)? can an altar rebind or fire an employee? Resolve these during requirements definition, not roadmap.
- run/mods parity: runClient will not include owo-lib/accessories/wildcard (separate game dir). Treat runClient as the clean-room test and the CurseForge instance as the compatibility gate; drop the three jars into run/mods/ if parity is needed. Verify FML scans run/mods in Phase 1.

## Sources

### Primary (HIGH confidence)
- javap and bytecode against neoforge-21.1.248 client and universal jars, client-1.21.1-20240808.144430-srg.jar, and loader-4.0.43.jar on this machine - the exact binaries the test instance loads. Verified: VillagerTrades and ItemListing, Villager, AbstractVillager, ResetProfession, VillagerMakeLove, PoiTypes, VillagerProfession, MerchantOffer(s), BlockBehaviour, IMenuTypeExtension, RegisterMenuScreensEvent, AttachmentType and Builder, PayloadRegistrar, IPayloadContext, DeferredHolder (exact crash message), LivingConversionEvent.Pre, BabyEntitySpawnEvent, EventBusSubscriber and AutomaticEventSubscriber
- crash-2026-09-03_11.44.08-client.txt plus logs/2026-09-03-*.log.gz and debug-*.log.gz - the exact failure stack, plus proof that later draft builds loaded and reached gameplay; live "[Employee heal]", "hasJobSite=false", and "offersAfterEagerCall=2" evidence
- Instances/test/minecraftinstance.json - the instance is neoforge-21.1.248 on MC 1.21.1, FML 4.0.43
- Live Maven metadata (NeoForged, Gradle Plugin Portal, ParchmentMC) and the official MDK-1.21.1-ModDevGradle template - all versions
- docs.neoforged.net/docs/1.21.1/ source markdown - registries, menus, screens, networking payload, attachments, datacomponents, blockentities, recipes, sides, events; neoforged/NeoForge 1.21.1 patch files (Villager, Zombie, VillagerMakeLove)
- Mojang official 1.21.1 mappings (client.txt, sha 2244b6f0) - member names and signatures

### Secondary (MEDIUM confidence)
- Minecraft Wiki (Trading, Villager, Librarian, Farmer) - 2 trades per tier, pool sizes, XP thresholds 0/10/70/150/250, restock rules, conversion odds, iron golem triggers, profession-lock-after-first-trade
- Comparable mods: Trade Picker, Easy Villagers (plus issue 199), Trading Post (57M downloads), Guard Villagers, Occultism, Malum, Villager Names - feature landscape and restock-timer precedent
- NeoForge issues 1606 (cancelled breeding leaves beds occupied) and 2510 (synced attachments not re-sent on dimension change)
- neoforged.net MDG versus NeoGradle positioning

### Tertiary (LOW confidence / needs validation)
- Which NeoForge 21.1.x introduced AttachmentType.Builder.sync (present in 21.1.248; introducing version not located - irrelevant while pinned)
- Whether MDG runClient scans run/mods in the 1.21.1 MDK (expected; verify in Phase 1)
- Exact trigger point of increaseMerchantCareer() relative to trade completion (private; the reconcile-after approach is deliberately robust to not knowing)
- Breeding-suppression approaches - all candidates unverified

---
*Research completed: 2026-09-04*
*Ready for roadmap: yes*
