package com.cxmxrgo.secondshift.gametest;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.trade.TradePoolCache;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * PICK-02 / PICK-05 / PICK-08 GameTest suite — {@link TradePoolCache#rollTier1Candidates}
 * correctness, proving 05-RESEARCH.md Finding 1's null/side-effect handling.
 *
 * <p>Mirrors {@link EmployeeGameTests}'s idiom exactly: {@code helper.assertTrue}/{@code
 * assertFalse}, {@code helper.succeed()}, no raw JUnit. Shares the {@code secondshift:empty}
 * structure and a fixed relative altar position.
 */
@GameTestHolder(SecondShift.MODID)
@PrefixGameTestTemplate(false)
public final class TradePoolCacheGameTests {

    private static final BlockPos ALTAR_POS = new BlockPos(4, 2, 4);

    private TradePoolCacheGameTests() {}

    @GameTest(template = "empty")
    public static void librarian_roll_includes_exactly_one_enchanted_book_offer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);

        for (int i = 0; i < 10; i++) {
            List<MerchantOffer> offers =
                    TradePoolCache.rollTier1Candidates(level, absAltarPos, VillagerProfession.LIBRARIAN);
            helper.assertTrue(!offers.isEmpty(), "librarian tier-1 roll must be non-empty, attempt " + i);

            long bookOffers = offers.stream()
                    .filter(offer -> offer.getResult().is(Items.ENCHANTED_BOOK))
                    .count();
            helper.assertTrue(bookOffers == 1,
                    "librarian tier-1 roll must include exactly one enchanted-book offer (PICK-05), got "
                            + bookOffers + " on attempt " + i);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cartographer_roll_never_throws_and_never_leaks_null(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Real, generated chunk position (not (0,0,0)) so the treasure-map search has real terrain.
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);

        Int2ObjectMap<VillagerTrades.ItemListing[]> byTier = VillagerTrades.TRADES.get(VillagerProfession.CARTOGRAPHER);
        VillagerTrades.ItemListing[] tier1 = (byTier != null) ? byTier.get(1) : null;
        int maxPoolSize = (tier1 != null) ? tier1.length : 0;

        List<MerchantOffer> offers =
                TradePoolCache.rollTier1Candidates(level, absAltarPos, VillagerProfession.CARTOGRAPHER);

        helper.assertTrue(offers != null, "cartographer roll must never return null (PICK-08)");
        // Note: offers.contains(null) would itself throw NPE — List.of()/List.copyOf() immutable
        // lists reject null in contains() by design (JEP 269). Check element-by-element instead.
        boolean hasNullElement = offers.stream().anyMatch(java.util.Objects::isNull);
        helper.assertFalse(hasNullElement, "cartographer roll must never leak a null offer into the list");
        helper.assertTrue(offers.size() <= maxPoolSize,
                "cartographer roll must return no more offers than the vanilla tier-1 pool size ("
                        + maxPoolSize + "), got " + offers.size());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void farmer_roll_produces_concrete_non_empty_offers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);

        List<MerchantOffer> offers =
                TradePoolCache.rollTier1Candidates(level, absAltarPos, VillagerProfession.FARMER);

        helper.assertTrue(!offers.isEmpty(), "farmer tier-1 roll must be non-empty");
        for (MerchantOffer offer : offers) {
            helper.assertTrue(!offer.getResult().isEmpty(),
                    "every farmer offer must have a non-empty result, got " + offer.getResult());
            helper.assertTrue(!offer.getCostA().isEmpty(),
                    "every farmer offer must have a non-empty costA, got " + offer.getCostA());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void throwaway_villager_is_never_added_to_the_level(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);

        int before = countEntities(level);
        TradePoolCache.rollTier1Candidates(level, absAltarPos, VillagerProfession.FARMER);
        int after = countEntities(level);

        helper.assertTrue(before == after,
                "rollTier1Candidates must not add the throwaway villager to the level, entity count went from "
                        + before + " to " + after);
        helper.succeed();
    }

    private static int countEntities(ServerLevel level) {
        int count = 0;
        for (Entity ignored : level.getAllEntities()) {
            count++;
        }
        return count;
    }
}
