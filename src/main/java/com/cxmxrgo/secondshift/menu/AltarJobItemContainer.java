package com.cxmxrgo.secondshift.menu;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * One-slot {@link Container} wrapper over {@link SoulAltarBlockEntity#getHeldJobItem()} /
 * {@link SoulAltarBlockEntity#setHeldJobItem(ItemStack)} — the display-only counterpart to
 * {@link AltarSoulContainer}, added so the socketed profession item can be shown as a second
 * read-only slot icon in the Binding Altar screen (per the round-6 UI redesign), mirroring
 * {@link AltarSoulContainer}'s exact "no caching, re-resolve every call" pattern.
 */
public class AltarJobItemContainer implements Container {

    private final Level level;
    private final BlockPos pos;

    public AltarJobItemContainer(Level level, BlockPos pos) {
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
        return be == null || be.isJobItemEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        SoulAltarBlockEntity be = resolve();
        return be == null ? ItemStack.EMPTY : be.getHeldJobItem();
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
            be.setHeldJobItem(stack);
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
            be.setHeldJobItem(ItemStack.EMPTY);
        }
    }
}
