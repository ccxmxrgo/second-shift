package com.cxmxrgo.secondshift.client.screen;

import com.cxmxrgo.secondshift.menu.PromotionRitualMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 7 (07-CONTEXT.md D-04): the Promotion Ritual screen — the same enchanting-table texture
 * and scrollable-list pattern {@code BindingAltarScreen} established in Phase 5, with one addition:
 * a Confirm button, since this picker is "pick {@code pickCount}" (usually 2) rather than
 * "click once to immediately bind". See {@code TradeCandidateList}'s toggle-mode doc comment for
 * how selection stays entirely client-side until Confirm is pressed.
 */
public class PromotionRitualScreen extends AbstractContainerScreen<PromotionRitualMenu> {

    private static final ResourceLocation ENCHANTING_TABLE_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/gui/container/enchanting_table.png");

    private static final int LIST_X = 60;
    private static final int LIST_Y = 14;
    private static final int LIST_WIDTH = 108;
    private static final int LIST_HEIGHT = 52;
    private static final int LIST_ITEM_HEIGHT = 20;
    private static final int CONFIRM_Y = LIST_Y + LIST_HEIGHT + 2;
    private static final int CONFIRM_HEIGHT = 14;

    private TradeCandidateList candidateList;
    private Button confirmButton;

    public PromotionRitualScreen(PromotionRitualMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void init() {
        super.init();

        List<ItemStack> candidates = new ArrayList<>();
        for (int i = 0; i < PromotionRitualMenu.MAX_CANDIDATE_SLOTS; i++) {
            ItemStack stack = this.menu.getSlot(PromotionRitualMenu.CANDIDATE_SLOT_BASE + i).getItem();
            if (stack.isEmpty()) {
                break;
            }
            candidates.add(stack);
        }

        int pickCount = this.menu.getPickCount();
        this.candidateList = new TradeCandidateList(this.minecraft, LIST_WIDTH, LIST_HEIGHT,
                topPos + LIST_Y, LIST_ITEM_HEIGHT, candidates, pickCount, index -> {});
        this.candidateList.setX(leftPos + LIST_X);
        this.candidateList.setOnSelectionChanged(this::updateConfirmButton);
        this.addRenderableWidget(this.candidateList);

        this.confirmButton = Button.builder(confirmLabel(0, pickCount), b -> onConfirm())
                .bounds(leftPos + LIST_X, topPos + CONFIRM_Y, LIST_WIDTH, CONFIRM_HEIGHT)
                .build();
        this.confirmButton.active = false;
        this.addRenderableWidget(this.confirmButton);
    }

    private void updateConfirmButton() {
        int picked = this.candidateList.getSelectedIndices().size();
        int need = this.menu.getPickCount();
        this.confirmButton.setMessage(confirmLabel(picked, need));
        this.confirmButton.active = picked == need;
    }

    private static Component confirmLabel(int picked, int need) {
        return Component.translatable("gui.secondshift.promotion_ritual.confirm", picked, need);
    }

    private void onConfirm() {
        if (this.candidateList.getSelectedIndices().size() != this.menu.getPickCount()) {
            return;
        }
        int packed = PromotionRitualMenu.encodeConfirm(this.candidateList.getSelectedIndices());
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, packed);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(ENCHANTING_TABLE_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
        if (this.candidateList != null && this.candidateList.children().isEmpty()) {
            guiGraphics.drawWordWrap(this.font,
                    Component.translatable("message.secondshift.altar.empty_pool"),
                    LIST_X, LIST_Y + 2, LIST_WIDTH, 0xFFFF5555);
        }
    }
}
