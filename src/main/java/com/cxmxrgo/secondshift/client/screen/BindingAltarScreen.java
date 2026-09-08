package com.cxmxrgo.secondshift.client.screen;

import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Binding Altar screen — reuses vanilla's real enchanting-table texture and layout per the user's
 * explicit request to make the altar "work with the enchanting table GUI", instead of a generic
 * or fully custom screen.
 *
 * <p><b>Why this extends {@code AbstractContainerScreen<BindingAltarMenu>} directly, not {@code
 * EnchantmentScreen}:</b> an earlier round of this redesign extended {@code EnchantmentScreen} to
 * reuse its background texture. That worked for a fixed 3-row layout, but {@code
 * EnchantmentScreen}'s OWN inherited {@code mouseClicked} does a hardcoded bounding-box check
 * against those same 3 fixed row positions (x=60, y=14+19*k, 108x19) and calls {@code
 * clickMenuButton(player, k)} directly — BEFORE any real widget (like this class's scrollable
 * {@link TradeCandidateList}) ever gets a chance to handle the click. Since {@link
 * BindingAltarMenu#clickMenuButton} is now re-enabled with real "bind candidate id" logic, that
 * old bounding-box check would fire on almost any click in the list's area and bind whichever
 * candidate happens to sit at index 0/1/2 — completely unrelated to which SCROLLED row the player
 * actually clicked. There is no way to override just the broken PART of {@code EnchantmentScreen}'s
 * {@code mouseClicked} (Java has no "skip one level of override" call), so this class instead
 * extends {@code AbstractContainerScreen} directly — the same base every container screen
 * (including {@code EnchantmentScreen} itself) ultimately derives from, and the one that actually
 * dispatches clicks to child widgets correctly. Nothing is lost: this class never used any
 * {@code EnchantmentScreen}-specific behavior (the 3D book, the cost-array tooltip) even before
 * this change — see git history for those now-fully-retired features.
 *
 * <p>See {@link BindingAltarMenu}'s doc comment for the round-15 "career path" redesign this
 * class implements: a real scrollable list ({@link TradeCandidateList}) of every candidate the
 * profession's top tier offers. This class's job is just: (1) draw the background texture
 * (unmodified vanilla enchanting-table art), (2) create and position that list in {@link #init()},
 * reading candidates from the menu's synced (but otherwise off-screen) candidate slots, and (3)
 * route a list-row click to the server via vanilla's existing menu-button RPC
 * ({@code Minecraft#gameMode#handleInventoryButtonClick}) — the same one {@code EnchantmentScreen}
 * itself uses for its own 3 real enchant options. No custom pixel-math beyond the list's own
 * bounding box, no custom texture, no custom tooltip code — the list's own rows show all the
 * cost/name/enchantment info {@code BindingAltarMenu} already baked into each candidate's display
 * item via {@code getHoverName()}.
 */
public class BindingAltarScreen extends AbstractContainerScreen<BindingAltarMenu> {

    private static final ResourceLocation ENCHANTING_TABLE_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/gui/container/enchanting_table.png");

    /** The candidate list's screen-relative bounding box — the same area {@link EnchantmentScreen}
     * itself reserves for its 3 enchant options, extended down to just above the player-inventory
     * label (vanilla's own inventory slots start at y=84 for the default 166-tall canvas this
     * screen keeps unmodified; y=80 leaves a small gap). */
    private static final int LIST_X = 60;
    private static final int LIST_Y = 14;
    private static final int LIST_WIDTH = 108;
    private static final int LIST_HEIGHT = 66;
    private static final int LIST_ITEM_HEIGHT = 20;

    private TradeCandidateList candidateList;

    public BindingAltarScreen(BindingAltarMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void init() {
        super.init();

        List<ItemStack> candidates = new ArrayList<>();
        for (int i = 0; i < BindingAltarMenu.MAX_CANDIDATE_SLOTS; i++) {
            ItemStack stack = this.menu.getSlot(BindingAltarMenu.CANDIDATE_SLOT_BASE + i).getItem();
            if (stack.isEmpty()) {
                break; // candidate slots are always filled contiguously from index 0 — see BindingAltarMenu
            }
            candidates.add(stack);
        }

        this.candidateList = new TradeCandidateList(this.minecraft, LIST_WIDTH, LIST_HEIGHT,
                topPos + LIST_Y, LIST_ITEM_HEIGHT, candidates,
                index -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, index));
        this.candidateList.setX(leftPos + LIST_X);
        this.addRenderableWidget(this.candidateList);
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
