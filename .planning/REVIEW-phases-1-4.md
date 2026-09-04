# Second Shift — Static Code Review, Phases 1–4

**Scope:** All 27 files under `src/main/java/com/cxmxrgo/secondshift/` (harvester tool, Soul
Altar block/BE, Binding Altar menu/screen, employee attachment/spawn/network). Static analysis
only — nothing executed, nothing modified.

**Reviewed against:** `CLAUDE.md` (project conventions, "What NOT to Use" table, ASVS L1 posture
for the network trust boundary).

## Summary

| Severity | Count |
|----------|-------|
| BLOCKER  | 1 |
| WARNING  | 4 |
| INFO     | 3 |

Overall the Phase 3–4 network/menu-state hardening (client-position spoofing, double-bind race)
is genuinely solid — `ServerPayloadHandler`/`BindingAltarMenu`/`SoulAltarBlockEntity` re-derive
all trust-sensitive state server-side and validate before mutation. Registration is complete and
correctly self-checked. The one BLOCKER found is a plain geometry bug in the core bind loop that
static analysis catches cleanly and that the pending manual-verification checkpoint should
specifically re-test.

---

## BLOCKER

### CR-01: Bound employee spawns inside/overlapping the job-site block, not beside it

**File:** `employee/EmployeeManager.java:53-54`
```java
BlockPos spawnPos = altarPos.above();
villager.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
```

**Issue:** `EmployeeManager.bind(ServerLevel, BlockPos altarPos)` is always called with the Soul
Altar's own position (see `ServerPayloadHandler.handleBindEmployee`, which derives `pos` from
`menu.access()` — the altar's `BlockPos`). The spawn position is computed as `altarPos.above()`.

But the entire bind precondition — enforced by `ProfessionResolver.fromAbove(level, altarPos)`
in both `SoulAltarBlock.useItemOn` and `useWithoutItem` — requires a **job-site block physically
placed at `altarPos.above()`**. That is the exact same `BlockPos` the new villager is moved to.

Every real bind therefore spawns the employee villager with its position (and thus its
collision AABB) coincident with the job-site block the player placed to make the bind legal in
the first place (cartography table, lectern, brewing stand, grindstone, etc. — all solid-ish
blocks with real collision shapes). The villager will spawn embedded in/overlapping that block:
visually clipped through the table, likely to jitter/get shoved to an unpredictable adjacent
tile by collision resolution on the next physics tick, and in the worst case (a job block with a
mostly-full collision shape) get stuck.

**Why the GameTest suite didn't catch it:** `EmployeeGameTests.bind_spawns_villager_with_employee_data`
asserts the villager ends up at `altarPos.above()` — but that test never places a job-site block
at that position, so it validates the coordinate math in isolation without exercising the actual
precondition (a solid block occupying that exact space). None of the `BindingAltarGameTests`
combine "job block present" with an actual `EmployeeManager.bind` call to check the resulting
villager isn't embedded in it.

**Concrete failure scenario:** Player places Soul Altar, puts a Cartography Table directly above
it (the documented/tested job-block pattern), sockets a Soul Block, clicks Confirm Hire. The new
villager spawns at the exact `BlockPos` of the Cartography Table.

**Fix:** Spawn the employee beside the altar, not inside the job-site block's cell — e.g. one
block further up (`altarPos.above(2)`), or offset horizontally from the altar on a side clear of
both the altar and the job block. Whichever position is chosen, add a GameTest that places a real
job-site block at `altarPos.above()` (mirroring the actual play pattern) and asserts the bound
villager's bounding box does not intersect it.

```java
// e.g. spawn one block above the job site instead of inside it
BlockPos spawnPos = altarPos.above(2);
villager.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
```

---

## Warnings

### WR-01: Consume-before-bind ordering has no rollback if `EmployeeManager.bind` throws

**File:** `network/ServerPayloadHandler.java:47-54`, `employee/EmployeeManager.java:44-51`

**Issue:** `ServerPayloadHandler.handleBindEmployee` deliberately clears the socketed Soul Block
(`be.setHeldSoulBlock(ItemStack.EMPTY)`, `setChanged()`, block update) **before** calling
`EmployeeManager.bind(serverLevel, pos)`, specifically to make a duplicate rapid click a no-op
(T-4-02, documented and correct for that goal). But this means the Soul Block is irrevocably
consumed even if `bind()` subsequently fails.

`bind()` is not currently fail-safe:
- `EntityType.VILLAGER.create(level)` returning `null` throws `IllegalStateException` (line 50).
- `BINDABLE_PROFESSIONS.get(random.nextInt(BINDABLE_PROFESSIONS.size()))` would throw
  `ArithmeticException`/`IndexOutOfBoundsException` if that list were ever empty (currently a
  safe hardcoded literal, but a future refactor — e.g. datapack-driven profession list per
  Phase 5+ — could make this reachable).

If either throws, the player has permanently lost their Soul Block (and the socketed-block
BE state is already cleared) with no employee spawned and no refund path — a genuine data-loss
scenario per the item cost investment (4 Soul Fragments → 1 Soul Block).

**Fix:** Wrap the `EmployeeManager.bind(...)` call in a try/catch inside the `access().execute`
lambda; on failure, restore the consumed Soul Block (or drop it) and log, rather than silently
losing it. Alternatively, validate/roll random selection before the consume step so `bind()`
itself cannot throw once the consume has committed.

### WR-02: `EmployeeData.offers` aliases the live, mutable `MerchantOffers` also installed on the villager

**File:** `employee/EmployeeManager.java:63-71`, `employee/EmployeeData.java:24-30`

**Issue:**
```java
MerchantOffers offers = rollDefaultTier1Offers(villager, profession);
villager.setOffers(offers);                                    // villager holds `offers`
...
villager.setData(ModAttachments.EMPLOYEE.get(), new EmployeeData(1, name, professionId, 1, offers)); // same instance
```
`EmployeeData` is a `record` (implying value semantics/immutability), but its `offers` field is
the *same* `MerchantOffers` object instance that `Villager#setOffers` installed as the live
trade list. `MerchantOffer` entries are mutable (uses/demand/specialPrice change as trades are
executed). Any future trading UI (Phase 5+) that lets the player actually trade with this
villager will mutate this shared object in place from the villager side, silently "mutating" the
supposedly-immutable `EmployeeData` record without ever going through
`setData(EMPLOYEE, ...)` again — meaning the attachment's client-sync path (`Builder#sync`) never
fires for those in-place trade-count changes, so a client observing the attachment (e.g.
`ClientEmployeeSyncDebug`, and any future trade-preview UI) can silently desync from the
villager's actual live offers.

**Fix:** Store a defensive copy in the attachment (`new MerchantOffers(offers.stream().map(MerchantOffer::copy).toList())`
or equivalent) so the attachment snapshot and the villager's live trade state are decoupled, and
re-sync the attachment explicitly (`setData`) whenever the snapshot needs to reflect a trade
change.

### WR-03: `ModRegistrySelfCheck`'s lang-key guardrail does not cover Phase 3/4 message keys

**File:** `ModRegistrySelfCheck.java:62-70`, cross-referenced against
`assets/secondshift/lang/en_us.json`

**Issue:** `EXTRA_LANG_KEYS` only lists the creative-tab title, three advancement title/desc
pairs, and the container title — all Phase 2-era keys. It does not include any of the
`message.secondshift.altar.*` keys (6 of them, used throughout `SoulAltarBlock` and
`BindingAltarMenu`'s D-11/D-12 messaging) or `gui.secondshift.binding_altar.confirm` (used in
`BindingAltarScreen`), all introduced in Phases 3–4. The class's own javadoc claims "every
registered item/block's descriptionId... must resolve" and frames this as the guardrail that
specifically prevents "the raw `item.secondshift.*` key shown in the UI" failure (POL-03) — but
that promise silently stopped being honored as soon as message/gui keys were introduced without
updating this list. A future typo in any of these 7 keys will render as a raw untranslated key in
the action bar or on the Confirm Hire button instead of hard-failing at startup the way the
guardrail is designed to.

**Fix:** Add the 6 `message.secondshift.altar.*` keys and `gui.secondshift.binding_altar.confirm`
to `EXTRA_LANG_KEYS`, and add a one-line comment (mirroring the D-08/D-10 comment already on the
`DeferredRegister` list) reminding future phases to extend this list alongside any new
`Component.translatable(...)` call site.

### WR-04: `ClientEmployeeSyncDebug` is research-spike scaffolding still active in shipped code

**File:** `client/ClientEmployeeSyncDebug.java`

**Issue:** The class's own javadoc identifies it as a diagnostic added to empirically answer a
research question about `AttachmentType.Builder#sync` behavior in 21.1.248 ("LIGHT spike
diagnostic... adds no rendering or gameplay behavior — it only logs"). That question has
presumably been answered by now (Phase 4 network/bind code is complete and depends on it
working). The handler still fires on every single `EntityJoinLevelEvent` for every villager with
`EmployeeData` on the client — i.e. every time a bound employee is loaded into a chunk, teleports,
or the client (re)connects — logging the full `EmployeeData` (name, profession, tier, and the
complete `MerchantOffers` list) via `LOGGER.info` indefinitely. This is dead debug instrumentation
left wired into the mod-bus permanently rather than being removed (or at minimum gated behind a
system property/debug flag) once its research purpose was served.

**Fix:** Remove the class now that the sync question is answered, or gate it behind a debug
flag/log level so it isn't unconditionally active in the shipped build.

---

## Info

### IN-01: "open Binding Altar menu" trigger duplicated between `useItemOn` and `useWithoutItem`

**File:** `content/block/SoulAltarBlock.java:128-130`, `:162-164`

Both interaction paths end with the identical
`if (player instanceof ServerPlayer sp) { sp.openMenu(be, buf -> buf.writeBlockPos(pos)); }`
block. Low risk since they're currently in sync, but a future change to one call site (e.g. an
attribute added to the open buffer) that isn't mirrored in the other is a one-line-diff-away bug.
Consider extracting a private `openBindingAltar(ServerPlayer, SoulAltarBlockEntity, BlockPos)`
helper.

### IN-02: `BindingAltarScreen`'s Confirm Hire button has no client-side re-entrancy guard

**File:** `client/screen/BindingAltarScreen.java:44-48`

Server-side idempotency (T-4-02) makes this harmless functionally, but repeated fast clicks will
send one `BindEmployeePayload` per click until the container closes. Since the container is
closed synchronously after the first successful bind (`sp.closeContainer()` in
`ServerPayloadHandler`), this is low-impact — but a one-line `btn.active = false` on first click
would avoid the redundant network traffic entirely and is a common pattern for this kind of
"confirm and close" button.

### IN-03: `MerchantOffers`/employee spawn logic path duplicates vanilla's "max 2 per tier" magic number without a named constant

**File:** `employee/EmployeeManager.java:85`

`if (offers.size() >= 2) break; // vanilla: max 2 per tier` — the `2` is documented in a comment
but not named. Minor; extract to a `private static final int MAX_TIER1_OFFERS = 2;` for
consistency with the rest of the file's constant style (`BINDABLE_PROFESSIONS`, `DATA_VERSION` in
the BE, `WISP_ROLL`/`EMBED_SCALE` in the renderer all use named constants).

---

## What was checked and found clean

- **Registration completeness:** all 6 `DeferredRegister`s (`ModItems.ITEMS`, `ModBlocks.BLOCKS`,
  `ModBlockEntities.BLOCK_ENTITIES`, `ModCreativeTab.TABS`, `ModMenus.MENUS`,
  `ModAttachments.ATTACHMENT_TYPES`) are registered in `SecondShift`'s constructor and covered in
  `ModRegistrySelfCheck`'s unbound-entry `Stream.of(...)`. No drift found there.
- **Network trust boundary:** `BindEmployeePayload` is a zero-field record specifically to avoid
  a spoofable client-supplied position; `ServerPayloadHandler` re-derives the altar position
  exclusively from the sender's own server-side `containerMenu`, re-validates `stillValid()`
  before acting, and performs the consume-and-bind as a single synchronous
  `ContainerLevelAccess.execute` call so a rapid double-send sees `be.isEmpty() == true` on the
  second attempt (verified correct — see WR-01 for the one related gap). This is good ASVS-style
  practice for an unauthenticated client→server trigger.
- **`SoulAltarBlockEntity.setHeldSoulBlock`** re-validates that any stack it's asked to persist
  is either empty or exactly `ModItems.SOUL_BLOCK_ITEM`, independent of the UI layer
  (`SoulSlot.mayPickup`/`mayPlace`) — defense in depth against a non-click-routed `Container`
  write (e.g. a creative-mode set-slot packet).
- **"What NOT to Use" compliance:** no `net.minecraftforge.*` imports, no `mods.toml` at the old
  path, no `SimpleChannel`/`NetworkRegistry`, no `ItemStack` NBT tags (uses codec-based
  `ItemStack#save`/`parse`), no item-stack data attachments (the employee record is an
  entity-level attachment, correctly not an item-level one), `HarvesterItem` is a plain `Item`
  (not `SwordItem`) with attributes supplied via `Item.Properties#attributes(SwordItem.createAttributes(...))`
  per the documented 1.21 pattern, `MenuScreens`/screens are registered via
  `RegisterMenuScreensEvent` rather than the deprecated `FMLClientSetupEvent` pattern.
- **Client/server class isolation:** `client/` package classes are consistently
  `Dist.CLIENT`-gated via `@EventBusSubscriber(..., value = Dist.CLIENT)`, and no
  `client.render`/`client.screen` type is referenced from `content/`, `event/`, `menu/`, or
  `registry/` packages.
- **Durability/attack semantics:** `HarvesterItem` correctly relies on the explicit
  `weapon.hurtAndBreak(1, reaper, EquipmentSlot.MAINHAND)` call in `HarvesterEvents` rather than
  double-counting durability loss, since a plain `Item` (not `SwordItem`) never triggers vanilla's
  own `hurtEnemy` durability path.

---

_Reviewed: 2026-09-04_
_Reviewer: Claude (adversarial static review)_
_Depth: standard (full read of all 27 files), targeted trust-boundary tracing on the network/menu/attachment path_
