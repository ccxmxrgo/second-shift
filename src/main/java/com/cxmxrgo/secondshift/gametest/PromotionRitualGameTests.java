package com.cxmxrgo.secondshift.gametest;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.employee.EmployeeData;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
import com.cxmxrgo.secondshift.event.EmployeeEvents;
import com.cxmxrgo.secondshift.menu.PromotionRitualMenu;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Phase 7 GameTest suite (PROG-01/02/03/04) — see {@code 07-CONTEXT.md} for the full design
 * rationale each test below verifies. Mirrors this project's established idiom exactly: {@code
 * helper.assertTrue}/{@code assertFalse}/{@code succeed()}, no raw JUnit, and — for the periodic
 * tick-driven behaviors — direct {@code NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(...))}
 * calls rather than {@code runAfterDelay} (06-CONTEXT.md's own documented GameTest-harness
 * limitation for anything relying on the periodic per-employee check).
 */
@GameTestHolder(SecondShift.MODID)
@PrefixGameTestTemplate(false)
public final class PromotionRitualGameTests {

    private static final BlockPos ALTAR_POS = new BlockPos(4, 2, 4);

    private PromotionRitualGameTests() {}

    private static MerchantOffer tier1Offer() {
        return new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 1, 1, 0.05F);
    }

    private static Villager bindEmployeeAt(GameTestHelper helper, BlockPos absAltarPos) {
        MerchantOffers offers = new MerchantOffers();
        offers.add(tier1Offer());
        return EmployeeManager.bind((ServerLevel) helper.getLevel(),
                absAltarPos, VillagerProfession.LIBRARIAN, offers, "Test Employee");
    }

    private static SoulAltarBlockEntity setupBoundAltar(GameTestHelper helper, BlockPos absAltarPos, Villager employee) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        SoulAltarBlockEntity be = (SoulAltarBlockEntity) helper.getLevel().getBlockEntity(absAltarPos);
        be.setEmployeeBound(true);
        be.setEmployeeId(employee.getUUID());
        be.setChanged();
        return be;
    }

    // --- PROG-01: tier is derived from vanilla's own level, not a new field ---

    @GameTest(template = "empty")
    public static void a_fresh_bind_starts_at_tier_1_matching_vanilla_level(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);

        EmployeeData data = employee.getData(ModAttachments.EMPLOYEE.get());
        helper.assertTrue(data.tier() == 1, "a fresh bind must start at tier 1, got " + data.tier());
        helper.assertTrue(employee.getVillagerData().getLevel() == 1,
                "a fresh bind's vanilla level must also be 1 (Novice), got " + employee.getVillagerData().getLevel());
        helper.succeed();
    }

    // --- PROG-02: vanilla's auto-appended trades are reverted on the next periodic check ---

    @GameTest(template = "empty")
    public static void vanilla_auto_appended_offers_are_reverted_on_the_next_check(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        setupBoundAltar(helper, absAltarPos, employee);

        // Simulate vanilla's own increaseMerchantCareer()+updateTrades(): the villager naturally
        // leveled to 2 through real trade XP, and vanilla auto-appended a trade the player never
        // chose (a Compass, arbitrarily, standing in for "whatever vanilla would have added").
        employee.setVillagerData(employee.getVillagerData().setLevel(2));
        MerchantOffers withUnchosenTrade = new MerchantOffers();
        withUnchosenTrade.add(tier1Offer());
        withUnchosenTrade.add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.COMPASS), 1, 1, 0.05F));
        employee.setOffers(withUnchosenTrade);
        helper.assertTrue(employee.getOffers().size() == 2, "setup sanity check — expected 2 offers before revert");

        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(employee));

        helper.assertTrue(employee.getOffers().size() == 1,
                "vanilla's auto-appended trade must be reverted on the next periodic check, found "
                        + employee.getOffers().size() + " offers");
        helper.assertTrue(employee.getOffers().get(0).getResult().is(Items.BREAD),
                "the surviving offer must be the player's original tier-1 choice, got "
                        + employee.getOffers().get(0).getResult());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void an_employee_still_at_its_installed_tier_is_never_touched(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        setupBoundAltar(helper, absAltarPos, employee);

        // Still at vanilla level 1 == data.tier() 1 — nothing should be reverted or signaled.
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(employee));

        helper.assertTrue(employee.getOffers().size() == 1,
                "an employee still at its installed tier must be left alone, found "
                        + employee.getOffers().size() + " offers");
        helper.succeed();
    }

    // --- PROG-04: installPromotion merges onto the existing offers and advances the tier ---

    @GameTest(template = "empty")
    public static void install_promotion_merges_new_offers_and_advances_tier(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        EmployeeData before = employee.getData(ModAttachments.EMPLOYEE.get());

        MerchantOffer chosenTier2 =
                new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.MAP), 1, 1, 0.05F);
        EmployeeManager.installPromotion(employee, before, 2, List.of(chosenTier2));

        EmployeeData after = employee.getData(ModAttachments.EMPLOYEE.get());
        helper.assertTrue(after.tier() == 2, "installPromotion must advance the stored tier, got " + after.tier());
        helper.assertTrue(employee.getOffers().size() == 2,
                "installPromotion must preserve the prior tier's trade AND add the new one, found "
                        + employee.getOffers().size() + " offers");
        boolean hasBread = employee.getOffers().stream().anyMatch(o -> o.getResult().is(Items.BREAD));
        boolean hasMap = employee.getOffers().stream().anyMatch(o -> o.getResult().is(Items.MAP));
        helper.assertTrue(hasBread, "the original tier-1 trade must survive a promotion");
        helper.assertTrue(hasMap, "the newly chosen tier-2 trade must be installed");
        helper.succeed();
    }

    // --- PROG-03/04: PromotionRitualMenu itself ---

    @GameTest(template = "empty")
    public static void promotion_ritual_menu_rolls_the_employees_next_tier_pool(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        SoulAltarBlockEntity be = setupBoundAltar(helper, absAltarPos, employee);
        employee.setVillagerData(employee.getVillagerData().setLevel(2)); // now promotable to tier 2

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        PromotionRitualMenu menu = new PromotionRitualMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);

        helper.assertTrue(menu.getTargetTier() == 2,
                "the ritual must target the employee's actual next vanilla level, got " + menu.getTargetTier());
        helper.assertTrue(!menu.getTierPool().isEmpty(),
                "Librarian's real tier-2 pool must not be empty");
        helper.assertTrue(menu.getPickCount() == Math.min(2, menu.getTierPool().size()),
                "pick count must be min(2, pool size)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void confirming_a_ritual_installs_the_chosen_trades_and_closes_the_menu(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        setupBoundAltar(helper, absAltarPos, employee);
        employee.setVillagerData(employee.getVillagerData().setLevel(2));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        PromotionRitualMenu menu = new PromotionRitualMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu; // stillValid() checks the player's own open menu isn't stale

        int pickCount = menu.getPickCount();
        int idxA = 0;
        int idxB = pickCount > 1 ? 1 : -1; // -1 decodes to "no second pick" via encodeConfirm's sentinel
        int packed = idxB >= 0
                ? PromotionRitualMenu.encodeConfirm(List.of(idxA, idxB))
                : PromotionRitualMenu.encodeConfirm(List.of(idxA));

        boolean handled = menu.clickMenuButton(player, packed);
        helper.assertTrue(handled, "a well-formed confirm click must be handled");

        EmployeeData data = employee.getData(ModAttachments.EMPLOYEE.get());
        helper.assertTrue(data.tier() == 2, "confirming must advance the employee to the target tier");
        helper.assertTrue(employee.getOffers().size() == 1 + pickCount,
                "confirming must add exactly pickCount new offers onto the original 1, found "
                        + employee.getOffers().size());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void a_malformed_confirm_click_never_installs_a_partial_promotion(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        setupBoundAltar(helper, absAltarPos, employee);
        employee.setVillagerData(employee.getVillagerData().setLevel(2));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        PromotionRitualMenu menu = new PromotionRitualMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        // A raw, forged id that decodes to no valid indices at all.
        boolean handled = menu.clickMenuButton(player, 999999);
        helper.assertFalse(handled, "a malformed/short selection must be rejected");

        EmployeeData data = employee.getData(ModAttachments.EMPLOYEE.get());
        helper.assertTrue(data.tier() == 1, "a rejected confirm must never advance the tier");
        helper.assertTrue(employee.getOffers().size() == 1, "a rejected confirm must never touch the offers");
        helper.succeed();
    }

    // --- PROG-03: SoulAltarBlock routes empty-hand right-click on a bound altar correctly ---

    @GameTest(template = "empty")
    public static void non_promotable_bound_employee_gets_the_plain_occupied_path(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        setupBoundAltar(helper, absAltarPos, employee); // still level 1 == tier 1 — not promotable

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        helper.useBlock(ALTAR_POS, player);

        helper.assertTrue(!(player.containerMenu instanceof PromotionRitualMenu),
                "a non-promotable bound employee must never open the Promotion Ritual");
        helper.succeed();
    }

    // --- Hygiene: cleanup helper doesn't throw for an unknown UUID ---

    @GameTest(template = "empty")
    public static void clear_signal_is_safe_for_an_unrelated_uuid(GameTestHelper helper) {
        EmployeeEvents.clearSignal(java.util.UUID.randomUUID()); // must not throw
        helper.succeed();
    }
}
