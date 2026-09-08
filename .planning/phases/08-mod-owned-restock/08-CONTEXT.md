---
phase: 08-mod-owned-restock
mode: autonomous (user asleep — /gsd-autonomous mandate)
requirements: [STOCK-01, STOCK-02, STOCK-03]
depends_on: [04-employee-attachment-spawn]
---

# Phase 8: Mod-Owned Restock — Design Decisions

Written directly (no discuss-phase interview — user asleep, autonomous mandate), continuing
directly from Phase 7 with no concurrent-agent dispatch (per the Phase 6 lesson).

## D-01: Call `Villager#restock()` directly, never `shouldRestock()`

Decompiled `Villager` source confirms `restock()` is `public` and does exactly one thing worth
doing here: `updateDemand()`, reset every offer's uses, resend to the trading player if one is
open. It has NO internal gate — the day/POI/twice-a-day gating lives entirely in the separate
`shouldRestock()`/`allowedToRestock()` pair, which is `private` and only ever called from the
brain's work-schedule behavior. Calling `restock()` directly and never touching
`shouldRestock()` at all is what makes this restock genuinely independent of POI, work schedule,
day/night, and dimension (success criterion 1) — there is nothing vanilla left in the loop to gate
on any of those.

## D-02: A separate attachment, not a 7th `EmployeeData` field

`EmployeeData`'s `StreamCodec.composite` chain hit its documented 6-component ceiling in Phase 6.
The restock timer (`lastRestockGameTime`, a `long`) is pure server-authoritative bookkeeping the
client never needs — it gets its own attachment (`secondshift:restock_timer`, `AttachmentType<Long>`)
with a serialize codec and no sync, rather than fighting the ceiling with a nested sub-record for
one field that doesn't need syncing anyway.

## D-03: Reuse the existing 40-tick periodic per-employee check

Phase 6 already established one per-employee heartbeat (`EmployeeEvents#onEntityTick`, gated on
`hasData(EMPLOYEE)`, every 40 ticks) for the breeding lock and altar tether; Phase 7 added the
tier-revert check to the same heartbeat. This phase adds one more cheap comparison to the same
check rather than introducing a second ticking system — the restock granularity (every 2 real
seconds) is more than fine against a config default measured in minutes.

## D-04: "At most once on reload, no burst" is free from the comparison's own shape

`if (now - lastRestock >= interval) { restock(); lastRestock = now; }` restocks exactly once no
matter how large `now - lastRestock` is — an employee unloaded for a real-world week comes back to
exactly one restock on its first post-load check, then resumes the normal cadence. No explicit
"catch up N times" loop was ever written, so there is nothing to accidentally get wrong here.

## D-05: The interval is a `ModConfig.Type.COMMON` value (STOCK-02)

The mod's first config value. `COMMON` (not `SERVER`) because restock timing is server-authoritative
logic with no legitimate per-world-save override need — a single shared config file is the
simplest correct choice, and NeoForge's `ModConfigSpec`/`registerConfig` is the standard, already-
present-on-the-classpath mechanism (no new dependency).

## D-06: Initialize the timer at bind time, not lazily on first tick

`EmployeeManager.bind()` writes `RESTOCK_TIMER = level.getGameTime()` before `addFreshEntity`. A
default-supplied `0L` (for a pre-Phase-8 save's employee that predates this attachment) reads as
"long ago" on that employee's very next periodic check, correctly restocking it once and then
behaving normally forever after — the same "no burst" guarantee (D-04) covers this backward-compat
case for free, so no separate migration logic was needed.

## Issues / accepted limitations

None new this phase — this is a small, self-contained addition on top of Phase 6/7's already-
established periodic-check infrastructure.
