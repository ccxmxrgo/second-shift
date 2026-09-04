---
phase: 02-economy-items-soul-altar-block
plan: 03
subsystem: content
tags: [neoforge, datapack, recipes, advancements, loot-table, blockstate, blockmodel, json-1.21.1]
requires:
  - phase: 02-economy-items-soul-altar-block
    provides: "02-01 — ModItems.SOUL_FRAGMENT / SOUL_BLOCK_ITEM / HARVESTER + ModBlocks.SOUL_BLOCK (light 7, animateTick wisps) registry holders; complete en_us.json incl. all 3 advancement title/description keys + lang-key self-check"
provides:
  - "assets/secondshift/blockstates/soul_block.json — single unconditional variant -> secondshift:block/soul_block"
  - "assets/secondshift/models/block/soul_block.json — minecraft:block/cube_all, texture all=secondshift:block/soul_block"
  - "assets/secondshift/models/item/soul_block.json — parents the block model"
  - "assets/secondshift/textures/block/soul_block.png — 16x16 placeholder soul-lit texture (pale glow pores)"
  - "data/secondshift/loot_table/blocks/soul_block.json — minecraft:block drops-self, survives_explosion condition, random_sequence secondshift:blocks/soul_block"
  - "data/secondshift/recipe/harvester.json — minecraft:crafting_shaped, 3 emerald + 2 bone + 1 soul_soil scythe -> secondshift:harvester (D-11)"
  - "data/secondshift/recipe/soul_block.json — minecraft:crafting_shapeless, 4x secondshift:soul_fragment -> 1 secondshift:soul_block (ECON-03)"
  - "data/secondshift/recipe/soul_fragment_from_block.json — minecraft:crafting_shapeless, 1x secondshift:soul_block -> 4 secondshift:soul_fragment (D-13 reverse, lossless)"
  - "data/secondshift/advancement/necromantic_apprentice.json — inventory_changed(minecraft:emerald) -> rewards.recipes [secondshift:harvester] (D-15 step 1)"
  - "data/secondshift/advancement/first_harvest.json — inventory_changed(secondshift:soul_fragment) -> rewards.recipes [secondshift:soul_block, secondshift:soul_fragment_from_block] (D-15 step 2, D-16, checker W4)"
affects: [02-04-soul-altar-interaction, 02-05-altar-renderer]
tech-stack:
  added: []
  patterns:
    - "Hand-written 1.21.1 datapack JSON (D-17): recipe result is {\"id\",\"count\"}; item predicates use \"items\": [ { \"items\": [id] } ]; no advancement/recipes/*.json so recipes stay gated until rewards.recipes grants them"
    - "Block completeness quartet: blockstate + block model + item model + loot table (+ texture) authored together for every custom block"
    - "Reverse conversion recipe with no unlockedBy is unlocked by co-listing it in the gating advancement's rewards.recipes"
key-files:
  created:
    - src/main/resources/assets/secondshift/blockstates/soul_block.json
    - src/main/resources/assets/secondshift/models/block/soul_block.json
    - src/main/resources/assets/secondshift/models/item/soul_block.json
    - src/main/resources/assets/secondshift/textures/block/soul_block.png
    - src/main/resources/data/secondshift/loot_table/blocks/soul_block.json
    - src/main/resources/data/secondshift/recipe/harvester.json
    - src/main/resources/data/secondshift/recipe/soul_block.json
    - src/main/resources/data/secondshift/recipe/soul_fragment_from_block.json
    - src/main/resources/data/secondshift/advancement/necromantic_apprentice.json
    - src/main/resources/data/secondshift/advancement/first_harvest.json
  modified: []
key-decisions:
  - "Harvester grid = pattern [\"EEE\",\"  B\",\"S B\"], keys E=minecraft:emerald B=minecraft:bone S=minecraft:soul_soil — 3-emerald horizontal blade, 2-bone vertical haft, soul soil charge at the base; within the D-11 range (~3 emerald + bone haft + soul soil)"
  - "Loot table copied verbatim from vanilla 1.21.1 data/minecraft/loot_table/blocks/diamond_block.json shape: type minecraft:block, one pool rolls 1.0 / bonus_rolls 0.0, minecraft:survives_explosion condition, single minecraft:item entry, random_sequence secondshift:blocks/soul_block"
  - "Advancement item-predicate shape uses the explicit list form \"items\": [ { \"items\": [ \"<id>\" ] } ] (RESEARCH A8); vanilla also accepts the bare-string form — both parse, list form kept for clarity"
  - "Both advancements are parent-less roots with a full display block — the rewards.recipes mechanic works without an advancement tree (RESEARCH A8); they create their own tab in the advancements screen, no crash"
duration: 12min
completed: 2026-09-04
---

# Phase 2 Plan 03: Recipes & Discovery-Chain Advancements Summary

**The soul economy is now tangible and gated: the Soul Block is a complete real block (blockstate/models/texture/drops-self loot table), the Harvester crafts shaped and both Soul Block conversions craft shapeless & lossless, and no recipe is known until its `inventory_changed` advancement grants it via `rewards.recipes` (emerald -> Harvester, first Fragment -> Soul Block +/-).**

## Performance

- **Duration:** ~12 min
- **Tasks:** 3 completed
- **Files created:** 10 (0 modified)

## Accomplishments

- Soul Block is no longer a purple cube — single-variant blockstate -> `cube_all` block model -> item model parenting the block model, a 16x16 placeholder soul-lit texture, and a drops-self `minecraft:block` loot table at the singular `loot_table/blocks/` path.
- Three vanilla crafting recipes: shaped Harvester (3 emerald + 2 bone + soul soil scythe), shapeless 4 Fragment -> 1 Soul Block, shapeless 1 Soul Block -> 4 Fragment (D-13 lossless round-trip). All use the 1.21.1 `result.{id,count}` shape; none carries `unlockedBy` / `has_item` and there is no `data/secondshift/advancement/recipes/` directory.
- Two discovery-chain advancements (D-15 steps 1 & 2): `necromantic_apprentice` grants the Harvester recipe on the first emerald; `first_harvest` grants BOTH Soul Block recipes (forward + reverse, checker W4) on the first Soul Fragment. Toast + chat announce on; titles/descriptions resolve against the 02-01 `en_us.json` keys.
- `./gradlew build` green after every task; `./gradlew runServer` boots to `Done (0.349s)! For help, type "help"` with zero datapack parse errors / ERROR lines in `run/logs/latest.log`.

## Task Commits

1. **Task 1: Soul Block assets + drops-self loot table** — `d5f22e7` (feat)
2. **Task 2: Harvester + Soul Block conversion crafting recipes** — `7d6a6b3` (feat)
3. **Task 3: discovery-chain advancements (Necromantic Apprentice, First Harvest)** — `da11fef` (feat)

**Plan metadata:** (final docs commit)

## Files Created

- `assets/secondshift/blockstates/soul_block.json` — single `""` variant -> `secondshift:block/soul_block`
- `assets/secondshift/models/block/soul_block.json` — `minecraft:block/cube_all`, `textures.all = secondshift:block/soul_block`
- `assets/secondshift/models/item/soul_block.json` — `{"parent": "secondshift:block/soul_block"}`
- `assets/secondshift/textures/block/soul_block.png` — 16x16 RGBA placeholder (dark warm base + pale-cyan glow pores so light 7 reads)
- `data/secondshift/loot_table/blocks/soul_block.json` — drops-self, `survives_explosion`
- `data/secondshift/recipe/harvester.json` — `crafting_shaped`, category `equipment`
- `data/secondshift/recipe/soul_block.json` — `crafting_shapeless`, category `misc`, 4 ingredients
- `data/secondshift/recipe/soul_fragment_from_block.json` — `crafting_shapeless`, `result.count = 4`
- `data/secondshift/advancement/necromantic_apprentice.json` — `inventory_changed`(emerald), `rewards.recipes [secondshift:harvester]`
- `data/secondshift/advancement/first_harvest.json` — `inventory_changed`(soul_fragment), `rewards.recipes [secondshift:soul_block, secondshift:soul_fragment_from_block]`

## Plan `<output>` — required records

| Item | Result |
|------|--------|
| **A7 — confirmed 1.21.1 recipe JSON shape** | Validated against `minecraft_1.21.1_client.jar` `data/minecraft/recipe/iron_pickaxe.json` (shaped) and `emerald.json` (shapeless reverse): `result` is `{"count": N, "id": "..."}`; shaped uses `key` + `pattern`; shapeless uses `ingredients: [ {"item": "..."} ]`. `category` values used: `equipment` / `misc`. |
| **A7 — confirmed 1.21.1 advancement JSON shape** | Validated against vanilla `advancement/story/mine_stone.json` and `advancement/recipes/misc/emerald.json`: `criteria.<name>.trigger` + `criteria.<name>.conditions`; `inventory_changed` condition is `{"items": [ {"items": <id\|tag\|list>} ]}`; `requirements` is a list-of-lists; `rewards.recipes` is a flat list; `display` carries `icon.{id}`, `title/description.{translate}`, `frame`, `show_toast`, `announce_to_chat`, `hidden`. |
| **A8 — fresh-world recipe-book gating** | Not yet run in a live client (end-of-phase HUMAN-CHECK per `02-VALIDATION.md` POL-04/D-15). Structural guarantee in place: no `advancement/recipes/*.json` authored, no `unlockedBy` in any recipe (`grep` gate passes), so `secondshift:harvester` / `secondshift:soul_block` / `secondshift:soul_fragment_from_block` are reachable **only** through `rewards.recipes`. `runServer` datapack load produced no errors. |
| **Final Harvester grid layout** | `["EEE", "  B", "S B"]` — `E=minecraft:emerald` (x3, blade), `B=minecraft:bone` (x2, haft), `S=minecraft:soul_soil` (x1, base charge). `crafting_shaped`, `category: equipment`, result `{"id": "secondshift:harvester", "count": 1}`. |
| **Soul Block loot-table shape** | `type: minecraft:block`; one pool, `rolls: 1.0`, `bonus_rolls: 0.0`, `conditions: [ {"condition": "minecraft:survives_explosion"} ]`, `entries: [ {"type": "minecraft:item", "name": "secondshift:soul_block"} ]`; `random_sequence: "secondshift:blocks/soul_block"`. |

## Decisions & Deviations

**None — plan executed exactly as written.** All three tasks landed with the files, shapes, and gates the plan specified. Harvester grid layout was Claude's discretion within D-11 (recorded above).

## Known Stubs

- `assets/secondshift/textures/block/soul_block.png` is a 16x16 procedurally-generated placeholder (POL-03 explicitly permits placeholder-quality textures this phase). A hand-drawn soul-soil/carved-stone texture is Phase 10 polish, not a functional gap.

## Pending HUMAN-CHECK (end-of-phase, `02-VALIDATION.md`)

- `runClient`: place a Soul Block — real texture (no purple cube), light ~7, occasional soul wisps, drops exactly one on break.
- `runClient` + JEI/EMI: all 3 recipes visible; 4 Fragment <-> 1 Soul Block round-trips losslessly at a table.
- Fresh survival world: `secondshift:harvester` absent from the recipe book at spawn; emerald pickup -> "Necromantic Apprentice" toast + recipe-unlock toast; first Fragment -> "First Harvest" toast + Soul Block recipe (forward & reverse) unlocked; neither recipe known before its trigger (A8).

## Next Phase Readiness

- 02-04 (Soul Altar block, its recipe, `soul_mason.json` -> altar recipe) is unblocked. The `soul_mason` title/description keys already exist in `en_us.json`.
- The hand-written-JSON pattern (D-17) is proven end to end for recipes / advancements / loot tables / blockstates / models — 02-04 follows the same approach for the altar.

## Self-Check: PASSED
