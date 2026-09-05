# Phase 5: Profession Resolution & Trade Picker - Research

**Researched:** 2026-09-05
**Domain:** 3 targeted spikes only — see scope note below
**Confidence:** HIGH (all 3 findings verified against decompiled 1.21.1 bytecode on this machine and existing shipped code)

## Scope Note

This phase's design is already fully specified in `.planning/research/ARCHITECTURE.md` (Slices
6-8), `FEATURES.md`, `PITFALLS.md` (Pitfalls 3, 4, 8, 12), and `STACK.md` §8. This document does
**not** repeat any of that — it answers only the 3 genuinely open items flagged in `05-CONTEXT.md`'s
canonical refs. Read those files first; this document assumes their content as given.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Phase 5 includes G-2's item-socket + hovering-render rework (job-site block becomes a
  socketed item, not a placed block), permanently resolving Phase 4's `altarPos.above(2)` spawn
  workaround.
- **D-02:** Candidate offers are click-to-toggle rows, capped at 2 selected, vanilla trade-screen style.
- **D-03:** Name field is editable, pre-filled from `EmployeeNames`. Blank-on-confirm silently falls
  back to the pre-filled default — never blocks Confirm, never allows empty `CustomName`.
- **D-04:** Right-clicking an already-occupied altar with bind items shows a themed message and never
  opens the screen — same no-op pattern as Phase 3's missing-job/missing-soul-block cases.

### Claude's Discretion
- Exact wording of all themed messages.
- GUI-03's happiness-state field this phase: static placeholder or omit.
- Scrolling/pagination for large candidate pools.
- Exact BE slot/data shape for the socketed job item (mirrors the existing Soul Block socket pattern).
- Trivial read-only career-path stub is fine if it falls out naturally; do not scope-expand to build it.

### Deferred Ideas (OUT OF SCOPE)
- Read-only 5-tier career-path preview panel (revisit later).
- Config-flag toggle for full enchantment enumeration on librarian trades.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ALTAR-02 | Real profession resolution from job-site item | Finding 2 — `ProfessionResolver` must gain an item/BlockState entry point reading the socketed stack, not `pos.above()` |
| ALTAR-04 | Consume Soul Block AND job item on successful bind | Finding 2 — second BE slot; Finding 3 — both consumed inside the same atomic confirm-time execute block |
| ALTAR-05 | One-employee-per-altar enforcement | Finding 3 — occupancy must be re-checked at confirm time, not just open time |
| PICK-01–08 | Trade pool roll, materialization, selection, naming, edge cases | Finding 1 — `getOffer` null/side-effect handling underpins PICK-02/03/08 |
| GUI-02 | Server-side re-validation of client selections | Finding 3 |
| GUI-03 | Menu displays name/profession/tier/trades | No new research needed — pure UI wiring per ARCHITECTURE.md Slice 8 |
</phase_requirements>

## Finding 1 — `ItemListing.getOffer` on a throwaway villager: side effects and null handling

**Confidence: HIGH** — verified by decompiling `VillagerTrades$*` classes from
`client-1.21.1-20240808.144430-srg.jar` (the exact jar the test instance runs), not from training
data or web search.

**Side effects — confirmed, listing-specific, not universal:**

- `TreasureMapForEmeralds#getOffer` (bytecode inspected in full): first checks
  `trader.level() instanceof ServerLevel` — returns `null` immediately if false. **A throwaway
  villager created via `EntityType.VILLAGER.create(serverLevel)` but never `addFreshEntity`'d still
  has a non-null `level()`** (the level is set at construction, independent of being added to the
  world), so this check passes even for a throwaway instance. It then calls
  `serverLevel.findNearestMapStructure(destination, trader.blockPosition(), 100, true)` using the
  throwaway's **current position** — if the throwaway was never `moveTo`'d to the altar, this
  searches near `(0,0,0)` (or wherever `create()` defaults it), not near the player. If a structure
  is found, it calls `MapItem.create(...)` and **`MapItem.renderBiomePreviewMap(serverLevel,
  stack)`**, which allocates and persists a new map `SavedData` entry in the level's data storage —
  **a real, permanent world-save side effect** — even though the throwaway villager and the rolled
  offer are only being used for a UI preview and may be discarded. Rolling this listing repeatedly
  (e.g., re-rolling on every menu open) leaks orphaned map IDs into the save file.
  - **Handling recommendation:** move the throwaway villager to the altar's `BlockPos` before rolling
    (cheap, makes the map target sensible if ever exposed) AND roll candidates exactly once per bind
    session (already required by PITFALLS.md Pitfall 8 / ARCHITECTURE.md Slice 7 — "generate once,
    store, reuse"). Do not re-roll on every screen open/close. This one-roll-per-session policy is
    the only viable mitigation — you cannot prevent the map-data allocation once `getOffer` is
    called at all if a `TreasureMapForEmeralds` listing happens to be selected, only avoid calling it
    redundantly. Cartographer is the only vanilla profession using this listing.
- `EmeraldsForVillagerTypeItem#getOffer` (Wandering-Trader-style listings, not present in vanilla
  `VillagerTrades.TRADES` for the 6 standard job professions but decompiled for completeness): checks
  `trader instanceof VillagerDataHolder` — `Villager` satisfies this, so no null for a real
  `Villager` throwaway. The backing `Map<VillagerType, Item>` is populated at listing-construction
  time via a lambda that **throws `IllegalStateException`** if any `VillagerType` is missing an
  entry — so in practice this listing never returns null for a `Villager` trader once constructed;
  it would only null out for a non-`VillagerDataHolder` `Entity`, which never happens if you always
  pass a `Villager`.
- No other side effects (network calls, entity mutation, persistent state writes outside the
  map-data case above) were found in any decompiled listing class (`EmeraldForItems`,
  `ItemsForEmeralds`, `DyedArmorForEmeralds`, `EnchantBookForEmeralds`, `EnchantedItemForEmeralds`,
  `ItemsAndEmeraldsToItems`, `SuspiciousStewForEmerald`, `TippedArrowForItemsAndEmeralds`,
  `FailureItemListing`). They read registries and construct `MerchantOffer`/`ItemCost`/`ItemStack`
  objects only — no mutation of the passed-in `trader`, no other level writes.

**Null returns for tier-1 pools — concrete per-profession answer:** For the 6 standard job
professions with real job-site blocks (Farmer, Fisherman, Shepherd, Fletcher, Librarian, Cartographer,
Cleric, Armorer, Weaponsmith, Toolsmith, Leatherworker, Butcher, Mason), tier-1 `ItemListing[]`
arrays use only `EmeraldForItems`, `ItemsForEmeralds`, `EnchantBookForEmeralds` (Librarian tier 1
includes one), and none of the null-prone listings (`TreasureMapForEmeralds` first appears at
Cartographer tier 1 in vanilla — it IS reachable at tier 1, so this is not purely a higher-tier
concern). Confirmed null-risk listing at tier 1: **Cartographer tier 1 includes
`TreasureMapForEmeralds`** (vanilla `VillagerTrades.TRADES`), which nulls out if
`findNearestMapStructure` finds nothing within the search radius from the throwaway's position —
plausible in ocean/void/superflat worlds or if the throwaway wasn't moved near real terrain.

**Concrete handling recommendation:** In `TradePoolCache.rollCandidates`, iterate the tier's
`ItemListing[]`, call `getOffer` once per listing, and **skip (do not re-roll) any null result** —
do not loop retrying the same listing, since `TreasureMapForEmeralds`'s failure mode
(`findNearestMapStructure` returning null) is not fixed by re-rolling the same listing at the same
position; it needs a different listing or a different position. Fewer-than-N candidates displayed is
the correct, safe behavior (matches PICK-08's "empty pool → themed message, no crash" spirit, scaled
down to "fewer candidates → still usable, no crash"). Move the throwaway villager to the altar's
position before rolling so the treasure-map case at least searches from a sensible location.

## Finding 2 — G-2's second socket: concrete BE/renderer additions

**Confidence: HIGH** — direct extension of the existing shipped pattern in `SoulAltarBlockEntity`
and `SoulAltarRenderer` (both read in full for this research).

**`SoulAltarBlockEntity` changes:**

- Rename `isEmpty()` → `isSoulBlockEmpty()` (breaking rename; update the 3 call sites:
  `SoulAltarBlock.useItemOn`/`useWithoutItem`/`onRemove`, `SoulAltarRenderer.render`,
  `ServerPayloadHandler.handleBindEmployee`).
- Add a second, independent field mirroring `heldSoulBlock` exactly:
  ```java
  private ItemStack heldJobItem = ItemStack.EMPTY;
  private static final String KEY_JOB_ITEM = "JobItem";

  public boolean isJobItemEmpty() { return heldJobItem.isEmpty(); }
  public ItemStack getHeldJobItem() { return heldJobItem; }
  public void setHeldJobItem(ItemStack stack) {
      // Validation happens at the call site (SoulAltarBlock#useItemOn), same division of
      // responsibility as setHeldSoulBlock: this method accepts EMPTY or any stack the block
      // has already confirmed maps to a real profession via ProfessionResolver.
      this.heldJobItem = (stack == null) ? ItemStack.EMPTY : stack;
  }
  ```
- `saveAdditional`/`loadAdditional`: add `KEY_JOB_ITEM` persistence in parallel with
  `KEY_SOUL_BLOCK` (identical `stack.save(registries)` / `ItemStack.parse(...)` pattern). Bump
  `DATA_VERSION` to `2`; treat a missing `KEY_JOB_ITEM` tag on load as `ItemStack.EMPTY` (backward
  compatible with any Phase-1-4 saves, consistent with the existing `DATA_VERSION` comment's stated
  intent, D-17).
- `getUpdateTag`/`getUpdatePacket` need no changes — they already delegate to `saveAdditional`,
  which will include the new field automatically.
- `onRemove` (drop-on-non-player-removal safety net) must be extended to also drop
  `heldJobItem` alongside `heldSoulBlock` when not `wasBrokenWhileCharged()`.
- `playerWillDestroy`'s charged-break path: decide whether a charged break also destroys the
  socketed job item (thematically consistent with "the Soul Block is destroyed") or drops it
  normally — Claude's discretion per CONTEXT.md, not researched further here since it's a design
  choice, not a technical unknown.

**`ProfessionResolver` addition:** the current `fromAbove(Level, BlockPos)` reads `pos.above()`'s
`BlockState`. G-2 needs profession resolved from the **socketed item's block state**, not a real
placed block. Add:
```java
public static Optional<VillagerProfession> fromItem(ItemStack stack) {
    if (!(stack.getItem() instanceof BlockItem blockItem)) return Optional.empty();
    return PoiTypes.forState(blockItem.getBlock().defaultBlockState()).flatMap(ProfessionResolver::fromPoi);
}
```
This reuses the existing `fromPoi(Holder<PoiType>)` — no change needed there. `fromAbove` can be
deleted once `SoulAltarBlock` and `BindingAltarGameTests` no longer call it (per CONTEXT.md's
explicit note that this phase reworks `ProfessionResolver.fromAbove`).

**`SoulAltarRenderer` changes** — the existing embedded/non-spinning Soul Block render
(`EMBED_Y = 0.78D`, `EMBED_SCALE = 0.5F`, no rotation) is the template for the socket mechanic but
must **not** be reused verbatim for the job item, since CONTEXT.md specifies "hovering + slowly
spinning" for the job item, visually distinct from the embedded, static Soul Block. Concrete
additions:
```java
private static final double HOVER_Y = 1.35D;       // above the top plate, clear of the embedded soul block
private static final float HOVER_SCALE = 0.4F;
private static final float SPIN_DEG_PER_TICK = 1.0F; // slow — tune in playtesting

// in render(...), after the existing embedded-soul-block block:
if (!be.isJobItemEmpty() && be.getHeldJobItem().getItem() instanceof BlockItem blockItem) {
    BlockState jobState = blockItem.getBlock().defaultBlockState();
    long gameTime = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
    float angle = (gameTime + partialTick) * SPIN_DEG_PER_TICK;
    pose.pushPose();
    pose.translate(CENTRE, HOVER_Y, CENTRE);
    pose.mulPose(Axis.YP.rotationDegrees(angle));
    pose.scale(HOVER_SCALE, HOVER_SCALE, HOVER_SCALE);
    pose.translate(-0.5D, -0.5D, -0.5D);
    blockRenderer.renderSingleBlock(jobState, pose, buffers, packedLight, OverlayTexture.NO_OVERLAY);
    pose.popPose();
}
```
Key differences from the Soul Block render that prevent visual overlap: separate vertical offset
(`HOVER_Y` above `EMBED_Y`), separate pose push/pop (independent transform stack), added
`Axis.YP.rotationDegrees(angle)` for spin, and using the ambient `packedLight` parameter (not
`LightTexture.FULL_BRIGHT`) since the job item is a normal held block, not an emissive artifact —
this is a discretionary call, flag it for the planner rather than lock it. `Axis` here is
`com.mojang.math.Axis` (already available in 1.21.1, no new dependency).

**Interaction-order note (flag for planner, not resolved here):** `SoulAltarBlock.useItemOn` must
now branch on stack type — Soul Block → existing soul-slot path; `BlockItem` resolving via
`ProfessionResolver.fromItem` → new job-slot path. Since both slots can be filled in either order
across two separate right-clicks, decide (planning-time decision, not a research unknown) whether
the screen opens after either slot fills, only after both fill, or reopens via `useWithoutItem`
regardless of fill state — this is a UX sequencing choice, not a technical spike.

## Finding 3 — `SelectTradesPayload` trust boundary: concrete re-validation checklist

**Confidence: HIGH** — direct extension of the shipped `BindEmployeePayload` /
`ServerPayloadHandler.handleBindEmployee` pattern (read in full) plus PITFALLS.md Pitfall 12's
already-stated general principles, made concrete for this payload's two new data fields (indices,
name).

The existing handler already establishes the atomicity pattern to extend, not replace:
`menu.stillValid(sp)` → `access().execute((level, pos) -> { atomic consume-then-act })`. Apply the
same shape to `SelectTradesPayload`, with these concrete additions:

1. **Trade indices — validate against the server's own list, never the client's:**
   - The candidate `MerchantOffer` list must already be materialized and stored server-side at
     menu-open time (BE field or menu field populated once — per Finding 1's "generate once, store,
     reuse" and ARCHITECTURE.md Slice 7). The payload carries only `int[]`/`List<Integer>` indices.
   - On receipt: bounds-check every index against `candidates.size()` (`0 <= i < size`); reject
     (silently no-op the whole payload, do not partially apply) if any index is out of range.
   - Reject duplicate indices (a modified client sending `[0, 0]` to pick one offer twice).
   - Enforce the count invariant from D-02/PICK-04: `selected.size() <= 2`, AND if
     `candidates.size() <= 2`, `selected` must equal the full candidate set (auto-select/lock) —
     don't trust the client to have honored the auto-lock UI state; re-derive and overwrite
     server-side rather than validating client intent matched it.
   - On any violation: no-op the confirm (do not spawn a partially-configured employee), optionally
     log server-side for debugging, matching the existing "stale/malicious send, silently ignore"
     tone in `ServerPayloadHandler`.

2. **Name string sanitization:**
   - Cap length (recommend 32 characters — generous for a villager name tag, short enough to avoid
     UI overflow in the altar screen and the in-world name tag render; this is a discretionary
     number, not a hard vanilla limit — no vanilla constant exists for custom mob names, only for
     signs/books, so pick one and document it).
   - Strip/reject formatting-code injection: the raw string must not be used directly inside
     `Component.literal(name)` if it contains `§` (section sign) sequences — `Component.literal`
     does not interpret `§` codes automatically (Minecraft's older raw-string formatting-code
     parsing applies to legacy string decoding paths, not `Component.literal`'s stored content), but
     defensively stripping `§` and other control characters is still correct practice for a
     client-supplied string that will be persisted (attachment) and rendered repeatedly. Trim
     leading/trailing whitespace.
   - After stripping, if the resulting string is blank, fall back to the pre-filled default name —
     this is the same fallback D-03 already specifies for "field cleared and confirmed blank";
     extend it to also cover "field non-blank but sanitizes to blank" (e.g., a string of only
     formatting codes/whitespace).

3. **Re-check altar occupancy/menu validity at confirm time, not just open time:**
   - D-04's screen-refuses-to-open check happens once, at interaction time, before the menu exists.
     Between that check and the player clicking Confirm, the altar's occupancy state is not
     re-verified anywhere in the currently-shipped flow (Phase 4's `menu.stillValid(sp)` checks
     block-entity existence/type and player proximity — it does **not** check "was this altar bound
     to an employee since the menu opened").
   - Concrete fix: add an occupancy marker to `SoulAltarBlockEntity` (e.g.
     `private boolean employeeBound` alongside the two item slots, persisted the same way, set
     `true` the instant `EmployeeManager.bind` succeeds). Inside the confirm handler's
     `access().execute((level, pos) -> { ... })` lambda — the same atomic block that already
     consumes the Soul Block per T-4-02's "atomic consume before bind" discipline — add a guard:
     `if (be.isEmployeeBound()) return;` **before** consuming either socketed item or calling
     `EmployeeManager.bind`. This closes the real (if rare, single-player-only) race: two rapid
     Confirm sends (double-click, or a delayed/duplicated packet) must not spawn two employees from
     one altar. This is the same class of race Phase 4 already solved for double-consuming the Soul
     Block (T-4-02) — Phase 5 needs the identical atomic-guard treatment applied to the new
     employee-bound invariant, not a new mechanism.
   - Set `employeeBound = true` inside that same atomic lambda, immediately after
     `EmployeeManager.bind(...)` returns successfully, before `sp.closeContainer()`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | 32-character cap for the employee name field is a reasonable discretionary limit (no vanilla constant governs custom mob names) | Finding 3 | Low — cosmetic; planner/user can adjust the number freely, no correctness impact |
| A2 | `HOVER_Y = 1.35D` / `SPIN_DEG_PER_TICK = 1.0F` are reasonable starting values for the hovering job-item render | Finding 2 | Low — visual tuning only, expected to be adjusted during playtesting per CONTEXT.md's "Claude's Discretion" on exact BE slot/render shape |
| A3 | Interaction-order for filling the two sockets (soul-first, job-first, or either-order-opens-screen) is unresolved and left to planning | Finding 2 | Medium — if planner doesn't make an explicit choice, `useItemOn`/`useWithoutItem` branching could end up inconsistent with D-01/D-02's existing one-way-socket UX |

**If this table is empty:** N/A — see rows above; none of these are compliance/security-critical,
all are tunable/discretionary implementation details already flagged as such in CONTEXT.md.

## Open Questions

None blocking. The interaction-order note in Finding 2 (A3) is a planning decision, not a research
gap — the underlying technical facts (both slots must be independently settable, in either order,
without relying on a single combined interaction) are established.

## Sources

### Primary (HIGH confidence)
- `client-1.21.1-20240808.144430-srg.jar` (`C:\Users\user\curseforge\minecraft\Install\libraries\net\minecraft\client\1.21.1-20240808.144430\`) — decompiled `VillagerTrades$TreasureMapForEmeralds`, `VillagerTrades$EmeraldsForVillagerTypeItem`, and confirmed the full inner-class list of `VillagerTrades$*` listing implementations via `javap -p -c`, this session.
- `src/main/java/com/cxmxrgo/secondshift/content/block/SoulAltarBlock.java` — existing socket/interaction pattern (read in full).
- `src/main/java/com/cxmxrgo/secondshift/content/blockentity/SoulAltarBlockEntity.java` — existing BE persistence pattern (read in full).
- `src/main/java/com/cxmxrgo/secondshift/client/render/SoulAltarRenderer.java` — existing renderer pattern (read in full).
- `src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java` — existing bind ordering (read in full).
- `src/main/java/com/cxmxrgo/secondshift/network/ServerPayloadHandler.java` — existing trust-boundary/atomicity pattern (read in full).
- `src/main/java/com/cxmxrgo/secondshift/trade/ProfessionResolver.java` — existing POI resolution (read in full).

### Secondary (MEDIUM confidence)
- `.planning/research/PITFALLS.md` Pitfall 8 — prior, broader verification of `getOffer` null/random behavior (cited, not re-derived).

## Metadata

**Confidence breakdown:**
- Finding 1 (getOffer side effects/nulls): HIGH — direct bytecode inspection this session.
- Finding 2 (BE/renderer socket additions): HIGH — direct extension of existing, already-shipped, fully-read code.
- Finding 3 (trust boundary): HIGH — direct extension of existing, already-shipped, fully-read pattern.

**Research date:** 2026-09-05
**Valid until:** No expiry driver — findings are tied to the pinned NeoForge 21.1.248/MC 1.21.1
version and this repo's own shipped code, neither of which changes on a calendar schedule.
