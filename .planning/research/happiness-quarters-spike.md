# Design Spike: Quarters & Happiness (Phase 9)

**Domain:** NeoForge 1.21.1 (21.1.248) / Java 21 — Second Shift
**Researched:** 2026-09-04
**Scope:** Pre-emptive design research only. No code changed. Answers the ROADMAP.md Phase 9 risk note
and the STATE.md flag calling this phase the least-researched chunk of the roadmap.

## Summary & Recommendation

**Overall confidence: MEDIUM.** The individual mechanisms (BFS enclosure check, `Container` chest
access, `MerchantOffer.addToSpecialPriceDiff`, attachment removal) are all HIGH-confidence, verified
NeoForge/vanilla APIs — none of this needs Mixin. What's still genuinely open, and should be settled
by discussion or a short in-editor spike before planning, not guessed here:

- **Exact interpretation of "3×3"** (see §1) — this is a game-design choice, not an API question. Recommend
  **floor-area BFS ≥ 9 reachable floor cells inside one sealed void region**, not a bounding-box measurement.
- **Performance ceiling for the BFS** on a malformed/open structure — solvable (bounded flood fill with
  a node cap), but the cap value and re-run cadence are tuning decisions this document proposes concrete
  defaults for, to be confirmed in planning.
- **No existing vanilla/NeoForge algorithm does this job.** Villager bed logic, POI, and raids all key off
  single-block point membership (a bed, a bell), never enclosure/roofing. You are writing this BFS from
  scratch — budget it as real, non-trivial new code, matching the roadmap's own risk flag.
- **No comparable open-source NeoForge mod was found with a directly reusable, license-clean enclosure
  algorithm** (see §5) — Minecolonies' building system is schematic-based (compares placed blocks to a
  known structure file), which is a fundamentally different problem than "is this arbitrary player-built
  room valid," and its source is LGPL-3.0 with GUI/keybind coupling that isn't worth adapting for one
  BFS function anyway.

This is enough to plan Phase 9 confidently *if* the roadmap accepts "write and GameTest a bounded BFS"
as an explicit, budgeted task rather than an unknown. It should not remain rated as the single biggest
unknown on the roadmap after this document — the remaining uncertainty is tuning, not feasibility.

---

## 1. Enclosure / Room Detection (HAPP-01)

### Is there anything to crib from?

**No.** Searched for: vanilla villager bed/sleep logic, raid/siege mechanics, `PointOfInterest`,
and general "room detection" prior art in NeoForge/Forge mods.

- **Villager bed logic** only checks a single `BlockPos` for a valid bed POI (`PoiTypes.HOME`) and a
  short pathfind to it — it never verifies the bed sits in an enclosed room. Vanilla doesn't care if a
  villager's "bed" is in the open air.
- **Raids/village detection** (`Raid`, village-bell logic) key off **POI density in a subchunk**, not
  geometry — "any subchunk containing a claimed bed/job-site/bell counts as village." No enclosure
  check exists anywhere in this path. (Confidence: HIGH — minecraft.wiki Raid/Village pages, corroborated
  by the POI-registry design already documented in this project's own STACK.md §9.)
- **`PointOfInterest`/`PoiTypes`** is single-block membership only (a `BlockState` matches a `PoiType`),
  with no adjacency or containment semantics.
- **No general-purpose "is this space enclosed" utility exists in NeoForge or vanilla.** This is
  confirmed by a targeted search for prior Forge/NeoForge mod-dev discussions on "detect enclosed space" —
  the only relevant thread is a modder asking exactly this question and being told to flood-fill it
  themselves (Forge Forums, "How would I detect and hide enclosed space from a 3d map").

**Conclusion:** there is nothing to call into. This has to be a small bespoke algorithm, and the roadmap
risk note is correct to flag it as net-new.

### Concrete algorithm

**What "3×3 enclosed space containing a door" should concretely mean:**

Recommend: **a single sealed void (air/passable) region, reachable by a bounded BFS from a seed point,
containing at least 9 distinct floor-level (walkable) cells, with every boundary cell of the flooded
region being solid/opaque except for exactly the door opening(s), and at least one `BlockTags.DOORS`
block among the boundary blocks.**

Why floor-area-BFS over a raw bounding box:
- A bounding box interpretation (`max - min >= 3` in X and Z) is trivially gameable with an L-shaped or
  doughnut room that has a 3×3 *bounding box* but no 3×3 of actual usable floor — cheap to detect, cheap
  to fake, wrong spirit for "quarters."
- A pure "flood-fill volume ≥ 27 blocks" (treating it as a 3×3×3 cube) is closer, but conflates floor
  area with ceiling height, and a tall thin room would pass without being room-like.
- **Floor-cell count is the cleanest single number that matches player intuition of "3×3 room"**: walk
  the flood-filled air region, keep the subset of cells that have a solid block directly below and are
  themselves passable, count distinct `(x, z)` columns. Require ≥ 9. This is a config-tunable constant
  (`HAPPINESS_QUARTERS_MIN_FLOOR_CELLS = 9`), not a hardcoded literal, so it can be revisited.

**The BFS itself (server-side, bounded):**

```
validateQuarters(ServerLevel level, BlockPos seed, BlockPos altarPos):
    if distance(seed, altarPos) > MAX_QUARTERS_SEARCH_RADIUS: return INVALID (too far)
    frontier = [seed]
    visited = {}
    doorSeen = false
    floorCells = {}
    while frontier not empty and visited.size < MAX_NODES:
        pos = frontier.pop()
        if pos in visited: continue
        if visited.size >= MAX_NODES:
            return INVALID (unbounded / open structure — treat as failure, not crash)
        state = level.getBlockState(pos)
        if state is a door block or state.is(BlockTags.DOORS):
            doorSeen = true
            # doors are passable for flood-fill purposes but flagged; do not flood through
            # into unrelated space beyond them for THIS pass — treat as a wall for containment,
            # but note as evidence of "has a door"
            visited.add(pos); continue
        if !state.isSolid... (blockState.getCollisionShape / isAir / canBeReplaced-style passability check):
            visited.add(pos)
            if level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), UP):
                floorCells.add((pos.x, pos.z))
            for each of 6 neighbors: frontier.push(neighbor)
        else:
            visited.add(pos)  # solid boundary; don't expand through it
    return VALID if doorSeen and floorCells.size() >= 9 and visited.size < MAX_NODES else INVALID
```

**Concrete answers to the sub-questions the task asked:**

| Question | Answer |
|---|---|
| **Search bounds/cap** | Hard cap on visited-node count (recommend **512–1000 nodes**, i.e. roughly a 10×10×10 room with margin) *and* a hard cap on search radius from the seed (recommend **16 blocks**, config value). Hitting either cap ends the BFS immediately and returns INVALID — never continue running. This is the fix for "a player accidentally leaves one gap open in a huge cavern": the fill runs away, hits the node cap almost immediately (a cavern has thousands of open cells within a 16-radius sphere), and fails closed rather than hanging. |
| **How "door" is detected** | `blockState.is(BlockTags.DOORS)` — HIGH confidence this vanilla tag exists and covers all vanilla + most modded doors (verified: vanilla ships `BlockTags.DOORS`, matches oak/spruce/iron/etc. doors; a `DoorBlock` instance check is a viable secondary/fallback for modded doors that don't register into the tag). Do **not** require the door lead directly outside — just require ≥1 `BlockTags.DOORS` block among the boundary of the enclosed region. This is deliberately lenient (a closet door inside a bigger enclosed structure still counts) — tightening it (e.g., "door must lead to open sky/outside") is a v1.x refinement, not a v1 requirement per HAPP-01's literal wording ("an enclosed space… containing a door"). |
| **How this ties to a specific altar** | **Explicit link, not nearest-altar search.** The altar already owns exactly one employee (Phase 5 success criterion 5: "each altar holds exactly one employee… survives save/load"). Store the quarters' **seed position** (the block the player designates, or — simpler — the employee's own current position at validation time, since employees are kept near their altar per EMP-07/Phase 6) directly in `EmployeeData` (or a new `quartersAnchor: BlockPos` field) once validated, and re-validate from that stored anchor. Do not do a "search for nearest altar" — the altar↔employee relationship is already 1:1 and explicit everywhere else in this codebase (Pattern 4 in ARCHITECTURE.md: recompute over persist, but keyed off a known anchor, not a scan). Concretely: seed the BFS at the employee entity's current `blockPosition()` each time it runs — this also naturally requires the employee to actually be standing in/near its quarters for them to register as valid, which is the intuitive behavior. |
| **How often it re-runs** | **Not every tick, and not on every player action.** Recommend the same staggered-tick pattern this project already uses for `LevelUpHandler`/`RestockHandler` (ARCHITECTURE.md Scale Considerations: `tickCount % N == 0`, only over loaded employees with the attachment): re-validate quarters on a **slow periodic tick, e.g. every 200–600 ticks (10–30s)** per employee, staggered by `entity.getId() % interval` so not all employees validate the same tick. This is cheap enough (a capped BFS of ~500 nodes, once every 10-30s, per employee) to not worry about performance at the project's stated 1–50 employee scale (ARCHITECTURE.md Scale Considerations). **Additionally**, trigger an immediate re-check on a narrow, cheap signal if you want snappier feedback — e.g. a `BlockEvent.BreakEvent`/`EntityPlaceEvent` game-bus handler that only re-flags "needs recheck" (sets a dirty bit on the attachment) when the changed block is within the last-known quarters' bounding volume, rather than running the full BFS from the event handler itself. This is an optimization, not a requirement — the periodic recheck alone satisfies HAPP-01/HAPP-03 correctness, just with up to ~30s of latency, which is acceptable for a happiness system (not a real-time gate). |

**Confidence: MEDIUM-HIGH.** The BFS mechanics, tag lookup, and cap/stagger pattern are all standard,
low-risk, and consistent with patterns this project has already proven out (Phase 8 restock staggering).
The one real unknown is whether **9 floor cells** and **512-1000 node cap / 16-block radius** feel right
in actual play — that's a tuning pass during Phase 9 itself, not a blocking unknown.

---

## 2. Food-Chest Access (HAPP-02)

### What counts as food

Recommend an **explicit `ItemTags` allowlist you define**, not vanilla's hidden villager-food logic.
Vanilla villagers do recognize a fixed food set for their own AI (bread, potato, carrot, beetroot, wheat,
seeds, torchflower seeds, pitcher pods — confirmed via Minecraft Wiki, but there is **no clean public
API exposing this as a queryable predicate**; it's baked into villager gather-AI internals, not a
reusable `Villager.isFood(ItemStack)`-style public method verified in this research pass). Rather than
depend on an unverified internal, define your own:

```java
// registry or content package
public static final TagKey<Item> HAPPINESS_FOOD =
        TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MODID, "employee_food"));
```

Ship a datapack tag (`data/secondshift/tags/item/employee_food.json`) seeded with the vanilla staples
(`minecraft:bread`, `minecraft:carrot`, `minecraft:potato`, `minecraft:baked_potato`,
`minecraft:beetroot`, `minecraft:cooked_beef`, etc. — pick a reasonable "any wholesome food" set). This
is **strictly better** than hardcoding a `List<Item>` in Java: it's config/datapack-overridable for free
(matches this project's own "no hardcoded profession list" philosophy already used for `ProfessionResolver`),
and other mods' food items can add themselves to the tag without a Second Shift patch.

### Finding a candidate chest

**Scan radius from the validated quarters anchor** (not the altar — the chest should be "near the
employee's room," which is the whole point of HAPP-02 being paired with HAPP-01). Concretely:

```java
// during the same periodic happiness-check pass as the quarters BFS
BlockPos anchor = quartersAnchor; // or employee.blockPosition()
for (BlockPos p : BlockPos.betweenClosed(anchor.offset(-r,-r,-r), anchor.offset(r,r,r))) {
    BlockEntity be = level.getBlockEntity(p);
    if (be instanceof Container container && hasFoodStocked(container)) {
        candidateChest = p;
        break;
    }
}
```

Recommend **radius 6-8 blocks** (config value), cubic search centered on the quarters anchor — cheap
enough at that volume (up to ~4000 positions, but `getBlockEntity` is a chunk-local map lookup, not a
blockstate scan of every position; still, gate this behind the same slow periodic tick as the BFS, never
per-tick). A tighter approach that avoids scanning empty air: iterate **loaded block entities in range**
via `ServerLevel#getBlockEntities` filtered by AABB if available, or simply accept the coordinate-cube
scan since it only runs every 10-30s per employee.

### Consumption timing/throttle

**Vanilla farmer/villager inspiration:** villagers periodically "gather" food from the ground and via
their own internal gather-AI, and — separately, this is the closest vanilla precedent for periodic food
draw — the wandering trader's llamas and villager breeding both use **infrequent, tick-gated checks**
rather than continuous consumption. There's no single vanilla "eats N food every M ticks" hook worth
copying directly (villager breeding-food thresholds are absolute item counts, not a draw-over-time
mechanic), so this needs its own throttle, matching this project's already-established restock-timer
pattern:

```java
// EmployeeData gains: long lastFoodDrawTick
// event/HappinessHandler (new, mirrors RestockHandler's shape):
if (gameTime - data.lastFoodDrawTick() >= config.foodDrawIntervalTicks()) {
    if (drawOneFoodItem(candidateChestContainer)) {
        data = data.withLastFoodDrawTick(gameTime).withFoodSatisfied(true);
    } else {
        data = data.withFoodSatisfied(false); // chest present but empty/no valid food = still unhappy-food
    }
}
```

Recommend the draw interval match or be a simple multiple of the restock interval already configured in
Phase 8 (e.g. default a few real-time minutes) — this is a tuning value, not an API question.

### APIs to use (no Mixin, no private-field access)

| Need | API | Confidence |
|---|---|---|
| Get the chest's inventory | `level.getBlockEntity(pos) instanceof Container container` | HIGH — verified via NeoForged docs (`docs.neoforged.net/docs/1.21.1/inventories/container/`), `Container#getItem/setItem/removeItem/getContainerSize/isEmpty/setChanged` all public |
| Double chests | A double chest is two adjacent `ChestBlockEntity`s each individually a `Container`; iterating both positions independently (not via `ChestBlock.getContainer`, which is more work than needed) is sufficient for "is there food here" — no need to merge them into one logical container for this feature |
| Directional/hopper-style restriction | `WorldlyContainer#getSlotsForFace/canTakeItemThroughFace` — **not needed** here since the mod reads/removes directly server-side, not through a hopper face; only relevant if you want to respect a player's hopper-filter setup, which is out of scope |
| Removing one food item | `container.removeItem(slot, 1)` — public, no Mixin | HIGH |
| Checking item identity against the tag | `stack.is(HAPPINESS_FOOD)` | HIGH — standard `ItemStack#is(TagKey<Item>)` |
| Capability lookup (alternative) | NeoForge's `IItemHandler` capability system (`level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)`) is the "more correct" NeoForge-idiomatic way to interact with arbitrary inventories including modded ones that don't implement vanilla `Container` | MEDIUM — more robust (covers ME systems, modded storage) but more code; **recommend starting with the direct `Container` cast** (matches this project's stated preference for the simplest API that works, and vanilla chests are the overwhelmingly likely case for "a food chest in an employee's room") and only reach for the capability API if playtesting shows a real need (e.g. player wants to use a modded storage block) |

**Confidence: HIGH on the mechanism, MEDIUM on the tuning values** (radius, interval, exact food tag
contents) — those are gameplay-feel decisions to make during Phase 9, not blocked by any technical unknown.

---

## 3. Price / Restock Modulation (HAPP-04, HAPP-05)

Given the already-documented `EmployeeData.offers` / `EmployeeManager` shape and Pattern 2
("revert, don't prevent" — ARCHITECTURE.md), the modulation should be **event-triggered, not periodic
re-materialization, and not a per-tick offer rebuild.**

### Recommended mechanism: `MerchantOffer#addToSpecialPriceDiff`

Vanilla already has a public, additive price-adjustment field on `MerchantOffer` used for exactly this
kind of situational discount/markup: **Hero of the Village** applies its trade discount via
`offer.addToSpecialPriceDiff(int)` rather than rebuilding the offer (confirmed: CraftTweaker's
1.21.1 `MerchantOffer` API docs list `addToSpecialPriceDiff(int specialPriceDiff)`; the Hero-of-the-Village
wiki page describes the discount as `specialPriceDiff = -floor(reputation * offer.priceMultiplier)` —
i.e. vanilla itself modulates price by nudging this field, not by re-rolling the trade). This is the
mechanism to copy:

- When happiness state **changes** (Unhappy→OK, OK→Happy, etc. — an edge-triggered event, not a
  continuous recompute), iterate `EmployeeData.offers()` and call `addToSpecialPriceDiff(delta)` on each
  `MerchantOffer`, where `delta` is derived from the new state (e.g. Happy = negative delta/discount,
  Unhappy = positive delta/markup) **minus** whatever delta the previous state had applied (so re-applying
  is idempotent and doesn't stack across repeated evaluations of the same state).
- Because `MerchantOffer` already has to serialize/sync (`MerchantOffer.CODEC` / `STREAM_CODEC`, both
  already verified and used by this project per STACK.md §8), and `specialPriceDiff` is a field of the
  offer itself, this survives save/load and client sync **for free** — no new codec plumbing needed.
- This sidesteps the exact trap Pattern 2 exists to avoid: there is no "vanilla auto-appends something
  every tick" pressure here (nothing external mutates offers due to happiness), so there's nothing to
  fight — a plain edge-triggered mutation is safe and cheap, unlike the level-up case which needed a
  revert-every-tick approach because vanilla's `updateTrades()` was the adversary.
- **Do not** re-roll/re-materialize offers when happiness changes — that would violate Anti-Pattern 3
  (never re-generate a chosen `ItemListing`; the player already picked a concrete offer) and would also
  undo any accumulated vanilla demand/reputation-based `specialPriceDiff` state that may coexist with
  the happiness adjustment. Track your own delta so it composes cleanly rather than overwriting.

### Restock speed (HAPP-05)

Trivial given Phase 8's already-planned mod-owned restock timer: multiply the **effective interval**
by a happiness-derived factor when the `RestockHandler` (Phase 8) checks
`gameTime - lastRestockTick >= interval`:

```java
long effectiveInterval = switch (happiness) {
    case HAPPY -> baseInterval;             // full speed
    case OK -> baseInterval;                // vanilla-equivalent speed
    case UNHAPPY -> baseInterval * config.unhappyRestockPenalty(); // slow, or Long.MAX_VALUE to mean "never"
};
```

No new mechanism needed — this is a one-line change to an interval comparison that Phase 8 already owns.
HAPP-05's "or not at all" language maps cleanly to a config value/sentinel meaning "never restock while
Unhappy," which the existing `gameTime - lastRestockTick >= effectiveInterval` check already supports for
free (a sufficiently large interval, or a boolean short-circuit before the comparison).

**Confidence: HIGH.** `addToSpecialPriceDiff` is a verified, public, vanilla-precedented mechanism that
fits this project's existing serialization and Pattern-2/Pattern-4 philosophy without inventing anything
new. The restock multiplier is a one-line addition to Phase 8's already-designed timer.

---

## 4. Quit-and-Revert (HAPP-06)

### Everything `bind` currently establishes (from the shipped `EmployeeManager.bind`, read directly)

| # | State established at bind | Reversal on quit | Confidence it's cleanly reversible |
|---|---|---|---|
| 1 | `villager.setVillagerData(...setProfession(profession).setLevel(1))` | `villager.setVillagerData(villagerData.setProfession(VillagerProfession.NONE).setLevel(1))` (or just leave profession as-is if "revert to unbound villager" doesn't require un-professioning — see caveat below) | **MEDIUM** — see caveat |
| 2 | `villager.setVillagerXp(1)` (deliberately ≥1 to defeat `ResetProfession`) | Setting XP back to `0` is a plain public setter call — mechanically trivial | HIGH (mechanically) / **MEDIUM** on intent — see caveat |
| 3 | `villager.setOffers(offers)` | `villager.setOffers(new MerchantOffers())` (empty) or restore vanilla's own lazy-generated 2-trade set by leaving offers empty and letting vanilla's normal `updateTrades()` path repopulate on next interaction | HIGH — `setOffers` is public and this project already calls it freely |
| 4 | `villager.setCustomName(...)` / `setCustomNameVisible(true)` | `villager.setCustomName(null)` / `setCustomNameVisible(false)` — both public vanilla `Entity` methods | HIGH |
| 5 | `villager.setData(ModAttachments.EMPLOYEE.get(), new EmployeeData(...))` | `villager.removeData(ModAttachments.EMPLOYEE.get())` — `IAttachmentHolder#removeData` is the documented removal method; confirmed present alongside `setData`/`getData`/`hasData` in the same NeoForge attachment API this project already uses (STACK.md §4) | HIGH |
| 6 | Soul Block consumed from the altar's BE slot at bind time | HAPP-06 explicitly says quit **drops** a Soul Block — this is a *new* item spawn (`level.addFreshEntity(new ItemEntity(...))` or equivalent drop), not a literal reversal of consumption; straightforward | HIGH |
| 7 | Altar↔employee link (however Phase 9 ends up storing it — see §1's recommendation of a `quartersAnchor`/implicit "one employee per altar" invariant already enforced by Phase 5 SC5) | "Releases its altar" (HAPP-06) — since ARCHITECTURE.md's Anti-Pattern 4 already establishes the altar BE stores **nothing** about the employee (recompute-over-persist), there may be *nothing on the altar side to revert at all* — the altar's "is occupied" check (Phase 5 SC5: "a second bind is refused") is presumably implemented as a live scan for a bound employee near/linked to the altar, which naturally un-refuses itself the instant the employee's attachment is removed (step 5) | **MEDIUM** — depends on exactly how Phase 5 implemented "each altar holds exactly one employee"; needs confirming against the actual Phase 5 code once it lands, but the architecture strongly suggests this is already self-reverting and needs no explicit altar-side cleanup |

### What's one-way or hard to reverse cleanly — flagged honestly

- **Villager mechanical level / profession-level XP is not fully "resettable to fresh-villager" in a
  vanilla-faithful sense.** `setVillagerData` and `setVillagerXp` are public setters — mechanically you
  *can* write any value, including `level=1, profession=NONE, xp=0`. But vanilla's fresh-unemployed-villager
  state is genuinely `profession = NONE` (or `NITWIT`, randomly, at natural spawn) with `xp = 0` — this
  is achievable, so there is **no persistent "senior forever" flag** in vanilla's own data model. The real
  risk is narrower than "can XP be reset" (yes, trivially) and is actually: **does the freshly-quit
  villager immediately re-acquire a job-site POI and a new profession from its surroundings**, since it's
  standing in a former employee's now-abandoned quarters/altar room, likely near the same job-site block
  that originally gave it its profession? That's a *design* question (should a quit villager be nudged
  away, or is re-employment by proximity an acceptable/even thematic outcome?), not an API limitation.
  Recommend flagging this explicitly as a Phase 9 planning decision: "quit reverts data cleanly; whether
  the wandering ex-employee immediately becomes a new villager's job-site claim is out of scope / accepted
  behavior."
- **Trade `uses`/`maxUses` counters and any `specialPriceDiff` accumulated per §3 are irrelevant once
  offers are cleared** (step 3 above) — clearing `MerchantOffers` entirely removes them, so this is not a
  separate reversal concern.
- **Villager reputation/gossip accumulated with the player during employment** (vanilla `GossipContainer`)
  is *not* touched by bind and is not part of `EmployeeData` — this is pre-existing vanilla state that
  bind never claimed ownership of, so there is nothing to revert; flagging only so it's not mistaken for
  an oversight.
- **Position/AI state**: if Phase 6 implements "keep employee near altar" via a brain memory or `NoAI`-style
  restriction (STACK.md/ARCHITECTURE.md flag this as its own unresolved spike — "no pre-researched hook"),
  whatever mechanism that turns out to be must **also** be reverted on quit, or a fired/quit villager will
  stay invisibly leashed to its old altar forever. This is a real dependency Phase 9 has on however Phase 6
  ultimately implements confinement — **cannot be fully specified here** because Phase 6 hasn't landed yet;
  flag it as a cross-phase check to do when Phase 9 is planned, not a Phase-9-only gap.

**Confidence: HIGH on the mechanical reversibility of every attachment/name/offer/XP field** (all public
setters, already exercised by this project's own `bind`). **MEDIUM on the altar-side "release" step**
pending confirmation of Phase 5's actual occupancy-tracking implementation, and **explicitly flagged
open** on the Phase-6-dependent confinement-reversal question above.

---

## 5. Comparable Open-Source Mods

| Mod | Technique | Reusable here? |
|---|---|---|
| **Minecolonies** (LGPL-3.0, ldtteam/minecolonies on GitHub) | Buildings are placed from **schematic files** (`.blueprint`); the colony's "building detection" is comparing placed blocks against a known structure template plus a builder-defined bounding box set at placement time — not a general "is any player-built room enclosed" check. Its colony-radius/claim system is chunk-based (similar in spirit to vanilla's POI-subchunk village detection), unrelated to room enclosure. | **No.** Different problem shape entirely (known template vs. arbitrary room) and its codebase is deeply coupled to its own building/GUI/pathfinding stack — adapting a fragment would cost more than writing the ~40-line BFS above from scratch. License (LGPL-3.0) would also require attribution/compatible-license handling for a "personal mod, MIT" project, which is friction for zero benefit here. |
| **Trading Post / Easy Villagers** (already cited in FEATURES.md for restock precedent) | Neither implements room/enclosure detection — Trading Post works by radius-scanning for nearby villagers, not housing; Easy Villagers confines villagers to placed single-block workstations, sidestepping room detection entirely. | Confirms this project's need is genuinely novel among the mods already surveyed for this project — no restock-style "just copy this" shortcut exists for enclosure detection. |
| **Guard Villagers** | No housing/room concept at all (patrol points only). | N/A |
| **General Forge/NeoForge modding community** | The one on-point community reference found (Forge Forums thread asking "how would I detect enclosed space") got the answer "flood-fill it yourself" — i.e. this is understood in the modding community as a DIY BFS problem, not something with an off-the-shelf utility. | Confirms the recommendation in §1 is the standard approach, not a novel invention. |

**Conclusion:** no license-safe, architecture-compatible shortcut exists. The BFS in §1 is original work,
but it is *standard, well-understood* original work (flood fill with a node cap is textbook), not a
research risk in the sense of "might not be achievable." The roadmap risk should be recharacterized from
"unknown algorithm" to "known algorithm, needs tuning + a GameTest for the pathological-cavern case."

---

## Sources

**HIGH confidence:**
- This project's own `.planning/research/STACK.md` (§4 attachments incl. `removeData`, §8 `MerchantOffer`/`MerchantOffers` codecs, §9 POI/profession resolution) and `.planning/research/ARCHITECTURE.md` (Patterns 1-4, Anti-Patterns 1-7, Scale Considerations staggered-tick precedent) — read directly for this spike.
- `src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java` and `EmployeeData.java` — read directly; the bind-side enumeration in §4 is transcribed from the actual shipped code, not inferred.
- [Containers | NeoForged docs (1.21.1)](https://docs.neoforged.net/docs/1.21.1/inventories/container/) — `Container` interface method list, `WorldlyContainer`, confirmed via WebFetch of the live page.
- Vanilla `BlockTags.DOORS` existence — standard, long-stable vanilla tag (general Minecraft/NeoForge knowledge, corroborated by search results confirming `DoorBlock`/`BlockTags.DOORS` usage patterns in 1.21 modding discussions).
- [Village – Minecraft Wiki](https://minecraft.wiki/w/Village) / [Raid – Minecraft Wiki](https://minecraft.wiki/w/Raid) — POI-subchunk village detection mechanism, confirming no enclosure check exists in vanilla's own village/raid logic.
- [Hero of the Village – Minecraft Wiki](https://minecraft.wiki/w/Hero_of_the_Village) and [MerchantOffer – CraftTweaker docs (1.21.1)](https://docs.blamejared.com/1.21.1/en/vanilla/api/villager/MerchantOffer/) — `addToSpecialPriceDiff` confirmed as a real public method and as vanilla's own precedent for non-destructive price modulation.

**MEDIUM confidence:**
- Villager food-item list (bread/carrot/potato/beetroot/wheat/seeds) — corroborated across multiple community wiki/guide sources but **no verified public API** exposing it as `Villager.isFood(ItemStack)`; this project should define its own `ItemTags` allowlist rather than depend on an unverified vanilla internal (recommendation in §2, not a blocker).
- [Forge Forums: "How would I detect and hide enclosed space"](https://forums.minecraftforge.net/topic/77096-how-would-i-detect-and-hide-enclosed-space-from-a-3d-map-and-only-render-open-space/) — community confirmation that no off-the-shelf enclosure utility exists; a discussion thread, not authoritative documentation, but consistent with the absence found elsewhere.
- Minecolonies building-detection characterization (schematic/template-based, LGPL-3.0) — from general knowledge of the mod's public design plus a GitHub issue skim (`ldtteam/minecolonies#3081`, `#4260` on bounding boxes); did not read Minecolonies source directly, so treat the "not reusable" conclusion as informed but not code-verified.

**LOW confidence / explicitly unresolved:**
- Whether Phase 5's actual altar-occupancy tracking is a live scan (self-reverting on attachment removal) or persists an explicit link somewhere else in the BE — could not verify without Phase 5 code existing yet at time of writing. Re-check when Phase 5 lands, before finalizing Phase 9's quit-and-revert plan.
- Phase 6's not-yet-designed "keep employee near altar" mechanism and its interaction with quit-and-revert (§4) — genuinely can't be resolved until Phase 6 exists; flagged as a cross-phase dependency, not solved here.

---

## What this changes for Phase 9's roadmap risk assessment

The ROADMAP.md risk note currently reads: *"Largest net-new chunk with the least research coverage
(quarters/structure detection, food-chest access, price + restock modulation, quit-and-revert).
Structure/enclosure detection and 'food it can draw from' both need a design spike during planning."*

After this spike:

- **Enclosure/structure detection**: de-risked from "unknown approach" to "known approach (bounded BFS +
  `BlockTags.DOORS`), needs tuning values (floor-cell minimum, node cap, radius, re-check cadence) decided
  during Phase 9 planning/discussion, and a GameTest specifically for the pathological-open-cavern case."
- **Food-chest access**: de-risked to "known approach (`Container` cast + a mod-owned `ItemTags` allowlist
  + a throttled draw timer mirroring Phase 8's restock timer)."
- **Price/restock modulation**: de-risked to "known, low-risk mechanism (`addToSpecialPriceDiff`, a
  one-line multiplier on Phase 8's existing interval check) that fits the project's established patterns
  without a new revert-every-tick fight."
- **Quit-and-revert**: the *data-attachment* side is fully enumerated and HIGH confidence (every field
  bind sets has a public, already-used reversal call). The **remaining open items are cross-phase
  dependencies** (Phase 5's occupancy-tracking shape, Phase 6's not-yet-built confinement mechanism) —
  these should be tracked as explicit Phase 9 planning inputs to re-verify once Phases 5 and 6 are
  actually implemented, not as Phase-9-specific unknowns.

Recommend downgrading Phase 9's roadmap risk language from "largest net-new chunk with least research
coverage" to something like: *"Net-new BFS/timer code (feasible, patterns established here); needs
in-play tuning of thresholds and confirmation against Phases 5-6's final shape before the quit-and-revert
plan is locked."* The phase is no longer the roadmap's biggest unknown — it's now its biggest chunk of
ordinary, budgetable new-code work.
