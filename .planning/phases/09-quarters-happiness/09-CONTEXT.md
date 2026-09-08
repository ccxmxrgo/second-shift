---
phase: 09-quarters-happiness
mode: autonomous (user asleep — /gsd-autonomous mandate)
requirements: [HAPP-01, HAPP-02, HAPP-03, HAPP-04, HAPP-05, HAPP-06, HAPP-07, STOCK-04]
depends_on: [05-profession-resolution-trade-picker, 08-mod-owned-restock]
---

# Phase 9: Quarters & Happiness — Design Decisions

Written directly (no discuss-phase interview — user asleep, autonomous mandate). This is the
ROADMAP's own flagged "largest net-new chunk with the least research coverage" phase — the design
spike it called for happens here, before implementation.

## D-01: Quarters detection is a bounded flood fill, not real structure recognition

Vanilla has no generic "is this an enclosed room" primitive — only per-villager bed/POI linking,
which doesn't apply to an arbitrary player-built room. `QuartersChecker.hasValidQuarters` is a
deliberately simple, deliberately bounded 6-connected flood fill from the employee's own
tether-home spawn point (`altarPos.above(2)`, matching Phase 6's tether-home position):
- Passability is "empty collision shape" (`BlockState#getCollisionShape`) — no hardcoded block
  list, works for any real room material.
- A door is treated as part of the wall boundary (its collision shape is non-empty when closed, so
  the fill correctly doesn't pass through it) — its adjacency to any interior cell is checked
  separately, satisfying "containing a door" without needing to walk through it.
- "At least 3×3" is interpreted as at least 9 distinct (x, z) footprint columns among the visited
  interior cells, regardless of room height or exact shape.
- A 400-cell visited cap doubles as the "did this leak into the open world" signal: a real room
  this small should never need anywhere near that many interior cells, so exceeding the cap before
  the frontier exhausts itself means the space isn't actually enclosed.

**Accepted limitation:** this cannot distinguish "a real room" from "a large, non-room enclosed
cavern" if a player somehow encloses one within 400 cells — a structural gap the ROADMAP's own risk
note anticipated ("structure/enclosure detection... needs a design spike"). Good enough for a
personal mod's actual playstyle; flagged here rather than silently accepted.

## D-02: Food is drawn (consumed), not just checked — with a read-only variant for status reads

"A nearby chest stocked with food it can draw from" is taken literally: `FoodChecker.tryConsumeFood`
scans a 6-block box around the altar for any `Container` block entity (chests, barrels, trapped
chests, shulker boxes — anything implementing the interface, not just the literal Chest block)
holding an item vanilla's own `Villager.FOOD_POINTS` (public: bread, potato, carrot, beetroot)
recognizes, and shrinks it by 1 on each successful periodic check. A separate `hasFoodAvailable`
(no shrink) exists specifically for the HAPP-07 status readout — a player just checking on an
employee must never accidentally consume its food supply by looking.

## D-03: The happiness meter is a 0-100 value on its own attachment, not derived instantaneously

A discrete Unhappy/OK/Happy state computed fresh every check would flicker at every band boundary
and wouldn't match the ROADMAP's own "moves it to Happy... moves it toward Unhappy" (gradual)
language. `secondshift:happiness` (`AttachmentType<Integer>`, no sync, default 50/OK) steps by 10
toward 100 when quarters+food both hold, toward 0 otherwise, on a 400-tick cadence (slower than the
40-tick heartbeat — a flood fill and a container scan are real work, unlike the other checks
sharing that heartbeat). `Happiness.fromMeter` splits it into thirds (0-33/34-66/67-100).

## D-04/D-05: Price and restock modulation reuse vanilla's own existing mechanisms

- **HAPP-04:** `MerchantOffer#setSpecialPriceDiff` is vanilla's OWN hero-of-the-village discount
  mechanism (`getModifiedCostCount` adds it directly, clamped to a minimum of 1) — reused here
  instead of inventing a new price-modification path. Happy = -1, OK = 0, Unhappy = +1, applied to
  every current offer on each happiness recompute.
- **STOCK-04:** the Phase 8 restock check reads the happiness attachment directly (cheap, no
  recompute) and skips the restock call entirely while Unhappy — "paused", not merely "slowed",
  since a full pause is the simplest correct interpretation of "slowly or not at all" and needs no
  new tunable.

**Accepted limitation:** a trade added via a Promotion Ritual between happiness recomputes won't
carry the correct price adjustment for up to 400 ticks (~20s) — the same "eventual consistency via
periodic check" pattern already accepted for Phase 7's tier-revert window (~2s there), just a
longer window here since this recompute is intentionally less frequent.

## D-06: HAPP-06's quit reverts, it does not kill

Unlike the ALTAR-06 firing smite (`EmployeeFiring.fire`, which kills the villager), a
sustained-Unhappy quit (`EmployeeManager.quit`) does NOT kill the entity — "reverts to an ordinary
unbound villager" means it keeps existing. Removing every Second Shift attachment
(`EMPLOYEE`/`RESTOCK_TIMER`/`HAPPINESS`/`UNHAPPY_STREAK_TICKS`) is sufficient: every Phase 6/7/8/9
handler is already gated on `hasData(EMPLOYEE)`, so all of them naturally stop applying from that
point on. It keeps its current profession/trades/name as leftover state — an ordinary vanilla
villager routinely carries exactly that combination, so nothing about it reads as broken. The
continuous-Unhappy streak is tracked in its own attachment (`UNHAPPY_STREAK_TICKS`, resets to 0 the
moment happiness leaves the Unhappy band), separate from the meter itself, since the streak and the
meter answer different questions ("how unhappy" vs. "how long has it been THIS unhappy").

## D-07: HAPP-07 is a chat/action-bar readout, not a new screen

No altar surface currently re-displays a bound employee's status at all (05-VERIFICATION.md's
GUI-03 resolution — an intentional, documented scope limit from Phase 5, never revisited). Rather
than building a new screen just for happiness, empty-hand right-click on a bound, non-promotable
altar now shows the happiness tier plus its specific cause (missing quarters, missing food, or
neither/both satisfied) as a themed message — reusing the exact interaction slot the "no promotion
pending" message already occupied, consistent with this mod's established message-heavy status-
readout style (the promotion signal, every altar-state message) rather than introducing a new UI
pattern for one phase.

## Issues / accepted limitations

- Quarters detection cannot distinguish a real room from a large enclosed non-room space under the
  400-cell cap (D-01).
- Up to a ~20-second window where a freshly-promoted trade doesn't yet carry the correct happiness
  price adjustment (D-05).
- HAPP-07's "altar GUI" requirement is satisfied via a chat/action-bar message, not a dedicated
  screen widget — a deliberate scope decision consistent with Phase 5's own GUI-03 precedent, not
  an oversight (D-07).
