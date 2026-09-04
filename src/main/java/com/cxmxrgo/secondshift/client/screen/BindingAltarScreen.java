package com.cxmxrgo.secondshift.client.screen;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Binding Altar screen (D-05 / D-06 / D-07 / GUI-01) — the client-only render half of the HARD
 * GATE. Blits the UI-SPEC 176x166 panel and relies entirely on vanilla defaults for the title
 * ("Binding Altar") and "Inventory" label positions; the one mod slot is drawn by vanilla's own
 * slot-rendering using {@link BindingAltarMenu}'s slot list.
 *
 * <p><b>Client-class isolation (Dist.CLIENT rule, mirrors {@code SoulAltarRenderer}):</b> this
 * class lives ONLY in {@code client/screen/} and must never be referenced from common code
 * ({@code menu/}, {@code content/}). It is constructed only from
 * {@code ClientModBusEvents.onRegisterScreens} via {@code RegisterMenuScreensEvent}.
 */
public class BindingAltarScreen extends AbstractContainerScreen<BindingAltarMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SecondShift.MODID, "textures/gui/binding_altar.png");

    public BindingAltarScreen(BindingAltarMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
}
