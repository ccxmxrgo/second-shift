package com.cxmxrgo.secondshift.network;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handler for {@link BindEmployeePayload} (Plan 04-03) — validates the sender's live
 * menu state, atomically consumes the socketed Soul Block, and calls
 * {@link EmployeeManager#bind(ServerLevel, net.minecraft.core.BlockPos)} exactly once per
 * successful bind.
 *
 * <p>Mirrors {@code SoulAltarBlock#useItemOn}'s validate-before-mutate discipline. T-4-01: the
 * payload carries zero fields (D-01) — the bind position is re-derived exclusively from
 * {@code sp.containerMenu}'s server-side {@link BindingAltarMenu#access()} state, never from any
 * client-supplied data. T-4-02: the Soul Block slot's {@code isEmpty()} check and its
 * {@code setHeldSoulBlock(EMPTY)} consume happen in the same synchronous
 * {@code access().execute(...)} lambda invocation as the {@code EmployeeManager.bind} call, with
 * no intervening logic — a second rapid Confirm click sees an already-empty block entity and
 * no-ops.
 */
public final class ServerPayloadHandler {

    private ServerPayloadHandler() {}

    public static void handleBindEmployee(BindEmployeePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sp)) {
            return;
        }
        if (!(sp.containerMenu instanceof BindingAltarMenu menu)) {
            return; // no Binding Altar menu open server-side — stale/malicious send, silently ignore
        }
        if (!menu.stillValid(sp)) {
            return; // re-derived, not trusted — reuses the existing altar/job-block/proximity check
        }

        menu.access().execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be) || be.isEmpty()) {
                return; // nothing to bind, or already consumed by a prior rapid click (T-4-02)
            }

            // Atomic consume BEFORE bind — a later-tick duplicate click sees be.isEmpty() == true.
            be.setHeldSoulBlock(ItemStack.EMPTY);
            be.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);

            if (level instanceof ServerLevel serverLevel) {
                EmployeeManager.bind(serverLevel, pos);
            }
        });

        sp.closeContainer();
    }
}
