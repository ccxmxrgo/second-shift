# Pitfalls Research

**Domain:** Minecraft NeoForge 1.21.1 mod — villager data attachments, trade manipulation, custom block-entity GUI
**Researched:** 2026-09-04
**Confidence:** HIGH (most claims verified by decompiling the exact artifacts installed on this machine: `client-1.21.1-20240808.144430-srg.jar`, `neoforge-21.1.248`, `loader-4.0.43.jar` (FML), plus the prior draft jar `second-shift-0.1.0.jar` and its runtime logs)

---

## Evidence Base (read this first)

Findings below are not recollection. They were verified by:

| Source | What it proved |
|--------|----------------|
| `crash-reports/crash-2026-09-03_11.44.08-client.txt` | The exact failure stack: `MenuScreens.init` → `RegisterMenuScreensEvent` → unbound `ResourceKey[minecraft:menu / secondshift:binding_altar]` |
| `logs/2026-09-03-*.log.gz` + `logs/debug-*.log.gz` | Later builds of the draft **did** load and run. The mod was not uniformly broken; the menu registration was. Also captured live `[Employee heal] … re-applied profession … set xp to stop future reverts` and `hasJobSite=false` |
| `mods/second-shift-0.1.0.jar` (Sep 3 23:39 build — a *later, working* build than the crash) | The corrected registration pattern; the real 1.21.1 API names the draft compiled against |
| `loader-4.0.43.jar` bytecode (`AutomaticEventSubscriber`, `EventBusSubscriber`) | How NeoForge 21.1 actually chooses the event bus (surprising — see Pitfall 2) |
| `client-1.21.1-…-srg.jar` bytecode (`Villager`, `AbstractVillager`, `ResetProfession`, `BlockBehaviour`, `PoiType`, `VillagerProfession`, `MerchantOffer(s)`) | Exact 1.21.1 method signatures and vanilla behaviour |
| docs.neoforged.net (1.21.1 versioned docs) | Attachments, networking, menus |

**Important correction to the project brief:** the crash report is from an *early* build. The jar currently sitting in the test instance is a much later build that loads fine and reaches gameplay. Treat "nothing worked" as "the first GUI attempt crashed on load, and later builds were never validated end-to-end."

---

## Critical Pitfalls

### Pitfall 1: The known crash — a `DeferredRegister` that nothing ever fills, surfacing only on the client

**What goes wrong:**

```
java.lang.NullPointerException: Trying to access unbound value:
  ResourceKey[minecraft:menu / secondshift:binding_altar]
  … while dispatching net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
  at net.minecraft.client.gui.screens.MenuScreens.init(MenuScreens.java:78)
  at net.neoforged.neoforge.client.ClientHooks.initClientHooks(ClientHooks.java:1025)
  at net.minecraft.client.Minecraft.<init>(Minecraft.java:601)
```

`DeferredHolder.get()` on an entry that was never actually inserted into `BuiltInRegistries.MENU`. The `DeferredHolder` object exists (so the code compiles and the field is non-null), but its backing `Holder.Reference` was never bound to a value.

**Why it happens — the root-cause class:**

There are exactly three ways to produce an unbound `DeferredHolder` in NeoForge 21.1, and they are all *silent at compile time and at server startup*:

1. **`DeferredRegister.register(modEventBus)` was never called for that register.** This is the overwhelmingly common one. `DeferredRegister.create(...)` and `.register("name", supplier)` do nothing on their own — they only queue entries. The register must be attached to the mod event bus (normally in the `@Mod` constructor) so it can listen for `RegisterEvent`. NeoForge emits **no warning** for a `DeferredRegister` that is never attached. Adding a new registry class (`SSMenus`) and forgetting the one extra `.register(modEventBus)` line in the mod constructor reproduces this exactly, and produces the failure *only for menus* while items/blocks keep working — which matches the observed symptom precisely.

2. **The class holding the entries is never class-loaded before `RegisterEvent` fires.** The `.register("binding_altar", …)` calls live in `SSMenus`'s static initializer. If nothing touches `SSMenus` before registry events run, the static init never runs and the register is empty. Attaching the register in the mod constructor via `SSMenus.MENUS.register(bus)` implicitly class-loads it, which is why that idiom is safe; a reflective/config-driven "register everything" helper is not.

3. **Mismatched registry or namespace** — e.g. `DeferredRegister.create(Registries.MENU, …)` in one place and lookups against a different key, or a modid typo so the entry lands under a different namespace than the screen registration expects.

**What it is *not* (ruled out by bytecode inspection):**

- It is **not** "screen registered on the wrong bus." The crash message proves the handler ran (`Second Shift (secondshift) encountered an error while dispatching …`). A wrong-bus handler would never fire at all — you'd get a silent no-GUI, not an NPE.
- It is **not** `@EventBusSubscriber(bus = …)` misuse. See Pitfall 2 — FML 4.0.43 ignores that attribute entirely.

**The correct 1.21.1 pattern (verified against the draft's working build):**

```java
// SSMenus.java
public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(BuiltInRegistries.MENU, SecondShift.MODID);

public static final DeferredHolder<MenuType<?>, MenuType<BindingAltarMenu>> BINDING_ALTAR =
        MENUS.register("binding_altar",
                () -> IMenuTypeExtension.create((id, inv, buf) ->
                        new BindingAltarMenu(BINDING_ALTAR.get(), id, inv,
                                buf != null ? buf.readBlockPos() : BlockPos.ZERO)));

// SecondShift.java — @Mod class constructor(IEventBus modEventBus, ModContainer container)
SSBlocks.BLOCKS.register(modEventBus);
SSItems.ITEMS.register(modEventBus);
SSBlockEntities.BLOCK_ENTITIES.register(modEventBus);
SSAttachments.ATTACHMENTS.register(modEventBus);
SSCreativeTabs.TABS.register(modEventBus);
SSMenus.MENUS.register(modEventBus);          // <-- the line whose absence causes the crash

// ClientSetup.java — @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
@SubscribeEvent
public static void onRegisterScreens(RegisterMenuScreensEvent event) {
    event.register(SSMenus.BINDING_ALTAR.get(), BindingAltarScreen::new);
}
```

Note `IMenuTypeExtension.create(...)` (NeoForge extension, gives you a `RegistryFriendlyByteBuf` for extra data) vs `new MenuType<>(MyMenu::new, FeatureFlags.DEFAULT_FLAGS)` (vanilla, no extra data). The buffer is `null` when the menu is constructed server-side — the draft's null check above is required, not optional.

**How to detect it before shipping a jar:**

| Method | Catches it? | Notes |
|--------|-------------|-------|
| `./gradlew build` | **No** | Compiles fine. `DeferredHolder.get()` is type-correct. |
| `./gradlew runData` | **No** | Datagen never constructs `Minecraft`, so `MenuScreens.init()` / `RegisterMenuScreensEvent` never fire. It *does* run registry events, so a self-check (below) works under `runData`. |
| `./gradlew runClient` | **Yes — reliably** | Identical code path: `Minecraft.<init>` → `ClientHooks.initClientHooks` → `MenuScreens.init()`. This is the single highest-value guard in the whole project. |
| Registration self-check | **Yes, with a readable message** | See below. |

Self-check — cheap, and it turns a cryptic NPE into a named list:

```java
@SubscribeEvent
static void onLoadComplete(FMLLoadCompleteEvent event) {
    event.enqueueWork(() -> {
        List<String> unbound = Stream.of(
                SSMenus.MENUS, SSBlocks.BLOCKS, SSItems.ITEMS,
                SSBlockEntities.BLOCK_ENTITIES, SSAttachments.ATTACHMENTS)
            .flatMap(dr -> dr.getEntries().stream())
            .filter(h -> !h.isBound())
            .map(h -> h.getId().toString())
            .toList();
        if (!unbound.isEmpty())
            throw new IllegalStateException("Unbound registry entries: " + unbound);
    });
}
```

`DeferredRegister#getEntries()` and `DeferredHolder#isBound()` both exist in 21.1. This fires on both client and dedicated server, and on `runData`.

**Warning signs:**
- A new registry class was added but the `@Mod` constructor diff has no matching `.register(modEventBus)` line.
- Items and blocks appear in-game but one specific feature is inert.
- Any use of `.get()` at *static-init* time rather than inside a supplier/lambda.

**Phase to address:** The very first phase after project skeleton. Do a "hello, menu" vertical slice — one block, one `MenuType`, one `AbstractContainerMenu` with only the player inventory, one `AbstractContainerScreen` — and get it opening under `runClient` **before** any mechanic exists. The PROJECT.md note "the trade-picker GUI must be built incrementally and tested in isolation" is exactly right; encode it as a phase gate.

---

### Pitfall 2: Believing `@EventBusSubscriber(bus = …)` controls the bus in NeoForge 21.1

**What goes wrong:** You spend debugging time on "wrong event bus" theories, or you copy a `bus = Bus.MOD` from a 1.20.x tutorial and assume it did something.

**Why it happens:** Nearly every tutorial and LLM answer from the 1.18–1.20 era says the bus is chosen by the annotation. In 1.21.1 it is not.

**Verified fact (decompiled `net/neoforged/fml/javafmlmod/AutomaticEventSubscriber.class` from `loader-4.0.43.jar`, the loader NeoForge 21.1.248 ships):**

- The annotation `net.neoforged.fml.common.EventBusSubscriber` does declare `Dist[] value()`, `String modid()`, **and** `Bus bus()`.
- `AutomaticEventSubscriber.inject(...)` reads only `value` and `modid` from the annotation data. **`bus` is never read.**
- Instead it reflects over `getDeclaredMethods()`, and for each `@SubscribeEvent` method partitions on `IModBusEvent.class.isAssignableFrom(parameterType)`:
  - all handlers are game-bus events → registers the whole class on `FMLLoader.getBindings().getGameBus()`, logging `Subscribing @EventBusSubscriber class {} to the game event bus`
  - all handlers are mod-bus events → registers on `ModContainer#getEventBus()`, logging `… to the mod event bus of mod {}`
  - mixed → logs `Found mix of game bus and mod bus listeners in @EventBusSubscriber class {}, registering them separately` and registers each method individually.
- It also hard-fails (`IllegalArgumentException`) on non-`static` `@SubscribeEvent` methods and on methods that do not take exactly one `Event` parameter.

The draft's own logs confirm this empirically: `ReapEvents` and `GooEvents` (both annotated with only `modid`) went to the **game** bus, while `ClientSetup` (annotated with `modid` + `Dist.CLIENT`, also no `bus`) went to the **mod** bus — purely because `RegisterMenuScreensEvent implements IModBusEvent`.

**How to avoid:** Do not reason about `bus` on `@EventBusSubscriber`. Do reason about it everywhere else — bus choice is still real and manual for:
- `DeferredRegister#register(IEventBus)` → **must** be the mod bus (constructor parameter).
- `modEventBus.addListener(...)` vs `NeoForge.EVENT_BUS.addListener(...)` when registering by hand.
- `ModContainer#registerConfig` and `registerExtensionPoint`.

**Warning signs:** A handler class where you can't say, from the event type alone, which bus it lands on. Grep `debug.log` for `Subscribing @EventBusSubscriber class` lines — the loader tells you the answer for every class, every launch.

**Phase to address:** Project skeleton / registration phase. Add a habit: after each launch, grep `logs/debug.log` for those three loader messages.

---

### Pitfall 3: `AbstractVillager#getOffers()` throws on the client, and silently fabricates random trades on the server

**What goes wrong:** Two distinct crashes/bugs from one method.

Decompiled `AbstractVillager#getOffers()` (1.21.1):

```java
public MerchantOffers getOffers() {
    if (this.level().isClientSide)
        throw new IllegalStateException("Cannot load Villager offers on the client");
    if (this.offers == null) { this.offers = new MerchantOffers(); this.updateTrades(); }
    return this.offers;
}
```

1. **Client-side call → hard `IllegalStateException`.** Any "shared" helper that reads offers and is reachable from a Screen, a client tick handler, or a client-side branch of a `PlayerInteractEvent` will crash the client.
2. **First server-side call lazily calls `updateTrades()`**, which (verified) does `VillagerTrades.TRADES.get(profession).get(level)` and `addOffersFromItemListings(offers, listings, 2)` — i.e. **appends two random vanilla trades**. Merely *looking* at a fresh employee's offers permanently gives it two trades you did not choose. The draft's own log line `[Employee bind] … offersAfterEagerCall=2` is this happening.

**How to avoid:**
- Guard every offers read with `if (!level.isClientSide)`, and never let a `Screen` reach one. Send the offer list to the client explicitly (`MerchantOffer.STREAM_CODEC` / `MerchantOffers.STREAM_CODEC` exist in 1.21.1 and take a `RegistryFriendlyByteBuf`).
- On bind, **never call `getOffers()` first**. Build a `MerchantOffers` yourself and install it with `Villager#setOffers(MerchantOffers)` (verified: a plain field assignment, no side effects). Then the lazy path never triggers.
- If you must clear trades, `setOffers(new MerchantOffers())` — do not `getOffers().clear()`.

**Warning signs:** `IllegalStateException: Cannot load Villager offers on the client` in `latest.log`. Employees that have "2 extra trades nobody picked." Trade counts that differ between the first and second time you open a villager.

**Phase to address:** The employee-binding phase, before any trade UI is layered on. Write the offers-installation helper once, server-only, and never call `getOffers()` outside it.

---

### Pitfall 4: `Villager#setVillagerData()` wipes offers when the profession changes

**What goes wrong:** The player picks trades, you then set the profession, and the trades vanish (replaced by two random ones the next time anything reads offers).

**Why it happens.** Decompiled `Villager#setVillagerData` (1.21.1):

```java
public void setVillagerData(VillagerData data) {
    VillagerData old = this.getVillagerData();
    if (old.getProfession() != data.getProfession())
        this.offers = null;                      // <-- silently discards all trades
    this.entityData.set(DATA_VILLAGER_DATA, data);
}
```

`offers = null` re-arms the lazy `updateTrades()` path from Pitfall 3. So the destructive combination is: choose trades → `setOffers(...)` → `setVillagerData(profession)` → offers gone → next read regenerates 2 random ones.

**How to avoid:** Enforce a strict order in the bind/level-up routine, and comment it:

1. `setVillagerData(data.setProfession(p).setLevel(n))`
2. `refreshBrain(serverLevel)` (rebuilds the brain for the new profession — the draft used this)
3. `setVillagerXp(...)` (see Pitfall 5)
4. `setOffers(chosenOffers)` **last**
5. Persist the chosen offers into the attachment so you can re-apply them idempotently

Also note `VillagerData#setProfession`/`setLevel` **return a new `VillagerData`** (record-like); calling them without reassigning is a no-op.

**Warning signs:** Trades correct immediately after binding but wrong after a relog, a level-up, or any profession touch-up. Offer count drifting to a multiple of 2.

**Phase to address:** Binding flow phase and, again, the leveling phase — the level-up ritual is the second place this order gets violated.

---

### Pitfall 5: Employees silently revert to unemployed — `ResetProfession` requires a claimed job-site POI

**What goes wrong:** A bound employee loses its profession (and therefore its trades) shortly after being created, or after a chunk reload.

**Why it happens.** Verified from `net/minecraft/world/entity/ai/behavior/ResetProfession.class` (1.21.1). It is a core villager brain behavior that triggers when **all** of:

- `MemoryModuleType.JOB_SITE` is **absent** (`BehaviorBuilder.Instance#absent(JOB_SITE)`)
- profession != `VillagerProfession.NONE`
- profession != `VillagerProfession.NITWIT`
- `villager.getVillagerXp() == 0`
- `villagerData.getLevel() <= 1`

…then it does `setVillagerData(data.setProfession(NONE))` + `refreshBrain(level)`. Because of Pitfall 4, that also **nulls the offers**.

This mod deliberately spawns employees with no job site (the altar keeps the job block; the employee doesn't claim a POI). The draft hit this in production — its logs show `hasJobSite=false` and a watchdog printing `[Employee heal] uuid=… re-applied profession=minecraft:librarian and set xp to stop future reverts`.

**How to avoid — in order of preference:**

1. **Set `villagerXp` > 0 at bind time.** This is the least invasive lever and it is what the draft converged on: the `getVillagerXp() == 0` clause is the cheapest of the five to break, and non-zero XP is semantically honest ("this employee has already worked"). Beware: XP also drives `shouldIncreaseLevel()`, so pick a value inside the current tier's band rather than an arbitrary number.
2. Also set level ≥ 2 where the design allows (breaks `getLevel() <= 1`).
3. Do **not** rely on a per-tick "heal" watchdog as the primary mechanism (the draft's `EntityTickEvent.Post` re-apply). It is a fine belt-and-braces safety net and a great *detector*, but as the primary fix it races the brain and produces visible flicker.
4. Do **not** fake a `JOB_SITE` memory pointing at the altar's job block — that claims a POI ticket and re-enables `WorkAtPoi`/`YieldJobSite`, dragging the employee toward the block and letting other villagers contest it.

**Warning signs:** Employee villager renders with no profession overlay. Trades empty after ~a few seconds. `getVillagerData().getProfession()` returns `minecraft:none` in a debug command. If you keep the draft's watchdog, log *when it fires* — a firing watchdog means the root cause is unfixed.

**Phase to address:** Employee traits / attachment phase. Add an in-game acceptance check: bind an employee, walk 200 blocks away, come back, relog, confirm profession + trades survive.

---

### Pitfall 6: `BabyEntitySpawnEvent` does not fire for villager breeding

**What goes wrong:** You cancel `BabyEntitySpawnEvent` to satisfy "employees cannot breed," it compiles, and villagers keep breeding. The draft's `GooEvents` subscribes to `BabyEntitySpawnEvent` — that handler is dead code for villagers.

**Why it happens:** `BabyEntitySpawnEvent` is fired from the `Animal`/`spawnChildFromBreeding` path. Villagers breed through the brain behavior `VillagerMakeLove#tryToGiveBirth`, which does not go through that path. NeoForge's 1.21.x patch to `VillagerMakeLove` adds only an `isAddedToLevel()` guard (so that a baby whose `FinalizeSpawnEvent` was cancelled doesn't count as a successful breed) — it does **not** add a baby-spawn hook. This has been a known Forge/NeoForge gap since at least 2020, and NeoForge issue #1606 (`[1.21.1] Cancelled villager breeding causes beds to be wrongly occupied`) is about the fallout.

**How to avoid — use a lever that actually exists on the villager brain path:**

- **Preferred:** erase `MemoryModuleType.BREED_TARGET` on employees (the draft already does this in its tick handler) *and* keep the employee out of the willing state. `VillagerMakeLove#checkExtraStartConditions` gates on `isBreedingPossible(villager)`; breaking that is more robust than reacting after the fact.
- **Also effective:** cancel `FinalizeSpawnEvent` for a baby villager whose spawn originates near/from employees — but note issue #1606: cancelling leaves beds marked occupied. Prefer preventing the attempt over cancelling the result.
- **Cheapest structural fix:** employees hold no food in their villager inventory, and you clear `MemoryModuleType.HOME`/bed claims — no bed, no breeding.

**Warning signs:** A baby villager exists near your employees. Your `BabyEntitySpawnEvent` handler's log line never appears (add one and confirm it *does* appear for cows before trusting it for villagers).

**Phase to address:** Employee traits phase. Acceptance test: two employees + a bed + bread on the floor + 5 minutes = no baby.

---

### Pitfall 7: Assuming restock re-randomizes trades (it doesn't) — the real re-roll is `updateTrades()`

**What goes wrong:** Teams defensively fight the wrong mechanic, or fail to defend against the right one.

**Verified from `Villager` bytecode (1.21.1):**

- `restock()` → `updateDemand()`, then for each existing `MerchantOffer` calls `resetUses()`, then `resendOffersToTradingPlayer()`, then bumps `lastRestockGameTime` / `numberOfRestocksToday`. **It does not add, remove, or re-roll offers.** Player-chosen trades survive restock unchanged. Restock does reset `specialPriceDiff`/demand-based pricing, which is desirable.
- `updateTrades()` is where offers are **appended**: it selects `VillagerTrades.TRADES.get(profession)` (or `VillagerTrades.EXPERIMENTAL_TRADES` when the `TRADE_REBALANCE` feature flag is enabled), takes `.get(level)`, and calls `addOffersFromItemListings(offers, listings, 2)` — two random listings.
- `updateTrades()` is called from exactly two places: `increaseMerchantCareer()` (i.e. **level-up**), and the lazy branch of `getOffers()` (Pitfall 3).

**So the real risk to "the player's chosen trades" is level-up, not restock.** `increaseMerchantCareer()` runs when accumulated `villagerXp` crosses a tier threshold during normal trading, and it immediately appends two random trades for the new tier — which is precisely the moment your design wants the player to *choose* instead.

**How to avoid:**
- Store the player's chosen `MerchantOffer` list in the attachment (`MerchantOffer.CODEC` and `MerchantOffers.CODEC` both exist in 1.21.1 — use them; do not hand-roll NBT).
- Treat the villager's live `MerchantOffers` as a **projection** of the attachment. After any event that can mutate it (level-up, profession change, load), re-apply from the attachment with `setOffers(...)`. This makes the system self-healing rather than order-dependent.
- Detect the level-up: `Villager#shouldIncreaseLevel()`/`increaseProfessionLevelOnUpdate` are private, so hook it externally — the draft's approach of watching `getVillagerData().getLevel()` in an `EntityTickEvent.Post` and reconciling is acceptable here.
- Do **not** try to suppress `updateTrades()`; reconcile after it.
- The draft's `GooEvents` uses `MerchantOffers.remove(int)` to prune unwanted appended trades. That works but is index-fragile — prefer full replacement from the attachment.

**Warning signs:** Trade count grows by exactly 2 after a level-up. A trade the player never chose appears. Trades correct in the altar UI but wrong in the villager's trade screen.

**Phase to address:** Leveling phase. Acceptance test: bind, choose 2 tier-1 trades, trade until level 2, confirm the tier-1 trades are unchanged and no tier-2 trades appeared before the ritual.

---

### Pitfall 8: `VillagerTrades.ItemListing#getOffer` can return `null`, is random, and needs a real entity in a real level

**What goes wrong:** NPE when generating trade candidates, or the offer the player selected in the UI is not the offer they receive.

**Verified:** the interface is exactly `MerchantOffer getOffer(Entity trader, RandomSource random)` — one method, nullable return by vanilla convention. Vanilla listings that return `null`: `TreasureMapForEmeralds` (no matching structure found nearby — it calls `ServerLevel#findNearestMapStructure`), enchanted-book listings under some conditions, and several `EmeraldsForVillagerTypeItem` variants when the villager biome type has no entry.

Two further consequences:
- **It is random.** Calling `getOffer` twice on the same listing yields different prices/enchantments. If the altar UI shows a preview generated at open time and the bind logic calls `getOffer` again at confirm time, the player gets something else.
- **It needs a real trader.** Passing `null` or a throwaway entity breaks the map/biome listings (they dereference `trader.level()` and cast to `ServerLevel`). Generate candidates against the actual villager you are about to bind, on the server, in a loaded chunk.

**How to avoid:**
- Null-check every `getOffer` result and skip the listing. Never assume `TRADES.get(profession).get(level)` yields N usable offers.
- **Generate once, store, reuse.** Roll the candidate `MerchantOffer[]` on the server when the ritual starts, keep it in the block entity / attachment, send it to the client for display, and commit *by index into that stored array* — never re-roll on confirm. (The draft's `TradePickerMenu` already carries a `MerchantOffer[] candidateOffers` field; keep that shape.)
- Be aware `VillagerTrades.TRADES` is `Map<VillagerProfession, Int2ObjectMap<ItemListing[]>>` and modded professions may be absent from it entirely (a mod may add a profession but supply trades via `VillagerTradesEvent` instead). Handle "profession present in registry but absent from `TRADES`" as an empty candidate list, not a crash.
- If you want to respect the 1.21 trade-rebalance experiment, mirror vanilla: check `level.enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE)` and read `VillagerTrades.EXPERIMENTAL_TRADES` instead. Otherwise document that you deliberately ignore it.

**Warning signs:** `NullPointerException` in the candidate-generation code with `TreasureMapForEmeralds` or `EmeraldsForVillagerTypeItem` on the stack. Cartographer/Librarian professions crashing where Farmer works. The confirmed trade's price differing from the previewed price.

**Phase to address:** Trade-candidate phase, before the picker UI. Write a `TradeCandidates` unit/GameTest that iterates **every** profession × every tier and asserts no exception and no nulls leak through.

---

### Pitfall 9: Client classes and `Minecraft.getInstance()` leaking into common code

**What goes wrong:** Works perfectly in single-player (the integrated server shares a JVM with the client), then `NoClassDefFoundError: net/minecraft/client/gui/screens/Screen` on a dedicated server — or, more insidiously, works everywhere because the class happens not to be loaded, until it is.

**Why it happens:** In single-player the client classes are always present, so dist mistakes never surface locally. This project's *only* test target is a single-player CurseForge instance, which means **this entire class of bug is invisible to the project's test loop.**

**1.21.1 specifics:**
- `DistExecutor` is deprecated/removed in NeoForge 21.x. The replacements are: put client code in a separate class annotated `@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)`, or guard with `FMLEnvironment.dist == Dist.CLIENT` / `!level.isClientSide` and keep the client type behind a separate class so it is never *linked* on the server.
- A method signature or field type mentioning a client class is enough to fail verification even if the code path is never taken. Guarding with an `if` does not help; the class must be in a different class file.
- `@OnlyIn(Dist.CLIENT)` exists but is for Mojang's own classes; don't use it as a substitute for structure.

**How to avoid:**
- Package discipline: everything under `com.cxmxrgo.secondshift.client` is client-only and is referenced from common code *never* — only via events. Enforce it with a build check (see below).
- Never call `Minecraft.getInstance()` outside that package.
- `Level#isClientSide` is a *logical* side check, not a physical dist check. Both are needed and they mean different things.

**Warning signs / detection:** Since the test instance can't catch it, add one of:
- `./gradlew runServer` once per phase — a dedicated server launch is the definitive test and costs ~30 seconds.
- A ArchUnit-style or simple Gradle check that greps compiled common packages for `net/minecraft/client/` references.

**Phase to address:** Skeleton phase (establish the package rule) and every GUI phase (re-verify with `runServer`). Given the project is explicitly single-player-only, a defensible alternative is to accept the risk and document it — but then be honest that the mod is not server-safe, rather than assuming it is.

---

### Pitfall 10: `ItemInteractionResult` vs `InteractionResult` — 1.21.1 is the odd one out

**What goes wrong:** You copy 1.21.2+/1.20.x block-interaction code and it either doesn't compile or, worse, compiles as an unrelated overload that is never called (so right-clicking the altar with an item in hand does nothing).

**Verified from `BlockBehaviour.class` in `client-1.21.1-20240808.144430-srg.jar`:**

```java
protected InteractionResult useWithoutItem(
        BlockState, Level, BlockPos, Player, BlockHitResult);

protected ItemInteractionResult useItemOn(
        ItemStack, BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult);
```

1.21.1 has **two** methods with **two different result types**. `ItemInteractionResult` has `SUCCESS`, `CONSUME`, `CONSUME_PARTIAL`, `FAIL`, `PASS_TO_DEFAULT_BLOCK_INTERACTION`, `SKIP_DEFAULT_BLOCK_INTERACTION`. Returning `PASS_TO_DEFAULT_BLOCK_INTERACTION` from the main hand is what makes `useWithoutItem` run afterwards.

1.21.2 unified everything into a sealed `InteractionResult` (absorbing `InteractionResultHolder` and `ItemInteractionResult`), and the pass-through now requires `InteractionResult.TRY_WITH_EMPTY_HAND`. **Every 1.21.2+ tutorial and most model training data is wrong for 1.21.1.**

For the Soul Altar this matters directly: inserting a Soul Block is an *item* interaction (`useItemOn`), while opening the menu empty-handed is `useWithoutItem`. The draft only overrode `useWithoutItem`.

**How to avoid:** Always add `@Override`. Java will reject a wrong signature. Pin the mapping in the code review checklist: "1.21.1 → `ItemInteractionResult useItemOn(...)`, `InteractionResult useWithoutItem(...)`."

**Warning signs:** Right-click with an item in hand does nothing while right-click with an empty hand works (or vice-versa). A method without `@Override` that you *think* is overriding something.

**Phase to address:** Soul Altar block phase.

---

### Pitfall 11: Data attachments — persistence, syncing, and copy semantics are three separate opt-ins

**What goes wrong:** The employee attachment is present at runtime, then gone after `/reload`, a relog, a dimension change, or is simply absent on the client so the renderer/GUI can't see it.

**Verified: `AttachmentType.Builder` in NeoForge 21.1.248 supports (from the draft's own bytecode, which links successfully at runtime):**

```java
AttachmentType.builder(EmployeeData::fresh)
        .serialize(EmployeeData.CODEC)                         // persistence to NBT
        .copyOnDeath()                                         // survive death/respawn copy
        .sync(ByteBufCodecs.fromCodec(EmployeeData.CODEC))     // client sync
        .build()
```

Registered on `NeoForgeRegistries.Keys.ATTACHMENT_TYPES` via `DeferredRegister.create(...)` — and, per Pitfall 1, that register must also be `.register(modEventBus)`'d.

The traps, individually:

| Trap | Reality in 21.1 |
|------|-----------------|
| "It persists by default" | **No.** Without `.serialize(...)` the attachment is runtime-only and vanishes on save/load. Silent. |
| "`.serialize(Codec)` just works" | Only if the `Codec` round-trips. A `Codec` that encodes fine but fails to decode makes the attachment silently reset to the default supplier on load. Test round-trip explicitly. |
| "It's on the client automatically" | **No.** The 1.21.1 docs page still says "you need to send a packet to the client yourself." That is out of date for 21.1.248, which *does* have `.sync(...)` — but `.sync` was added mid-21.1, so anything targeting an older 21.1.x won't have it. Since this project pins 21.1.248 exactly, `.sync` is available. |
| "sync means everyone sees it" | For an **entity** holder, `.sync` pushes to *players tracking that entity*. Out of tracking range → stale/absent. Use the `sync(predicate, streamCodec)` overload if you need to restrict recipients. |
| "sync sends deltas" | No — the `StreamCodec` overloads resend the **entire** attachment each time, discarding whatever was on the client. Keep `EmployeeData` small. |
| "`copyOnDeath` covers entities" | `copyOnDeath` is documented around **player** death/respawn (`PlayerEvent.Clone`). Employees are villagers; when a villager dies it is gone, so the relevant question is instead: does the attachment survive **dimension change** and **chunk unload/reload**? There is a known open NeoForge issue (#2510) about synced attachments on players not being re-sent on dimension change. |
| "`getData` is safe to call anywhere" | `IAttachmentHolder#getData(type)` **creates** the attachment from the default supplier if absent — so `getData(...)` on a plain wild villager silently marks it as having employee data. Use `hasData(...)` as the predicate, always. The draft does this correctly. |

**Warning signs:** Employee identity/trades reset after relog. Client-side name tag or renderer shows default values. `hasData` returning true for villagers you never bound (means someone called `getData` as a check).

**Phase to address:** Attachment phase. Acceptance test matrix: save+quit+reload, chunk unload (walk 300 blocks and back), nether portal round-trip, and (if multiplayer is ever in scope) a second client joining.

---

### Pitfall 12: Trusting client-sent selections in the menu

**What goes wrong:** A modified client (or a bugged one) sends a trade index outside the candidate array → `ArrayIndexOutOfBoundsException` server-side, or picks a trade it wasn't offered.

**Relevant to this design specifically:** the draft avoided custom networking entirely by overriding `AbstractContainerMenu#clicked(int slotId, int button, ClickType, Player)` in `TradePickerMenu` and treating slot clicks as the client→server channel. That is a legitimate and *simpler* choice than a custom payload — vanilla's `ServerboundContainerClickPacket`/`ServerboundContainerButtonClickPacket` already handle the transport, ordering, and main-thread dispatch for you. But it moves all validation onto you:

- `slotId` arrives unvalidated. Bounds-check against your candidate array before indexing.
- `clicked()` runs on **both** sides. Gate the state mutation on `!player.level().isClientSide` (or `player instanceof ServerPlayer`).
- `stillValid(Player)` must actually verify the player is still near the altar / still owns the employee. The draft has `stillValid` — make sure it isn't `return true`.
- `picksRemaining` must be authoritative on the server, decremented server-side, never trusted from the client.

**If you do add a custom payload instead** (1.21.1 pattern, verified from docs):

```java
@SubscribeEvent  // mod bus (RegisterPayloadHandlersEvent implements IModBusEvent)
public static void register(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar("1");   // version string, not optional-ness
    registrar.playToServer(PickTrade.TYPE, PickTrade.STREAM_CODEC, PickTradeHandler::handle);
}
```

- The `"1"` is a **version**; a mismatch between client and server disconnects. `registrar.optional()` is what makes a payload non-mandatory.
- Handlers default to the **main thread** in 21.1 (`registrar.executesOn(HandlerThread.NETWORK)` opts out). Use `context.enqueueWork(...)` if you switch to NETWORK.
- Exceptions inside an `enqueueWork` `CompletableFuture` are **swallowed** unless you chain `.exceptionally(...)`. This is a documented footgun and a classic source of "the button does nothing and there's nothing in the log."
- `StreamCodec.composite(...)` argument order is codec, getter, codec, getter, …, constructor. Getting the order wrong compiles (generics erase) and fails at runtime with a corrupt read.

**Warning signs:** `ArrayIndexOutOfBoundsException` in a menu class. A button that does nothing with no log output. Desyncs where the client shows a pick consumed but the server doesn't.

**Phase to address:** Trade-picker phase.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Hand-write JSON models/blockstates/loot tables/recipes instead of datagen | Faster for the first 3 items | Every rename becomes a silent purple-and-black cube or a no-drop block; no compile-time link to the registry name | Acceptable for ≤ ~6 assets in a personal mod; add `runData` the moment you rename anything |
| Per-tick "watchdog" that re-applies profession/trades (the draft's `[Employee watch]`) | Papers over Pitfalls 4/5/7 instantly | Masks the root cause forever; per-tick cost on every villager; visible flicker; spams logs | As a *detector* that logs when it fires — never as the fix |
| Reading `VillagerTrades.TRADES` directly instead of via a profession-agnostic helper | Trivially simple | Breaks for modded professions that use `VillagerTradesEvent`, and ignores `EXPERIMENTAL_TRADES` | Fine for MVP if you handle "profession missing from TRADES" as empty, not as a crash |
| Using slot clicks (`clicked()` override) as the client→server channel instead of a payload | Zero networking code | Every interaction is expressed as a fake inventory click; awkward for non-slot UI (name entry, tier buttons); all validation is manual | Good choice for a grid-of-trades picker; switch to a payload as soon as you need non-slot inputs |
| `versionRange = "[21.1.0,)"` in `neoforge.mods.toml` | Never blocks a launch | Silently allows loading against a NeoForge where an API you use (e.g. `AttachmentType.Builder#sync`) doesn't exist yet | Acceptable here because the target instance is pinned to 21.1.248; if you ever share the jar, tighten the lower bound to the version that introduced `.sync` |
| Skipping `runClient` and testing only by copying jars to the CurseForge instance | Matches the "real" environment | Turns a 20-second feedback loop into a 2-minute one, and loses the debugger; this is the loop that let the menu crash ship | Never — `runClient` should be the default loop, the CurseForge instance the integration gate |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Vanilla `Villager` brain | Overriding behaviour by fighting it every tick | Break the brain behaviour's *preconditions* (`ResetProfession`: XP > 0; `VillagerMakeLove`: no `BREED_TARGET`, no bed, no food). Call `refreshBrain(serverLevel)` after any `setVillagerData`. |
| `MerchantOffers` ↔ trade screen | Mutating `villager.getOffers()` while a player has the trade screen open | Vanilla only pushes offers to the client via `resendOffersToTradingPlayer()` / `Player#sendMerchantOffers`. Mutate offers only when `!villager.isTrading()`, or re-send explicitly afterwards. |
| POI registry → profession | Hardcoding a block→profession table | `BuiltInRegistries.POINT_OF_INTEREST_TYPE.holders()` → find the holder whose `PoiType#is(BlockState)` (or `matchingStates().contains(state)`) matches, then scan `BuiltInRegistries.VILLAGER_PROFESSION` for a profession whose `heldJobSite()` predicate accepts that holder. Both accessor names verified against 1.21.1: `PoiType#matchingStates()`, `PoiType#is(BlockState)`, `VillagerProfession#heldJobSite()`, `#acquirableJobSite()`. |
| Registry timing | Doing the POI scan during mod construction or `FMLCommonSetupEvent` | Both registries are `BuiltInRegistries` (frozen after registry events), so `FMLCommonSetupEvent` onwards is safe — but do the lookup **lazily at interaction time** anyway, so modded professions registered by any mod are visible and so a `BlockState` from the world is available. Note: in 1.21.1 `VillagerProfession` is a plain record in a code-driven `DefaultedRegistry` — it is **not** datapack-driven, so no `RegistryAccess`/`HolderLookup` plumbing is needed. |
| owo-lib / accessories in the test instance | Assuming mixin conflicts | Both apply mixins to `Minecraft` (visible in the crash report's transformer list) but neither touches `Villager`, `MenuScreens`, or the registry system. Low risk. Their presence *does* mean crash reports have long transformer annotations — read past them. |
| `run/mods` vs the CurseForge instance | Expecting `runClient` to include owo-lib/accessories/wildcard | It won't — separate game directory. FML scans `<gameDir>/mods`, so drop the three jars into `run/mods/` if you want parity. Otherwise treat `runClient` as the clean-room test and the CurseForge instance as the compatibility gate. |

---

## Performance Traps

Scale here is "how many employees in one world," not users.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Per-tick reconciliation on every villager (`EntityTickEvent.Post`) | TPS dip in a villager-dense area; the draft's log shows ~30 employees each logging every 10 ticks | Early-out on `hasData(EMPLOYEE)` **first**, before any other work; reconcile on a slow tick (every 100 ticks) or event-driven, not every tick | ~50+ villagers in loaded chunks |
| Logging inside the tick handler | 300 KB `debug.log` per session (observed in the draft: `[Employee watch] … tick=10/20/30…`) | Debug logging behind a config flag, off by default; never log per-entity per-tick | Immediately |
| Rebuilding the POI→profession map on every altar interaction | Micro-stutter on right-click | Compute once into a `Map<BlockState, VillagerProfession>` cached after registries freeze, invalidated never (built-in registries don't change at runtime) | Noticeable with many POI types from large modpacks |
| Regenerating trade candidates on every GUI tick/redraw | Prices flicker; server CPU | Generate once at ritual start, store, reuse (also required for correctness — Pitfall 8) | Immediately visible |

---

## Security Mistakes

Single-player is the stated target, so "security" here means "input validation and dupe prevention."

| Mistake | Risk | Prevention |
|---------|------|------------|
| Trusting `slotId` / button index from `clicked()` or a payload | Server-side `ArrayIndexOutOfBoundsException`; selecting a trade that wasn't offered | Bounds-check against the server-stored candidate array; treat out-of-range as a no-op, not a crash |
| Mutating `picksRemaining` client-side | Unlimited trade picks | Authoritative counter on the server / in the block entity |
| Not implementing `stillValid()` properly on the altar menu | Menu stays live after the altar is broken → item duplication or actions on a dead block entity | `stillValid` must re-check the block entity still exists, is the right type, and the player is within range (vanilla helper: `AbstractContainerMenu.stillValid(ContainerLevelAccess, Player, Block)`) |
| Dropping the Soul Block on death *and* letting the employee be re-harvested | Duplicating Soul Blocks | Make the drop conditional on attachment state and clear/mark the attachment atomically in `LivingDeathEvent` before spawning the item |
| `quickMoveStack` returning the wrong thing | Classic infinite-loop / item-dupe in custom menus | Implement it fully or return `ItemStack.EMPTY`; never leave the default |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Untranslated keys leaking into the UI | Player sees `menu.secondshift.binding_altar` as a window title | The draft's `en_us.json` has `menu.secondshift.trade_picker` but **no** `menu.secondshift.binding_altar` — exactly this bug. Generate lang from datagen, or add a startup check that every registered object's descriptionId resolves |
| Preview price ≠ received price | Player picks a cheap enchanted book, gets an expensive one | Roll once, store, commit by index (Pitfall 8) |
| Silent failure when the job block isn't a valid POI | Right-click does nothing, no feedback | The draft got this right (`message.secondshift.not_a_job_block` etc.) — keep the pattern: every rejected interaction sends a themed `Component.translatable` message |
| No visual distinction between employees and wild villagers | Player can't tell which villager is bound; harvests the wrong one | The draft added an `EmployeeAwareProfessionLayer` renderer + `employee.png`. This requires the attachment to be **synced** to the client (Pitfall 11) |
| Trades chosen but the villager still shows old trades until relog | Feels broken | Re-send offers after mutation; don't mutate while `villager.isTrading()` |

---

## "Looks Done But Isn't" Checklist

- [ ] **Every `DeferredRegister`:** often missing `.register(modEventBus)` in the `@Mod` constructor — verify with the `isBound()` self-check from Pitfall 1, and count register-classes vs `.register(` calls in the constructor
- [ ] **Every `MenuType`:** often missing a `RegisterMenuScreensEvent` entry, or has one for a type that never registered — verify by opening the GUI under `./gradlew runClient`
- [ ] **Every custom block:** often missing one of blockstate JSON / block model / item model / **loot table** — verify the block drops itself when broken and doesn't render as a purple cube
- [ ] **Data folder names:** 1.21 uses **singular** `data/<ns>/loot_table/`, `data/<ns>/recipe/`, `data/<ns>/advancement/`, `data/<ns>/tags/block/` — plural (1.20-style) folders are silently ignored. The draft's jar uses the correct singular form; don't regress it
- [ ] **`pack.mcmeta`:** the draft ships `pack_format: 34` (1.20.4) against a 1.21.1 game. Mod resources still load, so it fails silently — use **48** for 1.21.1
- [ ] **`en_us.json`:** often missing menu titles, creative-tab title, and death-message keys — verify by launching with the game language set to a locale you didn't write, so every key falls back visibly
- [ ] **Attachment:** often missing `.serialize(...)` — verify by save-quit-reload, not just by relogging in the same session
- [ ] **Attachment on the client:** often missing `.sync(...)` — verify anything that renders or displays attachment data
- [ ] **Employee traits:** each of "no zombification / no lightning conversion / no breeding" needs its own acceptance test; `BabyEntitySpawnEvent` in particular is dead code for villagers (Pitfall 6)
- [ ] **Dedicated-server safety:** often untested because the loop is single-player — one `./gradlew runServer` per phase
- [ ] **`neoforge.mods.toml`:** must be at `META-INF/neoforge.mods.toml` (not `META-INF/mods.toml`), `loaderVersion = "[4,)"`, and the `neoforge` dependency range must include 21.1.248

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Unbound registry entry (Pitfall 1) | **LOW** | Add the missing `.register(modEventBus)` line. No data migration. Add the `isBound()` self-check so it can't recur |
| Client class on server (Pitfall 9) | **LOW–MEDIUM** | Move the offending code to a `client` package + `Dist.CLIENT` subscriber. Cost rises with how deeply the client type leaked into shared signatures |
| Offers wiped by `setVillagerData` (Pitfall 4) | **LOW if the attachment is authoritative; HIGH if not** | If chosen trades live in the attachment, just re-apply. If they only lived on the villager, they're gone and every existing employee in the save is corrupt. **This is the strongest argument for making the attachment the source of truth from day one** |
| Employees reverted to `none` in an existing save (Pitfall 5) | **MEDIUM** | Write a one-off reconcile pass on world load: for every entity with the employee attachment, re-apply profession/level/XP/offers from the attachment. Worth building anyway as the general self-heal |
| Wrong `useItemOn` signature (Pitfall 10) | **LOW** | Add `@Override`, fix the return type. Compiler-guided |
| Attachment `Codec` doesn't round-trip (Pitfall 11) | **HIGH** | Data already written with the broken shape is unreadable. Add a version field to `EmployeeData` **before** first use so a migration is possible at all |
| Stale jar / duplicate mod id in the test instance | **LOW** | FML fails fast with a duplicate-modid error. Keep the jar filename constant and delete-then-copy in one scripted step |

---

## Pitfall-to-Phase Mapping

Phase names are indicative; map them onto whatever the roadmap actually calls them.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1 — Unbound `MenuType` / DeferredRegister | **Phase 1: Skeleton + GUI vertical slice** (before any mechanic) | `./gradlew runClient` opens a trivial menu from a trivial block; `isBound()` self-check throws on a deliberately un-attached register |
| 2 — Event bus confusion | Phase 1: Skeleton | `logs/debug.log` shows the expected `Subscribing @EventBusSubscriber class … to the {game,mod} event bus` line for every handler class |
| 9 — Client classes on the server | Phase 1 (package rule) + re-checked every GUI phase | `./gradlew runServer` starts clean |
| 10 — `ItemInteractionResult` vs `InteractionResult` | **Phase 2: Soul Altar block + block entity** | Right-click with **and** without an item both behave as designed |
| 12 — Untrusted client input in menus | **Phase 3: Menu + screen + client↔server channel** | Send an out-of-range slot/button index by hand; server logs a rejection, does not crash |
| 11 — Attachment persistence / sync / copy | **Phase 4: Employee attachment** | Save-quit-reload, chunk unload round-trip, portal round-trip all preserve data; client renderer sees it |
| 3 — `getOffers()` client crash + lazy 2-trade generation | Phase 4 (write the server-only offers helper here) | Fresh employee has exactly the trades chosen — never 2 extra |
| 5 — `ResetProfession` reverting employees | Phase 4: Employee traits | Bind → travel 300 blocks → relog → profession and trades intact; watchdog (if kept) never fires |
| 6 — `BabyEntitySpawnEvent` dead for villagers | Phase 4: Employee traits | Two employees + bed + bread + 5 min = no baby; confirm the chosen hook actually logs |
| 4 — `setVillagerData` wiping offers | **Phase 5: Binding flow** | Assert offer list identity before/after profession assignment |
| 8 — `ItemListing#getOffer` null / random / needs entity | Phase 5: Trade candidates | Loop every profession × tier 1–5, assert no exception, no nulls; assert preview offer == committed offer |
| POI → profession lookup correctness | Phase 5: Binding flow | Every vanilla job block maps to the right profession; an unknown block gives the themed rejection message, not a crash |
| 7 — Level-up appending 2 random trades | **Phase 6: Leveling ritual** | Trade to level 2; assert chosen tier-1 trades unchanged and no unchosen tier-2 trades present |
| Recipes (smithing vs crafting) | Phase 2: Items | `SmithingTransformRecipe` in 1.21.1 requires **three** ingredients — template, base, addition — and the template slot must be filled by a real item. If the Harvester has no thematic template item, use a crafting-table recipe; don't invent a filler smithing template for MVP |
| Assets / lang / loot / pack_format | Phase 2, re-checked at every phase | Launch with a non-English locale; break every block and confirm drops |
| Toolchain: jar collisions, Java 21, version range | Phase 1: Skeleton | Scripted `rm old jar && cp new jar`; `neoforge.mods.toml` range includes 21.1.248; Gradle toolchain pinned to Java 21 (Temurin 21.0.12 is installed at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`) |

---

## The One-Line Version

The prior draft did not fail because villagers are hard. It failed because a `MenuType` was never actually put into a registry and **nothing in the build pipeline could tell** — `./gradlew build` compiles it, `runData` never touches the client, and the only feedback loop was hand-copying a jar into a CurseForge instance. Fix the feedback loop first (`runClient` + a registration self-check + one `runServer` per phase), and every remaining pitfall on this list becomes a 20-second discovery instead of a 2-minute one.

---

## Sources

**Primary (bytecode / artifacts on this machine — HIGH confidence):**
- `C:\Users\user\curseforge\minecraft\Install\libraries\net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-srg.jar` — `Villager`, `AbstractVillager`, `VillagerProfession`, `VillagerTrades$ItemListing`, `MerchantOffer(s)`, `ResetProfession`, `VillagerMakeLove`, `BlockBehaviour`, `PoiType`, `SmithingTransformRecipe`
- `C:\Users\user\curseforge\minecraft\Install\libraries\net\neoforged\fancymodloader\loader\4.0.43\loader-4.0.43.jar` — `EventBusSubscriber`, `EventBusSubscriber$Bus`, `AutomaticEventSubscriber`
- `C:\Users\user\curseforge\minecraft\Instances\test\mods\second-shift-0.1.0.jar` (Sep 3 23:39 build) — working registration pattern, `AttachmentType.Builder` method set, POI/profession accessor names, asset/data layout, `pack.mcmeta`, `en_us.json`
- `C:\Users\user\curseforge\minecraft\Instances\test\crash-reports\crash-2026-09-03_11.44.08-client.txt`
- `C:\Users\user\curseforge\minecraft\Instances\test\logs\2026-09-03-*.log.gz`, `debug-*.log.gz` — bus-assignment log lines, `[Employee heal]` / `[Employee watch]` / `[Employee bind]` runtime evidence

**Official documentation (HIGH confidence):**
- https://docs.neoforged.net/docs/1.21.1/concepts/events/ — mod bus vs game bus, `IModBusEvent`
- https://docs.neoforged.net/docs/1.21.1/gui/menus/ — `IMenuTypeExtension.create`, `openMenu`, `SimpleMenuProvider`
- https://docs.neoforged.net/docs/1.21.1/networking/payload/ — `RegisterPayloadHandlersEvent`, `PayloadRegistrar`, `enqueueWork` exception swallowing
- https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/ — serialization; note this page is **stale** regarding sync
- https://docs.neoforged.net/docs/datastorage/attachments/ — `.sync(StreamCodec)` / `.sync(predicate, StreamCodec)` semantics, holder-type sync targets
- https://docs.neoforged.net/docs/1.21.1/items/interactionpipeline/ — `ItemInteractionResult` pass-through semantics
- https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md — confirms 1.21.2 unified `InteractionResult`, i.e. 1.21.1 is the last version with `ItemInteractionResult`

**Issue trackers (MEDIUM confidence):**
- https://github.com/neoforged/NeoForge/issues/1606 — `[1.21.1] Cancelled villager breeding causes beds to be wrongly occupied`
- https://github.com/neoforged/NeoForge/issues/2510 — synced attachments not re-sent on player dimension change

**LOW confidence / flagged for validation:**
- The exact NeoForge 21.1.x point release that introduced `AttachmentType.Builder#sync` — confirmed present in 21.1.248 by bytecode, but the introducing version was not located. Irrelevant while the target is pinned to 21.1.248.
- Whether `run/mods` is scanned by the ModDevGradle `runClient` task in the 1.21.1 MDK — expected (FML scans `<gameDir>/mods`) but not verified in this environment. Test once during Phase 1.
- The precise trigger point for `increaseMerchantCareer()` relative to the trade-completion event — `shouldIncreaseLevel()` and `increaseProfessionLevelOnUpdate` are private; the reconcile-after approach in Pitfall 7 is deliberately robust to not knowing.

---
*Pitfalls research for: NeoForge 1.21.1 villager/trade/GUI modding*
*Researched: 2026-09-04*
