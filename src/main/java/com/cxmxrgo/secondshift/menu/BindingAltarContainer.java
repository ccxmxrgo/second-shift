package com.cxmxrgo.secondshift.menu;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.Arrays;
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
 * <p><b>Round-10 checkpoint fix (real-playtest regression, 2026-09-08):</b> the first version of
 * this class computed every slot's display stack on the fly inside {@code getItem()} and made
 * {@code setItem()} a no-op on the theory that the container was "read-only". That broke the
 * client screen entirely: vanilla syncs a freshly-opened menu to the client via
 * {@code ClientboundContainerSetContentPacket}, which works by calling {@code setItem()} on the
 * CLIENT's own (separately-constructed, always-empty-candidates) container instance to apply the
 * server's authoritative stacks — exactly like any real chest's backing {@code SimpleContainer}.
 * A no-op {@code setItem()} silently discarded every synced candidate stack, so only the Confirm
 * emerald (built directly, unconditionally, inside the old {@code getItem()}) ever rendered. This
 * version instead behaves like a normal mutable item-array container — {@code setItem()} actually
 * stores the stack, {@code getItem()} just reads it back.
 *
 * <p><b>Round-11 intuitiveness pass (user feedback: "trades must be more intuitive"):</b> a bare
 * result item in a chest slot tells the player nothing about what the trade actually costs, or
 * whether they've selected it. Since a generic {@code Container} only ever gets to influence what
 * a real vanilla item tooltip shows, every candidate's displayed stack now carries {@link
 * DataComponents#LORE} listing its real cost item(s) (read straight off the same {@link
 * MerchantOffer} used to build the eventual bind — no separate "flavor text" to keep in sync) plus
 * a one-line selection-state hint; the Confirm slot's lore likewise tracks a live "Selected: N/2"
 * counter and switches between a ready/not-ready line. Both are rebuilt in place ({@link
 * #refreshCandidateDisplay}, {@link #refreshConfirmDisplay}) whenever the selection set changes, so
 * vanilla's own per-tick {@code AbstractContainerMenu#broadcastChanges()} re-syncs the new tooltip
 * text to the client automatically — still zero custom rendering code.
 *
 * <p>Extraction/insertion from this container is never legally reachable — {@link
 * BindingAltarMenu#clicked} intercepts every one of its slot indices before falling through to
 * vanilla's default pickup/place logic — so {@link #removeItem}/{@link #removeItemNoUpdate} are
 * trivial stubs, not because the container can't hold real state, but because nothing is ever
 * allowed to ask it to give an item up.
 */
public final class BindingAltarContainer implements Container {

    public static final int ROWS = 2;
    public static final int SIZE = ROWS * 9;
    public static final int CONFIRM_SLOT = 9;

    private final List<MerchantOffer> candidates;
    private final Set<Integer> selected;
    private final ItemStack[] items = new ItemStack[SIZE];

    public BindingAltarContainer(List<MerchantOffer> candidates, Set<Integer> selected) {
        this.candidates = candidates;
        this.selected = selected;
        Arrays.fill(items, ItemStack.EMPTY);

        for (int i = 0; i < candidates.size() && i < CONFIRM_SLOT; i++) {
            items[i] = buildCandidateDisplay(i);
        }
        items[CONFIRM_SLOT] = buildConfirmDisplay();
    }

    private boolean isAutoLocked() {
        return candidates.size() <= 2;
    }

    private ItemStack buildCandidateDisplay(int index) {
        MerchantOffer offer = candidates.get(index);
        boolean isSelected = selected.contains(index);

        ItemStack display = offer.getResult().copy();
        display.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, isSelected);

        List<Component> lore = new ArrayList<>();
        lore.add(costLine(offer.getCostA()));
        if (!offer.getCostB().isEmpty()) {
            lore.add(costLine(offer.getCostB()));
        }
        lore.add(Component.empty());
        if (isAutoLocked()) {
            lore.add(Component.translatable("gui.secondshift.binding_altar.trade_included")
                    .withStyle(ChatFormatting.GRAY));
        } else if (isSelected) {
            lore.add(Component.translatable("gui.secondshift.binding_altar.trade_selected")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            lore.add(Component.translatable("gui.secondshift.binding_altar.trade_click_to_select")
                    .withStyle(ChatFormatting.GRAY));
        }
        display.set(DataComponents.LORE, new ItemLore(lore));
        return display;
    }

    private static Component costLine(ItemStack cost) {
        return Component.translatable("gui.secondshift.binding_altar.cost_line",
                cost.getCount(), cost.getHoverName()).withStyle(ChatFormatting.YELLOW);
    }

    private ItemStack buildConfirmDisplay() {
        ItemStack confirm = new ItemStack(Items.EMERALD);
        confirm.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.secondshift.binding_altar.confirm")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));

        boolean ready = isAutoLocked() || selected.size() == 2;
        List<Component> lore = new ArrayList<>();
        lore.add(isAutoLocked()
                ? Component.translatable("gui.secondshift.binding_altar.confirm_auto").withStyle(ChatFormatting.GRAY)
                : Component.translatable("gui.secondshift.binding_altar.confirm_selected_count", selected.size())
                        .withStyle(ChatFormatting.GRAY));
        lore.add(ready
                ? Component.translatable("gui.secondshift.binding_altar.confirm_ready").withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.secondshift.binding_altar.confirm_not_ready").withStyle(ChatFormatting.RED));
        confirm.set(DataComponents.LORE, new ItemLore(lore));
        return confirm;
    }

    /**
     * Re-renders slot {@code index}'s displayed stack from the current selection set. Called from
     * {@link BindingAltarMenu#clicked} immediately after mutating {@link #getSelected}'s backing
     * set — the mutation itself is invisible to the client until some slot's stored {@link
     * ItemStack} actually changes, since sync is diff-based against the container's own state.
     */
    public void refreshCandidateDisplay(int index) {
        if (index >= 0 && index < candidates.size()) {
            items[index] = buildCandidateDisplay(index);
        }
    }

    /** Re-renders the Confirm slot's "Selected: N/2" / ready-not-ready lore. See {@link #refreshCandidateDisplay}. */
    public void refreshConfirmDisplay() {
        items[CONFIRM_SLOT] = buildConfirmDisplay();
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
        return slot >= 0 && slot < SIZE ? items[slot] : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY; // never legally reachable — BindingAltarMenu#clicked intercepts first
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY; // never legally reachable — BindingAltarMenu#clicked intercepts first
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // MUST actually store — this is how the client's own container instance receives the
        // server's synced stacks (see class javadoc). Server-side, nothing but this class's own
        // constructor/refresh methods ever calls this.
        if (slot >= 0 && slot < SIZE) {
            items[slot] = stack;
        }
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
        Arrays.fill(items, ItemStack.EMPTY);
    }
}
