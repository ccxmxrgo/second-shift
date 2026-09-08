package com.cxmxrgo.secondshift.client.screen;

import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Binding Altar screen — round-12 redesign (2026-09-08): reuses vanilla's real
 * {@link EnchantmentScreen} per the user's explicit request to make the altar "work with the
 * enchanting table GUI", instead of the generic {@code ContainerScreen} used in round-10.
 *
 * <p>See {@link BindingAltarMenu}'s doc comment for why the underlying menu is a real
 * {@code EnchantmentMenu} subclass, and why the trade rows and the reroll button are ordinary
 * {@code Slot}s rather than driving vanilla's enchant-button/cost-array system. Because of that
 * choice, this class needs to override only ONE thing: {@link #renderBg}, to paint each trade
 * row's parchment-bar background (enabled/disabled, exact same sprite constants and coordinates
 * {@code EnchantmentScreen} itself uses) and — the entire point of reusing this particular vanilla
 * screen, per the user's explicit request — the item's real name printed beside its icon, in the
 * same spot vanilla prints its scrambled rune text. Everything else — the background texture, the
 * item icon in each row and the reroll button (real vanilla per-slot rendering, since they're real
 * slots), the hover highlight, and the tooltip (real vanilla item tooltip, showing this class's
 * {@code DataComponents.LORE} cost/reroll-cost text) — is 100% inherited, unmodified vanilla
 * behavior. No custom pixel-math, no custom texture, no custom tooltip code.
 *
 * <p><b>Round-13:</b> the animated 3D book vanilla normally renders in the area now occupied by
 * the reroll button has been dropped entirely (it would render behind/through the reroll slot's
 * icon) — see git history for the round-12 version that reproduced it.
 */
public class BindingAltarScreen extends EnchantmentScreen {

    private static final ResourceLocation ENCHANTING_TABLE_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/gui/container/enchanting_table.png");
    private static final ResourceLocation ENCHANTMENT_SLOT_DISABLED_SPRITE =
            ResourceLocation.withDefaultNamespace("container/enchanting_table/enchantment_slot_disabled");
    private static final ResourceLocation ENCHANTMENT_SLOT_SPRITE =
            ResourceLocation.withDefaultNamespace("container/enchanting_table/enchantment_slot");

    /**
     * Takes the generic {@code EnchantmentMenu} type, not {@code BindingAltarMenu}, even though
     * only a {@code BindingAltarMenu} is ever actually passed here — {@code BindingAltarScreen}
     * inherits {@code MenuAccess<EnchantmentMenu>} from {@code AbstractContainerScreen<EnchantmentMenu>}
     * (that generic parameter is fixed by {@code EnchantmentScreen} itself, not something a
     * subclass can narrow), and {@code RegisterMenuScreensEvent#register}'s constructor-reference
     * matching requires an exact match against that inherited bound. {@link #getMenu()} downcasts
     * where the {@code BindingAltarMenu}-specific API is actually needed.
     */
    public BindingAltarScreen(EnchantmentMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(ENCHANTING_TABLE_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight);

        BindingAltarMenu menu = (BindingAltarMenu) this.menu;
        RenderSystem.enableBlend();
        for (int row = 0; row < BindingAltarMenu.OPTION_COUNT; row++) {
            int rowX = i + 60;
            int rowY = j + 14 + 19 * row;
            ItemStack rowItem = menu.getSlot(BindingAltarMenu.TRADE_SLOT_BASE + row).getItem();
            guiGraphics.blitSprite(!rowItem.isEmpty() ? ENCHANTMENT_SLOT_SPRITE : ENCHANTMENT_SLOT_DISABLED_SPRITE,
                    rowX, rowY, 108, 19);
            if (!rowItem.isEmpty()) {
                // The name text is the entire reason this altar reuses this particular vanilla
                // screen — real item name where vanilla prints scrambled rune filler, in the same
                // spot (just right of the 16x16 icon vanilla's own per-slot rendering draws there).
                guiGraphics.drawWordWrap(this.font, rowItem.getHoverName(), rowX + 20, rowY + 2, 84, 4210752);
            }
        }
        RenderSystem.disableBlend();

        // Round-14 (user feedback): the reroll button's tooltip covered the trade rows below it
        // whenever hovered (this button sits near the top of the screen, so vanilla's tooltip flips
        // upward, right over row 0/1). A short always-visible label under the icon means the player
        // never needs to hover it to know what it does.
        int rerollCenterX = i + BindingAltarMenu.REROLL_SLOT_X + 8;
        int rerollLabelY = j + BindingAltarMenu.REROLL_SLOT_Y + 18;
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.secondshift.binding_altar.reroll_short"),
                rerollCenterX, rerollLabelY, 4226832);
    }

    /**
     * Round-14 (follow-up): the always-visible "Reroll" label made the hover tooltip redundant,
     * but didn't stop it from popping up and still covering the trade rows above it. Suppress the
     * tooltip specifically for the reroll slot — every other slot (trade rows, player inventory)
     * keeps its normal vanilla tooltip.
     */
    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int x, int y) {
        if (this.hoveredSlot != null && this.hoveredSlot.index == BindingAltarMenu.REROLL_SLOT) {
            return;
        }
        super.renderTooltip(guiGraphics, x, y);
    }
}
