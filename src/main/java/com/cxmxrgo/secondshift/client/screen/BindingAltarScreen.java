package com.cxmxrgo.secondshift.client.screen;

import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;

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
 * {@code EnchantmentScreen} itself uses). Everything else — the background texture, the item icon
 * in each row and the reroll button (real vanilla per-slot rendering, since they're real slots),
 * the hover highlight, and the tooltip (real vanilla item tooltip, showing this class's {@code
 * DataComponents.LORE} cost/reroll-cost text) — is 100% inherited, unmodified vanilla behavior.
 * No custom pixel-math, no custom texture, no custom tooltip code.
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
            boolean active = !menu.getSlot(BindingAltarMenu.TRADE_SLOT_BASE + row).getItem().isEmpty();
            guiGraphics.blitSprite(active ? ENCHANTMENT_SLOT_SPRITE : ENCHANTMENT_SLOT_DISABLED_SPRITE, rowX, rowY, 108, 19);
        }
        RenderSystem.disableBlend();
    }
}
