package com.cxmxrgo.secondshift.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Display-only slot for the altar's socketed Soul Block (D-06, GUI-01). The player can neither
 * take from nor place into this slot this phase — vanilla's own click-handling already respects
 * {@link #mayPickup(Player)} / {@link #mayPlace(ItemStack)}, so no additional guarding is needed.
 */
public class SoulSlot extends Slot {

    public SoulSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }
}
