package com.cxmxrgo.secondshift.gametest;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.config.ModConfig;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
import com.cxmxrgo.secondshift.employee.Happiness;
import com.cxmxrgo.secondshift.employee.JobTitles;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import com.cxmxrgo.secondshift.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 10 GameTest suite (POL-05/06/07/08) — see {@code 10-CONTEXT.md} for the full design
 * rationale each test below verifies. Mirrors this project's established idiom throughout.
 *
 * <p>Every test that touches a shared {@code ModConfig} value resets it in a {@code finally}
 * block (or equivalent) so config state never leaks between tests — the same discipline
 * {@code RestockGameTests#the_restock_interval_is_a_positive_configurable_value} established in
 * Phase 8.
 */
@GameTestHolder(SecondShift.MODID)
@PrefixGameTestTemplate(false)
public final class PolishGameTests {

    private static final BlockPos ALTAR_POS = new BlockPos(4, 1, 4);
    private static final BlockPos SPAWN = new BlockPos(4, 2, 4);

    private PolishGameTests() {}

    private static Villager bindEmployeeAt(GameTestHelper helper, BlockPos absAltarPos) {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 1, 1, 0.05F));
        return EmployeeManager.bind((ServerLevel) helper.getLevel(),
                absAltarPos, VillagerProfession.LIBRARIAN, offers, "Aldric");
    }

    // --- POL-07: HR job titles ---

    @GameTest(template = "empty")
    public static void job_titles_map_tiers_in_order(GameTestHelper helper) {
        helper.assertTrue(JobTitles.forTier(1).equals("Intern"), "tier 1 must be Intern");
        helper.assertTrue(JobTitles.forTier(2).equals("Associate"), "tier 2 must be Associate");
        helper.assertTrue(JobTitles.forTier(3).equals("Senior"), "tier 3 must be Senior");
        helper.assertTrue(JobTitles.forTier(4).equals("Lead"), "tier 4 must be Lead");
        helper.assertTrue(JobTitles.forTier(5).equals("Principal"), "tier 5 must be Principal");
        helper.assertTrue(JobTitles.forTier(99).equals("Principal"), "a tier beyond the list must clamp, not throw");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bind_prefixes_the_chosen_name_with_the_tier_1_title(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        String displayName = employee.getCustomName().getString();
        helper.assertTrue(displayName.equals("Intern Aldric"),
                "a fresh bind's display name must be '<tier-1 title> <chosen name>', got '" + displayName + "'");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void promotion_refreshes_the_title_prefix_without_stacking(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        var data = employee.getData(ModAttachments.EMPLOYEE.get());
        MerchantOffer tier2Offer = new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.MAP), 1, 1, 0.05F);

        EmployeeManager.installPromotion(employee, data, 2, java.util.List.of(tier2Offer));

        String displayName = employee.getCustomName().getString();
        helper.assertTrue(displayName.equals("Associate Aldric"),
                "after one promotion the title must be replaced (not stacked), got '" + displayName + "'");
        helper.succeed();
    }

    // --- POL-06: configurable Soul Fragment drop count ---

    @GameTest(template = "empty")
    public static void soul_fragment_drop_count_is_configurable(GameTestHelper helper) {
        int original = ModConfig.SOUL_FRAGMENT_DROP_COUNT.get();
        try {
            ModConfig.SOUL_FRAGMENT_DROP_COUNT.set(3);

            Villager villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, SPAWN);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HARVESTER.get()));
            player.moveTo(villager.getX(), villager.getY(), villager.getZ());
            DamageSource source = helper.getLevel().damageSources().playerAttack(player);
            villager.hurt(source, 4.0F);

            helper.succeedWhen(() -> {
                helper.assertTrue(villager.isDeadOrDying(), "the Harvester must still instakill regardless of drop count");
                int total = helper.getLevel()
                        .getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(6.0D))
                        .stream()
                        .filter(e -> e.getItem().is(ModItems.SOUL_FRAGMENT.get()))
                        .mapToInt(e -> e.getItem().getCount())
                        .sum();
                helper.assertTrue(total == 3, "the configured drop count (3) must be honored, got " + total);
                ModConfig.SOUL_FRAGMENT_DROP_COUNT.set(original);
                helper.succeed();
            });
        } catch (RuntimeException e) {
            ModConfig.SOUL_FRAGMENT_DROP_COUNT.set(original);
            throw e;
        }
    }

    // --- POL-06: trait-immunity toggles ---

    @GameTest(template = "empty")
    public static void conversion_immunity_can_be_disabled(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        boolean original = ModConfig.CONVERSION_IMMUNITY_ENABLED.get();
        try {
            ModConfig.CONVERSION_IMMUNITY_ENABLED.set(false);
            LivingConversionEvent.Pre event = new LivingConversionEvent.Pre(
                    employee, net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER, x -> {});
            NeoForge.EVENT_BUS.post(event);
            helper.assertFalse(event.isCanceled(),
                    "with the toggle disabled, an employee's conversion must NOT be cancelled");
        } finally {
            ModConfig.CONVERSION_IMMUNITY_ENABLED.set(original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breeding_lock_can_be_disabled(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        boolean original = ModConfig.BREEDING_LOCK_ENABLED.get();
        try {
            ModConfig.BREEDING_LOCK_ENABLED.set(false);
            employee.setAge(0); // simulate the age having decayed/been reset below the reassert floor

            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(employee));

            helper.assertTrue(employee.getAge() == 0,
                    "with the toggle disabled, the breeding-lock age must NOT be re-asserted, got age=" + employee.getAge());
        } finally {
            ModConfig.BREEDING_LOCK_ENABLED.set(original);
        }
        helper.succeed();
    }

    // --- POL-06: happiness band thresholds are configurable ---

    @GameTest(template = "empty")
    public static void happiness_band_thresholds_are_configurable(GameTestHelper helper) {
        int originalUnhappy = ModConfig.HAPPINESS_UNHAPPY_MAX.get();
        int originalOk = ModConfig.HAPPINESS_OK_MAX.get();
        try {
            ModConfig.HAPPINESS_UNHAPPY_MAX.set(10);
            ModConfig.HAPPINESS_OK_MAX.set(20);

            helper.assertTrue(Happiness.fromMeter(10) == Happiness.UNHAPPY, "10 must be Unhappy with a lowered threshold");
            helper.assertTrue(Happiness.fromMeter(11) == Happiness.OK, "11 must be OK with a lowered threshold");
            helper.assertTrue(Happiness.fromMeter(21) == Happiness.HAPPY, "21 must be Happy with a lowered threshold");
        } finally {
            ModConfig.HAPPINESS_UNHAPPY_MAX.set(originalUnhappy);
            ModConfig.HAPPINESS_OK_MAX.set(originalOk);
        }
        helper.succeed();
    }

}
