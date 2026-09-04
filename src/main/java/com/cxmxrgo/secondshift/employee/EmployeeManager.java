package com.cxmxrgo.secondshift.employee;

import com.cxmxrgo.secondshift.registry.ModAttachments;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.List;

/**
 * Spawns a bound employee villager (EMP-01, EMP-02, EMP-08, D-03, D-05).
 *
 * <p>{@link #bind(ServerLevel, BlockPos)} is the phase's one genuinely new piece of business
 * logic — the real, permanent infrastructure this roadmap phase exists to deliver. It is called
 * by Plan 04-03's (throwaway) network bind trigger, but the method itself is not throwaway: every
 * later phase's re-bind/respawn logic must match this exact ordering.
 *
 * <p>The spawn ordering below is <b>non-negotiable</b> (RESEARCH.md Pattern 2):
 * {@code setVillagerData} (profession) must run BEFORE {@code setOffers}, because
 * {@code setVillagerData} nulls the villager's offers on profession change (Pitfall 4).
 * {@code setData(ModAttachments.EMPLOYEE, ...)} runs last among data/offers/name calls, and
 * {@code addFreshEntity} is always the final call so vanilla's own entity-add client sync fires
 * (Pitfall B — no manual "entity exists" payload is built here).
 */
public final class EmployeeManager {

    private static final List<VillagerProfession> BINDABLE_PROFESSIONS =
            List.of(VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.CLERIC);

    private EmployeeManager() {}

    /** Spawns a fresh, bound employee villager above {@code altarPos} and adds it to {@code level}. */
    public static Villager bind(ServerLevel level, BlockPos altarPos) {
        RandomSource random = level.getRandom();
        VillagerProfession profession = BINDABLE_PROFESSIONS.get(random.nextInt(BINDABLE_PROFESSIONS.size()));

        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            throw new IllegalStateException("EntityType.VILLAGER.create returned null");
        }

        BlockPos spawnPos = altarPos.above();
        villager.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);

        // Profession FIRST — setVillagerData nulls offers on profession change (Pitfall 4).
        villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(1));

        // Non-negotiable: keeps ResetProfession from firing since this employee never claims
        // its own job-site POI (EMP-02).
        villager.setVillagerXp(1);

        MerchantOffers offers = rollDefaultTier1Offers(villager, profession);
        villager.setOffers(offers); // LAST among data/offers calls (Pattern 2).

        String name = EmployeeNames.pickRandom(random);
        villager.setCustomName(Component.literal(name).withStyle(ChatFormatting.GREEN));
        villager.setCustomNameVisible(true);

        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        villager.setData(ModAttachments.EMPLOYEE.get(), new EmployeeData(1, name, professionId, 1, offers));

        level.addFreshEntity(villager);
        return villager;
    }

    /** Materializes up to 2 tier-1 offers for {@code profession}, following vanilla's own listing shape. */
    private static MerchantOffers rollDefaultTier1Offers(Villager forEntity, VillagerProfession profession) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> byTier = VillagerTrades.TRADES.get(profession);
        VillagerTrades.ItemListing[] tier1 = (byTier != null) ? byTier.get(1) : null;
        MerchantOffers offers = new MerchantOffers();
        if (tier1 != null) {
            RandomSource random = forEntity.getRandom();
            for (VillagerTrades.ItemListing listing : tier1) {
                if (offers.size() >= 2) break; // vanilla: max 2 per tier
                MerchantOffer offer = listing.getOffer(forEntity, random);
                if (offer != null) offers.add(offer);
            }
        }
        return offers; // may legitimately be empty — do not crash
    }
}
