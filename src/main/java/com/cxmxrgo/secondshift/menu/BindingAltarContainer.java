package com.cxmxrgo.secondshift.menu;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;
import java.util.Set;

/**
 * Backing container for the round-10 Binding Altar redesign: a plain vanilla {@link
 * net.minecraft.world.inventory.ChestMenu}-based UI (no custom Screen/texture at all — see
 * {@link BindingAltarMenu}'s doc comment for the full rationale). Row 1 (indices 0..N-1, N &lt;=
 * 9) shows the materialized tier-1 trade candidates as their real result item, with an
 * enchantment-glint overlay when selected. Slot {@link #CONFIRM_SLOT} (index 9, start of row 2)
 * is a fixed "Confirm Hire" indicator item — clicking it (handled in
 * {@link BindingAltarMenu#clicked}) triggers the bind. All other slots are empty.
 *
 * <p>Read-only: {@link #removeItem}/{@link #removeItemNoUpdate}/{@link #setItem} are all no-ops —
 * nothing in this container can ever leave via drag/shift-click/hopper. Selection and confirmation
 * both happen exclusively through {@link BindingAltarMenu#clicked}, which runs via vanilla's own
 * server-authoritative slot-click protocol ({@code ServerboundContainerClickPacket}) — there is no
 * client-supplied index list to bounds-check anymore, eliminating the entire class of trust-boundary
 * bug the prior custom-payload design required (05-RESEARCH.md Finding 3 / Bug D).
 */
public final class BindingAltarContainer implements Container {

    public static final int ROWS = 2;
    public static final int SIZE = ROWS * 9;
    public static final int CONFIRM_SLOT = 9;

    private final List<MerchantOffer> candidates;
    private final Set<Integer> selected;

    public BindingAltarContainer(List<MerchantOffer> candidates, Set<Integer> selected) {
        this.candidates = candidates;
        this.selected = selected;
    }

    /** The materialized candidate offers this container displays (never mutated here). */
    public List<MerchantOffer> getCandidates() {
        return candidates;
    }

    /** The live, mutable selected-index set — {@link BindingAltarMenu#clicked} writes to this directly. */
    public Set<Integer> getSelected() {
        return selected;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return candidates.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot >= 0 && slot < candidates.size()) {
            ItemStack display = candidates.get(slot).getResult().copy();
            display.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, selected.contains(slot));
            return display;
        }
        if (slot == CONFIRM_SLOT) {
            ItemStack confirm = new ItemStack(Items.EMERALD);
            confirm.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.secondshift.binding_altar.confirm"));
            return confirm;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // Read-only — selection state lives on the menu instance, never via container writes.
    }

    @Override
    public void setChanged() {
        // No persistent backing state to flush — candidates/selection live on the menu instance.
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // the menu's own stillValid is the real gate
    }

    @Override
    public void clearContent() {
        // No-op — read-only.
    }
}
