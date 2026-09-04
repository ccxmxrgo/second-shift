package com.cxmxrgo.secondshift.client;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.employee.EmployeeData;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
 * <p>{@code EntityJoinLevelEvent} is a game-bus event (unlike {@link ClientModBusEvents}'s
 * mod-bus events), so it needs its own {@code Dist.CLIENT}-gated subscriber class per the
 * PITFALLS §9 package-isolation convention — game bus is the default for
 * {@code @EventBusSubscriber} when {@code bus} is omitted.
 */
@EventBusSubscriber(modid = SecondShift.MODID, value = Dist.CLIENT)
public final class ClientEmployeeSyncDebug {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientEmployeeSyncDebug() {}

    @SubscribeEvent
    static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof Villager villager)) {
            return;
        }

        // T-4-03: hasData must be checked before getData — getData on a holder with no
        // attachment silently creates the default EmployeeData.EMPTY, which would
        // misreport a wild villager as "has employee data" if called first.
        if (!villager.hasData(ModAttachments.EMPLOYEE.get())) {
            return;
        }

        EmployeeData data = villager.getData(ModAttachments.EMPLOYEE.get());
        LOGGER.info("[SecondShift] client-side EmployeeData sync check: entity={} data={}", villager, data);
    }
}
