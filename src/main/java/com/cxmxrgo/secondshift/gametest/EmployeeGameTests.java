package com.cxmxrgo.secondshift.gametest;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.employee.EmployeeData;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import com.cxmxrgo.secondshift.trade.TradePoolCache;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * EMP-01 / EMP-02 / EMP-08 / EMP-09 GameTest suite — {@link EmployeeManager#bind} spawn
 * correctness.
 *
 * <p>Mirrors {@code HarvesterGameTests} / {@link BindingAltarGameTests} idiom exactly:
 * {@code helper.assertTrue}/{@code assertFalse}, {@code helper.succeed()}, no raw JUnit.
 * All tests share the {@code secondshift:empty} structure and a fixed relative altar position.
 *
 * <p>Plan 05-05: every call site below uses the real 5-arg
 * {@code bind(level, pos, profession, chosenOffers, name)} signature — there is no more internal
 * random profession pick or hardcoded default offer roll to exercise.
 */
@GameTestHolder(SecondShift.MODID)
@PrefixGameTestTemplate(false)
public final class EmployeeGameTests {

    private static final BlockPos ALTAR_POS = new BlockPos(4, 2, 4);
    private static final BlockPos WILD_VILLAGER_POS = new BlockPos(1, 2, 1);

    private EmployeeGameTests() {}

    /** Rolls a real profession's tier-1 pool and narrows it to at most {@code count} offers. */
    private static MerchantOffers realOffers(GameTestHelper helper, BlockPos altarPos,
            VillagerProfession profession, int count) {
        List<MerchantOffer> candidates =
                TradePoolCache.rollTier1Candidates((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                        altarPos, profession);
        MerchantOffers offers = new MerchantOffers();
        for (int i = 0; i < Math.min(count, candidates.size()); i++) {
            offers.add(candidates.get(i));
        }
        return offers;
    }

    private static boolean offersEqual(MerchantOffer a, MerchantOffer b) {
        return net.minecraft.world.item.ItemStack.matches(a.getResult(), b.getResult())
                && net.minecraft.world.item.ItemStack.matches(a.getCostA(), b.getCostA())
                && net.minecraft.world.item.ItemStack.matches(a.getCostB(), b.getCostB())
                && a.getMaxUses() == b.getMaxUses()
                && a.getXp() == b.getXp();
    }

    @GameTest(template = "empty")
    public static void bind_spawns_villager_with_employee_data(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        MerchantOffers chosenOffers = realOffers(helper, absAltarPos, VillagerProfession.LIBRARIAN, 2);
        Villager villager = EmployeeManager.bind((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                absAltarPos, VillagerProfession.LIBRARIAN, chosenOffers, "Custom Name");

        helper.assertTrue(villager.hasData(ModAttachments.EMPLOYEE.get()),
                "bound villager must carry the EmployeeData attachment (EMP-01)");
        EmployeeData data = villager.getData(ModAttachments.EMPLOYEE.get());
        helper.assertTrue(data.tier() == 1, "employee tier must be 1, got " + data.tier());
        helper.assertTrue(!data.name().isBlank(), "employee name must not be blank");
        helper.assertTrue(data.profession() != null, "employee profession must not be null");

        // Test 5 (Pitfall A regression) — position must be altarPos.above(2), clear of the
        // job-site block at altarPos.above() (CR-01 fix), not world origin.
        BlockPos expected = absAltarPos.above(2);
        helper.assertTrue(
                Math.abs(villager.getX() - (expected.getX() + 0.5D)) < 0.01D
                        && Math.abs(villager.getY() - expected.getY()) < 0.01D
                        && Math.abs(villager.getZ() - (expected.getZ() + 0.5D)) < 0.01D,
                "bound villager must be positioned at the center of altarPos.above(2), got "
                        + villager.position() + " expected around " + expected);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bind_villager_does_not_overlap_job_site_block(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        BlockPos jobSitePos = absAltarPos.above();
        helper.setBlock(ALTAR_POS.above(), Blocks.CARTOGRAPHY_TABLE.defaultBlockState());

        MerchantOffers chosenOffers = realOffers(helper, absAltarPos, VillagerProfession.CARTOGRAPHER, 2);
        Villager villager = EmployeeManager.bind((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                absAltarPos, VillagerProfession.CARTOGRAPHER, chosenOffers, "Mapper");

        AABB jobSiteAabb = new AABB(jobSitePos);
        helper.assertFalse(villager.getBoundingBox().intersects(jobSiteAabb),
                "bound villager's bounding box must not overlap the job-site block at altarPos.above() (CR-01), "
                        + "villager bb=" + villager.getBoundingBox() + " jobSite bb=" + jobSiteAabb);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bind_sets_villager_xp_at_least_one(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        MerchantOffers chosenOffers = realOffers(helper, absAltarPos, VillagerProfession.FARMER, 2);
        Villager villager = EmployeeManager.bind((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                absAltarPos, VillagerProfession.FARMER, chosenOffers, "Farmhand");

        helper.assertTrue(villager.getVillagerXp() >= 1,
                "bound villager must have villagerXp >= 1 so ResetProfession never fires (EMP-02)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bind_sets_green_always_visible_name(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        MerchantOffers chosenOffers = realOffers(helper, absAltarPos, VillagerProfession.FARMER, 2);
        Villager villager = EmployeeManager.bind((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                absAltarPos, VillagerProfession.FARMER, chosenOffers, "Farmhand");

        Component name = villager.getCustomName();
        helper.assertTrue(name != null, "bound villager must have a custom name (EMP-08)");
        helper.assertTrue(name.getStyle().getColor() != null
                        && name.getStyle().getColor().getValue() == ChatFormatting.GREEN.getColor(),
                "bound villager's name must be styled GREEN (EMP-08)");
        helper.assertTrue(villager.isCustomNameVisible(),
                "bound villager's name must be always-visible (EMP-08)");
        helper.succeed();
    }

    /**
     * Replaces the deleted {@code bind_profession_is_one_of_three_fixed} (tested the removed
     * {@code BINDABLE_PROFESSIONS} restriction). Proves the spawned villager's profession exactly
     * equals whatever was passed in, across two different real professions — any real profession
     * now works, not a fixed 3-profession subset.
     */
    @GameTest(template = "empty")
    public static void bind_profession_exactly_matches_passed_in_profession(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);

        MerchantOffers librarianOffers = realOffers(helper, absAltarPos, VillagerProfession.LIBRARIAN, 2);
        Villager librarian = EmployeeManager.bind((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                absAltarPos, VillagerProfession.LIBRARIAN, librarianOffers, "Scholar");
        helper.assertTrue(librarian.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN,
                "bound profession must exactly equal LIBRARIAN, got "
                        + librarian.getVillagerData().getProfession());

        MerchantOffers farmerOffers = realOffers(helper, absAltarPos, VillagerProfession.FARMER, 2);
        Villager farmer = EmployeeManager.bind((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                absAltarPos, VillagerProfession.FARMER, farmerOffers, "Tiller");
        helper.assertTrue(farmer.getVillagerData().getProfession() == VillagerProfession.FARMER,
                "bound profession must exactly equal FARMER, got " + farmer.getVillagerData().getProfession());
        helper.succeed();
    }

    /**
     * Test 1 + Test 2 from the plan behavior list: the spawned villager's live {@code getOffers()}
     * and its {@code EmployeeData} attachment both exactly equal the caller-supplied
     * {@code chosenOffers} and {@code name} — never vanilla's lazy-fabricated 2 random trades, and
     * never a re-randomized default name.
     */
    @GameTest(template = "empty")
    public static void bind_live_offers_and_attachment_exactly_match_chosen_offers_and_name(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        MerchantOffers chosenOffers = realOffers(helper, absAltarPos, VillagerProfession.LIBRARIAN, 2);
        String name = "Custom Name";

        Villager villager = EmployeeManager.bind((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                absAltarPos, VillagerProfession.LIBRARIAN, chosenOffers, name);

        MerchantOffers liveOffers = villager.getOffers();
        helper.assertTrue(liveOffers.size() == chosenOffers.size(),
                "live getOffers() size must exactly equal chosenOffers size, got " + liveOffers.size()
                        + " expected " + chosenOffers.size());
        for (int i = 0; i < chosenOffers.size(); i++) {
            helper.assertTrue(offersEqual(liveOffers.get(i), chosenOffers.get(i)),
                    "live offer at index " + i + " must exactly equal the chosen offer");
        }

        EmployeeData data = villager.getData(ModAttachments.EMPLOYEE.get());
        helper.assertTrue(data.name().equals(name),
                "EmployeeData name must exactly equal the passed-in name, got " + data.name());
        helper.assertTrue(data.offers().size() == chosenOffers.size(),
                "EmployeeData offers size must exactly equal chosenOffers size");
        for (int i = 0; i < chosenOffers.size(); i++) {
            helper.assertTrue(offersEqual(data.offers().get(i), chosenOffers.get(i)),
                    "EmployeeData offer at index " + i + " must exactly equal the chosen offer");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wild_villager_unaffected_by_bind_in_same_world(GameTestHelper helper) {
        Villager wild = helper.spawn(EntityType.VILLAGER, WILD_VILLAGER_POS);

        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        MerchantOffers chosenOffers = realOffers(helper, absAltarPos, VillagerProfession.FARMER, 2);
        EmployeeManager.bind((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                absAltarPos, VillagerProfession.FARMER, chosenOffers, "Farmhand");

        helper.assertFalse(wild.hasData(ModAttachments.EMPLOYEE.get()),
                "wild villager must not carry the EmployeeData attachment (EMP-09)");
        helper.assertTrue(wild.getCustomName() == null,
                "wild villager must not have a custom name (EMP-09)");
        helper.assertTrue(wild.getVillagerXp() == 0,
                "wild villager's XP must be untouched by bind (EMP-09)");
        helper.succeed();
    }
}
