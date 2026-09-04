# Phase 1: Skeleton & Feedback Loop - Context

**Gathered:** 2026-09-04
**Status:** Ready for planning

<domain>
## Phase Boundary

A pinned NeoForge 1.21.1 project skeleton that:
1. Builds a loadable jar via the Gradle wrapper (`./gradlew build`)
2. Launches to the main menu under `./gradlew runClient` and to "Done" under `./gradlew runServer`, mod loaded on both sides
3. Deploys itself into the CurseForge "test" instance's `mods/` folder
4. Aborts loading loudly, with a named list of the offending entries, on any unbound `DeferredRegister` entry

Requirements in scope: BUILD-01, BUILD-02, BUILD-03, BUILD-04, BUILD-05, POL-09 (6 total).

**Not in this phase:** any real mod content (items, blocks, altar, GUI, employees). The only registry
content is a single throwaway debug entry that exists to prove the guardrail fires (see D-07). Real
items/blocks begin in Phase 2.

</domain>

<decisions>
## Implementation Decisions

### Deploy loop & versioning (discussed in depth)

- **D-01:** Deployment is a dedicated Gradle task — `deployToTest` — that `dependsOn` `build`. It is
  **run on demand**, never wired into the `build` lifecycle. Claude runs `./gradlew deployToTest` only
  when an iteration is ready for the user to test in-game; plain `./gradlew build` (Claude's own
  compile-checks) must never write into the live instance.
- **D-02:** `deployToTest` first deletes the existing `secondshift-*.jar` from the instance `mods/`
  folder (glob match), then copies the freshly built jar in. Exactly one mod jar sits in `mods/` at
  any time.
- **D-03:** `mod_version` stays `0.1.0` for the entire v1 build (all 10 phases). No per-phase bump, no
  git-hash suffix. The glob-delete in D-02 depends on this staying a single predictable name.
- **D-04:** If `deployToTest` cannot write to the instance — game running and holding a Windows file
  lock, or the configured path does not exist — it **fails loudly** with a clear, actionable message
  (e.g. "close Minecraft and re-run" / "test instance path not found: <path>"). It does not warn-and-
  continue. The `build/libs/` jar remains regardless, so a failed deploy never produces a silent
  stale-jar test.
- **D-05:** The stale `second-shift-0.1.0.jar` left by the old draft (note: dashed name, different mod
  id `second-shift` vs `secondshift`) is deleted **once, manually, during Phase 1 setup**. After that
  the `secondshift-*.jar` glob in D-02 is the only cleanup needed — `deployToTest` does not sweep the
  dashed pattern.
- **D-06:** The test-instance mods path lives in the **committed** `gradle.properties` as
  `test_instance_mods_dir=C:/Users/user/curseforge/minecraft/Instances/test/mods` (forward slashes for
  Gradle). It is personal-repo machine config and is fine to commit; no gitignored local override.
  `deployToTest` reads this key.

### Skeleton scope / token registry content (research default — user deferred)

- **D-07:** To satisfy BUILD-03 / success criterion 3 (temporarily un-registering a `DeferredRegister`
  must abort startup with a named list), Phase 1 ships **one throwaway debug item** in a real
  `DeferredRegister<Item>` attached in the `@Mod` constructor. It needs no texture, model, recipe, or
  creative-tab entry — it exists only to give the self-check something to bind and to exercise the
  "comment out `.register(modBus)` → startup aborts" test. It is **removed in Phase 2** when the real
  `ModItems` register lands. Do not bring the Harvester / Soul Fragment / Soul Block forward into
  Phase 1.

### Self-check strictness (research default — user deferred)

- **D-08:** The unbound-registry self-check follows PITFALLS.md §1 exactly: an `FMLLoadCompleteEvent`
  handler (`event.enqueueWork`) that streams `getEntries()` across every mod `DeferredRegister`,
  filters `!isBound()`, and `throw`s an `IllegalStateException` listing the unbound IDs. It **hard-
  aborts on both dev and the packaged jar** — this is a personal mod and a loud crash with a named
  list is the desired outcome, not a soft log. Fires on client, dedicated server, and `runData`.
- **D-09:** Phase 1 scope for the check is **`isBound()` only**. The descriptionId/lang-key
  resolution check that PITFALLS.md suggests (every registered object's `descriptionId` resolves to an
  `en_us.json` key) is **not** built in Phase 1 — revisit when there is real registered content with
  translation keys (Phase 2+ / Phase 10 polish).
- **D-10:** The register list fed to the self-check is **hand-maintained** (an explicit
  `Stream.of(REGISTER_A, REGISTER_B, ...)` in the handler), matching the research example. Each new
  `Mod*` register class added in later phases must be added to this stream — call that out in those
  phases' plans.

### runClient parity (research default — user deferred)

- **D-11:** `runClient` stays a **clean room** — it runs only Second Shift, without owo-lib /
  accessories / wildcard. It is the fast iteration + registration/GUI-crash gate. The CurseForge
  "test" instance (which has those three mods) is the separate compatibility gate, exercised via
  `deployToTest` + a real launch. Do **not** copy the three mods into `run/mods/`.
- **D-12:** Phase 1 must **verify that FML actually scans `run/mods/`** in this MDG 2.0.146 / 1.21.1
  setup (SUMMARY.md flags this as an unverified assumption). If it does not, note the actual dev-mods
  mechanism for later phases; nothing in v1 currently depends on extra dev mods, so a negative result
  is informational, not blocking.

### Claude's Discretion

- Exact `deployToTest` task implementation (Groovy `Copy` + `delete`, or a `doLast` block), task
  group/description, and whether it prints the resolved destination path on success.
- Names of the `Mod*` register holder classes and the debug item (`debug_marker`, `placeholder`,
  etc.).
- The `gradle.properties` key name if `test_instance_mods_dir` collides with anything from the MDK
  template (it does not, but Claude may prefix it).
- Whether the one-time dashed-jar delete (D-05) is done via a throwaway `./gradlew` invocation, a
  shell `rm`, or folded into the first `deployToTest` run as a documented one-off.
- All toolchain wiring already fixed by STACK.md (build.gradle / settings.gradle / gradle.properties /
  neoforge.mods.toml contents, the `-Xmx2G`, the removed `gameTestServer` run, `neo_version_range`).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Toolchain & bootstrap (Phase 1 is a full transcription of this)
- `.planning/research/STACK.md` — the complete bootstrap spec: MDK download/overwrite procedure,
  exact `build.gradle` / `settings.gradle` / `gradle.properties` / `neoforge.mods.toml` contents,
  version pins (NeoForge 21.1.248, MDG 2.0.146, Gradle 9.2.1, Parchment 2024.11.17, Java 21), the
  `neo_version` vs `neo_version_range` split, the removed `maven-publish` / `gameTestServer` blocks,
  `forge.logging.markers=REGISTRIES`, and the stale-jar deletion.
- `.planning/research/STACK.md` §1 — `DeferredRegister` creation + "every register attached in the
  `@Mod` constructor" rule.
- `CLAUDE.md` — mirrors the STACK.md stack tables (Recommended Stack, What NOT to Use, Version
  Compatibility); the `## GSD Workflow Enforcement` and constraint sections are authoritative for how
  Claude runs the build.

### The self-check / guardrail (BUILD-03)
- `.planning/research/PITFALLS.md` §1 — the unbound-`DeferredRegister` root-cause analysis, the
  "which tool catches it" table (only `runClient` and the self-check do), and the exact
  `FMLLoadCompleteEvent` + `getEntries().filter(!isBound())` self-check code.
- `.planning/research/ARCHITECTURE.md` "The rule that prevents the crash" + the `SecondShift`
  constructor sketch (register-every-`DeferredRegister`-in-one-visible-block).
- `.planning/research/ARCHITECTURE.md` Slice 0 — the Phase 1 acceptance test ("`runClient` reaches
  the main menu; mod appears in the Mods list; `runServer` starts").

### Pitfalls that land in Phase 1
- `.planning/research/PITFALLS.md` §2 — `@EventBusSubscriber` "bus" attribute is ignored in NeoForge
  21.1; grep `debug.log` for the bus-assignment line each launch.
- `.planning/research/PITFALLS.md` §9 — client classes leaking into common code is invisible in
  single-player; one `runServer` per phase is the mitigation.
- `.planning/research/PITFALLS.md` "Looks Done But Isn't" checklist — `pack.mcmeta` `pack_format` 48,
  singular 1.21 data folder names (`recipe/`, `loot_table/`, `advancement/`).
- `.planning/research/SUMMARY.md` "Gaps to Address" — the `run/mods` scan verification (D-12) and the
  runClient-vs-instance parity framing (D-11).

### Scope & requirements
- `.planning/ROADMAP.md` "Phase 1: Skeleton & Feedback Loop" — goal, the 4 success criteria, requirement list.
- `.planning/REQUIREMENTS.md` — BUILD-01..05, POL-09 full text.

### External (not in-repo)
- `https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle` — the authoritative 1.21.1 MDK template;
  source of the Gradle wrapper jar (which cannot be authored as text) and the baseline text files
  STACK.md then overwrites.
- `https://docs.neoforged.net/docs/1.21.1/` — versioned NeoForge docs (registries, sides, events).

</canonical_refs>

<code_context>
## Existing Code Insights

Greenfield — the repo currently contains only `CLAUDE.md` and `.planning/`. No source tree, no Gradle
files, no `.gitignore` for build artifacts yet.

### Reusable Assets
- None (no code).

### Established Patterns
- None yet. Phase 1 *establishes* the foundational patterns every later phase depends on:
  - `registry/Mod*` holder classes, each `.register(modBus)`'d in the `SecondShift` constructor as one
    visible block.
  - The `client/` package convention (client-only code isolated by package; never imported from
    common) — scaffolded now even though no client code exists yet.
  - The hand-maintained self-check register list (D-10).

### Integration Points
- The `@Mod` constructor `SecondShift(IEventBus modBus, ModContainer container)` is the single wiring
  point for all future `DeferredRegister`s, payloads, config, and setup listeners.
- `test_instance_mods_dir` in `gradle.properties` + the `deployToTest` task is the seam between the
  build and the user's in-game testing for every subsequent phase.

</code_context>

<specifics>
## Specific Ideas

- The user wants the iteration loop to protect the live instance: Claude's routine compile-checks
  (`./gradlew build`) must be invisible to the running game / the `mods/` folder. Only an explicit
  `./gradlew deployToTest` touches it.
- A failed deploy must be impossible to miss — no silent fallback to a stale jar.
- Keep it simple for a solo personal mod: one jar name, one path key, no versioning ceremony.

</specifics>

<deferred>
## Deferred Ideas

- **descriptionId / lang-key resolution self-check** — extend the startup guardrail to assert every
  registered object's `descriptionId` resolves to an `en_us.json` entry (PITFALLS.md flags the
  draft's missing `menu.secondshift.binding_altar` title as exactly this bug). Belongs where real
  translated content exists — Phase 2 onward, or the Phase 10 polish sweep.
- **Per-phase version bumps / git-hash jar names** — considered and rejected for v1; could matter if
  the user later wants to keep a shelf of old jars to bisect regressions.
- **`deployToTest` sweeping the dashed `second-shift-*.jar` pattern** — unnecessary once the one-time
  manual delete is done; only revisit if the dashed jar somehow reappears.
- **Datagen / `runData`** — deliberately not wired in Phase 1 (research: hand-write JSON until ~10
  registry objects). Revisit when registry object count or blockstate variants justify it (likely
  around Phase 2–5).
- **owo-lib / accessories / wildcard in `run/mods/`** — only if a future phase needs dev-time parity
  with the instance; nothing in v1 does.

</deferred>

---

*Phase: 1-skeleton-feedback-loop*
*Context gathered: 2026-09-04*
