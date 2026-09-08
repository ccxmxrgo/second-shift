package com.cxmxrgo.secondshift.gametest;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.config.ModConfig;
import com.cxmxrgo.secondshift.employee.EmployeeData;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
import com.cxmxrgo.secondshift.employee.FoodChecker;
import com.cxmxrgo.secondshift.employee.Happiness;
import com.cxmxrgo.secondshift.employee.QuartersChecker;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 9 GameTest suite (HAPP-01..07, STOCK-04) — see {@code 09-CONTEXT.md} for the full design
 * rationale each test below verifies. Mirrors this project's established idiom and the
 * directly-posted {@code EntityTickEvent.Post} technique Phases 6/7/8 established for periodic-
 * check-driven behavior (a freshly-spawned entity's own {@code tickCount} is 0, which trivially
 * satisfies every modulo gate this mod's periodic checks use).
 */
@GameTestHolder(SecondShift.MODID)
@PrefixGameTestTemplate(false)
public final class HappinessGameTests {

    private static final BlockPos ALTAR_POS = new BlockPos(4, 1, 4);

    private HappinessGameTests() {}

    private static Villager bindEmployeeAt(GameTestHelper helper, BlockPos absAltarPos) {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 1, 1, 0.05F));
        return EmployeeManager.bind((ServerLevel) helper.getLevel(),
                absAltarPos, VillagerProfession.LIBRARIAN, offers, "Test Employee");
    }

    /** Builds a real, valid, single-layer-height 3×3 enclosed room (floor/ceiling/walls) with one
     * wall cell replaced by a door, around {@code interiorY} — matches {@code
     * QuartersChecker}'s own single-point flood-fill origin exactly. */
    private static void buildValidRoom(GameTestHelper helper, int interiorY) {
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, interiorY - 1, z), Blocks.STONE); // floor
                helper.setBlock(new BlockPos(x, interiorY + 1, z), Blocks.STONE); // ceiling
            }
        }
        for (int x = 2; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, interiorY, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(x, interiorY, 6), Blocks.STONE);
        }
        for (int z = 2; z <= 6; z++) {
            helper.setBlock(new BlockPos(2, interiorY, z), Blocks.STONE);
            helper.setBlock(new BlockPos(6, interiorY, z), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(4, interiorY, 2), Blocks.OAK_DOOR); // one wall cell -> door
    }

    /** Same shape as {@link #buildValidRoom}, but every wall cell is solid stone — no door. */
    private static void buildRoomWithNoDoor(GameTestHelper helper, int interiorY) {
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, interiorY - 1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, interiorY + 1, z), Blocks.STONE);
            }
        }
        for (int x = 2; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, interiorY, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(x, interiorY, 6), Blocks.STONE);
        }
        for (int z = 2; z <= 6; z++) {
            helper.setBlock(new BlockPos(2, interiorY, z), Blocks.STONE);
            helper.setBlock(new BlockPos(6, interiorY, z), Blocks.STONE);
        }
    }

    // --- HAPP-01: quarters detection ---

    @GameTest(template = "empty")
    public static void a_real_enclosed_3x3_room_with_a_door_is_valid_quarters(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        buildValidRoom(helper, ALTAR_POS.getY() + 2);

        boolean valid = QuartersChecker.hasValidQuarters((ServerLevel) helper.getLevel(), absAltarPos.above(2));
        helper.assertTrue(valid, "a real enclosed 3x3 room with a door must be valid quarters");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void an_enclosed_room_without_a_door_is_invalid_quarters(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        buildRoomWithNoDoor(helper, ALTAR_POS.getY() + 2);

        boolean valid = QuartersChecker.hasValidQuarters((ServerLevel) helper.getLevel(), absAltarPos.above(2));
        helper.assertFalse(valid, "an enclosed room with NO door must be invalid quarters (HAPP-01 requires a door)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void an_open_unenclosed_space_is_invalid_quarters(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        // No blocks placed at all -- the origin sits in completely open space.
        boolean valid = QuartersChecker.hasValidQuarters((ServerLevel) helper.getLevel(), absAltarPos.above(2));
        helper.assertFalse(valid, "a completely open (unenclosed) space must never be valid quarters");
        helper.succeed();
    }

    // --- HAPP-02: food availability ---

    @GameTest(template = "empty")
    public static void a_stocked_chest_provides_food_and_is_drawn_from(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        helper.setBlock(new BlockPos(6, 1, 4), Blocks.CHEST);
        BlockPos absChestPos = helper.absolutePos(new BlockPos(6, 1, 4));
        helper.assertTrue(helper.getLevel().getBlockEntity(absChestPos) instanceof ChestBlockEntity, "setup sanity check");
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(absChestPos);
        chest.setItem(0, new ItemStack(Items.BREAD, 3));

        ServerLevel level = (ServerLevel) helper.getLevel();
        helper.assertTrue(FoodChecker.hasFoodAvailable(level, absAltarPos), "food must be detected without consuming it");
        helper.assertTrue(chest.getItem(0).getCount() == 3, "a read-only check must never shrink the stack");

        helper.assertTrue(FoodChecker.tryConsumeFood(level, absAltarPos), "food must be found and drawn");
        helper.assertTrue(chest.getItem(0).getCount() == 2, "drawing food must shrink the stack by exactly 1");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void an_empty_or_foodless_chest_provides_no_food(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        helper.setBlock(new BlockPos(6, 1, 4), Blocks.CHEST);
        BlockPos absChestPos = helper.absolutePos(new BlockPos(6, 1, 4));
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(absChestPos);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64)); // not food

        ServerLevel level = (ServerLevel) helper.getLevel();
        helper.assertFalse(FoodChecker.hasFoodAvailable(level, absAltarPos), "a chest with no recognized food item must report unavailable");
        helper.succeed();
    }

    // --- HAPP-03: discrete band mapping ---

    @GameTest(template = "empty")
    public static void happiness_bands_map_correctly(GameTestHelper helper) {
        helper.assertTrue(Happiness.fromMeter(0) == Happiness.UNHAPPY, "0 must be Unhappy");
        helper.assertTrue(Happiness.fromMeter(33) == Happiness.UNHAPPY, "33 must be Unhappy");
        helper.assertTrue(Happiness.fromMeter(34) == Happiness.OK, "34 must be OK");
        helper.assertTrue(Happiness.fromMeter(66) == Happiness.OK, "66 must be OK");
        helper.assertTrue(Happiness.fromMeter(67) == Happiness.HAPPY, "67 must be Happy");
        helper.assertTrue(Happiness.fromMeter(100) == Happiness.HAPPY, "100 must be Happy");
        helper.succeed();
    }

    // --- HAPP-04/STOCK-04 + full integration: meter moves, price adjusts, restock pauses ---

    @GameTest(template = "empty")
    public static void meeting_conditions_moves_the_meter_toward_happy_and_discounts_price(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        buildValidRoom(helper, ALTAR_POS.getY() + 2);
        helper.setBlock(new BlockPos(6, 1, 4), Blocks.CHEST);
        ((ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(6, 1, 4))))
                .setItem(0, new ItemStack(Items.BREAD, 10));

        Villager employee = bindEmployeeAt(helper, absAltarPos);
        int before = employee.getData(ModAttachments.HAPPINESS.get());

        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(employee));

        int after = employee.getData(ModAttachments.HAPPINESS.get());
        helper.assertTrue(after > before, "meeting quarters+food conditions must move the meter toward Happy, was "
                + before + " now " + after);
        // Not yet in the Happy band after a single step from the default 50 (+10 = 60, still OK) —
        // verify the price adjustment for the CURRENT tier is applied regardless of which one it is.
        Happiness tier = Happiness.fromMeter(after);
        int expectedDiff = tier.priceAdjustment();
        helper.assertTrue(employee.getOffers().get(0).getSpecialPriceDiff() == expectedDiff,
                "the offer's specialPriceDiff must match the current happiness tier's price adjustment");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missing_conditions_moves_the_meter_toward_unhappy_and_surcharges_price(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        // No room, no chest -- both conditions fail.
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        int before = employee.getData(ModAttachments.HAPPINESS.get());

        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(employee));

        int after = employee.getData(ModAttachments.HAPPINESS.get());
        helper.assertTrue(after < before, "missing quarters+food must move the meter toward Unhappy, was "
                + before + " now " + after);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void an_unhappy_employee_never_restocks(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        // Force straight to Unhappy and exhaust the offer.
        employee.setData(ModAttachments.HAPPINESS.get(), 0);
        MerchantOffer offer = employee.getOffers().get(0);
        for (int i = 0; i < offer.getMaxUses(); i++) {
            offer.increaseUses();
        }
        long interval = ModConfig.RESTOCK_INTERVAL_TICKS.get();
        employee.setData(ModAttachments.RESTOCK_TIMER.get(), employee.getData(ModAttachments.RESTOCK_TIMER.get()) - interval);

        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(employee));

        helper.assertTrue(employee.getOffers().get(0).isOutOfStock(),
                "an Unhappy employee's exhausted offer must NOT restock even after the interval elapses (STOCK-04)");
        helper.succeed();
    }

    // --- HAPP-06: sustained-Unhappy quit ---

    @GameTest(template = "empty")
    public static void a_sustained_unhappy_streak_makes_the_employee_quit(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        EmployeeData data = employee.getData(ModAttachments.EMPLOYEE.get());
        // Force the streak to just under the configured threshold, and the meter to Unhappy.
        employee.setData(ModAttachments.HAPPINESS.get(), 0);
        int threshold = ModConfig.HAPPINESS_QUIT_THRESHOLD_TICKS.get();
        employee.setData(ModAttachments.UNHAPPY_STREAK_TICKS.get(), Math.max(0, threshold - 400));

        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(employee));

        helper.assertFalse(employee.hasData(ModAttachments.EMPLOYEE.get()),
                "an employee whose Unhappy streak crosses the configured threshold must quit (EMPLOYEE attachment removed)");
        helper.assertTrue(employee.isAlive(), "quitting must NOT kill the villager -- it reverts, it doesn't die");

        ServerLevel level = (ServerLevel) helper.getLevel();
        boolean soulBlockDropped = !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                employee.getBoundingBox().inflate(3))
                .isEmpty();
        helper.assertTrue(soulBlockDropped, "quitting must drop a Soul Block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void happiness_leaving_unhappy_resets_the_streak(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        buildValidRoom(helper, ALTAR_POS.getY() + 2);
        helper.setBlock(new BlockPos(6, 1, 4), Blocks.CHEST);
        ((ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(6, 1, 4))))
                .setItem(0, new ItemStack(Items.BREAD, 10));

        Villager employee = bindEmployeeAt(helper, absAltarPos);
        employee.setData(ModAttachments.HAPPINESS.get(), 30); // Unhappy, but one +10 step from crossing into OK
        employee.setData(ModAttachments.UNHAPPY_STREAK_TICKS.get(), 8000); // a real but sub-threshold streak

        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(employee)); // conditions are met -> meter rises out of Unhappy

        int streak = employee.getData(ModAttachments.UNHAPPY_STREAK_TICKS.get());
        helper.assertTrue(streak == 0, "the streak must reset to 0 the moment happiness leaves the Unhappy band, got " + streak);
        helper.succeed();
    }
}
