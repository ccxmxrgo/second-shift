# Phase 4: Employee Attachment & Spawn - Research

**Researched:** 2026-09-04
**Domain:** NeoForge 1.21.1 (21.1.248) data attachments + entity spawning (`minecraft:villager`)
**Confidence:** HIGH for spawn ordering / registry wiring / persistence (binary-verified in project research); MEDIUM for the entity-attachment-sync empirical behavior (documented API, not yet exercised in this codebase) — this is the phase's one LIGHT spike.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Employee naming**
- **D-01:** No custom text-input UI this phase. The employee spawns with an auto-generated
  default name (Claude's discretion on the generation scheme — recommend a small curated pool
  fitting the "Second Shift" HR conceit, e.g. generic office-worker names, picked randomly per
  bind; a simple "Employee #N" counter is an acceptable fallback if a themed pool feels like scope
  creep). The temporary Confirm button therefore needs no payload fields beyond triggering the
  bind (simpler than the original research note's "SelectTradesPayload, name only" — no name is
  sent from the client this phase).
- **D-02:** The name must remain changeable via a vanilla Name Tag after spawn — do not override
  `Villager`'s interact-with-name-tag handling. Acceptance criterion: using a Name Tag on a
  freshly-bound employee renames it exactly like a vanilla villager.

**Profession**
- **D-03:** No single hardcoded profession. On each bind, `EmployeeManager.bind(...)` randomly
  picks one of 3 fixed professions: `FARMER`, `LIBRARIAN`, and a third — Claude's discretion,
  recommend `CLERIC`. All three get their normal vanilla level-1 offer pool (not a hardcoded
  trade). Still "fixed-profession, no picker" — the player makes no choice, the game rolls one
  of the 3.

**Visual distinction (EMP-08)**
- **D-04:** Custom name only — no particle this phase (FEATURES.md's soul-wisp is deferred).
  Name renders in green (`ChatFormatting.GREEN` or equivalent) and always-visible
  (`setCustomNameVisible(true)`).

**Spawn position**
- **D-05:** Spawn directly `pos.above()` the altar (not adjacent-block search). If provably
  obstructed in practice, that's an edge case for the planner/executor to note, not a reason to
  add adjacent-block search logic this phase.

### Claude's Discretion
- Exact default-name generation scheme (themed pool vs. counter — see D-01).
- Third profession choice beyond Farmer/Librarian (recommend Cleric — see D-03).
- Exact green color code / `ChatFormatting` constant used for the name.
- Whether the temporary Confirm button lives inline in `BindingAltarScreen` or needs a tiny new
  widget class — implementation detail for the planner.
- How `EmployeeData`'s version field and CODEC/STREAM_CODEC shape are structured, beyond
  containing name + profession + tier=1 + offers (per research/ARCHITECTURE.md's design).

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope. The soul-wisp particle (FEATURES.md) and real
name-entry UI were both explicitly deferred to later phases per D-01/D-04, not lost.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| EMP-01 | An employee is a `minecraft:villager` carrying a serialized, client-synced `EmployeeData` attachment (name, profession, tier, chosen offers, happiness, timers) — no custom entity type | §"Standard Stack" (`AttachmentType.builder().serialize().sync()`), §"LIGHT Spike Resolution", §"Code Examples" (EmployeeData record + registration) |
| EMP-02 | Employees spawn with villager XP ≥ 1 so vanilla never resets their profession to unemployed | §"Common Pitfalls" Pitfall 5 (ResetProfession), §"Code Examples" (bind ordering) |
| EMP-08 | Employees are visually distinguishable from wild villagers (always-visible custom name at minimum) | §"Code Examples" (green always-visible name), §"Validation Architecture" REQ-EMP-08 |
| EMP-09 | Villagers without the `EmployeeData` attachment behave exactly as vanilla — zero side effects | §"Architecture Patterns" Pattern 1 (`hasData` guard), §"Validation Architecture" REQ-EMP-09 (regression check) |
</phase_requirements>

## Summary

This phase's architecture, API surface, and the majority of its pitfalls are **already fully
researched and binary-verified** in `.planning/research/ARCHITECTURE.md` (Slice 5) and
`.planning/research/STACK.md` (§4, §8, §10) — this document does not re-derive that work. What
this document adds: (1) a concrete Validation Architecture (Nyquist) section proving persistence,
sync, and the wild-villager regression empirically rather than by inspection; (2) a firm
recommendation on the flagged "LIGHT spike" — ship the documented `.sync(STREAM_CODEC)` API as
primary, verify it empirically as the *first* task of the phase (not a separate research spike
before planning), with the manual-payload fallback pre-designed but not built unless the spike
fails; (3) villager-entity-spawn-specific pitfalls (registration count, name-tag interaction,
`addFreshEntity` positioning, `ModRegistrySelfCheck` 6th register) layered on top of the already-
documented `setVillagerData`/`setOffers`/`setVillagerXp` ordering trap (STACK.md Pitfall 4).

**Primary recommendation:** Build `EmployeeData` + `ModAttachments.EMPLOYEE` using
`AttachmentType.builder(...).serialize(CODEC).sync(STREAM_CODEC)` exactly as STACK.md §4
specifies. Treat the `.sync` verification as an in-phase GameTest/manual check (not a
separate research task) — write the villager, attach data, then have a second "simulated
client" observer (or a manual `runClient` + `/data get entity` check) confirm the value is
readable. Do not pre-build the manual-payload fallback; only reach for it if the spike proves
`.sync` doesn't fire for entities.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Employee identity (name, profession, tier, offers) | API/Backend (common, server-authoritative) | — | Lives entirely in `EmployeeData` attachment on the entity; server is the only writer (STACK.md §4, ARCHITECTURE.md "Where the employee's identity lives") |
| Attachment persistence (disk) | Database/Storage (NBT via `.serialize(CODEC)`) | — | NeoForge attachment serialization is the persistence layer here — no separate DB/file needed |
| Attachment sync to client | API/Backend → Client bridge (`.sync(STREAM_CODEC)`) | Frontend/Client (rendering the synced value, later phases) | Sync is a server push; this phase only needs to prove the value *arrives* client-side, not that anything renders it yet |
| Villager entity spawn | API/Backend (server: `EntityType.VILLAGER.create` + `addFreshEntity`) | — | Entity creation and world insertion are server-authoritative; `addFreshEntity` triggers vanilla's own client entity-add sync automatically |
| Temporary Confirm button | Client (screen widget) → API/Backend (payload handler) | — | UI trigger is client; the actual bind logic (`EmployeeManager.bind`) is common/server, per the existing `menu`/`client/screen` split (ARCHITECTURE.md) |
| Wild-villager non-interference (EMP-09) | API/Backend (every event handler gate) | — | Enforced entirely by `hasData(EMPLOYEE)` guards in common-side handlers; no client-tier involvement |
| `ModRegistrySelfCheck` registration guard | API/Backend (startup, both sides) | — | Runs identically client/server via `FMLLoadCompleteEvent`, per the established pattern (ModRegistrySelfCheck.java) |

## Standard Stack

### Core

No new external libraries. This phase is 100% NeoForge/vanilla API surface already pinned by
the project (NeoForge 21.1.248 / MC 1.21.1 / Java 21) — see `./CLAUDE.md` and
`.planning/research/STACK.md` for the toolchain, which does not change.

| API surface | Verified against | Purpose | Confidence |
|---|---|---|---|
| `AttachmentType.builder(Supplier).serialize(Codec).sync(StreamCodec).build()` | `javap` against `neoforge-21.1.248-universal.jar` (STACK.md §4) | `EmployeeData` persistence + client sync | HIGH (existence), MEDIUM (entity-sync behavior empirically — see LIGHT spike below) |
| `DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID)` | Same | `ModAttachments` registry holder | HIGH |
| `EntityType.VILLAGER.create(ServerLevel)` / `level.addFreshEntity(Entity)` | Mojang 1.21.1 mappings (STACK.md, ARCHITECTURE.md Slice 5 pseudocode) | Spawning the employee villager | HIGH |
| `Villager#setVillagerData` / `#setOffers` / `#setVillagerXp` / `#setCustomName` / `#setCustomNameVisible` | `javap` (STACK.md §8) | Configuring the spawned villager | HIGH |
| `MerchantOffers.CODEC` / `.STREAM_CODEC` | `javap` (STACK.md §8) | Persisting/syncing the chosen level-1 offers inside `EmployeeData` | HIGH |
| `RecordCodecBuilder` (`com.mojang.serialization.codecs`) | Vanilla/DFU, already used pattern in the codebase idiom | `EmployeeData.CODEC` | HIGH |
| `StreamCodec.composite(...)` | STACK.md §3 (networking pattern reused for the record's STREAM_CODEC) | `EmployeeData.STREAM_CODEC` | HIGH |

### Supporting

None required. `ChatFormatting.GREEN` (vanilla enum) covers D-04's color requirement with zero
new dependency.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `.sync(STREAM_CODEC)` (declarative attachment sync) | Manual `CustomPacketPayload` clientbound payload, sent on `EntityJoinLevelEvent`/on bind | Only reach for this if the LIGHT spike (below) proves `.sync` doesn't fire for entity holders in 21.1.248. Costs a new payload type + explicit send call at bind time + explicit resend-on-tracking-start handling; the declarative form is strictly less code if it works. |
| `IAttachmentSerializer<?, EmployeeData>` | `.serialize(Codec)` | STACK.md already resolved this: use `.serialize(Codec)` via `RecordCodecBuilder`; `IAttachmentSerializer` only earns its keep for mutable handler-style objects, which `EmployeeData` (an immutable record) is not. |
| Themed name pool | `Employee #N` counter | Both acceptable per D-01 (Claude's discretion). A themed pool needs a small static `List<String>` and a `RandomSource` pick; a counter needs a world-scoped counter (itself state — arguably *more* work than a pool, since a counter must persist somewhere or resets on reload). **Recommend the themed pool** — no persistent counter state needed, fits the HR conceit, trivially cheap. |

**Installation:** None — no `build.gradle` changes this phase.

**Version verification:** N/A — no new packages. NeoForge/MC/Java versions unchanged from the
pinned toolchain (already verified live in STACK.md: NeoForge `21.1.248`, confirmed present on
`maven.neoforged.net` and installed in the CurseForge `test` instance).

## Package Legitimacy Audit

**Not applicable.** This phase installs no external packages (npm/PyPI/crates or otherwise) —
it is pure NeoForge/vanilla Minecraft API usage against the already-pinned toolchain. No
`slopcheck` run was needed or performed.

## Architecture Patterns

Full architecture (package layout, component responsibilities, build-order Slice 5, data-flow
tables) is defined in `.planning/research/ARCHITECTURE.md` — **read that document**, this section
only adds phase-4-specific detail not already there.

### System Architecture Diagram (this phase's slice)

```
┌─ CLIENT ────────────────────────────────────────────────────────────────┐
│  BindingAltarScreen (existing, Phase 3)                                 │
│    + temporary "Confirm" button/widget (new, throwaway)                 │
│         │ on click: PacketDistributor.sendToServer(BindEmployeePayload) │
└─────────┼─────────────────────────────────────────────────────────────┘
          │  (payload: altar pos only — no name field, per D-01)
┌─────────▼─────────────────────────────────────────────────────────────┐
│  COMMON — server-side handling                                        │
│                                                                         │
│  ServerPayloadHandler.handleBindEmployee                               │
│    1. validate: player.containerMenu is BindingAltarMenu at altarPos   │
│    2. EmployeeManager.bind(serverLevel, altarPos)                      │
│         a. profession = pickRandom(FARMER, LIBRARIAN, CLERIC)          │
│         b. villager = EntityType.VILLAGER.create(level)                │
│         c. villager.moveTo(pos.above())                                │
│         d. villager.setVillagerData(new VillagerData(PLAINS,           │
│                profession, 1))                                         │
│         e. villager.setVillagerXp(>=1, within tier-1 band)             │
│         f. offers = rollDefaultTier1Offers(profession)  (TradePool     │
│                helper — reuse ARCHITECTURE.md's TradePoolCache/        │
│                candidate-materialization shape, simplified: take       │
│                all/first-2 candidates, no player picker this phase)    │
│         g. villager.setOffers(offers)          <- LAST, per Pitfall 4  │
│         h. name = pickRandom(NAME_POOL)                                │
│         i. villager.setCustomName(Component.literal(name)              │
│                .withStyle(GREEN))                                      │
│         j. villager.setCustomNameVisible(true)                        │
│         k. villager.setData(EMPLOYEE, new EmployeeData(name,           │
│                professionId, tier=1, offers, version=1))               │
│         l. level.addFreshEntity(villager)                              │
│    3. close menu / feedback                                            │
│                                                                         │
│  ModAttachments.EMPLOYEE (AttachmentType<EmployeeData>)                │
│    .serialize(EmployeeData.CODEC)   -> disk persistence (auto)         │
│    .sync(EmployeeData.STREAM_CODEC) -> client push on tracking (auto,  │
│         verify empirically — LIGHT spike)                              │
└─────────────────────────────────────────────────────────────────────┘
          │  attachment sync (automatic, per NeoForge tracking rules)
┌─────────▼─────────────────────────────────────────────────────────────┐
│  CLIENT — receives synced EmployeeData for any tracked employee entity │
│    (nothing renders it yet this phase — sync fires, no consumer UI;   │
│     the verification step reads it via a debug log / `/data get`)     │
└─────────────────────────────────────────────────────────────────────┘

  Guardrail (both sides, common): every trait/behavior handler added in
  later phases gates on `villager.hasData(ModAttachments.EMPLOYEE)`.
  This phase adds NO trait handlers (EMP-03/04/05/06 are Phase 6) —
  its only EMP-09 obligation is: don't touch wild villagers *anywhere*
  in the code this phase adds (the bind path only ever creates a new
  entity; it never mutates an existing one).
```

### Recommended Project Structure (this phase's additions only)

```
src/main/java/com/cxmxrgo/secondshift/
├── registry/
│   └── ModAttachments.java          DeferredRegister<AttachmentType<?>>, EMPLOYEE holder
├── employee/
│   ├── EmployeeData.java            record + CODEC + STREAM_CODEC + version field
│   ├── EmployeeManager.java         static bind(ServerLevel, BlockPos) -> Villager
│   └── EmployeeNames.java           (optional, if themed pool chosen) small String pool + picker
├── network/
│   ├── BindEmployeePayload.java     C->S, altar pos only (no name field per D-01)
│   └── ServerPayloadHandler.java    extended: handleBindEmployee
├── client/screen/
│   └── BindingAltarScreen.java      MODIFIED: temporary Confirm button/widget
└── ModRegistrySelfCheck.java        MODIFIED: Stream.of(...) gains ModAttachments.ATTACHMENT_TYPES
```

### Pattern 1: Attachment-as-identity, `hasData` guard everywhere (reaffirmed from ARCHITECTURE.md)

**What:** The employee *is* a `minecraft:villager` plus the `EmployeeData` attachment. This
phase's only identity check anywhere is `villager.hasData(ModAttachments.EMPLOYEE)` — never a
class check, tag, or name check.
**When:** Any code (this phase or later) that needs to ask "is this an employee?"
**Example (EMP-09 guardrail shape):**
```java
// Source: ARCHITECTURE.md "Where the employee's identity lives"; STACK.md §4 hasData/getData note
if (!villager.hasData(ModAttachments.EMPLOYEE)) {
    return; // wild villager — untouched, no side effects
}
```
This phase adds no behavior handlers that need this guard (traits are Phase 6), but the pattern
must be established now because the attachment registration itself is the thing later phases key
off of — and it is trivially easy to accidentally call `getData(...)` (which *creates* a default
attachment on read) instead of `hasData(...)` when writing the temporary Confirm-button plumbing.
Grep for any `.getData(ModAttachments.EMPLOYEE)` call outside `EmployeeManager.bind` and treat it
as a bug per STACK.md Pitfall 11's "`getData` is safe to call anywhere" trap.

### Pattern 2: Spawn ordering — data before add, offers last

**What:** `EntityType.VILLAGER.create(level)` → position → `setVillagerData` (profession+level) →
`setVillagerXp` → `setOffers` (last) → `setCustomName`/`setCustomNameVisible` → `setData(EMPLOYEE,
...)` → `level.addFreshEntity(villager)`.
**When:** Every employee spawn, this phase and Phase 5+ (promotion won't re-spawn, but any future
re-bind/respawn logic must follow the same order).
**Why this order, concretely (STACK.md Pitfall 4, verified from decompiled `Villager#setVillagerData`):**
`setVillagerData` **nulls `offers`** whenever the profession changes (`if (old.getProfession() !=
data.getProfession()) this.offers = null;`). So profession must be set **before** `setOffers`, not
after — reversing this order silently discards the trades the very next time anything reads
`getOffers()` (which also has the destructive lazy-generate-2-random-trades side effect, STACK.md
Pitfall 3). This phase has no "chosen trades" player picker yet (professions/offers are rolled by
`EmployeeManager.bind` itself), so the ordering bug is *easier* to introduce here, not harder —
there is no picker UI forcing a deliberate sequence, just a single method body to get right.
**Example:**
```java
// Source: ARCHITECTURE.md Slice 5 pseudocode + STACK.md Pitfall 4 ordering constraint
Villager v = EntityType.VILLAGER.create(level);
v.moveTo(pos.above().getX() + 0.5, pos.above().getY(), pos.above().getZ() + 0.5, 0f, 0f);
v.setVillagerData(v.getVillagerData().setProfession(profession).setLevel(1)); // FIRST
v.setVillagerXp(1); // or a value within the tier-1 XP band — see EMP-02 below
v.setOffers(defaultTier1Offers);                                              // LAST
v.setCustomName(Component.literal(name).withStyle(ChatFormatting.GREEN));
v.setCustomNameVisible(true);
v.setData(ModAttachments.EMPLOYEE, new EmployeeData(name, professionId, 1, defaultTier1Offers, 1));
level.addFreshEntity(v);
```
Note `VillagerData#setProfession`/`#setLevel` return a **new** `VillagerData` (record-like) —
`v.getVillagerData().setProfession(x)` without reassigning into `setVillagerData(...)` is a no-op.

### Anti-Patterns to Avoid

- **Calling `villager.getOffers()` anywhere before or during bind:** STACK.md Pitfall 3 —
  `AbstractVillager#getOffers()` throws on the client and, on the server, lazily calls
  `updateTrades()` on first access if `offers == null`, silently appending 2 random vanilla
  trades. Build the `MerchantOffers` object yourself and install with `setOffers(...)`; never
  read-then-write.
- **Reading `hasJobSite`/relying on a claimed POI to keep the profession locked:** this phase's
  employee never claims a POI (the job block sits on the altar, not reachable by the entity).
  `ResetProfession`'s brain behavior (STACK.md Pitfall 5) fires when `JOB_SITE` memory is absent
  **and** `villagerXp == 0` **and** `level <= 1`. Since this phase's employee has no job site by
  design, `villagerXp >= 1` is the *only* lever available and is non-negotiable — EMP-02 is not
  optional polish, it is load-bearing for EMP-01 (the employee must stay employed at all).
- **A per-tick "watchdog" that re-applies profession/XP defensively:** acceptable only as a
  *detector* (log when it fires) never as the primary fix — get `villagerXp >= 1` right once at
  bind time (STACK.md "Technical Debt Patterns").
- **Overriding `Villager`'s name-tag interaction to "lock" the auto-generated name:** violates
  D-02 explicitly. Do not touch `Villager#mobInteract`/`InteractionHand` name-tag handling in any
  way. The bind flow only calls `setCustomName` once, at spawn; vanilla's existing name-tag
  `interactLivingEntity` path is left completely alone.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Employee record persistence to NBT | A custom `INBTSerializable`/manual `CompoundTag` read-write | `AttachmentType.Builder#serialize(EmployeeData.CODEC)` via `RecordCodecBuilder` | Free round-trip, matches the rest of the codebase's codec usage (recipes/data components), and STACK.md already resolved this exact question |
| Employee record client sync | A hand-rolled clientbound `CustomPacketPayload` sent on every relevant mutation | `AttachmentType.Builder#sync(EmployeeData.STREAM_CODEC)` (pending this phase's spike) | Declarative, resends the whole object automatically on tracking start — one line vs. an entire payload type + send-call-site tracking problem |
| Default trade generation for the 3 fixed professions | A hardcoded literal `MerchantOffer` list per profession | `VillagerTrades.TRADES.get(profession).get(1)` (tier-1 `ItemListing[]`) materialized via `listing.getOffer(villager, random)`, taking up to 2 non-null results | Matches ARCHITECTURE.md's `TradePoolCache.rollCandidates` shape (simplified — no player picker, no `VillagerTradesEvent` cache needed yet since this phase's fixed 3 professions are vanilla-only); staying on the real vanilla pool now means Phase 5's real picker is a strict superset, not a rewrite |
| Random name / profession selection | A custom weighted-random utility | `RandomSource` (`level.getRandom()` or `RandomSource.create()`) + `list.get(random.nextInt(list.size()))` | Vanilla already provides `RandomSource`; no library needed for picking 1 of 3/1 of N |

**Key insight:** Everything this phase needs already has a first-class NeoForge/vanilla API. The
only genuinely new engineering is `EmployeeData`'s shape and `EmployeeManager.bind`'s ordering —
both fully specified in ARCHITECTURE.md's Slice 5 pseudocode. Resist the temptation to build a
"mini trade-pool cache" system this phase; a plain static helper that reads `VillagerTrades.TRADES`
directly is sufficient since this phase's 3 professions are vanilla-only (no modded-profession
concern until Phase 5's `ProfessionResolver`-driven picker).

## LIGHT Spike Resolution — Does `AttachmentType.Builder#sync` fire for entities in 21.1.248?

**Recommendation: implement the documented API as primary, verify empirically as the first task
of the implementation plan (not a separate research-only spike before planning). Do not pre-build
the manual-payload fallback.**

**Reasoning:**

1. **Existence is HIGH confidence, verified twice independently in this project's own research.**
   STACK.md §4 disassembled `AttachmentType$Builder` from the exact `neoforge-21.1.248-universal.jar`
   installed in the test instance and confirmed `sync(StreamCodec<? super
   RegistryFriendlyByteBuf, T>)` exists as a real method with the javadoc *"Requests that this
   attachment be synced to all clients that receive the holding object."* PITFALLS.md
   independently confirms the same method set from the prior draft's own working jar
   (`second-shift-0.1.0.jar`, a build that loaded and ran). Two independent binary sources agree
   the method exists and is callable. This is not a training-data claim — it's disassembled from
   the exact binary this project ships against.

2. **The only open question is *runtime behavior for entity holders specifically*, not API
   existence.** The stale docs page (`docs.neoforged.net/docs/1.21.1/datastorage/attachments/`)
   predates `.sync` and describes the pre-`.sync` manual-payload world; it says nothing about
   `.sync` failing for entities — it simply doesn't mention `.sync` at all. There is no source,
   official or otherwise, asserting `.sync` *doesn't* work for entities. The "gap" flagged in
   STACK.md is conservatism (an unexercised code path), not a documented failure.

3. **PITFALLS.md's own table already states the mechanism precisely** (Pitfall 11): *"For an
   entity holder, `.sync` pushes to players tracking that entity... sync sends the entire
   attachment each time... Use `hasData` not `getData` as the predicate."* This reads as the
   documented, expected behavior of a supported feature, not an unverified guess — PITFALLS.md
   independently arrived at the same "should work, verify empirically" conclusion STACK.md did,
   from a different verification pass.

4. **A pre-built fallback is wasted work if the spike passes (the likely case), and is trivial to
   add later if it doesn't.** The fallback (`BindEmployeePayload`-shaped clientbound payload sent
   on `addFreshEntity` + `PlayerEvent.StartTracking`) is a small, well-understood addition — the
   project already has the exact payload-registration pattern proven twice (Phase 3's menu
   open-buffer, and STACK.md §3's `SelectTradesPayload` shape). Building it speculatively before
   confirming it's needed is the wasted-effort branch, not the safe one.

**Concrete verification procedure for the plan to include as an early task:**

1. Implement `ModAttachments.EMPLOYEE` with `.serialize(CODEC).sync(STREAM_CODEC)` exactly as
   specified.
2. Implement `EmployeeManager.bind` and wire the temporary Confirm button end-to-end.
3. Under `./gradlew runClient` (single-player, so client and integrated server share a JVM but
   still go through the real tracking/sync machinery — NeoForge's entity attachment sync is not
   bypassed by singleplayer): bind an employee, then run `/data get entity <uuid>
   EmployeeData` or equivalent — actually, prefer a **debug log line in a client-side read path**
   (e.g. a `ClientGameBusEvents`-listened tick or an `EntityJoinLevelEvent` handler gated
   `level.isClientSide` and `Dist.CLIENT`) that reads `entity.getData(ModAttachments.EMPLOYEE)`
   and logs it. If the log line prints the real name/profession/tier client-side, sync fires.
4. **If it fails** (log line prints the default/empty `EmployeeData`, or the read throws): fall
   back immediately in the same phase — add `BindEmployeePayload`-shaped clientbound payload
   (`playToClient`), sent once from `EmployeeManager.bind` right after `addFreshEntity`, and
   additionally on `PlayerEvent.StartTracking` (NeoForge event, fires when a player starts
   tracking an entity — covers the "walked away and came back" / "reconnected" case that a
   one-shot bind-time send would miss).
5. Either way, this verification **is** the phase's Validation Architecture proof for "client-side
   sync fires for entities" (see below) — do not treat it as separate from the phase's required
   validation work.

**Confidence: MEDIUM overall** (existence HIGH, entity-specific runtime behavior unverified until
step 3 above runs) — this is exactly the LIGHT-spike classification the roadmap already assigned
it. The recommendation resolves the *planning* question (build it now, verify inline, keep the
fallback pre-designed-not-pre-built) even though the *runtime* question stays open until the plan
executes step 3.

## Common Pitfalls

The full pitfall catalogue for this domain lives in `.planning/research/PITFALLS.md` — this phase
is explicitly mapped to Pitfalls 3, 4, 5, 6 (partially — traits are Phase 6, but the "no clean
breeding hook" note is irrelevant this phase), 9, and 11 in that document's "Pitfall-to-Phase
Mapping" table. Read that table. Below are villager-spawn-specific details **not** already fully
spelled out there or in ARCHITECTURE.md/STACK.md.

### Pitfall A: `EntityType.VILLAGER.create(level)` needs a `ServerLevel`, and position must be set before `addFreshEntity`

**What goes wrong:** `EntityType<T>#create(Level)` (1.21.1 signature — verify exact overload at
implementation time; some 1.21.x versions expose `create(ServerLevelAccessor, EntitySpawnReason)`
as the modern overload) returns an entity with default position `(0,0,0)`. Calling
`addFreshEntity` before positioning spawns the villager at the world origin, not above the altar.
**Why it happens:** `create()` only constructs the entity object; it does not consult the caller's
intended spawn point. This is easy to get backwards when copying vanilla spawn-egg code that uses
a different entry point (`EntityType#spawn`, which *does* take a position but goes through
additional spawn-egg-specific logic like `finalizeSpawn` you don't want here).
**How to avoid:** Explicit order: `create` → `moveTo(x, y, z, yaw, pitch)` (or
`setPos(pos.above().getX() + 0.5, ...)`) → configure villager data/offers/xp/name/attachment →
`addFreshEntity`. `pos.above()` per D-05 should be converted to entity-center coordinates
(`+0.5` on X/Z) so the villager doesn't spawn clipped into a block corner.
**Warning signs:** Employee appears at world spawn (0,0,0) or falls through the floor at the
altar's exact block corner instead of standing centered above it.

### Pitfall B: `addFreshEntity` triggers vanilla's own entity-add sync — do not also send a manual "entity exists" payload

**What goes wrong:** Building a custom "employee spawned" clientbound payload under the
mistaken belief that entity existence itself needs manual syncing, duplicating vanilla's
`ClientboundAddEntityPacket` machinery.
**Why it happens:** Confusing "does the client know this entity exists" (vanilla, automatic, via
`addFreshEntity` → tracking) with "does the client know this entity's `EmployeeData`" (the
attachment sync question this phase's LIGHT spike is actually about). These are two separate
systems.
**How to avoid:** `level.addFreshEntity(villager)` is sufficient for entity existence/position/
basic state (custom name, villager profession rendering) to reach the client — that part is
already proven vanilla machinery, not something this phase needs to verify. Only the
`EmployeeData` attachment payload is in question.

### Pitfall C: `ModRegistrySelfCheck`'s `Stream.of(...)` guardrail must gain a 6th register, and the existing test's manual-detach verification pattern should be repeated

**What goes wrong:** Adding `ModAttachments.ATTACHMENT_TYPES` without extending
`ModRegistrySelfCheck`'s `Stream.of(ModItems.ITEMS, ModBlocks.BLOCKS, ModBlockEntities.
BLOCK_ENTITIES, ModCreativeTab.TABS, ModMenus.MENUS)` (currently 5 registers, per the actual
source file) leaves the new attachment register unguarded — a repeat of the exact class of bug
this file exists to prevent (PITFALLS.md Pitfall 1).
**How to avoid:** Add `ModAttachments.ATTACHMENT_TYPES` as the 6th entry in that `Stream.of(...)`
call. The existing D-14 verification pattern from Phase 3 Plan 01 (temporarily comment out the
`.register(modBus)` line, rebuild, confirm a named hard-abort, then restore) is directly reusable
— repeat it for `ModAttachments.ATTACHMENT_TYPES.register(modBus)` as this phase's equivalent
proof.
**Note:** `ModAttachments` is registered via `NeoForgeRegistries.ATTACHMENT_TYPES`
(`DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID)` per STACK.md §1's verified
registry-key list), not `Registries.MENU`-style `BuiltInRegistries` — confirm the self-check's
`.getEntries()`/`.isBound()` calls work identically across `DeferredRegister<AttachmentType<?>>`
and the other 5 already-covered register types (they should — `DeferredRegister` is generic over
the registry, and the existing code already treats all 5 uniformly in one `Stream.of(...)`).

### Pitfall D: Green custom name must not fight vanilla's own name-tag color/formatting

**What goes wrong:** Hardcoding `Component.literal(name).withStyle(ChatFormatting.GREEN)` at bind
time is correct (D-04), but if a later Name Tag rename (D-02) is expected to *also* render green,
that's an unstated assumption — vanilla `Nameable#setCustomName` via name tag applies whatever
`Style` the new `Component` carries, which by default is **no color** (white/default), not green.
**Why it matters:** D-02's acceptance criterion is "renames it exactly like a vanilla villager" —
i.e., a name-tag rename should behave exactly as vanilla (plain white name), not preserve green.
Green is specifically the *auto-generated default name's* visual signal (D-04); it is not meant to
be a permanent "this is an employee" marker independent of the attachment. Confirm this reading
with the planner/executor rather than assuming green must persist through a rename — the
attachment (not the name color) is the actual source of truth for "is this an employee," per
EMP-09's `hasData` pattern. Losing the green tint on rename is expected and correct, not a bug.
**How to avoid:** Do not add any code that re-applies green styling on interact-with-name-tag —
per D-02, leave `Villager`'s vanilla name-tag handling completely untouched. This "pitfall" is
really a documentation note to prevent a well-intentioned but wrong "keep it green forever" fix.

## Code Examples

### `EmployeeData` record shape (CODEC + STREAM_CODEC, version field)

```java
// Source: ARCHITECTURE.md "Where the employee's identity lives" (field list) +
// STACK.md §4/§8 (CODEC/STREAM_CODEC existence for MerchantOffers) — synthesized for Phase 4's
// reduced scope (no pendingLevel/lastRestockTick needed until Phase 7/8, but the record should
// still declare a version field now per CLAUDE.md's Pitfall 11 recovery-cost note: "add a version
// field to EmployeeData before first use so a migration is possible at all").
public record EmployeeData(
        int version,               // schema version — start at 1
        String name,
        ResourceLocation profession,
        int tier,
        MerchantOffers offers
) {
    public static final EmployeeData EMPTY = new EmployeeData(
            1, "", ResourceLocation.withDefaultNamespace("none"), 0, new MerchantOffers());

    public static final Codec<EmployeeData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("version").forGetter(EmployeeData::version),
            Codec.STRING.fieldOf("name").forGetter(EmployeeData::name),
            ResourceLocation.CODEC.fieldOf("profession").forGetter(EmployeeData::profession),
            Codec.INT.fieldOf("tier").forGetter(EmployeeData::tier),
            MerchantOffers.CODEC.fieldOf("offers").forGetter(EmployeeData::offers)
    ).apply(inst, EmployeeData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EmployeeData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EmployeeData::version,
                    ByteBufCodecs.STRING_UTF8, EmployeeData::name,
                    ResourceLocation.STREAM_CODEC, EmployeeData::profession,
                    ByteBufCodecs.VAR_INT, EmployeeData::tier,
                    MerchantOffers.STREAM_CODEC, EmployeeData::offers,
                    EmployeeData::new);
}
```

### `ModAttachments` registration

```java
// Source: STACK.md §4 (verified builder surface) + §1 (registry key)
public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SecondShift.MODID);

public static final Supplier<AttachmentType<EmployeeData>> EMPLOYEE =
        ATTACHMENT_TYPES.register("employee", () ->
                AttachmentType.builder(() -> EmployeeData.EMPTY)
                        .serialize(EmployeeData.CODEC)
                        .sync(EmployeeData.STREAM_CODEC)
                        .build());
```

### Default tier-1 offer materialization (reduced scope — no player picker this phase)

```java
// Source: STACK.md §8 (ItemListing/getOffer contract), Pitfall 8 (null-check + real entity)
static MerchantOffers rollDefaultTier1Offers(Villager forEntity, VillagerProfession profession) {
    Int2ObjectMap<VillagerTrades.ItemListing[]> byTier = VillagerTrades.TRADES.get(profession);
    VillagerTrades.ItemListing[] tier1 = (byTier != null) ? byTier.get(1) : null;
    MerchantOffers offers = new MerchantOffers();
    if (tier1 != null) {
        RandomSource random = forEntity.getRandom();
        for (VillagerTrades.ItemListing listing : tier1) {
            if (offers.size() >= 2) break;                 // vanilla: max 2 per tier
            MerchantOffer offer = listing.getOffer(forEntity, random); // may be null (Pitfall 8)
            if (offer != null) offers.add(offer);
        }
    }
    return offers; // may legitimately be empty for a pathological pool — do not crash (POL-08 precedent)
}
```
Note: `getOffer` is called against the **real** villager being bound (already positioned, not yet
`addFreshEntity`'d — still needs `level()` to resolve for map/biome-dependent listings per
Pitfall 8's "needs a real trader" note; confirm at implementation time whether a not-yet-added
entity has a valid `level()` reference — if not, call this **after** `addFreshEntity` and
`setOffers` a second time, or verify `EntityType.create(level)` already wires `level()` before
`addFreshEntity` is called, which is the expected/standard case for entity construction).

## State of the Art

Nothing in this phase's domain has changed since ARCHITECTURE.md/STACK.md/PITFALLS.md were
researched (2026-09-04, same day) — no deprecated/updated approach to note. The one genuinely
"new" question this phase introduces beyond that research is the LIGHT spike (resolved above),
which is a verification gap, not a stale-documentation gap.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `AttachmentType.Builder#sync` fires reliably for entity attachment holders in NeoForge 21.1.248 (not just block-entity/chunk holders) | LIGHT Spike Resolution | If wrong, the phase needs the manual-payload fallback (pre-designed above); this is explicitly budgeted as an in-phase verification step, not a blind assumption carried into later phases |
| A2 | A themed default-name pool (vs. a persistent "Employee #N" counter) is lower implementation cost, since a counter requires its own persisted state (world-scoped, not per-entity) that doesn't otherwise exist | Standard Stack "Alternatives Considered" | Low — this is a Claude's-discretion item per CONTEXT.md D-01; either choice satisfies the requirement, this is a recommendation not a locked fact |
| A3 | `EntityType.VILLAGER.create(level)` returns an entity whose `level()` is already resolvable before `addFreshEntity`, so `ItemListing#getOffer`'s map/biome-dependent listings (Pitfall 8) work correctly pre-add | Code Examples "Default tier-1 offer materialization" | Medium — if wrong, a `NullPointerException` or `ClassCastException` could surface for listings that need `ServerLevel`; mitigated by the documented fallback (materialize offers after `addFreshEntity`, then `setOffers` again) noted inline in the code example |
| A4 | Vanilla's `Villager` name-tag interaction handling is untouched by anything else in the existing Phase 1-3 codebase (no override already exists that would need to be reconciled with D-02) | Common Pitfalls Pitfall D | Low — grep confirms no existing `mobInteract`/name-tag override in the codebase per the files read for this research; flagged for the planner to double-check at implementation time since this research did not exhaustively grep the full `content/` package |

**If this table is empty:** N/A — see entries above.

## Open Questions

1. **Does `EntityType.VILLAGER.create(level)`'s returned entity have a valid `level()` reference before `addFreshEntity`?**
   - What we know: `create(Level)` constructs the entity with the given level reference stored
     (standard vanilla entity-construction pattern — the level is a constructor parameter chain,
     not something set later by `addFreshEntity`).
   - What's unclear: Whether `ItemListing#getOffer`'s internal `(ServerLevel) trader.level()`
     casts and `findNearestMapStructure`-style calls (Pitfall 8) function correctly against an
     entity that exists but hasn't been added to any tick/tracking system yet — vs. requiring the
     entity to be visible in `ServerLevel#getEntities` first.
   - Recommendation: Treat as LOW risk (this phase's 3 fixed professions — Farmer/Librarian/Cleric
     — have no map/biome-dependent tier-1 listings in vanilla; the risky `ItemListing` types
     (`TreasureMapForEmeralds`, `EmeraldsForVillagerTypeItem`) are Cartographer/other-profession
     specific per PITFALLS.md Pitfall 8's own listing). If it does surface, the documented
     workaround (materialize offers after `addFreshEntity`, `setOffers` again) is cheap. No
     dedicated spike needed — note in the plan as a fallback-if-observed.

2. **Exact `EntityType<T>#create` overload available in 1.21.1** (`create(Level)` vs.
   `create(ServerLevelAccessor, EntitySpawnReason)` vs. other 1.21.x variants).
   - What we know: ARCHITECTURE.md's Slice 5 pseudocode uses `EntityType.VILLAGER.create(level)`.
     STACK.md did not `javap` this specific method signature (it verified `Villager`/
     `AbstractVillager`/`VillagerData` members, not `EntityType#create`'s exact overload set).
   - What's unclear: Whether 1.21.1 already has the `EntitySpawnReason`-taking overload (added in
     later 1.21.x per general MC version history) or only the simpler `create(Level)`.
   - Recommendation: Cheap to resolve at implementation time — the compiler will fail loudly on a
     wrong overload, and `MobSpawnType`/`EntitySpawnReason`-taking `create` variants are
     backward-compatible in intent (pass `MobSpawnType.MOB_SUMMONED` or equivalent if the extra
     param is required). Not worth a pre-implementation spike; flag as "verify overload at
     `./gradlew build` time" in the plan.

## Environment Availability

No new external dependencies this phase (no new CLI tools, services, or runtimes beyond the
already-verified toolchain: NeoForge 21.1.248, Java 21, Gradle 9.2.1 wrapper — all confirmed
present and working as of Phase 3's `runClient`/`runServer`/`runGameTestServer` passes). Skipping
this section's table per the "code/config-only changes" exemption is not quite accurate (this
phase does add a new entity-spawning code path), but no *new tool* is required to build or test
it — the existing `./gradlew runClient` / `runServer` / `runGameTestServer` loop is sufficient.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | NeoForge `GameTestHelper` / `@GameTest` (`net.neoforged.neoforge.gametest`), already proven in this codebase (`HarvesterGameTests`, `BindingAltarGameTests`) |
| Config file | none — GameTests are discovered via `@GameTestHolder(SecondShift.MODID)` class annotation, run with `./gradlew runGameTestServer` |
| Quick run command | `./gradlew runGameTestServer` (existing 14 tests run in seconds; adding a handful more for this phase stays fast) |
| Full suite command | `./gradlew runGameTestServer` (same command — this project has no separate "full" vs "quick" tier yet) |

**Caveat:** two of this phase's four success criteria are **not** expressible as a single-tick
GameTest: (1) save/reload + 300-block round-trip persistence, and (3) client-side sync — GameTest
helpers run server-side in a synthetic single-player-like harness and do not exercise the real
save/quit/relaunch cycle or a real client attachment read. These two require a **manual verification
pass** (documented below) in addition to automated GameTest coverage of what *can* be automated
(spawn correctness, XP≥1, name/color, wild-villager non-interference within one GameTest world).

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EMP-01 | Bind spawns a `minecraft:villager` carrying `EmployeeData` (`hasData` true, fields populated) | unit/integration (GameTest) | `./gradlew runGameTestServer` — new method e.g. `bind_spawns_villager_with_employee_data` | ❌ Wave 0 |
| EMP-01 (persistence) | `EmployeeData` survives save/reload and a 300-block chunk-unload round trip | manual (not GameTest-expressible — requires real save/quit/relaunch + real chunk unload) | manual: `/data get entity <uuid>` before/after `/save-all` + client relaunch, and after walking 300 blocks away and back | N/A — manual procedure below |
| EMP-01 (sync) | Client-side read of the synced attachment matches server value (the LIGHT spike proof) | manual + a diagnostic log line (see "LIGHT Spike Resolution" step 3) | manual: `runClient`, bind, inspect the diagnostic client-read log line | ❌ Wave 0 (the diagnostic log-reading handler itself) |
| EMP-02 | `villagerXp >= 1` on every freshly bound employee | unit/integration (GameTest) | `./gradlew runGameTestServer` — new method e.g. `bind_sets_villager_xp_at_least_one` | ❌ Wave 0 |
| EMP-08 | Custom name is green and always-visible | unit/integration (GameTest) | `./gradlew runGameTestServer` — new method e.g. `bind_sets_green_always_visible_name` (assert `getCustomName()` style + `isCustomNameVisible()`) | ❌ Wave 0 |
| EMP-08 (visual) | Name actually renders green in the real client HUD (style application producing the expected visual) | manual (rendering is not GameTest-observable) | manual: `runClient`, bind, look at the name plate | N/A — manual procedure below |
| EMP-08 (name-tag) | D-02: a Name Tag renames the employee exactly like a vanilla villager | unit/integration (GameTest) — can simulate via calling the same interaction path a GameTest would use for a vanilla villager, OR manual | `./gradlew runGameTestServer` if `GameTestHelper` can drive a simulated name-tag interact; otherwise manual | ❌ Wave 0 (attempt automated first) |
| EMP-09 | A wild villager (no attachment) in the same world is completely unaffected — spawned via vanilla means, never touched by any code this phase adds | unit/integration (GameTest) — the regression check | `./gradlew runGameTestServer` — new method e.g. `wild_villager_unaffected_by_bind_in_same_world` (spawn a plain villager alongside a bound employee in one test, assert the wild one has `hasData == false`, unmodified XP/name/offers) | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew build` (compile-time correctness) + `./gradlew runGameTestServer` for any task touching `EmployeeManager`/`ModAttachments`/spawn logic
- **Per wave merge:** full `./gradlew runGameTestServer` (14 existing + this phase's new methods) + `./gradlew runServer` (client-class-leak gate — this phase's work is common-side per the Architectural Responsibility Map, but the habit is established and cheap, ~0.3s)
- **Phase gate:** all 4 roadmap success criteria proven — 2 by GameTest (spawn correctness / EMP-02 / EMP-08 mechanical / EMP-09), 2 by the manual procedure below (persistence round-trip, client sync) — before `/gsd:verify-work`

### Manual Verification Procedure (persistence + sync — required, not optional)

Because GameTest cannot exercise a real save/quit/relaunch cycle or a real distinct client
process, the roadmap's success criteria 2 and 3 require this explicit manual pass (run once,
documented in the phase's completion evidence):

1. `./gradlew runClient`. Place a Soul Altar, place a job-site block on top, insert a Soul Block,
   click the temporary Confirm button. Note the spawned employee's UUID (`/data get entity <uuid>`
   or F3 target inspection).
2. `/data get entity <uuid>` — confirm the `secondshift:employee` (or equivalent) attachment key
   appears under the entity's persistent data with the expected name/profession/tier/offers.
3. Walk (or `/tp`) 300+ blocks away so the employee's chunk unloads, then return. `/data get
   entity <uuid>` again — confirm identical data (chunk-unload round trip).
4. `/save-all`, then fully quit and relaunch `runClient`, reload the same world. `/data get entity
   <uuid>` again — confirm identical data (save/reload round trip).
5. For the client-sync check: confirm the diagnostic client-read log line (added per "LIGHT Spike
   Resolution" step 3) printed the correct populated `EmployeeData`, not the `EMPTY` default, at
   bind time and after steps 3-4's reload.
6. Spawn or find a wild villager elsewhere in the same world (e.g. `/summon minecraft:villager`).
   Confirm: no green name, no custom name, `hasData` false via a temporary debug command/log if
   available, normal vanilla trading behavior.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Single-player personal mod, no auth surface this phase |
| V3 Session Management | No | N/A |
| V4 Access Control | Partial — yes | The temporary Confirm-button payload must be validated server-side exactly like Phase 3's pattern: re-check `player.containerMenu instanceof BindingAltarMenu` at the claimed `altarPos`, and `stillValid` (already proven in Phase 3). No new trust boundary beyond what Phase 3 established — this phase's payload carries *less* data (no name field per D-01) so the attack surface is strictly smaller than the original `SelectTradesPayload` design. |
| V5 Input Validation | Yes | The `BindEmployeePayload`'s only field is the altar `BlockPos` (or none at all, if it's read from `player.containerMenu`'s already-validated state instead of the payload). Validate the pos matches an open, `stillValid` `BindingAltarMenu` before spawning anything — never trust a client-supplied position to determine spawn location. Per STACK.md "What NOT to Use": never trust client-sent data without server-side re-derivation. |
| V6 Cryptography | No | N/A |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Client sends a `BindEmployeePayload` for an altar it doesn't own/isn't near, triggering a spawn at an arbitrary position | Spoofing / Elevation of Privilege | Server handler re-derives the altar position from the player's **currently open** `BindingAltarMenu` (server-side state), never from a client-supplied `BlockPos` in the payload — mirrors STACK.md §3's "never trust the client's chosen indices" pattern, applied here to position instead of trade indices |
| Rapid repeated Confirm clicks spawning multiple employees from one Soul Block | Repudiation / Resource exhaustion (minor, single-player) | The existing `BindingAltarMenu`/`SoulAltarBlockEntity` slot-consumption pattern (Phase 2/3) already handles this class of problem for the Soul Block insert; extend the same "consume-then-invalidate" discipline to the bind action itself — after a successful bind, the menu should close or the slot should empty so a second Confirm click has nothing to act on. Concretely: the handler should check the Soul Block slot is non-empty *and* consume/clear it as part of the same server-side operation that spawns the employee, so a second rapid click server-side sees an empty slot and no-ops rather than spawning a second employee. |
| A wild villager acquiring the `EmployeeData` attachment accidentally via a stray `getData(...)` call (not malicious, but a correctness/security-adjacent bug — EMP-09 is explicitly a "zero side effects" requirement) | Tampering (unintended) | Covered by Architecture Patterns Pattern 1 — `hasData` not `getData` as the identity check, everywhere |

## Sources

### Primary (HIGH confidence)
- `.planning/research/ARCHITECTURE.md` — Slice 5 pseudocode, package layout, component
  responsibilities, "Where the employee's identity lives". Researched 2026-09-04 same session.
- `.planning/research/STACK.md` §4 (AttachmentType/Builder, disassembled from
  `neoforge-21.1.248-{client,universal}.jar` on this machine), §8 (Villager/MerchantOffers API,
  same verification method), §1 (registry key constants).
- `.planning/research/PITFALLS.md` — Pitfalls 3, 4, 5, 6, 9, 11 and the "Pitfall-to-Phase Mapping"
  table's Phase 4 row; verified against decompiled `client-1.21.1-…-srg.jar`,
  `loader-4.0.43.jar`, and the prior draft's working jar/logs.
- `src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java` (read directly this session)
  — actual current `Stream.of(...)` register list (5 entries) that must gain a 6th.
- `.planning/phases/03-menu-screen-harness-hard-gate/03-01-SUMMARY.md`,
  `03-02-SUMMARY.md` (read directly this session) — proven register-in-constructor pattern, D-14
  manual-detach verification methodology being reused for `ModAttachments` in this phase.
- `src/main/java/com/cxmxrgo/secondshift/gametest/HarvesterGameTests.java` (read directly this
  session) — confirms `@GameTestHolder(SecondShift.MODID)` + `@PrefixGameTestTemplate(false)` is
  the established test-authoring pattern to follow for this phase's new GameTest methods.

### Secondary (MEDIUM confidence)
- `.planning/research/FEATURES.md` — "Profession is lost until first trade" vanilla mechanic
  (Minecraft Wiki-sourced), employee-visual-distinction table-stakes note, restock/XP-threshold
  context (not directly load-bearing this phase but informs why `villagerXp >= 1` matters).

### Tertiary (LOW confidence)
- None new this session — all claims trace to the primary sources above or to direct reads of
  the current codebase.

## Metadata

**Confidence breakdown:**
- Standard stack / API surface: HIGH — zero new libraries, all APIs already binary-verified in
  ARCHITECTURE.md/STACK.md against the exact installed NeoForge jar.
- Architecture: HIGH — this phase's slice is a direct, minimally-adapted subset of
  ARCHITECTURE.md's already-designed Slice 5.
- Pitfalls: HIGH for the already-documented ones (STACK.md/PITFALLS.md, binary-verified); MEDIUM
  for the two Open Questions above (entity `level()` pre-add timing, exact `create` overload) —
  both are cheap-to-discover-at-build-time, not blocking.
- LIGHT spike (entity attachment sync): MEDIUM — API existence HIGH, entity-specific runtime
  behavior unverified until the plan's first implementation task runs the procedure in this
  document.

**Research date:** 2026-09-04
**Valid until:** No expiry driver identified (no external package versions pinned this phase,
toolchain unchanged) — re-validate only if the project's NeoForge/MC version pin ever changes.

---
*Research for: Phase 4 — Employee Attachment & Spawn (Second Shift, NeoForge 1.21.1 / 21.1.248)*
*Researched: 2026-09-04*
