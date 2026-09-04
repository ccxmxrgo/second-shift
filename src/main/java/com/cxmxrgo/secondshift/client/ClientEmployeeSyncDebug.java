package com.cxmxrgo.secondshift.client;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.employee.EmployeeData;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;

/**
 * LIGHT spike diagnostic (04-RESEARCH.md "LIGHT Spike Resolution"; EMP-01).
 *
 * <p>Answers, empirically, whether {@link net.neoforged.neoforge.attachment.AttachmentType.Builder#sync}
 * actually delivers {@link EmployeeData} to the client for an entity holder in NeoForge
 * 21.1.248 (the 1.21.1 docs page predates the entity-sync guarantee). This class adds no
 * rendering or gameplay behavior — it only logs, once a real bind is possible via Plan
 * 04-02/04-03, so the outcome can be observed under {@code runClient}.
 *
 * <p><b>Poll-based, not synchronous</b> (see
 * {@code .planning/research/entity-attachment-sync-timing-investigation.md}): NeoForge bundles
 * the attachment-sync sub-packet strictly after the entity-spawn packet inside the same
 * {@code ClientboundBundlePacket}. Checking {@code hasData} synchronously inside
 * {@link EntityJoinLevelEvent} is therefore structurally guaranteed to observe {@code false} —
 * the sync payload has not been processed yet at that point. Instead, {@link #onEntityJoinLevel}
 * only records the candidate villager's entity id, and {@link #onClientTick} polls the tracked
 * ids once per client tick (bounded to {@link #TIMEOUT_TICKS} ticks) until the attachment
 * actually arrives or the poll times out.
 *
 * <p>{@code EntityJoinLevelEvent} is a game-bus event (unlike {@link ClientModBusEvents}'s
 * mod-bus events), so it needs its own {@code Dist.CLIENT}-gated subscriber class per the
 * PITFALLS §9 package-isolation convention — game bus is the default for
 * {@code @EventBusSubscriber} when {@code bus} is omitted.
 */
@EventBusSubscriber(modid = SecondShift.MODID, value = Dist.CLIENT)
public final class ClientEmployeeSyncDebug {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Bounded poll window: 1 second at 20 TPS, per the investigation doc's recommendation. */
    private static final int TIMEOUT_TICKS = 20;

    /** Entity id -> ticks-pending. Client thread only; both handlers run on the client thread. */
    private static final Int2IntOpenHashMap PENDING = new Int2IntOpenHashMap();

    private ClientEmployeeSyncDebug() {}

    @SubscribeEvent
    static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof Villager)) {
            return;
        }

        int id = entity.getId();
        if (!PENDING.containsKey(id)) {
            PENDING.put(id, 0);
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            // Do not clear PENDING here — the level may simply not be ready yet this tick.
            return;
        }

        IntList ids = new IntArrayList(PENDING.keySet());
        for (int i = 0; i < ids.size(); i++) {
            int id = ids.getInt(i);
            Entity entity = level.getEntity(id);
            if (entity == null) {
                PENDING.remove(id);
                continue;
            }

            if (entity instanceof Villager villager && villager.hasData(ModAttachments.EMPLOYEE.get())) {
                EmployeeData data = villager.getData(ModAttachments.EMPLOYEE.get());
                LOGGER.info("[SecondShift] client-side EmployeeData sync check: entity={} data={}", villager, data);
                PENDING.remove(id);
                continue;
            }

            int ticksPending = PENDING.get(id) + 1;
            if (ticksPending > TIMEOUT_TICKS) {
                LOGGER.warn("[SecondShift] client-side EmployeeData sync check TIMED OUT for entity={}", id);
                PENDING.remove(id);
            } else {
                PENDING.put(id, ticksPending);
            }
        }
    }
}
