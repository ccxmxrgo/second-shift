# Phase 2: Economy Items & Soul Altar Block - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-04
**Phase:** 2-economy-items-soul-altar-block
**Areas discussed:** Altar scope this phase, Harvester design & feel, Harvester recipe & economy tone, Soul Fragment & Soul Block presentation

---

## Altar scope this phase

| Option | Description | Selected |
|--------|-------------|----------|
| Pull Slice 3 forward | BE with a 1-slot inventory; right-click inserts a Soul Block, empty-hand retrieves, break drops contents. No menu. De-risks Phase 3. | ✓ |
| Inert block only | Altar crafts/places/breaks/drops itself; BE registered but stateless, no interaction. | |
| Inert, no block entity yet | Plain block; defer ModBlockEntities + EntityBlock to Phase 3. | |

**User's choice:** Pull Slice 3 forward.

| Option | Description | Selected |
|--------|-------------|----------|
| No automation access | Slot is player-interaction only; no Capability/IItemHandler. | ✓ |
| Hoppers can insert only | Hopper feeds a Soul Block in, can't extract. | |
| Full hopper in/out | Standard container automation. | |

**User's choice:** No automation — "no automation, it's a ritual haha".

| Option | Description | Selected |
|--------|-------------|----------|
| Floating/embedded render + faint FX | BER shows the Soul Block hovering/set into the altar, slow bob, soul particle wisp. | ✓ (later refined) |
| Blockstate change only | 'empty' vs 'charged' model variant, no BER. | |
| Invisible until you interact | Slot is purely logical this phase. | |

**User's choice:** Floating/embedded render + faint FX — later refined (see Soul Block area) to an **eye-of-ender-style embedded, glowing, no-bob** render.

| Option | Description | Selected |
|--------|-------------|----------|
| Carved stone pedestal, pickaxe, stone-ish hardness | Blackstone/deepslate lectern silhouette with soul-fire accents, ~3.5 hardness, non-full-cube. | ✓ |
| Full block, obsidian-tier | Dense cube, high blast resistance, slow to break. | |
| Bone/flesh construct | Bone blocks + sinew, axe/sword to break. | |

**User's choice:** Carved stone pedestal.

| Option | Description | Selected |
|--------|-------------|----------|
| Single block | One placed block is the whole altar; job-site block goes on top. | ✓ |
| Small multiblock | Altar + pillars; requires a built structure. | |

**User's choice:** Single block.

| Option | Description | Selected |
|--------|-------------|----------|
| Mid cost, thematic | Blackstone/deepslate + soul soil + bone/skull + weighty centerpiece. | ✓ |
| Cheap | Stone/cobble + soul sand + bone. | |
| Expensive / late-game gated | Nether star / netherite / reinforced deepslate. | |

**User's choice:** Mid cost, thematic — later refined to use **emerald** as the binding element for consistency with the Harvester.

| Option | Description | Selected |
|--------|-------------|----------|
| Sound + small particle burst | Borrowed vanilla soul sound + soul particles on insert, softer on retrieve. | ✓ |
| Sound only | Audio confirmation, no extra particles. | |
| Nothing this phase | Silent; BER is the only feedback. | |

**User's choice:** Sound + small particle burst.

**Notes:** The retrieval and break-behavior answers here were **overridden** during the Soul Block discussion — socketing became one-way (no retrieval) and breaking a charged altar destroys everything with a half-heart hit + cosmetic lightning. See the Soul Block area below and CONTEXT.md D-03/D-04.

---

## Harvester design & feel

**User first asked for research on real necromancer weapons.** Findings relayed: scythe (most iconic modern trope, Malum precedent), sickle (folkloric ancestor), bone weapons, ritual dagger/athame, staff/wand (caster, not a harvest tool).

| Option | Description | Selected |
|--------|-------------|----------|
| Scythe | Most iconic 'reaper' read; Malum precedent; custom angled model. | ✓ |
| Sickle / soul hook | Folkloric ancestor; one-handed hooked blade; easier model. | |
| Bone ritual dagger | Carved-bone sacrificial knife; 'ritual murder' framing; cheapest model. | |

**User's choice:** Scythe.

| Option | Description | Selected |
|--------|-------------|----------|
| Deliberately weak | Low melee damage, ritual implement not a weapon. | |
| Sword-tier (iron-ish) | Competent sidearm. | |
| Strong / scales with material | Netherite-adjacent. | |

**User's choice:** *Redefined the mechanic* — "Is it possible to insta kill Villagers... nice particles... doesn't deal as much damage to other mobs." Instakill villagers, modest damage to everything else.

| Option | Description | Selected |
|--------|-------------|----------|
| Always one-shot, any villager | Ignores health/armor/resistance; guarantees the 1-Fragment drop. | ✓ |
| One-shot only below ~half health | A finisher; adds a survival/flee case. | |
| Huge damage, not literally infinite | ~1000 dmg; a damage cap could technically survive. | |

**User's choice:** Always one-shot, any villager.

| Option | Description | Selected |
|--------|-------------|----------|
| Feeble ~1-2 damage | Barely a weapon against anything else. | |
| Unarmed-ish ~1 damage | Essentially does nothing. | |
| Modest ~3-4 damage (stone-sword tier) | Weak but usable panic weapon. | ✓ |

**User's choice:** Modest ~3-4 damage.

| Option | Description | Selected |
|--------|-------------|----------|
| Soul burst + wisps rising | Particles at the body, wisps drift up and toward the player/scythe, vanilla soul sound. | ✓ |
| Sharp implosion | Particles snap inward (Malum-style 'shattered into a spirit'). | |
| Big dramatic column | Tall soul-fire pillar + loud sound every harvest. | |

**User's choice:** Soul burst + wisps rising.

| Option | Description | Selected |
|--------|-------------|----------|
| Has durability, enchantable | ~250 uses, iron-ish; Unbreaking/Mending useful, Looting a no-op. | ✓ |
| Unbreakable, not enchantable | Craft once, keep forever. | |
| High durability, enchantable | Netherite-tier (~2000). | |

**User's choice:** Has durability, enchantable.

| Option | Description | Selected |
|--------|-------------|----------|
| Single target only | No sweep; one swing = one villager = one soul. | ✓ |
| Sweep, villagers in arc die | Wide swing reaps a crowd, each drops 1 Fragment. | |
| Sweep for non-villagers only | Normal sweep vs mobs, instakill only on the main target. | |

**User's choice:** Single target only.

| Option | Description | Selected |
|--------|-------------|----------|
| Left-click only | Just swings; no right-click this phase. | ✓ |
| Right-click inspects a villager | Preview profession/biome without killing. | |
| Right-click charges a stronger reap | Hold to wind up a flashier harvest. | |

**User's choice:** Left-click only. (Right-click inspect → deferred idea.)

---

## Harvester recipe & economy tone

| Option | Description | Selected |
|--------|-------------|----------|
| Deliberate but not grindy | Modest one-time craft; souls farmed actively but binding is a decision. | ✓ |
| Souls are precious — lean scarce | Mid-game gated, limited durability. | |
| Souls are cheap — lean abundant | Trivial recipe, early access. | |

**User's choice:** Deliberate but not grindy.

| Option | Description | Selected |
|--------|-------------|----------|
| Bone + iron + soul soil | Iron scythe head, bone haft, soul soil charge; iron age + 1 Nether trip. | ✓ (modified) |
| Bone + iron only (no Nether) | Fully overworld-craftable. | |
| Netherite / wither-touched | Gated behind the fortress. | |

**User's choice:** Option 1 **but the blade is made of emerald, not iron** — "villagers' currency becomes the reaping tool".

| Option | Description | Selected |
|--------|-------------|----------|
| 2-3 emeralds | An investment (some trading / a mining trip) without being brutal. | ✓ |
| 1 emerald | Thematic touch only, not a real gate. | |
| Emerald block (9) | A serious commitment. | |

**User's choice:** 2-3 emeralds.

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — reversible like a storage block | 4 Fragments ⇄ 1 Block, no loss. | ✓ |
| No — one-way | Fragments → Block only. | |

**User's choice:** Reversible.

| Option | Description | Selected |
|--------|-------------|----------|
| Villager harvest only | Sole source; keeps 'every employee = 4 real villagers' airtight. | ✓ |
| Also a rare mob drop | Wither skeletons / zombies occasionally drop one. | |
| Also tradeable | A cleric / wandering trader sells them. | |

**User's choice:** Villager harvest only.

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — emerald as the altar's binding element | Frame + soul soil + skull/bone + 1-2 emeralds. | ✓ |
| No — echo shard / wither rose centerpiece | Keeps the altar cost profile distinct. | |
| Cheaper, no rare centerpiece | Just stone + soul soil + bone. | |

**User's choice:** Yes — emerald binding element.

| Option | Description | Selected |
|--------|-------------|----------|
| Unlock on picking up the trigger item | Standard vanilla has_item unlock + toast. | |
| All known from the start | No discovery toast. | |
| Gated behind a custom advancement | Authored advancement chain. | ✓ |

**User's choice:** Custom advancement chain — **and** wants max intuitiveness, raised custom paintings + in-village hints (→ deferred idea, flagged for roadmap).

| Option | Description | Selected |
|--------|-------------|----------|
| Unlocks on picking up an emerald | First emerald → 'Necromantic Apprentice' advancement → Harvester recipe. | ✓ |
| Known from world start | Harvester always in the book. | |
| Unlocks near a village / job-site block | Proximity trigger. | |

**User's choice:** Unlocks on picking up an emerald.

| Option | Description | Selected |
|--------|-------------|----------|
| Emerald → Harvester → harvest a villager → Soul Block → altar | Each advancement description carries the next clue. | ✓ |
| Same but altar known with the Harvester | Collapses the last gate. | |
| Flat — one advancement per item, no chain | Least authored. | |

**User's choice:** The full guided chain.

---

## Soul Fragment & Soul Block presentation

| Option | Description | Selected |
|--------|-------------|----------|
| Placeable, quiet ambient presence | Real block, ~7 light, occasional soul wisps. | ✓ |
| Placeable but inert | Decorative cube, no light/particles. | |
| Item only, not placeable | Pure crafting item / altar input. | |

**User's choice:** *Answered by analogy* — "behave similar to the ender eye, when you place it into the end portal frame" (i.e. sockets into the altar, embedded + glowing). Placeable ambient block confirmed in a follow-up.

| Option | Description | Selected |
|--------|-------------|----------|
| One-way — committed once socketed | Eye-of-ender style; no retrieval; returned only if the altar breaks. | ✓ (extended) |
| Retrievable after all | Empty-hand right-click pulls it back. | |
| One-way, not returned on altar break either | Fully consumed. | |

**User's choice:** Not retrievable — **and extended**: "if you break the altar, it explodes dealing half a heart of damage, also killing the employee, with a thunder bolt. no drops. (the mod is supposed to enhance the vanilla experience with a twist, not to make super easy)". Recognized as ALTAR-06 (Phase 6).

| Option | Description | Selected |
|--------|-------------|----------|
| Charged-altar consequences now, employee-kill in Phase 6 | Phase 2: no-drop + half-heart + lightning on charged break; employee-kill deferred. | ✓ |
| Just one-way socketing now, defer all break drama to Phase 6 | No damage/lightning yet. | |
| Full ALTAR-06 now with a stand-in employee-kill hook | Most forward work. | |

**User's choice:** Charged-altar consequences now, employee-kill in Phase 6.

| Option | Description | Selected |
|--------|-------------|----------|
| Placeable block with quiet ambient presence | Buildable, ~7 light, soul wisps. | ✓ |
| Placeable but totally inert | Plain decorative cube. | |
| Effectively socket-only | Technically a BlockItem but plain and not for building. | |

**User's choice:** Placeable block with quiet ambient presence.

| Option | Description | Selected |
|--------|-------------|----------|
| Embedded + glowing, eye-of-ender style | Recessed into the altar top, lit, slow wisp, no bob. | ✓ |
| Embedded but floating with a slow bob | Suspended just above the surface. | |
| Blockstate model swap, no BER | 'charged' variant with a visible soul core. | |

**User's choice:** Embedded + glowing, eye-of-ender style.

| Option | Description | Selected |
|--------|-------------|----------|
| Subtle glint / enchant-style shimmer | Faint foil so a dropped Fragment is easy to spot. | ✓ |
| Plain item | No shimmer. | |
| Glowing dropped entity | Emits light + hovers with particles. | |

**User's choice:** Subtle glint / enchant-style shimmer.

| Option | Description | Selected |
|--------|-------------|----------|
| Claude makes clean placeholder art now, you replace later | Consistent 16x16 stand-ins (blackstone greys + soul-cyan), polish deferred. | ✓ |
| You'll provide the textures | Claude wires models, waits on real PNGs. | |
| Placeholder now, with a specific style brief first | User describes the look first. | |

**User's choice:** Claude makes clean placeholder art now, replace later.

---

## Claude's Discretion

- datagen vs. hand-written JSON for models/blockstates/recipes/advancements/loot tables (chain size may justify revisiting STACK.md §12).
- Harvester item class (plain `Item` + attributes vs. `SwordItem`/`TieredItem` subclass); which event hook implements the instakill vs. the drop replacement; how the vanilla sweep is disabled.
- Exact recipe grid layouts and quantities within the stated ranges.
- Exact vanilla sound IDs / particle types for socket + harvest + ambient FX; exact Soul Block light level (~7).
- Creative tab icon, registry id, ordering, title.
- BER vs. emissive-model implementation for the embedded Soul Block; altar model geometry; block hardness/resistance numbers.
- Whether the altar BE stores a format/version int.
- Advancement working names + clue wording; the "harvest a villager" advancement trigger mechanism.
- Whether to add the descriptionId/lang-key resolution self-check now.

## Deferred Ideas

- **Custom paintings + in-village discovery hints** — a new discovery/lore capability; FLAG FOR ROADMAP (own phase or Phase 10 bolt-on).
- **Harvester right-click "inspect villager"** — revisit once binding exists if scouting-before-bind is annoying.
- **descriptionId / lang-key resolution self-check** — carried from Phase 1; could start here or wait for Phase 10.
- **Bespoke `SoundEvent`/`ParticleType` registration** — Phase 10 (POL-05) / v1.x (PRES-02); this phase borrows vanilla.
- **Datagen adoption** — revisit at Phase 3-5 if kept hand-written now.
