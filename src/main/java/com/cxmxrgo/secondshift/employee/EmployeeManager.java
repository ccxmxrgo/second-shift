package com.cxmxrgo.secondshift.employee;

import com.cxmxrgo.secondshift.registry.ModAttachments;
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

/**
 * Spawns a bound employee villager (EMP-01, EMP-02, EMP-08, D-03, D-05).
 *
 * <p>{@link #bind(ServerLevel, BlockPos, VillagerProfession, MerchantOffers, String)} is the
 * phase's one genuinely new piece of business logic — the real, permanent infrastructure this
 * roadmap phase exists to deliver. Plan 05-05 gives it its real signature: the caller (now
 * {@code ServerPayloadHandler}) supplies the already-resolved profession, the already-validated
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

    private EmployeeManager() {}

    /**
     * Spawns a fresh, bound employee villager above {@code altarPos} and adds it to {@code level},
     * with the given {@code profession}, live trade {@code chosenOffers}, and display {@code name}
     * — all three already resolved/validated/sanitized by the caller (PICK-01 upstream for
     * profession, {@code ServerPayloadHandler}'s trust-boundary validation for offers and name).
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

        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        villager.setData(ModAttachments.EMPLOYEE.get(), new EmployeeData(1, name, professionId, 1, chosenOffers));

        level.addFreshEntity(villager);
        return villager;
    }
}
