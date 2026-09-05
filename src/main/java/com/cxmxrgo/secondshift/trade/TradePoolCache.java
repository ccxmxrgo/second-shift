package com.cxmxrgo.secondshift.trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Materializes a profession's real, vanilla tier-1 {@code ItemListing[]} pool into concrete
 * {@link MerchantOffer}s (PICK-02/05/08), following 05-RESEARCH.md Finding 1's exact side-effect
 * and null-handling rules.
 *
 * <p>Pure, stateless utility — mirrors {@link ProfessionResolver}'s static-class shape. Rolls
 * against a throwaway {@link Villager} that is <b>never</b> added to the level (Finding 1: a
 * villager's {@code level()} is non-null at construction, independent of
 * {@code addFreshEntity}, so listings like {@code TreasureMapForEmeralds} that gate on
 * {@code ServerLevel} still function without the throwaway ever joining the world). The
 * throwaway is moved to the altar position before rolling so any position-dependent listing
 * (the Cartographer's treasure-map search) has a sensible origin.
 *
 * <p>Deliberately has no caching/memoization: "roll at most once per bind session" is a
 * session-lifecycle concern owned by the caller (the BE's {@code candidatesRolled()} guard from
 * Plan 05-01), not this pure function — calling this twice for the same session would double the
 * one-time side effects Finding 1 documents (e.g. orphaned map {@code SavedData} allocation).
 */
public final class TradePoolCache {

    private TradePoolCache() {}

    /**
     * Rolls {@code profession}'s real, vanilla tier-1 listing pool into concrete offers using a
     * throwaway villager positioned at {@code altarPos}. Never throws; skips (does not retry) any
     * listing whose {@code getOffer} returns {@code null} (Finding 1 — retrying does not fix a
     * null {@code TreasureMapForEmeralds} result, since the failure is structural to the search
     * position, not transient randomness). Returns {@code List.of()} if the profession has no
     * tier-1 pool or the throwaway villager could not be constructed (PICK-08 empty-pool case).
     */
    public static List<MerchantOffer> rollTier1Candidates(
            ServerLevel level, BlockPos altarPos, VillagerProfession profession) {
        Villager throwaway = EntityType.VILLAGER.create(level);
        if (throwaway == null) {
            return List.of();
        }

        // Move BEFORE rolling (Finding 1) — makes TreasureMapForEmeralds's search origin sensible.
        throwaway.moveTo(altarPos.getX() + 0.5D, altarPos.getY(), altarPos.getZ() + 0.5D, 0.0F, 0.0F);
        throwaway.setVillagerData(throwaway.getVillagerData().setProfession(profession).setLevel(1));
        // Never level.addFreshEntity(throwaway) — it must not join the level (Finding 1 Test 4).

        Int2ObjectMap<VillagerTrades.ItemListing[]> byTier = VillagerTrades.TRADES.get(profession);
        VillagerTrades.ItemListing[] tier1 = (byTier != null) ? byTier.get(1) : null;
        if (tier1 == null) {
            return List.of();
        }

        RandomSource random = level.getRandom();
        List<MerchantOffer> offers = new ArrayList<>(tier1.length);
        for (VillagerTrades.ItemListing listing : tier1) {
            MerchantOffer offer = listing.getOffer(throwaway, random);
            if (offer != null) {
                offers.add(offer);
            }
        }
        return List.copyOf(offers);
    }
}
