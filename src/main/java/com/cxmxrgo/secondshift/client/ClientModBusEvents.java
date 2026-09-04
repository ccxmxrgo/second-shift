package com.cxmxrgo.secondshift.client;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.client.render.SoulAltarRenderer;
import com.cxmxrgo.secondshift.registry.ModBlockEntities;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.slf4j.Logger;

/**
 * Client-only entry point for Second Shift (PITFALLS §9 package rule + D-11).
 *
 * <p>Establishes the {@code com.cxmxrgo.secondshift.client} package and proves
 * {@code Dist.CLIENT} class isolation — the dedicated server must never load this class.
 * It registers the charged-altar {@link SoulAltarRenderer} from
 * {@link EntityRenderersEvent.RegisterRenderers} (D-05 / POL-03); this is the only place
 * {@code SoulAltarRenderer} may be named.
 *
 * <p>{@code Bus.MOD} is set explicitly for parity with the canonical refs; FML derives
 * it regardless ({@code FMLClientSetupEvent} and {@code EntityRenderersEvent} are both
 * mod-bus events — PITFALLS §2).
 */
@EventBusSubscriber(modid = SecondShift.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModBusEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientModBusEvents() {}

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("[SecondShift] client setup ok");
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SOUL_ALTAR_BE.get(), SoulAltarRenderer::new);
        LOGGER.info("[SecondShift] registered SoulAltarRenderer for secondshift:soul_altar");
    }
}
