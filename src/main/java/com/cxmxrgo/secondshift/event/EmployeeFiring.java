package com.cxmxrgo.secondshift.event;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The delayed half of ALTAR-06's two-strike altar-destruction sequence (06-CONTEXT.md D-07):
 * {@link com.cxmxrgo.secondshift.content.block.SoulAltarBlock#playerWillDestroy} fires the first,
 * altar-side lightning bolt immediately; this class fires the second, employee-side bolt {@link
 * #SMITE_DELAY_TICKS} later, so the two strikes read as cause and effect rather than one event.
 *
 * <p>A tiny in-memory tick-counted queue, keyed on the employee's UUID and dimension — not a
 * BlockEntity/attachment field, since a pending smite has no natural persistent owner (the altar
 * that scheduled it may already be gone by the time this drains, and the queue surviving a server
 * restart is not a real requirement for a half-second delay). Degrades safely: if the employee is
 * gone, unloaded, or already dead when the timer fires, the entry is simply dropped.
 *
 * <p><b>The firing kill must not drop a Soul Block (EMP-06).</b> Rather than threading a "this
 * death is a firing" flag through the death handler, {@link #fire} removes the {@code
 * EmployeeData} attachment BEFORE killing. The villager dies as an ordinary villager, so {@link
 * EmployeeEvents}'s other-death handler correctly does nothing, and the altar link is already
 * severed by {@code playerWillDestroy} itself (which clears {@code employeeBound}/{@code
 * employeeId} synchronously, before this class ever runs).
 */
@EventBusSubscriber(modid = SecondShift.MODID)
public final class EmployeeFiring {

    /** ~0.5s — real, not cosmetic sugar; see class doc. */
    public static final int SMITE_DELAY_TICKS = 10;

    private record PendingSmite(UUID employeeId, ResourceKey<Level> dimension, int ticksRemaining) {
        PendingSmite tick() {
            return new PendingSmite(employeeId, dimension, ticksRemaining - 1);
        }
    }

    private static final List<PendingSmite> PENDING = new ArrayList<>();

    private EmployeeFiring() {}

    /** Schedules {@code employeeId} (in {@code level}'s dimension) to be smitten in {@link
     * #SMITE_DELAY_TICKS} ticks. Safe to call even if the entity later goes missing. */
    public static void schedule(ServerLevel level, UUID employeeId) {
        PENDING.add(new PendingSmite(employeeId, level.dimension(), SMITE_DELAY_TICKS));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();

        // Two passes: first replace every entry with its ticked (decremented) version and split
        // off the ones that reached zero, THEN fire those — so firing (which never re-schedules
        // today, but might in a future change) can never observe or corrupt a list still being
        // iterated for the decrement itself.
        List<PendingSmite> ready = new ArrayList<>();
        List<PendingSmite> stillPending = new ArrayList<>(PENDING.size());
        for (PendingSmite smite : PENDING) {
            PendingSmite ticked = smite.tick();
            if (ticked.ticksRemaining() <= 0) {
                ready.add(ticked);
            } else {
                stillPending.add(ticked);
            }
        }
        PENDING.clear();
        PENDING.addAll(stillPending);

        for (PendingSmite smite : ready) {
            fire(server, smite);
        }
    }

    private static void fire(MinecraftServer server, PendingSmite smite) {
        ServerLevel level = server.getLevel(smite.dimension());
        if (level == null) {
            return; // dimension unloaded — degrade safely
        }
        if (!(level.getEntity(smite.employeeId()) instanceof Villager employee) || !employee.isAlive()) {
            return; // gone, unloaded, or already dead — degrade safely
        }

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(employee.position());
            bolt.setVisualOnly(true); // flash + thunder only — matches the altar-side bolt, no fire/damage from the bolt itself
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.SOUL, employee.getX(), employee.getY() + employee.getBbHeight() * 0.5D,
                employee.getZ(), 20, 0.3D, 0.4D, 0.3D, 0.02D);

        // EMP-06 must not fire for this death — remove the attachment BEFORE killing (see class doc).
        employee.removeData(ModAttachments.EMPLOYEE.get());
        employee.kill();
    }
}
