---
phase: 02-economy-items-soul-altar-block
plan: 05
subsystem: client
tags: [neoforge, blockentityrenderer, EntityRenderersEvent, client-class-leak, runServer, emissive, particles, java21]

requires:
  - phase: 02-economy-items-soul-altar-block
    provides: "02-01 — SoulAltarBlockEntity.isEmpty()/getHeldSoulBlock() + getUpdateTag/getUpdatePacket client sync; ModBlockEntities.SOUL_ALTAR_BE; client/ClientModBusEvents.java (Dist.CLIENT mod-bus subscriber)"
  - phase: 02-economy-items-soul-altar-block
    provides: "02-04 — SoulAltarBlock#useItemOn calls level.sendBlockUpdated(...) after a socket so the client BE mirrors server truth; assets/secondshift/models/block/soul_block.json; ModBlocks.SOUL_ALTAR VoxelShape (top plate y 11..14)"
provides:
  - "client/render/SoulAltarRenderer — BlockEntityRenderer<SoulAltarBlockEntity>; early-returns on be.isEmpty(), else renders the secondshift:soul_block model recessed into the altar top at LightTexture.FULL_BRIGHT (emissive, no bob) + a throttled ParticleTypes.SOUL wisp"
  - "client/ClientModBusEvents#onRegisterRenderers — EntityRenderersEvent.RegisterRenderers handler wiring SoulAltarRenderer::new to ModBlockEntities.SOUL_ALTAR_BE"
  - "Phase-2 mandatory ./gradlew runServer client-class-leak gate — PASSED (Done (0.320s), no NoClassDefFoundError, no net/minecraft/client reference, SoulAltarRenderer never loaded on the dedicated server)"
affects: []

tech-stack:
  added: []
  patterns:
    - "Client-only BlockEntityRenderer: implements BlockEntityRenderer<T>, ctor takes BlockEntityRendererProvider.Context (matches ::new), render() is a pure function of BE accessors; registered ONLY from EntityRenderersEvent.RegisterRenderers in the Dist.CLIENT mod-bus subscriber"
    - "Emissive embedded model: Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY) inside push/scale/recentre/pop, no time-based transform"
    - "Throttled BER particle: level.getRandom().nextInt(N) roll inside render() gates level.addParticle(ParticleTypes.SOUL, ...) so the wisp stays slow + sparse"

key-files:
  created:
    - src/main/java/com/cxmxrgo/secondshift/client/render/SoulAltarRenderer.java
  modified:
    - src/main/java/com/cxmxrgo/secondshift/client/ClientModBusEvents.java

key-decisions:
  - "Embedded model drawn via BlockRenderDispatcher#renderSingleBlock (the 5-arg vanilla overload) rather than ItemRenderer#renderStatic — simplest path for a block model; NeoForge marks the 5-arg form deprecated in favour of the ModelData overload but it compiles + renders fine (harmless javac deprecation note)"
  - "FULL_BRIGHT applied as the packedLight argument to renderSingleBlock (LightTexture.FULL_BRIGHT = 0xF000F0), NOT via a custom render type / emissive model flag — eye-of-ender style, D-05"
  - "Wisp throttle: level.getRandom().nextInt(50) == 0 per render frame (~once/second at 60fps), emitted 0.2 above the embedded block with a tiny +y velocity — slow + sparse per D-05"
  - "Placement constants: horizontal centre 0.5, embed centre y 0.78, scale 0.5 — reads as recessed into the altar top plate; exact aesthetic is a human-check item"
  - "Task 2 required zero renderer edits — the grep gate + runServer both passed first try, so no cross-plan file touch was needed"

patterns-established:
  - "Charged-altar BER pattern: BE-state-driven client geometry with FULL_BRIGHT emissive + throttled particle, isolated in client/render/ and named only from ClientModBusEvents"

requirements-completed: [POL-03]

duration: 9min
completed: 2026-09-04
---

# Phase 2 Plan 05: Charged Soul Altar Renderer Summary

**A client-only `BlockEntityRenderer<SoulAltarBlockEntity>` now draws the socketed Soul Block embedded and full-bright (eye-of-ender style, no bob) in the altar top with a slow soul-wisp, registered from `EntityRenderersEvent.RegisterRenderers`, and the phase's mandatory `./gradlew runServer` client-class-leak gate passes clean — closing Phase 2.**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-09-04T10:56:00Z
- **Completed:** 2026-09-04T11:05:00Z
- **Tasks:** 2 completed
- **Files created/modified:** 2 (1 created, 1 modified)

## Accomplishments

- `SoulAltarRenderer` implements `BlockEntityRenderer<SoulAltarBlockEntity>`: `be.isEmpty()` short-circuits to render nothing extra; a charged altar renders `ModBlocks.SOUL_BLOCK.defaultBlockState()` via `BlockRenderDispatcher#renderSingleBlock` scaled to 0.5 and recessed into the top plate at `LightTexture.FULL_BRIGHT`, with no rotation/bob.
- Throttled `ParticleTypes.SOUL` wisp: a `RandomSource.nextInt(50)` roll per frame gates a single upward soul particle just above the embedded block.
- `ClientModBusEvents.onRegisterRenderers` (`EntityRenderersEvent.RegisterRenderers`, mod bus, `Dist.CLIENT`) wires `SoulAltarRenderer::new` to `ModBlockEntities.SOUL_ALTAR_BE`; class Javadoc updated (no longer says "no render code").
- Grep gate green: no `SoulAltarRenderer` / `PoseStack` / `MultiBufferSource` / `net.minecraft.client` reference under `content/`, `event/`, or `registry/`.
- **Phase-2 mandatory `./gradlew runServer` — PASSED:** dedicated server reached `Done (0.320s)! For help, type "help"` with `secondshift` loaded, gametest namespace enabled, and NO `NoClassDefFoundError` / `ClassNotFoundException` / `net/minecraft/client` reference in `run/logs/latest.log`. `SoulAltarRenderer` was never class-loaded server-side — the renderer (and all of Phase 2's client code) is proven isolated.
- Final `./gradlew runClient` smoke: reached `Sound engine started` / `OpenAL initialized`; `[SecondShift] registered SoulAltarRenderer for secondshift:soul_altar` logged; `4 item(s), 2 block(s), 1 block-entity type(s), 1 creative tab(s)` all bound; no `Unbound registry entries`, no `Unresolved lang keys`, no `NoClassDefFoundError`, no missing-model/texture errors for `soul_*`.

## Task Commits

1. **Task 1: SoulAltarRenderer + RegisterRenderers wiring** — `b590b89` (feat)
2. **Task 2: Client-class-leak gate — the mandatory phase runServer** — no commit (verification-only; the renderer needed no edits — `runServer` + grep gate passed first try)

**Plan metadata:** (final docs commit)

## Files Created/Modified

- `client/render/SoulAltarRenderer.java` (created) — `BlockEntityRenderer<SoulAltarBlockEntity>`; `render()` early-returns on `be.isEmpty()`, else `push → translate(0.5, 0.78, 0.5) → scale(0.5) → translate(-0.5,-0.5,-0.5) → renderSingleBlock(soulBlock, pose, buffers, FULL_BRIGHT, NO_OVERLAY) → pop`, then `emitWisp(be)`.
- `client/ClientModBusEvents.java` (modified) — added `onRegisterRenderers(EntityRenderersEvent.RegisterRenderers)` calling `event.registerBlockEntityRenderer(ModBlockEntities.SOUL_ALTAR_BE.get(), SoulAltarRenderer::new)`; added imports for `SoulAltarRenderer`, `ModBlockEntities`, `EntityRenderersEvent`; updated class Javadoc.

## Plan `<output>` — required records

| Item | Result |
|------|--------|
| **Block-render call for the embedded model** | `Minecraft.getInstance().getBlockRenderer().renderSingleBlock(BlockState, PoseStack, MultiBufferSource, int packedLight, int packedOverlay)` — the vanilla 5-arg overload on `BlockRenderDispatcher`. State is `ModBlocks.SOUL_BLOCK.get().defaultBlockState()`. (NeoForge marks this overload deprecated in favour of the `ModelData` / `RenderType` variant — compiles + renders fine; only a `javac` note.) |
| **How `FULL_BRIGHT` was applied** | Passed `LightTexture.FULL_BRIGHT` (`0xF000F0`) as the `packedLight` argument to `renderSingleBlock`, with `OverlayTexture.NO_OVERLAY` as `packedOverlay`. No custom render type, no emissive model flag. |
| **Wisp throttle** | Per render frame: `be.getLevel().getRandom().nextInt(50) != 0` → skip; otherwise one `level.addParticle(ParticleTypes.SOUL, x, y+0.2, z, 0, 0.015, 0)` with a ±0.075 horizontal jitter. ~1 particle/second at 60 fps. |
| **`runServer` "Done" line** | `[04Sept2026 10:59:18.839] [Server thread/INFO] [net.minecraft.server.dedicated.DedicatedServer/]: Done (0.320s)! For help, type "help"` |
| **No-leak confirmation** | `run/logs/latest.log` scanned — zero matches for `NoClassDefFoundError\|ClassNotFoundException\|net/minecraft/client`. `secondshift` present, gametest namespace `[secondshift]` enabled. `SoulAltarRenderer` never appears in the server log (not class-loaded). |
| **Final `runClient` smoke result** | Reached `Sound engine started`; renderer registration logged; all 4 items / 2 blocks / 1 BE type / 1 tab bound; no unbound-registry / lang-key / NoClassDefFound / missing-asset errors. |
| **Cross-plan file touch to fix a leak** | None — no leak, no `content/`/`registry/` edits needed. |

## Decisions Made

See `key-decisions` frontmatter. Highlights: `renderSingleBlock` (5-arg vanilla overload) for the embedded model; `FULL_BRIGHT` applied as the packed-light argument (not a render-type trick); wisp throttled by a per-frame `RandomSource` roll.

## Deviations from Plan

None - plan executed exactly as written. Task 2 was verification-only and produced no code changes (the grep gate and `runServer` both passed on the first attempt).

## Issues Encountered

None. `./gradlew build` green first try (2 pre-existing `EventBusSubscriber.Bus` deprecation warnings carried over from the Phase 1 subscriber style — out of scope, unchanged). The `runClient` shader warning `rendertype_entity_translucent_emissive could not find sampler named Sampler2` is a vanilla dev-client log line unrelated to this mod.

## Known Stubs

None. `assets/secondshift/textures/block/soul_altar.png` and `soul_block` textures remain the placeholder-quality art from 02-03/02-04 (POL-03 permits placeholder art this phase; hand-drawn art is Phase 10 polish) — not a stub in this plan's scope.

## Verify-time flags (surface at end-of-phase verification)

- **HUMAN-CHECK deferred to the phase gate (`human_verify_mode: end-of-phase`):** empty altar shows only the pedestal; socketing a Soul Block makes it appear embedded + glowing immediately (live sync), full-bright in shadow, occasional slow soul wisp, no bob; charged render stays correct after save-quit-reload and a chunk unload/reload round trip (`02-VALIDATION.md` row 2-05-01, POL-03).
- Placement constants (`EMBED_Y = 0.78`, `EMBED_SCALE = 0.5`) are a first pass — if the embedded block reads as floating above the plate rather than recessed, nudge `EMBED_Y` down / `EMBED_SCALE` down in `SoulAltarRenderer`.
- No custom `SoundEvent`/`ParticleType` registration this phase (deferred to Phase 10, POL-05) — the wisp borrows vanilla `ParticleTypes.SOUL`.

## Next Phase Readiness

- **Phase 2 is complete.** The tangible soul economy is fully in place: Harvester instakill + Soul Fragment drop (02-02), Soul Block + assets (02-03), Soul Altar craft/place/socket/break (02-04), charged-altar visual (02-05). The mandatory `runServer` client-class-leak gate for the phase is closed.
- Phase 3 (menu/screen harness — the HARD GATE) is unblocked: `ClientModBusEvents` is the established `Dist.CLIENT` mod-bus subscriber to extend with `RegisterMenuScreensEvent`; the BE client-sync quartet pattern is proven end-to-end.

## Self-Check: PASSED

- `src/main/java/com/cxmxrgo/secondshift/client/render/SoulAltarRenderer.java` — FOUND
- Commit `b590b89` — FOUND in git history
- `./gradlew build` exit 0; `runServer` reached "Done" with no client-class leak; `runClient` reached the sound engine with the renderer registered.

---
*Phase: 02-economy-items-soul-altar-block*
*Completed: 2026-09-04*
