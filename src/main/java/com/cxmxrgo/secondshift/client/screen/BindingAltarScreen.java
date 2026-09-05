package com.cxmxrgo.secondshift.client.screen;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import com.cxmxrgo.secondshift.network.SelectTradesPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

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
 *
 * <p><b>Plan 05-05:</b> the "Confirm Hire" button below still derives an interim placeholder
 * selection (the first up-to-2 candidate offers, empty name string) — this selection logic is
 * deliberately throwaway, superseded by Plan 05-06's real name-field + click-to-toggle candidate
 * rows. The {@link SelectTradesPayload} it sends and the server-side handler it exercises are NOT
 * throwaway; this button is only the temporary trigger for permanent plumbing (mirrors Phase 4's
 * exact "throwaway trigger now, real UI next plan" pattern).
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
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.secondshift.binding_altar.confirm"),
                        btn -> {
                            // Interim placeholder selection (Plan 05-06 replaces this with the real
                            // name-field + click-to-toggle candidate rows). Empty name string
                            // triggers the server-side blank-fallback to the pre-filled default
                            // (D-03).
                            int n = Math.min(2, this.getMenu().getCandidateOffers().size());
                            int[] indices = java.util.stream.IntStream.range(0, n).toArray();
                            PacketDistributor.sendToServer(new SelectTradesPayload(indices, ""));
                        })
                .bounds(leftPos + 8, topPos + 60, 80, 20)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
}
