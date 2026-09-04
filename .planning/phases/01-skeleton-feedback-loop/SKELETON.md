# Walking Skeleton — Second Shift

**Phase:** 1
**Generated:** 2026-09-04

## Capability Proven End-to-End

Running `./gradlew runClient` launches Minecraft 1.21.1 on NeoForge 21.1.248 with the
Second Shift mod loaded (listed in the Mods menu), a **real** `DeferredRegister<Item>`
holding one entry that binds successfully, and an `FMLLoadCompleteEvent` self-check that
**hard-aborts the launch with a named list** the moment any registry holder is left
unbound — and `./gradlew deployToTest` copies that exact jar into the CurseForge "test"
instance's `mods/` folder on demand, replacing the previous build.

This is the thinnest end-to-end stack that proves the feedback loop the prior draft
lacked: a registration bug now surfaces in seconds under `runClient` / `runData` instead
of as a cryptic client-init `NullPointerException` in a crash report.

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Build system | ModDevGradle (MDG) `2.0.146` on the Gradle `9.2.1` wrapper | Official `MDK-1.21.1-ModDevGradle` pairing; simplest buildscript for a single-version solo mod (STACK.md) |
| Mod loader / game / JDK | NeoForge `21.1.248` / Minecraft `1.21.1` / Temurin JDK 21 | Hard constraint — must match the CurseForge "test" instance exactly |
| Version strategy | Compile-pin `neo_version=21.1.248`; runtime range `neo_version_range=[21.1,)`; `mod_version` frozen at `0.1.0` for all of v1 (D-03) | Patch-rollback safety without bricking the jar; one predictable jar name (`secondshift-0.1.0.jar`) so the deploy glob is stable |
| Group / mod id | `mod_group_id=com.cxmxrgo.secondshift`, `mod_id=secondshift` | STACK.md; `base.archivesName = mod_id` names the jar |
| Registration pattern | Flat `registry/Mod*` holder classes; **every** `DeferredRegister` attached to the mod bus in the `SecondShift` `@Mod` constructor as one visible block | Structurally prevents the unbound-holder crash that killed the prior draft (ARCHITECTURE "The rule that prevents the crash") |
| Registry guardrail | `FMLLoadCompleteEvent` self-check streaming `getEntries()` / `!isBound()` over a **hand-maintained** register list (D-08/D-09/D-10); hard-throws `IllegalStateException` on both dev and the packaged jar | Turns a cryptic `DeferredHolder#value()` NPE into a named list of unbound IDs (PITFALLS §1) |
| Client / server split | Annotation + package: all client-only code under `com.cxmxrgo.secondshift.client`, isolated with `@EventBusSubscriber(value = Dist.CLIENT)`, never imported from common code | Dedicated-server safety is invisible to a single-player-only test loop (PITFALLS §9); one `./gradlew runServer` per phase is the check |
| Event-bus reasoning | Never rely on the `@EventBusSubscriber(bus = …)` attribute — FML 4.0.43 ignores it and picks the bus from the event type. Grep `run/logs/debug.log` for the `Subscribing @EventBusSubscriber class … to the {mod,game} event bus` line each launch | PITFALLS §2 |
| Dev iteration loop | `./gradlew runClient` is the primary loop and a **clean room** — only Second Shift, no owo-lib / accessories / wildcard (D-11). One `./gradlew runServer` per phase for dist-safety | Fast registration/GUI-crash gate, kept separate from the compatibility gate |
| Deploy loop | On-demand `./gradlew deployToTest` (`dependsOn build`, **never** wired into the `build` lifecycle — D-01) reads `test_instance_mods_dir` from committed `gradle.properties` (D-06), glob-deletes `secondshift-*.jar` then copies the fresh jar (D-02), and fails loudly on a missing path or file lock (D-04) | Claude's routine `./gradlew build` compile-checks never touch the live instance; a failed deploy can never produce a silent stale-jar test |
| Datagen | Not wired. The `data` run block stays in `build.gradle` (costs nothing) but no providers exist; hand-write JSON until ~10 registry objects | Avoids "provider ran but produced nothing" noise while the real risk is elsewhere (STACK.md §12) |
| Removed from stock MDK | `maven-publish` / `publishing {}` (no publishing — project constraint); the `gameTestServer` run (crashes with no gametests registered) | STACK.md "Differences from the stock MDK" |
| Directory layout | `src/main/java/com/cxmxrgo/secondshift/{registry,content,employee,trade,menu,network,client}` | ARCHITECTURE.md "Recommended Project Structure" — one package per concern; `registry/` flat and behaviour-free |
| Resource pack | `pack.mcmeta` `pack_format` **48**; datapack folders use the **singular** 1.21 names (`recipe/`, `loot_table/`, `advancement/`) | POL-09; the prior draft shipped `pack_format` 34 (PITFALLS "Looks Done But Isn't") |
| Libraries | None. No Mixin, no Access Transformers, no JEI/owo-lib/Curios dependencies | Every behaviour in the roadmap is reachable via public NeoForge events + APIs (STACK.md) |

## Stack Touched in Phase 1

- [x] Project scaffold — MDG plugin, Gradle wrapper, `build.gradle` / `settings.gradle` / `gradle.properties`, `.gitignore`, Java 21 toolchain, `REGISTRIES` log marker
- [x] Entrypoint + run configs — `@Mod("secondshift")` class; `runClient` / `runServer` / `runData` configured
- [x] "Real read/write" of the registry — a real `DeferredRegister<Item>` with one bound entry (D-07), plus the self-check that reads `getEntries()` / `isBound()` back
- [x] UI — `./gradlew runClient` reaches the main menu with the mod loaded
- [x] Deployment — `./gradlew deployToTest` into the CurseForge "test" instance, plus `./gradlew runClient` as the documented full-stack local run

## Out of Scope (Deferred to Later Slices)

Being explicit so later phases do not re-litigate Phase 1's minimalism:

- Any real mod content — items, blocks, the Soul Altar, GUI/menu, employees, attachments, data components (Phase 2+). The only registry content is the D-07 throwaway `debug_marker` item, removed in Phase 2.
- The descriptionId / lang-key resolution self-check — the guardrail is `isBound()` **only** in Phase 1 (D-09). Revisit when real translated content exists (Phase 2+ / Phase 10).
- Per-phase version bumps / git-hash jar names — `mod_version` stays `0.1.0` (D-03).
- `deployToTest` sweeping the dashed `second-shift-*.jar` pattern — one-time manual delete only (D-05).
- Datagen / `runData` provider wiring — deliberately unwired (STACK.md §12).
- owo-lib / accessories / wildcard in `run/mods/` — `runClient` stays a clean room (D-11); the CurseForge instance is the separate compatibility gate.
- Complete `en_us.json`, models, textures, recipes, advancements, creative tab — Phase 2 and Phase 10.

## Subsequent Slice Plan

Each later phase adds one vertical slice on top of this skeleton without altering its
architectural decisions:

- **Phase 2 — Economy Items & Soul Altar Block:** Harvester / Soul Fragment / Soul Block items + creative tab + models/textures + vanilla recipes; `SoulAltarBlock` (`EntityBlock`) + `SoulAltarBlockEntity`; guaranteed 1-Fragment Harvester kill. Removes the D-07 `debug_marker`; adds `ModItems` / `ModBlocks` / `ModBlockEntities` to the self-check register list (D-10).
- **Phase 3 — Menu & Screen Harness (HARD GATE):** `ModMenus` + `MENUS.register(modBus)`; empty "Binding Altar" screen opens under `runClient` with no crash. The exact failure area the prior draft died on.
- **Phase 4 — Employee Attachment & Spawn:** `ModAttachments` + `EmployeeData` (record + `CODEC` + `STREAM_CODEC` + `.sync` + version field); bind spawns a persistent, named villager with synced attachment data.
- **Phase 5 — Profession Resolution & Trade Picker:** the core value — pick profession + tier-1 trades from the real vanilla pool at the altar.
- **Phase 6 — Employee Traits, Death & Firing.** **Phase 7 — Progression & Promotion Ritual.** **Phase 8 — Mod-Owned Restock.** **Phase 9 — Quarters & Happiness.** **Phase 10 — Polish, Config & Invalid States.**
