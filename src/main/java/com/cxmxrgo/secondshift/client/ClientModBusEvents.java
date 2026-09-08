package com.cxmxrgo.secondshift.client;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.client.render.SoulAltarRenderer;
import com.cxmxrgo.secondshift.client.screen.BindingAltarScreen;
import com.cxmxrgo.secondshift.client.screen.PromotionRitualScreen;
import com.cxmxrgo.secondshift.registry.ModBlockEntities;
import com.cxmxrgo.secondshift.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

/**
 * Client-only entry point for Second Shift (PITFALLS §9 package rule + D-11).
 *
 * <p>Establishes the {@code com.cxmxrgo.secondshift.client} package and proves
 * {@code Dist.CLIENT} class isolation — the dedicated server must never load this class.
 * It registers the charged-altar {@link SoulAltarRenderer} from
 * {@link EntityRenderersEvent.RegisterRenderers} (D-05 / POL-03); this is the only place
 * {@code SoulAltarRenderer} may be named. It also binds {@code secondshift:binding_altar} to
 * {@link BindingAltarScreen} from {@link RegisterMenuScreensEvent} (GUI-01) — the only place
 * {@code BindingAltarScreen} may be named. See {@link
 * com.cxmxrgo.secondshift.menu.BindingAltarMenu}'s doc comment for why round-12 reuses vanilla's
 * real {@code EnchantmentScreen} (via this thin subclass) instead of round-10's generic {@code
 * ContainerScreen}.
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
        // POL-06: the Mods-menu Config button — NeoForge's own generic ConfigurationScreen reads
        // every ModConfigSpec this mod registered (see ModConfig), no hand-rolled screen needed.
        // Deliberately registered here (client-only event) rather than in SecondShift's
        // constructor (common code, runs on the dedicated server too, where ConfigurationScreen's
        // GUI classes must never be touched).
        SecondShift.CONTAINER.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        LOGGER.info("[SecondShift] client setup ok");
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SOUL_ALTAR_BE.get(), SoulAltarRenderer::new);
        LOGGER.info("[SecondShift] registered SoulAltarRenderer for secondshift:soul_altar");
    }

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.BINDING_ALTAR.get(), BindingAltarScreen::new);
        LOGGER.info("[SecondShift] registered BindingAltarScreen for secondshift:binding_altar");
        event.register(ModMenus.PROMOTION_RITUAL.get(), PromotionRitualScreen::new);
        LOGGER.info("[SecondShift] registered PromotionRitualScreen for secondshift:promotion_ritual");
    }
}
