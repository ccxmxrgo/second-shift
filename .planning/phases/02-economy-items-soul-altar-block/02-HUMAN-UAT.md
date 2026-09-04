---
status: passed
phase: 02-economy-items-soul-altar-block
source: [02-VERIFICATION.md]
started: 2026-09-04T11:20:00Z
updated: 2026-09-04T12:05:00Z
---

## Current Test

[all tests complete — passed]

## Tests

### 1. Creative tab — one tab, real icons
expected: Open the creative menu in `runClient`. Exactly one "Second Shift" tab; it contains Harvester, Soul Fragment, Soul Block, Soul Altar — each with a real icon, no black/magenta missing-texture cube.
result: pass

### 2. Harvester combat feel + foil
expected: Hold the Harvester in `runClient`; hit a row of 3 pigs. Only the struck pig takes damage (no sweep arc); ~2 hits to kill one pig (modest ~3-4 damage). Harvester and Soul Fragment show real models in hand; Soul Fragment has an enchant-glint foil.
result: pass

### 3. Soul Block render, light, wisp, drop
expected: Place a Soul Block in `runClient`; observe, then break it. Real textured block (no purple cube), emits light (~level 7), occasional rising soul wisps; breaking it drops exactly one Soul Block.
result: pass

### 4. Recipes in JEI/EMI + both conversion directions
expected: In the test instance with JEI/EMI: open the recipe viewer and search Harvester / Soul Block. All three recipes (harvester, soul_block, soul_fragment_from_block) appear automatically; crafting 4 Soul Fragments → Soul Block and Soul Block → 4 Soul Fragments both work at a vanilla table with no material loss.
result: pass

### 5. Discovery chain — recipe-book gating + toasts
expected: Fresh SURVIVAL world in `runClient`. `secondshift:harvester` absent from the recipe book until an emerald is obtained, then "Necromantic Apprentice" advancement toast + a recipe-unlock toast fire. `soul_block` (+ reverse) absent until the first Soul Fragment, then "First Harvest" toast. `soul_altar` absent until a Soul Block is crafted, then "Soul Mason" toast.
result: pass

### 6. Soul Altar socket — one-way, automation-proof, persists
expected: Place a Soul Altar in `runClient`. First right-click holding a Soul Block consumes exactly one Soul Block into the altar (soul sound + particle burst). Second right-click and empty-hand do nothing (one-way, no retrieval). Hopper/dropper cannot insert or extract. After `/data get block <pos>` and save-quit-reload, the BE still holds the Soul Block and the charged render persists.
result: pass

### 7. Empty altar break drops itself
expected: Break an EMPTY Soul Altar in `runClient` (with a pickaxe). The altar block drops itself (ALTAR-07). [CR-01 fix: altar now requires a pickaxe as the correct tool.]
result: pass

### 8. Charged altar break — D-04 behaviour (design confirm)
expected: Socket a Soul Block, then break the CHARGED altar. Nothing drops at all (not the Soul Block, not the altar block); the breaking player loses exactly 1 hp (half a heart); a cosmetic lightning bolt flashes with thunder — no fire, no damage to nearby blocks or mobs. Also confirm the "charged altar lost entirely" reading feels right.
result: pass (D-04 "charged altar lost entirely" accepted)

### 9. Charged altar renderer — emissive, no bob, sync after reload
expected: Socket a Soul Block; the embedded Soul Block appears in the altar top immediately, full-bright even in shadow, no bob/spin, occasional slow soul wisp. Walk away past chunk-unload distance and back, then save-quit-reload — render is still correct (not stale, not missing).
result: pass

### 10. Non-English locale — no raw lang keys
expected: Launch `runClient` (or the test instance) in a non-English locale. No raw `item.secondshift.*` / `block.secondshift.*` / `advancement.secondshift.*` keys shown anywhere; the startup lang-key self-check does not abort.
result: pass

### 11. Test-instance co-existence
expected: `./gradlew deployToTest` and launch alongside owo-lib / accessories / wildcard. Game loads with no crash; Second Shift content present.
result: pass

### 12. Harvester reap — exactly-1-Fragment (also GameTest-covered)
expected: Hit any villager (adult or baby) once with the Harvester → dies instantly, drops exactly one Soul Fragment. Sword kill → no Fragment, vanilla loot intact.
result: pass — behaviour correct. FOLLOW-UP: the harvest moment wants more visual punch (see Gaps).

## Summary

total: 12
passed: 12
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

### G-1 — Villager soul-harvest FX lacks visual impact (polish, non-blocking)
The reap works correctly but the death-moment FX (vanilla `SOUL_ESCAPE` sound + soul-particle burst) is underwhelming for "you just tore out a soul". Wants a bigger beat — e.g. an upward soul column + particles streaming to the player, a heavier sound sting, a brief flash. Candidate for the Phase 10 polish pass (POL-05, custom SoundEvent/ParticleType) or a small standalone tweak using vanilla particles now. Not a Phase 2 blocker — deferred.
