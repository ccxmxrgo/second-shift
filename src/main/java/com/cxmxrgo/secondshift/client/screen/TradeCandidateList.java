package com.cxmxrgo.secondshift.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Round-15 redesign: a real scrollable list of every rolled "career path" candidate, replacing
 * the earlier fixed-3-row layout. Reuses vanilla's own {@link ObjectSelectionList} base class (the
 * same one backing things like the resource-pack and world-selection screens) rather than any
 * hand-rolled scrolling/clipping logic — only the row content (icon + name + cost, all already
 * baked into each candidate's display {@link ItemStack} by {@code BindingAltarMenu}) and the
 * click routing are custom.
 *
 * <p>Deliberately overrides {@link #renderListBackground} and {@link #renderListSeparators} to
 * no-ops: vanilla's defaults draw a standalone dark menu-list background and header/footer bars,
 * which would visually clash with sitting inside the enchanting table's own tan panel — this list
 * is meant to read as part of that panel, not as its own separate box.
 */
@OnlyIn(Dist.CLIENT)
public class TradeCandidateList extends ObjectSelectionList<TradeCandidateList.Entry> {

    private static final int ROW_WIDTH = 100;
    private static final int NAME_COLOR_NORMAL = 4210752;
    private static final int NAME_COLOR_HOVERED = 16777088;

    public TradeCandidateList(Minecraft minecraft, int width, int height, int y0, int itemHeight,
            List<ItemStack> candidates, IntConsumer onSelect) {
        super(minecraft, width, height, y0, itemHeight);
        for (int i = 0; i < candidates.size(); i++) {
            ItemStack stack = candidates.get(i);
            if (!stack.isEmpty()) {
                this.addEntry(new Entry(stack, i, onSelect));
            }
        }
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    @Override
    protected void renderListBackground(GuiGraphics guiGraphics) {
        // Intentionally empty — the enchanting table's own background shows through.
    }

    @Override
    protected void renderListSeparators(GuiGraphics guiGraphics) {
        // Intentionally empty — this list is part of the altar's existing panel, not a standalone menu.
    }

    public static class Entry extends ObjectSelectionList.Entry<Entry> {
        private final ItemStack stack;
        private final int index;
        private final IntConsumer onSelect;

        Entry(ItemStack stack, int index, IntConsumer onSelect) {
            this.stack = stack;
            this.index = index;
            this.onSelect = onSelect;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int iconY = top + (height - 16) / 2;
            guiGraphics.renderItem(this.stack, left, iconY);
            guiGraphics.renderItemDecorations(minecraft.font, this.stack, left, iconY);
            guiGraphics.drawWordWrap(minecraft.font, this.stack.getHoverName(), left + 20, top + 2, width - 20,
                    hovering ? NAME_COLOR_HOVERED : NAME_COLOR_NORMAL);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            this.onSelect.accept(this.index);
            return true;
        }

        @Override
        public Component getNarration() {
            return this.stack.getHoverName();
        }
    }
}
