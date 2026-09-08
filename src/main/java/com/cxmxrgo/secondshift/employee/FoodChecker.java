package com.cxmxrgo.secondshift.employee;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * HAPP-02 (09-CONTEXT.md D-02): "a nearby chest stocked with food it can draw from" — searches a
 * bounded box around the altar for any block entity implementing {@link Container} (covers
 * chests, barrels, trapped chests, shulker boxes — any real storage block, not just the literal
 * Chest block) holding at least one item vanilla's own {@link
 * net.minecraft.world.entity.npc.Villager#FOOD_POINTS} recognizes, and "draws" (consumes) one unit
 * of it per successful check — matching the phrase "it can draw from" literally, rather than just
 * checking presence without consequence.
 */
public final class FoodChecker {

    /** Search radius (blocks) around the altar — generous enough for "a nearby chest" without
     * scanning the whole loaded world. */
    private static final double SEARCH_RADIUS = 6.0D;

    private FoodChecker() {}

    /**
     * Scans for a nearby stocked container and consumes exactly one unit of the first matching
     * food item found. Returns {@code true} if food was found and drawn, {@code false} if no
     * nearby container currently holds any recognized food item. Use this only from the periodic
     * happiness recompute — a mere status READ must use {@link #hasFoodAvailable} instead, which
     * never mutates anything.
     */
    public static boolean tryConsumeFood(ServerLevel level, BlockPos altarPos) {
        return findFirstFoodStack(level, altarPos, true) != null;
    }

    /** Read-only presence check — never shrinks anything. Use for a status readout (HAPP-07) that
     * must not have side effects just from the player checking. */
    public static boolean hasFoodAvailable(ServerLevel level, BlockPos altarPos) {
        return findFirstFoodStack(level, altarPos, false) != null;
    }

    private static ItemStack findFirstFoodStack(ServerLevel level, BlockPos altarPos, boolean consume) {
        AABB box = new AABB(altarPos).inflate(SEARCH_RADIUS);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container container)) {
                continue;
            }
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && net.minecraft.world.entity.npc.Villager.FOOD_POINTS.containsKey(stack.getItem())) {
                    if (consume) {
                        stack.shrink(1);
                        container.setChanged();
                    }
                    return stack;
                }
            }
        }
        return null;
    }
}
