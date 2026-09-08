---
phase: 07-progression-promotion-ritual
mode: autonomous (user asleep — /gsd-autonomous mandate)
requirements: [PROG-01, PROG-02, PROG-03, PROG-04]
depends_on: [05-profession-resolution-trade-picker, 06-employee-traits-death-firing]
---

# Phase 7: Progression & Promotion Ritual — Design Decisions

Written directly (no discuss-phase interview — user asleep, autonomous mandate). Decisions below
are derived from: (1) the ROADMAP's own Phase 7 goal/success-criteria text (written before Phase 5's
redesign rounds, but never superseded), and (2) the research already captured in memory
`second-shift-phase7-tier-gated-trade-idea` (2026-09-08 update), which independently converges on
the same shape: real vanilla trade-XP leveling, no shortcuts, a re-triggerable altar ritual per tier.

## D-01: What "tier" means, and where it lives

`EmployeeData.tier` (already exists, unused until now beyond a hardcoded `1`) becomes the
employee's **last officially installed** tier — i.e. the highest tier whose trades the player has
actually confirmed via a bind or a Promotion Ritual. Vanilla's own `VillagerData.getLevel()` is a
**different**, uncontrolled number: it advances automatically the moment trade XP crosses a
threshold, independent of anything this mod does. The gap `vanillaLevel > data.tier()` IS the
"promotable" state — computed on the fly every check, never persisted as its own boolean.

**Why not add a `promotable` boolean field:** `EmployeeData`'s `StreamCodec.composite` is already at
its hard 6-component ceiling (documented in that class's own doc comment since Phase 6). Deriving
promotability from two numbers that already exist avoids a 7th field entirely — no nested sub-record,
no hand-written `StreamCodec`, and one less piece of state that could ever desync from the truth.

## D-02: Reverting vanilla's auto-appended trades (PROG-02)

There is no clean event for "villager leveled up" (confirmed again against decompiled
`Villager#increaseMerchantCareer` — private, called only from the level-up XP check, calls
`updateTrades()` immediately after). Per the ROADMAP's own pre-authorized risk note ("revert, don't
prevent"), this phase does not try to intercept the level-up — it lets vanilla do whatever it wants,
then **reverts the offer list** on the very next periodic check.

Reuses the exact per-employee 40-tick periodic check `EmployeeEvents#onEntityTick` already runs for
the breeding-lock reassert and altar tether (06-CONTEXT.md D-04) — one more cheap comparison on an
already-existing per-employee heartbeat, not a new ticking system. Each check: if
`vanillaLevel > data.tier()`, unconditionally `villager.setOffers(data.offers())` (the last
player-confirmed set) — idempotent, safe to run every 2 seconds indefinitely until the ritual
resolves it. A max 2-second window where a just-leveled employee could show an unchosen trade is an
accepted, documented gap (see Issues/Limitations) — a true zero-tick guarantee needs a Mixin into
`increaseMerchantCareer`/`updateTrades`, which the ROADMAP's own risk note explicitly did not
pre-authorize as a first resort.

## D-03: The "ready for promotion" signal (PROG-03)

On the same periodic check, the moment `vanillaLevel > data.tier()` is newly true (tracked via an
in-memory, non-persisted `Map<UUID,Integer> lastSignaledLevel` — same "acceptable to lose on
restart, self-heals next tick" pattern as `EmployeeFiring`'s pending-smite queue, Phase 6 D-01
patterns list), emits:
- `ParticleTypes.HAPPY_VILLAGER` above the employee (server-broadcast, no per-player targeting
  needed — matches the mod's existing particle calls elsewhere).
- An action-bar message to every real player within 32 blocks (`ServerLevel#getEntitiesOfClass`
  scoped to `ServerPlayer`), naming the employee and its new tier.

Re-fires once per newly-reached tier (keyed by the actual level number, not just a boolean), so an
employee that reaches tier 3 while the player was offline for the tier-2 ritual still gets its own
fresh signal instead of silently sitting promotable forever.

## D-04: The ritual UI reuses Phase 5's picker infrastructure, not a new design

Right-clicking a bound altar empty-handed while its employee is promotable opens
`PromotionRitualMenu`/`PromotionRitualScreen` — the same enchanting-table-texture +
`TradeCandidateList` scrollable-picker pattern `BindingAltarMenu`/`BindingAltarScreen` already
established in Phase 5, rolling `TradePoolCache.rollCandidatesForTier` (now public) for the
specific tier just reached instead of tier 1 or the max tier.

**"Pick 2" (ROADMAP success criterion 3), without a new network payload:** selection happens
entirely client-side first — `TradeCandidateList` gains a toggle-select mode (up to
`min(2, pool.size())` rows highlighted, click again to un-toggle) — and only a single "Confirm"
button click sends one packed int through vanilla's existing `clickMenuButton`
RPC (`idxA * 32 + idxB`, `31` meaning "no second pick" when the pool has only one listing). No new
`CustomPacketPayload` needed, matching the precedent `BindingAltarMenu`'s own doc comment sets
("no new payload needed") — this mod's established minimalism principle.

**Existing trades stay locked, not shown as pickable (success criterion 3):** the ritual screen
shows ONLY the new tier's pool — the employee's already-installed trades aren't re-litigated or
re-shown at all here (there is no altar surface yet that shows a bound employee's current trade list
back to the player at all — see 05-VERIFICATION.md's GUI-03 resolution, still true). "Locked" is
satisfied by omission: nothing already chosen can ever be re-picked or lost through this screen,
because this screen never touches `data.offers()`'s existing entries, only appends to them.

## D-05: Confirming a ritual (PROG-04)

`EmployeeManager.installPromotion(level, villager, data, newTier, chosenOffers)`: merges
`chosenOffers` onto a copy of `data.offers()` (preserves every prior tier's trades — success
criterion 4), calls `villager.setOffers(merged)` (so the live entity's tradeable list updates
immediately, same tick), and writes back a new `EmployeeData` with `tier = newTier` and
`offers = merged`. Once `data.tier()` catches up to `vanillaLevel`, the periodic revert (D-02)
naturally stops touching this employee's offers until it levels again.

## D-06: Where the ritual can be triggered from

Empty-hand right-click on a bound altar only (mirrors D-02's existing "empty hand = look/reopen"
convention from Phase 5) — holding an item on a bound altar keeps showing the plain "occupied"
message, so a player mid-task with something in hand never accidentally opens a ritual screen.
Distance/proximity is already guaranteed by the altar tether (Phase 6 D-04: an employee can't
wander more than 48 blocks from its own altar without being teleported home), so this phase adds no
new proximity check beyond "is this altar's bound employee's UUID resolvable and promotable right
now" (`ServerLevel#getEntity(UUID)`, same technique `EmployeeFiring` already uses).

## Issues / accepted limitations

- **Up to a ~2-second window** between a real level-up and this mod's revert where vanilla's own
  auto-picked trade is live and could theoretically be traded once. Documented, not fixed — a
  zero-window guarantee needs a Mixin the ROADMAP's own risk note reserves as a later option, not a
  first resort for a personal mod. If this proves visible/exploitable in play, revisit with a Mixin
  into `Villager#updateTrades`.
- **The promotion-ready signal is in-memory, not persisted.** A server restart between "employee
  leveled" and "player saw the signal" means the signal fires again next tick after restart
  (self-heals) rather than staying silently signaled — the safe failure direction (re-notify, never
  silently drop a promotion the player hasn't acted on yet).
