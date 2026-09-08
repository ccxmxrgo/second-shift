package com.cxmxrgo.secondshift.client.screen;

import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;

/**
 * Binding Altar screen — round-12 redesign (2026-09-08): reuses vanilla's real
 * {@link EnchantmentScreen} per the user's explicit request to make the altar "work with the
 * enchanting table GUI", instead of the generic {@code ContainerScreen} used in round-10.
 *
 * <p>See {@link BindingAltarMenu}'s doc comment for why the underlying menu is a real
 * {@code EnchantmentMenu} subclass, and why the 3 trade rows are ordinary {@code Slot}s rather
 * than driving vanilla's enchant-button/cost-array system. Because of that choice, this class
 * needs to override only ONE thing: {@link #renderBg}, to paint each row's parchment-bar
 * background (enabled/disabled, exact same sprite constants and coordinates {@code
 * EnchantmentScreen} itself uses) and to keep the enchanting table's iconic animated book present.
 * Everything else — the background texture, the item icon in each row (real vanilla per-slot
 * rendering, since the rows are real slots), the hover highlight, and the tooltip (real vanilla
 * item tooltip, showing this class's {@code DataComponents.LORE} cost text) — is 100% inherited,
 * unmodified vanilla behavior. No custom pixel-math, no custom texture, no custom tooltip code.
 *
 * <p><b>Why the book model is re-declared instead of reused:</b> {@code EnchantmentScreen}'s own
 * {@code bookModel} field and {@code renderBook} method are both {@code private}, so a subclass in
 * a different package (required — {@code Dist.CLIENT} package isolation, PITFALLS §9) cannot call
 * or reuse them at all. This class holds its own {@link BookModel} instance and a verbatim copy of
 * {@code renderBook}'s body, reading the SAME public, inherited animation-state fields ({@code
 * time}/{@code flip}/{@code open}/etc.) that {@code EnchantmentScreen}'s own (now-dead, since we
 * never call it) {@code renderBook} would have used — {@code tickBook()} (inherited, unmodified,
 * called every tick via {@code containerTick()}) keeps updating them regardless.
 *
 * <p><b>Known accepted limitation:</b> the book's "open" animation is driven by {@code
 * EnchantmentMenu.costs[]} being nonzero (real vanilla logic in {@code tickBook()}, which this
 * class does not override). {@link BindingAltarMenu} deliberately keeps {@code costs[]} at 0
 * forever (see its doc comment), so the book renders as permanently closed rather than fluttering
 * open — a missed cosmetic flourish, not a bug, and arguably fitting for a closed grimoire prop on
 * a necromancy altar.
 */
public class BindingAltarScreen extends EnchantmentScreen {

    private static final ResourceLocation ENCHANTING_TABLE_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/gui/container/enchanting_table.png");
    private static final ResourceLocation ENCHANTING_BOOK_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/entity/enchanting_table_book.png");
    private static final ResourceLocation ENCHANTMENT_SLOT_DISABLED_SPRITE =
            ResourceLocation.withDefaultNamespace("container/enchanting_table/enchantment_slot_disabled");
    private static final ResourceLocation ENCHANTMENT_SLOT_SPRITE =
            ResourceLocation.withDefaultNamespace("container/enchanting_table/enchantment_slot");

    private BookModel bookModel;

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
    protected void init() {
        super.init();
        this.bookModel = new BookModel(this.minecraft.getEntityModels().bakeLayer(ModelLayers.BOOK));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(ENCHANTING_TABLE_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight);
        renderBook(guiGraphics, i, j, partialTick);

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

    /**
     * Verbatim copy of {@code EnchantmentScreen#renderBook} (private in the vanilla class — see
     * class doc), reading this class's own {@link #bookModel} but the SAME inherited, public
     * animation-state fields {@code tickBook()} (also inherited, unmodified) keeps updating.
     */
    private void renderBook(GuiGraphics guiGraphics, int x, int y, float partialTick) {
        float f = Mth.lerp(partialTick, this.oOpen, this.open);
        float f1 = Mth.lerp(partialTick, this.oFlip, this.flip);
        Lighting.setupForEntityInInventory();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float) x + 33.0F, (float) y + 31.0F, 100.0F);
        guiGraphics.pose().scale(-40.0F, 40.0F, 40.0F);
        guiGraphics.pose().mulPose(Axis.XP.rotationDegrees(25.0F));
        guiGraphics.pose().translate((1.0F - f) * 0.2F, (1.0F - f) * 0.1F, (1.0F - f) * 0.25F);
        float f3 = -(1.0F - f) * 90.0F - 90.0F;
        guiGraphics.pose().mulPose(Axis.YP.rotationDegrees(f3));
        guiGraphics.pose().mulPose(Axis.XP.rotationDegrees(180.0F));
        float f4 = Mth.clamp(Mth.frac(f1 + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float f5 = Mth.clamp(Mth.frac(f1 + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        this.bookModel.setupAnim(0.0F, f4, f5, f);
        VertexConsumer vertexconsumer = guiGraphics.bufferSource().getBuffer(this.bookModel.renderType(ENCHANTING_BOOK_LOCATION));
        this.bookModel.renderToBuffer(guiGraphics.pose(), vertexconsumer, 15728880, OverlayTexture.NO_OVERLAY);
        guiGraphics.flush();
        guiGraphics.pose().popPose();
        Lighting.setupFor3DItems();
    }
}
