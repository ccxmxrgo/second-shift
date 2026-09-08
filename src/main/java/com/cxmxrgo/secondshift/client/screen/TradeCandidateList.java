package com.cxmxrgo.secondshift.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
 *
 * <p><b>Phase 7 addition (07-CONTEXT.md D-04):</b> a second construction mode — pass {@code
 * maxSelections > 1} — turns a row click into a purely client-side toggle (up to {@code
 * maxSelections} rows highlighted at once) instead of firing {@code onSelect} immediately. This
 * backs {@code PromotionRitualScreen}'s "pick 2" picker: nothing goes over the network until a
 * separate Confirm button reads {@link #getSelectedIndices()}. {@code maxSelections <= 1} keeps
 * the original immediate-fire behavior {@code BindingAltarScreen} still uses unchanged.
 */
@OnlyIn(Dist.CLIENT)
public class TradeCandidateList extends ObjectSelectionList<TradeCandidateList.Entry> {

    private static final int ROW_WIDTH = 100;
    private static final int NAME_COLOR_NORMAL = 4210752;
    private static final int NAME_COLOR_HOVERED = 16777088;
    private static final int NAME_COLOR_SELECTED = 0x55FF55;

    private final int maxSelections;
    private final Set<Integer> selected = new LinkedHashSet<>();
    private Runnable onSelectionChanged = () -> {};

    /** Original single-immediate-select constructor — {@code onSelect} fires the moment a row is
     * clicked, exactly as before this class supported toggle mode. */
    public TradeCandidateList(Minecraft minecraft, int width, int height, int y0, int itemHeight,
            List<ItemStack> candidates, IntConsumer onSelect) {
        this(minecraft, width, height, y0, itemHeight, candidates, 1, onSelect);
    }

    /**
     * Toggle-select constructor. When {@code maxSelections > 1}, a row click never calls
     * {@code onSelect} directly — it toggles that row's membership in {@link #selected} (capped at
     * {@code maxSelections}) and calls {@link #setOnSelectionChanged} so the owning screen can
     * enable/disable its Confirm button. {@code onSelect} is unused in this mode; pass a no-op.
     */
    public TradeCandidateList(Minecraft minecraft, int width, int height, int y0, int itemHeight,
            List<ItemStack> candidates, int maxSelections, IntConsumer onSelect) {
        super(minecraft, width, height, y0, itemHeight);
        this.maxSelections = Math.max(1, maxSelections);
        for (int i = 0; i < candidates.size(); i++) {
            ItemStack stack = candidates.get(i);
            if (!stack.isEmpty()) {
                this.addEntry(new Entry(stack, i, this, onSelect));
            }
        }
    }

    /** Called by the owning screen to react whenever the toggled selection set changes (e.g. to
     * enable/disable a Confirm button). No-op in single-select mode. */
    public void setOnSelectionChanged(Runnable callback) {
        this.onSelectionChanged = callback;
    }

    public boolean isToggleMode() {
        return this.maxSelections > 1;
    }

    public Set<Integer> getSelectedIndices() {
        return Set.copyOf(this.selected);
    }

    private void toggle(int index) {
        if (this.selected.remove(index)) {
            this.onSelectionChanged.run();
            return;
        }
        if (this.selected.size() < this.maxSelections) {
            this.selected.add(index);
            this.onSelectionChanged.run();
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
        private final TradeCandidateList owner;
        private final IntConsumer onSelect;

        Entry(ItemStack stack, int index, TradeCandidateList owner, IntConsumer onSelect) {
            this.stack = stack;
            this.index = index;
            this.owner = owner;
            this.onSelect = onSelect;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                int mouseX, int mouseY, boolean hovering, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int iconY = top + (height - 16) / 2;
            guiGraphics.renderItem(this.stack, left, iconY);
            guiGraphics.renderItemDecorations(minecraft.font, this.stack, left, iconY);

            boolean isSelected = this.owner.isToggleMode() && this.owner.selected.contains(this.index);
            Component name = isSelected
                    ? Component.literal("✔ ").append(this.stack.getHoverName())
                    : this.stack.getHoverName();
            int color = isSelected ? NAME_COLOR_SELECTED : (hovering ? NAME_COLOR_HOVERED : NAME_COLOR_NORMAL);
            guiGraphics.drawWordWrap(minecraft.font, name, left + 20, top + 2, width - 20, color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.owner.isToggleMode()) {
                this.owner.toggle(this.index);
            } else {
                this.onSelect.accept(this.index);
            }
            return true;
        }

        @Override
        public Component getNarration() {
            return this.stack.getHoverName();
        }
    }
}
