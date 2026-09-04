package com.cxmxrgo.secondshift.menu;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * One-slot {@link Container} wrapper over {@link SoulAltarBlockEntity#getHeldSoulBlock()} /
 * {@link SoulAltarBlockEntity#setHeldSoulBlock(ItemStack)} (D-06, GUI-01, RESEARCH Open Question
 * 1 option b).
 *
 * <p>Deliberately NOT a {@code SimpleContainer} — this class holds no item state of its own and
 * re-resolves the backing {@link SoulAltarBlockEntity} from {@code level}/{@code pos} on every
 * call, so it stays correct across chunk/BE reloads on both sides and the BE remains the single
 * source of truth (matches the BE's existing "one field" persistence model from Phase 2).
 *
 * <p>The one slot is read-only this phase (D-06) — {@link #removeItem(int, int)} and
 * {@link #removeItemNoUpdate(int)} are unreachable via UI ({@link SoulSlot#mayPickup} /
 * {@link SoulSlot#mayPlace} both return {@code false}) but must still compile correctly.
 */
public class AltarSoulContainer implements Container {

    private final Level level;
    private final BlockPos pos;

    public AltarSoulContainer(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos;
    }

    private SoulAltarBlockEntity resolve() {
        return level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be ? be : null;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        SoulAltarBlockEntity be = resolve();
        return be == null || be.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        SoulAltarBlockEntity be = resolve();
        return be == null ? ItemStack.EMPTY : be.getHeldSoulBlock();
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
        SoulAltarBlockEntity be = resolve();
        if (be != null) {
            be.setHeldSoulBlock(stack);
        }
    }

    @Override
    public void setChanged() {
        SoulAltarBlockEntity be = resolve();
        if (be != null) {
            be.setChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // the menu's own stillValid is the real gate (D-16)
    }

    @Override
    public void clearContent() {
        SoulAltarBlockEntity be = resolve();
        if (be != null) {
            be.setHeldSoulBlock(ItemStack.EMPTY);
        }
    }
}
