package com.cxmxrgo.secondshift.client;

import com.cxmxrgo.secondshift.SecondShift;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

/**
 * Client-only entry point for Second Shift (PITFALLS §9 package rule + D-11).
 *
 * <p>Its only job in Phase 1 is to establish the {@code com.cxmxrgo.secondshift.client}
 * package and prove {@code Dist.CLIENT} class isolation works — the dedicated server
 * must never load this class. No screen / menu / render code (none exists yet).
 *
 * <p>{@code Bus.MOD} is set explicitly for parity with the canonical refs; FML derives
 * it regardless ({@code FMLClientSetupEvent} is a mod-bus event — PITFALLS §2).
 */
@EventBusSubscriber(modid = SecondShift.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModBusEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientModBusEvents() {}

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("[SecondShift] client setup ok");
    }
}
