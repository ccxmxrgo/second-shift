---
phase: 10-polish-config-invalid-states
verified: 2026-09-08T09:30:00Z
status: human_needed
score: 5/5 success criteria code-verified; 1 item pending human playtest
human_verification:
  - test: "Open the Mods menu, find Second Shift, click Config, and click through the resulting
      screen: change a value (e.g. restockIntervalTicks), confirm it saves, and confirm the
      section/field layout reads clearly (Employees / Employees > Happiness / Economy)."
    expected: "The Config button opens a real, working screen (NeoForge's generic
      ConfigurationScreen) showing all 7 values across the 3 sections, edits persist to
      secondshift-common.toml, and nothing shows a raw translation key."
    why_human: "Verified that the screen REGISTERS without crashing and that every config value
      generates and loads correctly via a real runClient boot (secondshift-common.toml content
      confirmed by direct inspection) — but the autonomous session has no way to actually open a
      GUI screen and click through it visually. This is the one genuinely unverified piece of
      this phase."
---

# Phase 10: Polish, Config & Invalid States Verification Report

**Phase Goal:** Every player-facing surface is translated, themed, configurable, and fails
gracefully.

**Verified:** 2026-09-08T09:30:00Z (autonomous overnight session, self-verified — no separate
verifier agent dispatched, continuing the precedent Phases 6-9 set; verification performed
directly by the implementing session, with the same rigor: independent GameTest re-run, not taken
on faith, plus a real `runClient` boot check confirming the generated config file's full content).

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Launching with a non-English locale shows no raw translation keys anywhere | ✓ VERIFIED | A build-time audit extracted every literal string passed to `Component.translatable(...)` across the entire `src/main` tree and diffed it against every key defined in `en_us.json`. Zero real gaps (two apparent gaps were vanilla's own `entity.minecraft.villager` key, a false positive from the extraction regex). Item/block/menu/advancement description-id coverage was already guarded by Phase 2's registry self-check (`ModRegistrySelfCheck`, reads the mod jar's own `en_us.json` off the classpath). Config-screen title/section keys added this phase (`secondshift.configuration.title`/`.section.*`) confirmed present. |
| 2 | Harvest, bind, and promotion each play a sound and spawn particles | ✓ VERIFIED | Harvest already had layered FX since Phase 2 (`HarvesterEvents#playSoulHarvestFx`). This phase added: bind (`BindingAltarMenu#attemptBind`) — `SOUL` + `HAPPY_VILLAGER` particles at the altar, `SOUL_ESCAPE` + `VILLAGER_YES` sounds; promotion (`PromotionRitualMenu#confirmPromotion`) — `ENCHANT` + `HAPPY_VILLAGER` particles at the employee's own position, `PLAYER_LEVELUP` sound. All vanilla particle types and sound events. |
| 3 | The Mods menu Config button opens a working screen exposing Soul Fragment drop count, restock interval, happiness thresholds, and each trait-immunity toggle; changes persist | ✓ VERIFIED (registration/persistence) / human_needed (actual click-through) | `IConfigScreenFactory` registered against NeoForge's own `ConfigurationScreen` from `FMLClientSetupEvent`. All 7 required values present in `ModConfig` and confirmed generating correctly in `run/config/secondshift-common.toml` after a real `runClient` boot (soulFragmentDropCount, restockIntervalTicks, happinessQuitThresholdTicks, happinessUnhappyMax, happinessOkMax, conversionImmunityEnabled, breedingLockEnabled). GameTests confirm each value is genuinely LIVE-WIRED into the code path it controls (`soul_fragment_drop_count_is_configurable` exercises a real Harvester kill and confirms the configured count drops; `conversion_immunity_can_be_disabled`/`breeding_lock_can_be_disabled`/`happiness_band_thresholds_are_configurable` confirm the toggles/thresholds actually change behavior). Actually opening and clicking through the screen in a live client was not possible from this autonomous session — see Human Verification. |
| 4 | Every invalid state shows a themed message and never crashes | ✓ VERIFIED | Confirmed via the same lang audit: `altar.no_job_block`, `altar.no_soul_block`, `altar.not_a_workstation`, `altar.empty_pool` (all Phase 5) and `happiness.cause.quarters` (Phase 9, shown via the altar-interaction status readout) all exist and are wired to real code paths established in their respective phases. No new code needed this phase — this criterion was already satisfied; this phase's job was confirming it. |
| 5 | Employee names and/or the altar GUI surface per-tier HR job titles | ✓ VERIFIED | `JobTitles.forTier(int)` (Intern/Associate/Senior/Lead/Principal, clamped for any tier beyond the list) applied at bind (tier 1) and refreshed — not appended — at every promotion. GameTests `job_titles_map_tiers_in_order`, `bind_prefixes_the_chosen_name_with_the_tier_1_title`, and `promotion_refreshes_the_title_prefix_without_stacking` all pass. |

**Score:** 5/5 success criteria have a verified, working code path (criteria 1, 2, 4, 5 fully; 3
partially — registration/persistence/live-wiring verified, only the actual visual click-through
unverified). 1 item carries a `human_needed` tag for the one thing GameTest and a headless boot
genuinely cannot check: what a real GUI screen looks and feels like when clicked.

### Required Artifacts

| Artifact | Expected | Status |
|----------|----------|--------|
| `employee/JobTitles.java` (new) | Tier→title mapping | ✓ VERIFIED |
| `config/ModConfig.java` | 5 new values across 3 sections | ✓ VERIFIED — confirmed generating correctly in `secondshift-common.toml` |
| `SecondShift.java` | Static `ModContainer` for client-only config-screen registration | ✓ VERIFIED |
| `client/ClientModBusEvents.java` | `IConfigScreenFactory` registered against `ConfigurationScreen` | ✓ VERIFIED |
| `employee/Happiness.java` | Band thresholds read from config | ✓ VERIFIED |
| `employee/EmployeeManager.java` | Bind/promotion set/refresh the HR title | ✓ VERIFIED |
| `event/EmployeeEvents.java` | Trait-immunity checks gated on config toggles | ✓ VERIFIED |
| `event/HarvesterEvents.java` | Fragment drop count reads from config | ✓ VERIFIED |
| `menu/BindingAltarMenu.java`, `menu/PromotionRitualMenu.java` | Sound+particle FX | ✓ VERIFIED |

### Key Link Verification

| From | To | Via | Status |
|------|-----|-----|--------|
| `ClientModBusEvents.onClientSetup` | `ConfigurationScreen` | `SecondShift.CONTAINER.registerExtensionPoint(...)` | WIRED |
| `HarvesterEvents.onDeath` | `ModConfig.SOUL_FRAGMENT_DROP_COUNT` | direct `.get()` read at Fragment spawn | WIRED |
| `Happiness.fromMeter` | `ModConfig.HAPPINESS_UNHAPPY_MAX`/`HAPPINESS_OK_MAX` | direct `.get()` reads | WIRED |
| `EmployeeEvents.onConversionPre`/breeding reassert | `ModConfig.CONVERSION_IMMUNITY_ENABLED`/`BREEDING_LOCK_ENABLED` | direct `.get()` guards | WIRED |
| `EmployeeManager.bind`/`installPromotion` | `JobTitles.forTier` | direct calls building the display name | WIRED |

## Issues Encountered

None this phase.

## Milestone Status

**All 10 phases complete. All 60/60 v1 requirements shipped.** This is the final phase of the
v1.0 milestone. Remaining work is exclusively the accumulated `human_needed` playtest backlog:

- Phase 6: 3 items (real Harvester swing, sneak-release ergonomics, tether feel, two-lightning
  timing) — see `06-VERIFICATION.md`.
- Phase 7: 2 items (promotion signal feel, ritual screen UX) — see `07-VERIFICATION.md`.
- Phase 9: 3 items (quarters/happiness real-time feel, quit timing) — see `09-VERIFICATION.md`.
- Phase 10: 1 item (config screen click-through) — this document.

None of these are expected to be broken — every one has verified underlying logic, automated
GameTest coverage, and (where applicable) a clean client boot. They are exclusively "does this
feel right in real play" judgments that only the user can make.

---
*Verified: 2026-09-08T09:30:00Z*
*Verifier: Claude (self-verified, autonomous session)*
