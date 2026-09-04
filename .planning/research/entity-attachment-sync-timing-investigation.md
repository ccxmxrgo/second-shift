# Entity Attachment Sync Timing Investigation

## Verdict (HIGH confidence)

**Attachment sync is NOT broken.** `AttachmentType.Builder#sync(STREAM_CODEC)` works correctly for entities in NeoForge 21.1.248. The diagnostic (`ClientEmployeeSyncDebug`) is checking `hasData()` at a point in client-side packet processing that is **structurally guaranteed to run before the sync data is applied** — this is not a race or a rare timing gap, it is a deterministic ordering fact in NeoForge's own code, every single time, for every entity. That is why the log line never appeared once across ~25 launches: the check cannot ever succeed as written, regardless of how many times you retry.

This is confirmed directly from NeoForge 21.1.248 source (decompiled/patched vanilla sources recovered from the Gradle NeoFormRuntime cache, plus the NeoForge `-sources.jar` for the exact same version), not from web docs or guesswork.

## Evidence trail

### 1. How and when NeoForge syncs an entity attachment to the client

Traced the real path in `net.minecraft.server.level.ServerEntity` (vanilla class, patched by NeoForge) and `net.neoforged.neoforge.attachment.AttachmentSync`:

- When a player starts tracking an entity, `ServerEntity#addPairing(ServerPlayer player)` runs (called from `ChunkMap`'s per-tick tracking-distance logic — this is the `PlayerEvent.StartTracking` moment, confirmed by the adjacent `EventHooks.onStartEntityTracking(this.entity, player)` call in the same method).
- `addPairing` builds **one single packet list** via `sendPairingData(...)`, in this exact order:
  1. `entity.getAddEntityPacket(this)` — the spawn packet
  2. entity tracked-data, attributes, equipment, passengers, leash packets (as applicable)
  3. **last**: `net.neoforged.neoforge.attachment.AttachmentSync.syncInitialEntityAttachments(this.entity, p_289562_, p_289563_::accept)` — this appends a `SyncAttachmentsPayload` packet
- All of these packets are wrapped into **one `ClientboundBundlePacket`** and sent as a single network send (`player.connection.send(new ClientboundBundlePacket(list))`).

So: yes, the sync packet (`SyncAttachmentsPayload`) is a genuinely separate payload from the entity's spawn packet — but it is bundled together with it and sent atomically in the same tick, not on some arbitrary later tick.

Client side, in `net.minecraft.client.multiplayer.ClientPacketListener`:
- `handleBundlePacket(ClientboundBundlePacket p_packet)` iterates `p_packet.subPackets()` and calls `packet.handle(this)` on each **synchronously, in order, in the same method call** — no tick boundary between them.
- The **first** sub-packet handled is `handleAddEntity(ClientboundAddEntityPacket)`, which calls `this.level.addEntity(entity)`.
- `ClientLevel#addEntity(Entity entity)` posts `EntityJoinLevelEvent` **synchronously as literally the first line of the method**, before the entity is even added to `entityStorage`:
  ```java
  public void addEntity(Entity entity) {
      if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(entity, this)).isCanceled()) return;
      ...
      this.entityStorage.addEntity(entity);
  ```
- The `SyncAttachmentsPayload` sub-packet is processed **later in the same bundle loop** (it was appended last server-side), which calls into `AttachmentSync.receiveSyncedDataAttachments(...)` and only *there* does `holder.getAttachmentMap().put(type, result)` actually apply the value.

### 2. Is the hypothesis correct?

**Yes, and it is stronger than "a possible gap" — it is a guaranteed ordering, always in this direction, within the same tick/same synchronous call stack:**

`EntityJoinLevelEvent` (→ fires `ClientEmployeeSyncDebug#onEntityJoinLevel`) always runs **strictly before** the attachment data is written into the holder's attachment map, because:
- Both happen inside one `handleBundlePacket` call, processing sub-packets in list order.
- The add-entity packet (which triggers `EntityJoinLevelEvent`) is always first in that list.
- The attachment-sync packet is always appended last, after every other pairing packet.

There is no scenario in 21.1.248 where the attachment packet could arrive and be processed before the entity's own join event fires for this initial-tracking path — the code structurally forbids it. It is not "a tick or two later" — it is milliseconds later, within the exact same call frame, but still strictly *after* the check already ran and returned.

(Separately, `AttachmentSync.onChunkSent` — for block-entity/chunk attachments — is unrelated to this entity case and confirms NeoForge treats entity-attachment sync as a distinct, later-queued mechanism from chunk/block-entity sync, consistent with the hypothesis's framing.)

### 3. The correct way to observe "the client now has the synced value"

No dedicated NeoForge client event exists for "attachment sync packet applied." Confirmed by reading `AttachmentSync.receiveSyncedDataAttachments(...)` and `SyncAttachmentsPayload.java` in full — the method mutates the holder's attachment map directly and returns; it does not post any event afterward. There is nothing named like an "attachment sync" client event anywhere in the `net.neoforged.neoforge.attachment` or `net.neoforged.neoforge.network.payload` packages in this version.

**Recommended fix for the diagnostic** — do not rely on any join-time event. Instead, poll on the client tick event for a short, bounded window:

- Subscribe to `ClientTickEvent.Post` (or `.Pre`) instead of / in addition to `EntityJoinLevelEvent`.
- On each client tick, scan currently loaded `Villager` entities in the client level (`Minecraft.getInstance().level.entitiesForRendering()` or `level.getEntities()`), or — simpler and cheaper — keep a small `Map<Integer, Integer>` of "entity id → ticks since first observed without data" seeded when `EntityJoinLevelEvent` fires for a villager that currently reports `!hasData(...)`.
- On a later tick (even the very next tick is enough — the sync packet arrives in the same bundle as the join packet, so it will already be applied by the first subsequent tick), re-check `hasData()`/`getData()` for entities in that map; log and remove them from the map once `hasData()` becomes true, and also give up (remove + optionally log a real failure) after some generous bound like 20 ticks (1 second) to actually catch a genuine failure-to-sync case rather than a timing artifact.
- This distinguishes "attachment genuinely never arrives" (bug) from "arrives one tick after join" (working as designed) — which is exactly the empirical question the LIGHT spike wants to answer, without depending on packet-internal ordering assumptions.

Concretely, the minimal correct change is: **do not do the `hasData()` check inside the `EntityJoinLevelEvent` handler itself.** Instead, in that handler, only *record* the villager's entity id (if it's a candidate, e.g. any client-visible `Villager`) into a static tracking collection; do the actual `hasData()`/`getData()`/log check in a `ClientTickEvent.Post` handler iterated over that collection, checking again every tick until either it succeeds (log + remove) or a max-tick timeout elapses (log a "sync never arrived" warning + remove). This requires no assumption about exact tick offset and is robust even if `PlayerEvent.StartTracking`-driven sync were ever delayed further in a future NeoForge version.

### 4. Ruling out other explanations

- **Log filtering / level config**: Ruled out directly. `run/logs/latest.log` contains multiple other `com.cxmxrgo.secondshift.*` `INFO`-level log lines (e.g. `[SecondShift] loading 0.1.0 on NeoForge`, `[SecondShift] registered BindingAltarScreen for secondshift:binding_altar`, `[SecondShift] common setup - ...`, `[SecondShift] menu registered: secondshift:binding_altar`) at `INFO` level from both `modloading-worker-0` and `Render thread`. No `log4j2.xml` override exists under `run/config` or the project root beyond the MDK defaults. Since `ClientEmployeeSyncDebug` uses the identical `LogUtils.getLogger()` + `LOGGER.info(...)` pattern as these other working lines, and other `com.cxmxrgo` INFO output is confirmed present, a log-level/filter explanation is excluded.
- **Sync-predicate-gated overload (`BiPredicate<IAttachmentHolder, ServerPlayer>` overload) causing the packet to be gated per-player**: Ruled out by reading `src/main/java/com/cxmxrgo/secondshift/registry/ModAttachments.java` directly — it calls the plain `.sync(EmployeeData.STREAM_CODEC)` overload (single-arg `StreamCodec`), not the predicate-gated 2-arg overload. Confirmed in `AttachmentSync.syncUpdate`/`syncInitialAttachments`: with no `syncHandler` predicate override, NeoForge's default `AttachmentSyncHandler` created from a bare `StreamCodec` has no additional per-player gating logic beyond "does this attachment have a sync handler at all" (`type.syncHandler != null`), which is true here. So this is not player-count-gated or conditionally suppressed for the local single-player/LAN test scenario.
- **`hasData` misbehaving by creating a default value**: Not applicable to why the log never fires — the diagnostic already guards against this correctly (checks `hasData()` before `getData()`, per its own `T-4-03` comment), and this concern is about a *different* bug shape (false positive) than the one observed (complete silence), so it doesn't explain the symptom either way.
- **Server-side persistence bug**: Ruled out already by the user's own manual test — `/data get entity <uuid>` confirms the attachment persists correctly server-side across chunk-unload/save-reload. This investigation only concerned the *client-side observability* of sync, and confirms the server-side mechanism (`AttachmentSync.syncInitialEntityAttachments`, called from `ServerEntity.sendPairingData`) is real, wired up, and unconditionally attempted for every attachment with a sync handler.

## Sources

- `C:\Users\user\.gradle\caches\modules-2\files-2.1\net.neoforged\neoforge\21.1.248\f0378c9b210dfc258170b6412a85fdffe4aea7fe\neoforge-21.1.248-sources.jar` — exact-version NeoForge sources jar, read directly: `net/neoforged/neoforge/attachment/AttachmentSync.java`, `net/neoforged/neoforge/attachment/AttachmentType.java`, `net/neoforged/neoforge/network/payload/SyncAttachmentsPayload.java`. **HIGH** — this is the exact binary/source pair used to compile against for this project.
- `C:\Users\user\.gradle\caches\neoformruntime\intermediate_results\sourcesAndCompiledWithNeoForge_cca44f8311ba9c1569e5cd6465f2cd3b8cb86277_output.jar` — decompiled-and-NeoForge-patched vanilla sources for this exact NeoForge/MC version, read directly: `net/minecraft/server/level/ServerEntity.java` (`addPairing`, `sendPairingData`), `net/minecraft/client/multiplayer/ClientPacketListener.java` (`handleBundlePacket`, `handleAddEntity`), `net/minecraft/client/multiplayer/ClientLevel.java` (`addEntity` — shows the `EntityJoinLevelEvent` post as literally the first statement). **HIGH** — this is the actual patched vanilla source tree NeoForge produces for 21.1.248 via NeoFormRuntime, on this machine, for this project's toolchain.
- `C:\Users\user\Documents\PROJECTS\necromancy-mod\src\main\java\com\cxmxrgo\secondshift\registry\ModAttachments.java` — confirms the plain `.sync(EmployeeData.STREAM_CODEC)` overload is used (not the `BiPredicate`-gated overload). **HIGH**
- `C:\Users\user\Documents\PROJECTS\necromancy-mod\src\main\java\com\cxmxrgo\secondshift\client\ClientEmployeeSyncDebug.java` — the diagnostic under investigation; confirms it checks `hasData()` synchronously inside the `EntityJoinLevelEvent` handler. **HIGH**
- `C:\Users\user\Documents\PROJECTS\necromancy-mod\run\logs\latest.log` — grepped directly; confirms other `com.cxmxrgo.secondshift.*` `INFO` lines are present and unfiltered, ruling out a log-level explanation. **HIGH**

### Known gaps

- Did not locate/read the exact `PlayerEvent.StartTracking`/`ChunkMap` call site that invokes `serverEntity.addPairing(player)` in full (only found the call site reference at `ChunkMap.java:1339`); this doesn't affect the verdict since `addPairing`'s internals (the actual bundling/ordering) were read in full and are what determines the ordering guarantee — including that source would only add confirmation of *when in the tick* tracking starts, not change the client-side ordering conclusion.
- Did not trace the exact class that dispatches `SyncAttachmentsPayload` client-side to `AttachmentSync.receiveSyncedDataAttachments` (likely a small handler in `NetworkInitialization`/a `ClientPayloadHandler`-style class); confirmed the payload and the receiving method exist and are wired (the mod's own confirmed server-side persistence + this ordering analysis together fully explain the symptom without needing that last wiring detail).
