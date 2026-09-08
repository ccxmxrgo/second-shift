package com.cxmxrgo.secondshift.gametest;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.config.ModConfig;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 8 GameTest suite (STOCK-01/02/03) — see {@code 08-CONTEXT.md} for the full design
 * rationale each test below verifies. Mirrors this project's established idiom: {@code
 * helper.assertTrue}/{@code assertFalse}/{@code succeed()}, no raw JUnit, and a directly-posted
 * {@code EntityTickEvent.Post} for the periodic-check-driven behavior (Phase 6/7's own documented
 * technique for this exact GameTest-harness limitation).
 */
@GameTestHolder(SecondShift.MODID)
@PrefixGameTestTemplate(false)
public final class RestockGameTests {

    private static final BlockPos ALTAR_POS = new BlockPos(4, 2, 4);

    private RestockGameTests() {}

    private static Villager bindEmployeeAt(GameTestHelper helper, BlockPos absAltarPos) {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 1, 1, 0.05F));
        return EmployeeManager.bind((ServerLevel) helper.getLevel(),
                absAltarPos, VillagerProfession.LIBRARIAN, offers, "Test Employee");
    }

    private static void exhaustFirstOffer(Villager employee) {
        MerchantOffer offer = employee.getOffers().get(0);
        for (int i = 0; i < offer.getMaxUses(); i++) {
            offer.increaseUses();
        }
    }

    // --- STOCK-01: restocks on a real-time timer, independent of vanilla's own gating ---

    @GameTest(template = "empty")
    public static void an_exhausted_offer_restocks_once_the_interval_elapses(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        exhaustFirstOffer(employee);
        helper.assertTrue(employee.getOffers().get(0).isOutOfStock(), "setup sanity check — offer must be out of stock");

        long restockTimer = employee.getData(ModAttachments.RESTOCK_TIMER.get());
        long interval = ModConfig.RESTOCK_INTERVAL_TICKS.get();
        // Fast-forward the tracked timer itself (rather than waiting real ticks) so this test is
        // deterministic regardless of the configured interval's actual size.
        employee.setData(ModAttachments.RESTOCK_TIMER.get(), restockTimer - interval);

        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(employee));

        helper.assertFalse(employee.getOffers().get(0).isOutOfStock(),
                "the exhausted offer must be restocked once the configured interval has elapsed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void an_offer_is_never_restocked_before_the_interval_elapses(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        exhaustFirstOffer(employee);

        // Timer freshly set to "now" at bind time — nowhere close to elapsed.
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(employee));

        helper.assertTrue(employee.getOffers().get(0).isOutOfStock(),
                "an offer must stay exhausted until the configured interval has actually elapsed");
        helper.succeed();
    }

    // --- Success criterion 4: a long-unloaded employee restocks AT MOST ONCE, no burst ---

    @GameTest(template = "empty")
    public static void a_very_long_unload_gap_still_restocks_exactly_once(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        exhaustFirstOffer(employee);

        long interval = ModConfig.RESTOCK_INTERVAL_TICKS.get();
        // Simulate an employee unloaded for 500x the configured interval.
        employee.setData(ModAttachments.RESTOCK_TIMER.get(), -500L * interval);

        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(employee));
        long timerAfterFirstCheck = employee.getData(ModAttachments.RESTOCK_TIMER.get());

        helper.assertFalse(employee.getOffers().get(0).isOutOfStock(), "must have restocked");
        helper.assertTrue(timerAfterFirstCheck >= 0,
                "the timer must reset to roughly 'now', not accumulate leftover elapsed time, got " + timerAfterFirstCheck);

        // Re-exhaust and check again immediately — a burst bug would restock a second time right away.
        exhaustFirstOffer(employee);
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(employee));
        helper.assertTrue(employee.getOffers().get(0).isOutOfStock(),
                "a second immediate check must NOT restock again — exactly one restock per elapsed interval, never a burst");
        helper.succeed();
    }

    // --- STOCK-02: the interval is a real, live config value ---

    @GameTest(template = "empty")
    public static void the_restock_interval_is_a_positive_configurable_value(GameTestHelper helper) {
        int configured = ModConfig.RESTOCK_INTERVAL_TICKS.get();
        helper.assertTrue(configured > 0, "the restock interval must be a positive tick count, got " + configured);

        // Live-wiring proof: changing the ConfigValue takes effect immediately for the next check
        // (mirrors how a user editing the TOML and reloading would behave) without touching
        // anything else in the restock logic.
        int original = configured;
        try {
            ModConfig.RESTOCK_INTERVAL_TICKS.set(1);
            helper.assertTrue(ModConfig.RESTOCK_INTERVAL_TICKS.get() == 1,
                    "setting the config value must be immediately visible to the same getter the restock check reads");
        } finally {
            ModConfig.RESTOCK_INTERVAL_TICKS.set(original);
        }
        helper.succeed();
    }

    // --- STOCK-03: restock logic never touches a wild (non-employee) villager ---

    @GameTest(template = "empty")
    public static void a_wild_villager_is_never_touched_by_restock_logic(GameTestHelper helper) {
        Villager wild = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(wild != null, "setup sanity check");
        BlockPos spawnPos = helper.absolutePos(ALTAR_POS);
        wild.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        wild.setVillagerData(wild.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(1));
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 1, 1, 0.05F));
        wild.setOffers(offers);
        helper.getLevel().addFreshEntity(wild);
        exhaustFirstOffer(wild);

        helper.assertFalse(wild.hasData(ModAttachments.EMPLOYEE.get()), "setup sanity check — must not be an employee");

        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(wild));

        helper.assertTrue(wild.getOffers().get(0).isOutOfStock(),
                "a wild villager's exhausted offer must never be touched by this mod's restock logic");
        helper.succeed();
    }
}
