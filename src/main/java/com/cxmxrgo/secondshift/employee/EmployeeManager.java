package com.cxmxrgo.secondshift.employee;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;

import java.util.Optional;
import java.util.UUID;

/**
 * Spawns a bound employee villager (EMP-01, EMP-02, EMP-08, D-03, D-05) and manages the
 * altar↔employee link's lifecycle (Phase 6, 06-CONTEXT.md D-01).
 *
 * <p>{@link #bind(ServerLevel, BlockPos, VillagerProfession, MerchantOffers, String)} is the
 * phase's one genuinely new piece of business logic — the real, permanent infrastructure this
 * roadmap phase exists to deliver. Plan 05-05 gives it its real signature: the caller (now
 * {@code BindingAltarMenu}) supplies the already-resolved profession, the already-validated
 * chosen offers, and the already-sanitized name — this method no longer picks a random profession
 * or rolls a hardcoded default trade pool (superseded by {@link
 * com.cxmxrgo.secondshift.trade.TradePoolCache}).
 *
 * <p>The spawn ordering below is <b>non-negotiable</b> (RESEARCH.md Pattern 2):
 * {@code setVillagerData} (profession) must run BEFORE {@code setOffers}, because
 * {@code setVillagerData} nulls the villager's offers on profession change (Pitfall 4).
 * {@code setData(ModAttachments.EMPLOYEE, ...)} runs last among data/offers/name calls, and
 * {@code addFreshEntity} is always the final call so vanilla's own entity-add client sync fires
 * (Pitfall B — no manual "entity exists" payload is built here).
 */
public final class EmployeeManager {

    /**
     * Phase 6 (06-CONTEXT.md D-03): employees are held at a positive age as a breeding-precondition
     * break — see {@link com.cxmxrgo.secondshift.event.EmployeeEvents} for the periodic re-assert.
     * This is exactly vanilla's own post-breeding cooldown value ({@code VillagerMakeLove.breed}
     * writes the same constant onto both real parents), chosen so a bound employee looks and
     * behaves identically to any adult villager mid-cooldown — no rendering change, no AI change,
     * no trade effect, and never mistaken for a baby ({@code isBaby()} is {@code age < 0}).
     */
    public static final int BREEDING_LOCK_AGE = 6000;

    private EmployeeManager() {}

    /**
     * Spawns a fresh, bound employee villager above {@code altarPos} and adds it to {@code level},
     * with the given {@code profession}, live trade {@code chosenOffers}, and display {@code name}
     * — all three already resolved/validated/sanitized by the caller. Records {@code altarPos} on
     * the spawned employee's {@link EmployeeData} (Phase 6 D-01) and applies the breeding-lock age
     * (Phase 6 D-03) before the entity ever joins the level.
     */
    public static Villager bind(ServerLevel level, BlockPos altarPos, VillagerProfession profession,
            MerchantOffers chosenOffers, String name) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            throw new IllegalStateException("EntityType.VILLAGER.create returned null");
        }

        BlockPos spawnPos = altarPos.above(2);
        villager.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);

        // Profession FIRST — setVillagerData nulls offers on profession change (Pitfall 4).
        villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(1));

        // Bug B fix (Phase 5 checkpoint): refreshBrain MUST run immediately after setVillagerData
        // and before setVillagerXp/setOffers (PITFALLS.md Pitfall 4 / 05-RESEARCH.md Finding 3) —
        // without it the villager's brain retains the activity/schedule set built for its previous
        // (profession-less) VillagerData, which gates vanilla trade-screen-opening behavior in
        // Villager#mobInteract via brain-driven state (this was also the root cause of Bug D).
        villager.refreshBrain(level);

        // Non-negotiable: keeps ResetProfession from firing since this employee never claims
        // its own job-site POI (EMP-02).
        villager.setVillagerXp(1);

        villager.setOffers(chosenOffers); // LAST among data/offers calls (Pattern 2).

        villager.setCustomName(Component.literal(name).withStyle(ChatFormatting.GREEN));
        villager.setCustomNameVisible(true);

        // Phase 6 D-03: breeding-precondition break. Applied at spawn, before addFreshEntity, so
        // the employee is never observably breedable even for a single tick.
        villager.setAge(BREEDING_LOCK_AGE);

        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        villager.setData(ModAttachments.EMPLOYEE.get(),
                new EmployeeData(1, name, professionId, 1, chosenOffers, Optional.of(altarPos)));

        level.addFreshEntity(villager);
        return villager;
    }

    /**
     * Phase 6 (06-CONTEXT.md D-01 "release semantics"): frees an altar's one-employee slot so it
     * can be re-bound. Called from every death/removal path (Harvester recovery, other-death
     * drop-recovery, altar-destruction firing) via {@link com.cxmxrgo.secondshift.event.EmployeeEvents}.
     *
     * <p>Guarded on identity — the altar is only released if its stored {@link
     * SoulAltarBlockEntity#getEmployeeId()} matches {@code employeeId}, or is {@code null} (a
     * pre-Phase-6 save with an {@code employeeBound} flag but no recorded UUID). This means a
     * stale {@code altarPos} on some other altar's employee can never free an altar that
     * legitimately belongs to a different, living employee. A no-op (not an error) if {@code
     * altarPos} is absent, the block there is no longer a Soul Altar, or the BE is gone —
     * an employee that wandered away from a destroyed/replaced altar has nothing left to release.
     */
    public static void releaseAltar(ServerLevel level, Optional<BlockPos> altarPos, UUID employeeId) {
        if (altarPos.isEmpty()) {
            return;
        }
        BlockPos pos = altarPos.get();
        if (!level.getBlockState(pos).is(ModBlocks.SOUL_ALTAR.get())) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be)) {
            return;
        }
        UUID stored = be.getEmployeeId();
        if (stored != null && !stored.equals(employeeId)) {
            return; // belongs to a different, living employee — never released by a stale link
        }
        be.setEmployeeBound(false);
        be.setEmployeeId(null);
        be.setChanged();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
    }
}
