package com.cxmxrgo.secondshift.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Binding Altar candidate-row list (05-UI-SPEC.md "Screen Layout" / "Row visual states") — a
 * vanilla {@link ObjectSelectionList} of tier-1 {@link MerchantOffer} rows, click-to-toggle
 * (D-02), capped at 2 selections, with a permanently-locked display mode for auto-locked pools
 * (PICK-04, pool size &lt;= 2).
 *
 * <p><b>Client-class isolation:</b> lives only in {@code client/screen/}, mirroring {@link
 * BindingAltarScreen}'s own isolation rule — never referenced from common code.
 */
public class TradeCandidateList extends ObjectSelectionList<TradeCandidateList.Entry> {

    /** Row visual-state colors, per 05-UI-SPEC.md's Color contract. */
    static final int COLOR_UNSELECTED_FILL = 0xFF8B8B8B;
    static final int COLOR_HOVER_OVERLAY = 0x80FFFFFF;
    static final int COLOR_SELECTED_BORDER = 0xFFFFD700;
    static final int COLOR_SELECTED_FILL = 0x40FFD700;
    static final int COLOR_REJECT_BORDER = 0xFFFF5555;

    private static final int MAX_SELECTED = 2;
    private static final int REJECT_FLASH_TICKS = 6;

    private final boolean autoLocked;

    /**
     * @param onSelectionChanged invoked with the row's candidate index after every successful
     *     toggle (selection ON or OFF); the owning screen re-derives its full selected-index list
     *     from {@link #getSelectedIndices()} rather than relying on the passed value alone.
     */
    public TradeCandidateList(Minecraft minecraft, int width, int height, int top, int itemHeight,
                               List<MerchantOffer> candidates, boolean autoLocked, IntConsumer onSelectionChanged) {
        super(minecraft, width, height, top, itemHeight);
        this.autoLocked = autoLocked;
        for (int i = 0; i < candidates.size(); i++) {
            this.addEntry(new Entry(i, candidates.get(i), autoLocked, this, onSelectionChanged));
        }
    }

    /** Number of currently-selected (non-locked-exempt) rows across the whole list. */
    int selectedCount() {
        int count = 0;
        for (Entry entry : children()) {
            if (entry.selected) {
                count++;
            }
        }
        return count;
    }

    /** The candidate indices the player has currently selected (or all, if auto-locked). */
    public List<Integer> getSelectedIndices() {
        return children().stream().filter(e -> e.selected).map(e -> e.index).toList();
    }

    @Override
    public int getRowWidth() {
        return this.width - 6;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getRight() - 6;
    }

    /** One candidate row (one materialized {@link MerchantOffer}). */
    public static class Entry extends ObjectSelectionList.Entry<Entry> {

        private final int index;
        private final MerchantOffer offer;
        private final boolean locked;
        private final TradeCandidateList owner;
        private final IntConsumer onSelectionChanged;

        private boolean selected;
        private int rejectFlashTicksRemaining;

        Entry(int index, MerchantOffer offer, boolean locked, TradeCandidateList owner, IntConsumer onSelectionChanged) {
            this.index = index;
            this.offer = offer;
            this.locked = locked;
            this.owner = owner;
            this.onSelectionChanged = onSelectionChanged;
            this.selected = locked;
        }

        @Override
        public Component getNarration() {
            return offer.getResult().getHoverName();
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                            int mouseX, int mouseY, boolean hovered, float partialTick) {
            if (rejectFlashTicksRemaining > 0) {
                rejectFlashTicksRemaining--;
            }

            // Background fill.
            guiGraphics.fill(left, top, left + width, top + height, TradeCandidateList.COLOR_UNSELECTED_FILL);
            if (hovered && !selected) {
                guiGraphics.fill(left, top, left + width, top + height, TradeCandidateList.COLOR_HOVER_OVERLAY);
            }

            boolean flashing = rejectFlashTicksRemaining > 0;
            if (selected && !flashing) {
                guiGraphics.fill(left, top, left + width, top + height, TradeCandidateList.COLOR_SELECTED_FILL);
                guiGraphics.renderOutline(left, top, width, height, TradeCandidateList.COLOR_SELECTED_BORDER);
            } else if (flashing) {
                guiGraphics.renderOutline(left, top, width, height, TradeCandidateList.COLOR_REJECT_BORDER);
            }

            int x = left + 2;
            int iconY = top + (height - 18) / 2;

            if (selected && !locked) {
                // Small gold checkmark glyph left of the icon.
                net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
                guiGraphics.drawString(font, "✓", x, top + (height - 8) / 2, TradeCandidateList.COLOR_SELECTED_BORDER, false);
            }

            int iconX = x + 10;
            guiGraphics.renderItem(offer.getResult(), iconX, iconY);
            int textX = iconX + 18 + 4;

            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            int textY = top + (height - 8) / 2;
            guiGraphics.drawString(font, offer.getResult().getHoverName(), textX, textY, 0xFFFFFFFF, false);

            String price = priceText();
            int priceWidth = font.width(price);
            int priceX = left + width - 2 - priceWidth;
            guiGraphics.drawString(font, price, priceX, textY, 0xFFA0A0A0, false);
        }

        private String priceText() {
            ItemStack costA = offer.getCostA();
            StringBuilder sb = new StringBuilder();
            sb.append(costA.getCount()).append(' ').append(costA.getHoverName().getString());
            ItemStack costB = offer.getCostB();
            if (!costB.isEmpty()) {
                sb.append(" + ").append(costB.getCount()).append(' ').append(costB.getHoverName().getString());
            }
            if (locked) {
                sb.append(' ').append(Component.translatable("gui.secondshift.binding_altar.trade_locked").getString());
            }
            return sb.toString();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (locked) {
                // Non-interactive, no-op click (D-04's "no-op, don't disable-gray-out" tone).
                return false;
            }
            if (!selected && owner.selectedCount() >= MAX_SELECTED) {
                rejectFlashTicksRemaining = REJECT_FLASH_TICKS;
                return true;
            }
            selected = !selected;
            onSelectionChanged.accept(index);
            return true;
        }
    }
}
