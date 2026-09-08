---
phase: 10-polish-config-invalid-states
mode: autonomous (user asleep — /gsd-autonomous mandate)
requirements: [POL-02, POL-05, POL-06, POL-07, POL-08]
depends_on: [2, 3, 4, 5, 6, 7, 8, 9]
---

# Phase 10: Polish, Config & Invalid States — Design Decisions

Written directly (no discuss-phase interview — user asleep, autonomous mandate). The final phase
of the milestone — depends on every prior phase, all now complete.

## D-01: POL-02 (complete lang coverage) verified by a build-time key audit, not a GameTest

A `Component.translatable(key)` always constructs successfully regardless of whether `key`
resolves — GameTest's dedicated server doesn't even load the client lang JSON as a resource pack
asset, so no GameTest can meaningfully assert "this key resolves to real text." The real check is
static: every literal string passed to `Component.translatable(...)` across the whole `src/main`
tree was extracted and diffed against every key defined in `en_us.json`. Zero gaps found (two
false-positive matches were vanilla's own `entity.minecraft.villager` key, picked up by the
extraction regex, not a real gap). Re-run after every new key this phase added (config section
titles). This audit is documented here rather than faked as a test.

## D-02: POL-05 sound/particle feedback for bind and promotion

Harvest already had layered FX since Phase 2 (`HarvesterEvents#playSoulHarvestFx`). Bind
(`BindingAltarMenu#attemptBind`) had none — added a soul-particle burst + `HAPPY_VILLAGER`
particles at the altar plus `SOUL_ESCAPE` + `VILLAGER_YES` sounds. Promotion
(`PromotionRitualMenu#confirmPromotion`) had none — added `ENCHANT` + `HAPPY_VILLAGER` particles at
the EMPLOYEE's own position (not the altar — promotion is about them, not the altar) plus
`PLAYER_LEVELUP`, deliberately reading as more triumphant than the bind FX. All vanilla particle
types and sound events — no new assets.

## D-03: POL-06 config — NeoForge's own generic `ConfigurationScreen`, no hand-rolled screen

NeoForge 21.1.248 ships `net.neoforged.neoforge.client.gui.ConfigurationScreen` +
`IConfigScreenFactory` — a real, generic config screen that reads any mod's registered
`ModConfigSpec` automatically. Registered via
`container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)` from
`FMLClientSetupEvent` (client-only — the registration call itself must never run on a dedicated
server, since `ConfigurationScreen` is a real GUI class). `SecondShift.CONTAINER` is a static field
set in the constructor specifically so `ClientModBusEvents` (a `Dist.CLIENT`-only class) can reach
the container without its own separate resolution path. No hand-rolled screen needed — this is
the standard, "spend nothing extra" way to satisfy POL-06's "a config screen" requirement on this
NeoForge version.

**New config values added this phase** (all `ModConfig.Type.COMMON`, alongside Phase 8/9's
`restockIntervalTicks`/`happinessQuitThresholdTicks`): `soulFragmentDropCount` (was hardcoded to
1), `happinessUnhappyMax`/`happinessOkMax` (were hardcoded 33/66 in `Happiness.fromMeter`),
`conversionImmunityEnabled`/`breedingLockEnabled` (the two Phase 6 trait immunities — each
documented as "an honest opt-out, not a balance lever," since a personal mod's config exists for
debugging/curiosity, not for a player to cheese its own systems).

## D-04: POL-07 HR job titles — a display-name prefix, refreshed on promotion

`JobTitles.forTier(int)` (Intern/Associate/Senior/Lead/Principal, clamped) is purely cosmetic —
never read back by any game logic, so it can never desync a save. Applied at `EmployeeManager.bind`
(tier 1) and refreshed (not appended) at `EmployeeManager.installPromotion` — `EmployeeData.name()`
is always the bare chosen name without any title, so rebuilding "`<title> <name>`" from scratch on
every promotion can never accumulate stale prefixes.

## D-05: POL-08 invalid-state coverage — already satisfied by Phases 2-9, confirmed by audit

Every state POL-08 lists already has a themed `Component.translatable` message from earlier
phases: no job block (`altar.no_job_block`, Phase 5), no Soul Block (`altar.no_soul_block`, Phase
5), unmapped job block (`altar.not_a_workstation`, Phase 5), empty pool
(`altar.empty_pool`, Phase 5), altar with no valid quarters (`happiness.cause.quarters`, Phase 9 —
shown via the same altar-interaction status readout HAPP-07 already built). No new code needed for
POL-08 itself; this phase's job was to confirm the coverage is real and complete, which the D-01
lang audit does as a side effect (every one of those keys appeared in the "defined" set with no
gap).

## Issues / accepted limitations

None new this phase — every piece here builds directly on infrastructure Phases 2-9 already
established (the config pattern from Phase 8, the message style from every prior phase, the
attachment/event patterns throughout).
