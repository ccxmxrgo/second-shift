package com.cxmxrgo.secondshift.client.screen;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import com.cxmxrgo.secondshift.network.SelectTradesPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/**
 * Binding Altar screen (D-05 / D-06 / D-07 / GUI-01 / GUI-03) — the client-only render half of the
 * HARD GATE.
 *
 * <p><b>Round-6 UI redesign (2026-09-07, per user-provided mockup — replaces Plan 05-06's original
 * layout):</b> the editable name field is REMOVED this phase (employee always gets its
 * auto-generated default name — see {@code SelectTradesPayload}'s empty-name send below, which
 * relies on {@code ServerPayloadHandler.sanitizeName}'s existing fallback-to-default behavior, so
 * no payload/network changes were needed). Layout, top to bottom: a full-width Confirm Hire button
 * at the very top, then the Soul Block + profession-item sockets side by side (both rendered via
 * real {@code SoulSlot}s in {@link BindingAltarMenu}) with the scrollable trade-candidate list to
 * their right at the same height, then the profession name / Happiness placeholder in the narrow
 * column directly under the two sockets, then the standard player inventory grid.
 *
 * <p><b>Client-class isolation (Dist.CLIENT rule, mirrors {@code SoulAltarRenderer}):</b> this
 * class lives ONLY in {@code client/screen/} and must never be referenced from common code
 * ({@code menu/}, {@code content/}). It is constructed only from
 * {@code ClientModBusEvents.onRegisterScreens} via {@code RegisterMenuScreensEvent}.
 */
public class BindingAltarScreen extends AbstractContainerScreen<BindingAltarMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SecondShift.MODID, "textures/gui/binding_altar.png");

    private static final int COLOR_EMPTY_STATE = 0xFFFF5555;
    private static final int COLOR_MUTED = 0xFFA0A0A0;
    private static final int COLOR_DISPLAY_GOLD = 0xFFFFD700;

    /** Trade list occupies the region to the right of the two sockets, same top as the sockets. */
    private static final int TRADE_LIST_X = 56;
    private static final int TRADE_LIST_Y = 32;
    private static final int TRADE_LIST_WIDTH = 112;
    private static final int TRADE_LIST_HEIGHT = 48;
    private static final int TRADE_LIST_ITEM_HEIGHT = 16;

    private static final int CONFIRM_BUTTON_X = 8;
    private static final int CONFIRM_BUTTON_Y = 6;
    private static final int CONFIRM_BUTTON_WIDTH = 160;
    private static final int CONFIRM_BUTTON_HEIGHT = 20;

    /** Narrow column directly under the two sockets (x=8 to x=48) — profession name + Happiness. */
    private static final int LABEL_X = 8;
    private static final int PROFESSION_LABEL_Y = 54;
    private static final int HAPPINESS_LABEL_Y = 64;

    private TradeCandidateList candidateList;

    public BindingAltarScreen(BindingAltarMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 172;
        this.inventoryLabelY = BindingAltarMenu.INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();

        List<MerchantOffer> candidates = this.getMenu().getCandidateOffers();
        if (!candidates.isEmpty()) {
            this.candidateList = new TradeCandidateList(
                    this.minecraft, TRADE_LIST_WIDTH, TRADE_LIST_HEIGHT, topPos + TRADE_LIST_Y,
                    TRADE_LIST_ITEM_HEIGHT, candidates, this.getMenu().isAutoLocked(), index -> {});
            this.candidateList.setX(leftPos + TRADE_LIST_X);
            this.addRenderableWidget(this.candidateList);
        }

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.secondshift.binding_altar.confirm"),
                        btn -> {
                            int[] indices = this.candidateList == null
                                    ? new int[0]
                                    : this.candidateList.getSelectedIndices().stream().mapToInt(Integer::intValue).toArray();
                            // Round-6 redesign: no name field this phase — always send "", and
                            // ServerPayloadHandler.sanitizeName's existing fallback-to-default
                            // behavior applies the altar's auto-generated default name.
                            PacketDistributor.sendToServer(new SelectTradesPayload(indices, ""));
                        })
                .bounds(leftPos + CONFIRM_BUTTON_X, topPos + CONFIRM_BUTTON_Y, CONFIRM_BUTTON_WIDTH, CONFIRM_BUTTON_HEIGHT)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);

        Optional<VillagerProfession> profession = this.getMenu().getProfession();
        if (profession.isPresent()) {
            Component professionName = Component.translatable(
                    "entity.minecraft.villager." + BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession.get()).getPath());
            guiGraphics.drawString(this.font, professionName, LABEL_X, PROFESSION_LABEL_Y, COLOR_DISPLAY_GOLD, false);
        }

        // GUI-03: happiness has nothing real to show until Phase 9 — static muted placeholder only.
        guiGraphics.drawString(this.font, "Happiness: N/A", LABEL_X, HAPPINESS_LABEL_Y, COLOR_MUTED, false);

        if (this.candidateList == null) {
            guiGraphics.drawString(this.font, "Nothing to Offer", TRADE_LIST_X, TRADE_LIST_Y + 2, COLOR_EMPTY_STATE, false);
            List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(
                    Component.translatable("message.secondshift.altar.empty_pool"), TRADE_LIST_WIDTH);
            int y = TRADE_LIST_Y + 12;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                guiGraphics.drawString(this.font, line, TRADE_LIST_X, y, COLOR_EMPTY_STATE, false);
                y += 10;
            }
        }
    }
}
