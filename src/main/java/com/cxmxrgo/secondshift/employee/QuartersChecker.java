package com.cxmxrgo.secondshift.employee;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * HAPP-01 (09-CONTEXT.md D-01): "an enclosed space of at least 3×3 containing a door" — a bounded
 * flood fill from a room's interior, since vanilla has no generic "is this an enclosed room"
 * primitive (only per-villager bed/POI linking, which doesn't apply here).
 *
 * <p><b>Deliberately simple, deliberately bounded, deliberately documented as an approximation:</b>
 * a real structure-detection system (arbitrary room shapes, multiple doors, non-cardinal cutouts)
 * is out of scope for a personal mod's Phase 9. This flood fill:
 * <ul>
 *   <li>Starts at {@code origin} (the employee's own tether-home spawn point) and expands through
 *   every 6-connected "passable" neighbor (empty collision shape — covers air, open trapdoors,
 *   carpets, etc. without hardcoding a block list).</li>
 *   <li>Is capped at {@link #MAX_VISITED} visited cells. If the fill exhausts its frontier before
 *   hitting the cap, the room is "enclosed" (bounded by solid blocks on every side reached); if it
 *   hits the cap first, it's treated as open/leaking (a real room this small should never need
 *   anywhere near that many interior cells).</li>
 *   <li>Counts the DISTINCT (x, z) columns among visited cells as the room's footprint — "at least
 *   3×3" is interpreted as at least 9 distinct columns, regardless of room height or shape.</li>
 *   <li>Requires at least one {@link DoorBlock} adjacent to some visited interior cell — the door
 *   itself is treated as part of the wall boundary (a closed door has a real collision shape, so
 *   the fill correctly does NOT pass through it), and its adjacency is checked separately.</li>
 * </ul>
 */
public final class QuartersChecker {

    /** Visited-cell cap — bounds both the search cost and doubles as the "did this leak into the
     * open world" signal (see class doc). Generous enough for any reasonably-sized real room. */
    private static final int MAX_VISITED = 400;

    /** Minimum distinct (x, z) footprint columns for "at least 3×3". */
    private static final int MIN_FOOTPRINT_COLUMNS = 9;

    private static final int[][] NEIGHBOR_OFFSETS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private QuartersChecker() {}

    /** True if {@code origin} sits inside a valid enclosed 3×3-or-larger room containing a door. */
    public static boolean hasValidQuarters(ServerLevel level, BlockPos origin) {
        if (!isPassable(level, origin)) {
            return false; // the employee's own spot must be inside the room, not embedded in a wall
        }

        Set<Long> visited = new HashSet<>();
        Set<Long> footprintColumns = new HashSet<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(origin);
        visited.add(origin.asLong());
        boolean doorFound = false;

        while (!frontier.isEmpty()) {
            if (visited.size() > MAX_VISITED) {
                return false; // leaked into the open world before enclosing
            }
            BlockPos current = frontier.poll();
            footprintColumns.add(columnKey(current.getX(), current.getZ()));

            for (int[] offset : NEIGHBOR_OFFSETS) {
                BlockPos neighbor = current.offset(offset[0], offset[1], offset[2]);
                if (!visited.add(neighbor.asLong())) {
                    continue;
                }
                BlockState state = level.getBlockState(neighbor);
                if (state.getBlock() instanceof DoorBlock) {
                    doorFound = true;
                }
                if (isPassable(level, neighbor, state)) {
                    frontier.add(neighbor);
                } else {
                    // A solid neighbor is a wall cell, not part of the room's interior — don't
                    // recurse into it (matches the "wall boundary" treatment for doors too), but
                    // it still counted toward MAX_VISITED above as a boundary check.
                    visited.remove(neighbor.asLong());
                }
            }
        }

        return doorFound && footprintColumns.size() >= MIN_FOOTPRINT_COLUMNS;
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos) {
        return isPassable(level, pos, level.getBlockState(pos));
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
        return shape.isEmpty();
    }
}
