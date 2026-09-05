---
phase: 04-employee-attachment-spawn
reviewed: 2026-09-05T00:00:00Z
depth: standard
files_reviewed: 13
files_reviewed_list:
  - src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java
  - src/main/java/com/cxmxrgo/secondshift/SecondShift.java
  - src/main/java/com/cxmxrgo/secondshift/client/ClientEmployeeSyncDebug.java
  - src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java
  - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeData.java
  - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeManager.java
  - src/main/java/com/cxmxrgo/secondshift/employee/EmployeeNames.java
  - src/main/java/com/cxmxrgo/secondshift/gametest/EmployeeGameTests.java
  - src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java
  - src/main/java/com/cxmxrgo/secondshift/network/BindEmployeePayload.java
  - src/main/java/com/cxmxrgo/secondshift/network/ServerPayloadHandler.java
  - src/main/java/com/cxmxrgo/secondshift/registry/ModAttachments.java
  - src/main/resources/assets/secondshift/lang/en_us.json
findings:
  critical: 0
  warning: 5
  info: 2
  total: 7
status: issues_found
---

# Phase 4: Code Review Report

**Reviewed:** 2026-09-05T00:00:00Z
**Depth:** standard
**Files Reviewed:** 13
**Status:** issues_found

## Summary

Reviewed the current (post-fix) state of Phase 4's employee-attachment-spawn slice: the
`EmployeeData` attachment contract, `EmployeeManager.bind`, the network bind trigger and its
server handler, the Binding Altar menu/screen, the registry self-check guardrails, and the
client-side sync-timing debug diagnostic. The two bugs fixed by the prior quick task
(spawn-position overlap in `EmployeeManager`, sync-check timing in `ClientEmployeeSyncDebug`)
are correctly resolved in the current code and are backed by GameTest coverage
(`bind_villager_does_not_overlap_job_site_block`). No new crash-causing or security-relevant
defects were found. However, several correctness/robustness gaps and one self-check coverage
gap were found, all classified as Warnings, plus two minor Info-level quality notes.

No Critical/Blocker findings — nothing here causes a crash, data corruption that is currently
reachable, or a security issue.

## Warnings

### WR-01: Soul Block is consumed even if the bind never happens

**File:** `src/main/java/com/cxmxrgo/secondshift/network/ServerPayloadHandler.java:42-55`

**Issue:** Inside `menu.access().execute((level, pos) -> { ... })`, the Soul Block consumption
(`be.setHeldSoulBlock(ItemStack.EMPTY); be.setChanged(); level.sendBlockUpdated(...)`) runs
unconditionally once `be.isEmpty()` is false, but the actual `EmployeeManager.bind` call is
gated behind a separate `if (level instanceof ServerLevel serverLevel)` check performed
*after* the consume. If that instanceof check were ever false (e.g. this handler is reused
from a different registration path in a future phase, or `ContainerLevelAccess` is
constructed against a non-`ServerLevel` `Level` by a refactor), the player's Soul Block would
be silently destroyed with no employee ever spawned — a data-loss path with no player-visible
error. Today the check is always true in practice (the payload is only ever handled
server-side), but the code has two independently-gated effects that are supposed to be atomic
and currently are not actually coupled by a shared condition — only by accident of how the
method is invoked today.

**Fix:** Gate the consume on the same condition as the bind, or restructure so there is a
single conditional that both consumes and binds:
```java
menu.access().execute((level, pos) -> {
    if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be) || be.isEmpty()) {
        return;
    }
    if (!(level instanceof ServerLevel serverLevel)) {
        return; // never consume without also binding
    }
    be.setHeldSoulBlock(ItemStack.EMPTY);
    be.setChanged();
    level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
    EmployeeManager.bind(serverLevel, pos);
});
```

### WR-02: "Confirm Hire" with no Soul Block gives no player feedback

**File:** `src/main/java/com/cxmxrgo/secondshift/network/ServerPayloadHandler.java:31-58`

**Issue:** When `be.isEmpty()` is true (player clicks "Confirm Hire" with no Soul Block
socketed), the lambda simply `return`s and the handler falls through to `sp.closeContainer()`
— the altar UI closes with zero feedback. This is inconsistent with the existing UX
established elsewhere in the mod: `SoulAltarBlock` already has a dedicated
`message.secondshift.altar.no_soul_block` lang key ("Nothing to bind. Bring a Soul Block to
staff this altar.") used for the equivalent right-click-without-soul-block case, but this
key is never sent from `ServerPayloadHandler`. A player who opens the altar, forgets to
insert a Soul Block, and clicks Confirm Hire gets no explanation for why nothing happened.

**Fix:** Send the existing message before returning:
```java
if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be) || be.isEmpty()) {
    sp.displayClientMessage(Component.translatable("message.secondshift.altar.no_soul_block"), true);
    return;
}
```

### WR-03: `EmployeeData.EMPTY`'s `MerchantOffers` is a shared mutable singleton returned by the attachment default supplier

**File:** `src/main/java/com/cxmxrgo/secondshift/employee/EmployeeData.java:32-33`,
`src/main/java/com/cxmxrgo/secondshift/registry/ModAttachments.java:26`

**Issue:** `ModAttachments.EMPLOYEE` is built with `AttachmentType.builder(() -> EmployeeData.EMPTY)`
— the supplier always returns the exact same static `EmployeeData.EMPTY` record instance for
every entity that has never had the attachment explicitly set (every wild villager, and every
employee villager before `setData` is called). `EmployeeData` itself is an immutable record,
but its `offers` field holds a mutable `MerchantOffers` (`new MerchantOffers()`), and that
single list instance is shared by reference across every entity's default value — NeoForge's
`AttachmentHolder` caches whatever the supplier returns per-holder, but the *supplier* itself
returns the same aliased object every time. If any future code path calls
`entity.getData(ModAttachments.EMPLOYEE).offers()` on an entity that has not been bound yet
and mutates the returned list (e.g. `.add(...)`), it corrupts the default `offers` list for
every other unbound entity in the game simultaneously. Nothing in the currently-reviewed code
does this yet (bind() always constructs a fresh `MerchantOffers` before calling `setData`), so
this is a latent landmine rather than an active bug, but it is exactly the kind of shared-
mutable-default bug that is invisible until a later phase (trade editing, respawn/rebind
logic) touches it.

**Fix:** Make the default supplier construct a fresh instance per call instead of capturing a
shared static singleton with a mutable field:
```java
public static EmployeeData empty() {
    return new EmployeeData(1, "", ResourceLocation.withDefaultNamespace("none"), 0, new MerchantOffers());
}
// ModAttachments:
AttachmentType.builder(EmployeeData::empty)
```

### WR-04: New "Confirm Hire" lang key not covered by the descriptionId self-check guardrail

**File:** `src/main/java/com/cxmxrgo/secondshift/ModRegistrySelfCheck.java:62-70`

**Issue:** `ModRegistrySelfCheck`'s entire stated purpose (per its class doc, "prevents the
raw `item.secondshift.*` key shown in the UI failure — POL-03") is to hard-abort startup if
any UI-facing lang key is missing. `EXTRA_LANG_KEYS` enumerates the creative-tab title and the
three advancement title/description pairs plus `container.secondshift.binding_altar`, but this
phase introduces a brand-new UI string, `gui.secondshift.binding_altar.confirm`
(`BindingAltarScreen.java:45`), which is not added to `EXTRA_LANG_KEYS`. If that key were ever
deleted from `en_us.json`, the self-check would not catch it — the button would silently
render the raw key, which is the exact failure class this guardrail exists to prevent.

**Fix:** Add the new key to the list:
```java
private static final List<String> EXTRA_LANG_KEYS = List.of(
        "itemGroup.secondshift.main",
        "gui.secondshift.binding_altar.confirm",
        "advancement.secondshift.necromantic_apprentice.title",
        ...
```

### WR-05: `ClientEmployeeSyncDebug` is always-on production instrumentation, not gated behind a debug flag

**File:** `src/main/java/com/cxmxrgo/secondshift/client/ClientEmployeeSyncDebug.java:44-112`

**Issue:** This class's own doc comment describes it as a "LIGHT spike diagnostic" that
"adds no rendering or gameplay behavior — it only logs." As written it is unconditionally
active for every player in every world: `onEntityJoinLevel` tracks the entity id of *every*
`Villager` that joins the client level — not just bound employees, but every wild villager in
every loaded chunk, village, or trading hall — and `onClientTick` polls all pending ids every
client tick until each either syncs or times out at 20 ticks, logging at `INFO` on every
resolution and `WARN` on every timeout. In a normal village with dozens of villagers this
produces continuous log spam and per-tick work with no way to disable it short of a rebuild.
The RESEARCH doc frames this as intentionally answering an empirical question during Phase 4
development, but it is wired into the permanent event-bus subscriber set with no dev-only
gate (e.g. a system property, `-Dsecondshift.debug=true`, or simply being removed now that the
sync-timing question has been answered and encoded in the class's own javadoc).

**Fix:** Either delete this class now that the LIGHT-spike question is answered (its own
javadoc already documents the resolved finding), or gate it behind a debug flag so it does not
run in every player's normal game:
```java
private static final boolean DEBUG_ENABLED =
        Boolean.getBoolean("secondshift.debug.employeeSync");

@SubscribeEvent
static void onEntityJoinLevel(EntityJoinLevelEvent event) {
    if (!DEBUG_ENABLED || !event.getLevel().isClientSide()) return;
    ...
```

## Info

### IN-01: `BindingAltarMenu.owningPlayer` is assigned but never read

**File:** `src/main/java/com/cxmxrgo/secondshift/menu/BindingAltarMenu.java:29,50`

**Issue:** `owningPlayer` is set in the core constructor and documented as "stored now for
Plan 02 Task 3's D-12 forced-close messaging; not used yet," but `stillValid` reads its
`player` parameter, not the field, and nothing else in the class reads `owningPlayer`. This is
dead state today — harmless, but worth either wiring in (if D-12 messaging is meant to use the
stored player rather than the live `stillValid` parameter) or removing until it's needed.

**Fix:** Remove the field until an actual consumer exists, or use it explicitly where D-12
messaging is implemented, to avoid an unused-field warning accumulating silently.

### IN-02: Magic numbers for button bounds

**File:** `src/main/java/com/cxmxrgo/secondshift/client/screen/BindingAltarScreen.java:47`

**Issue:** `.bounds(leftPos + 8, topPos + 60, 80, 20)` hardcodes the confirm button's
position/size inline with no named constants, unlike `imageWidth`/`imageHeight` which are set
as fields. Minor — this screen is explicitly throwaway per its own doc comment (superseded by
Phase 5's real trade picker), so this is not worth blocking on, just noting for whoever writes
the Phase 5 replacement.

**Fix:** If this layout survives past the throwaway screen, extract the bounds into named
constants alongside `imageWidth`/`imageHeight`.

---

_Reviewed: 2026-09-05T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
