package com.cxmxrgo.secondshift.gametest;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * ECON-02 GameTest suite — the villager-instakill / guaranteed-Soul-Fragment mechanic.
 *
 * <p><b>RED baseline for plan 02-01.</b> {@code event/HarvesterEvents} does not exist yet,
 * so every test that expects the reap to happen currently FAILS. Plan 02-02 adds the
 * {@code LivingDamageEvent.Pre} + {@code LivingDropsEvent} handlers and flips the three
 * positive tests GREEN; the three exclusion tests
 * ({@link #harvester_hit_wandering_trader_no_fragment},
 * {@link #harvester_hit_zombie_villager_no_fragment},
 * {@link #sword_kill_villager_no_fragment}) must stay GREEN across that change.
 *
 * <p>Each test drives damage the same way the real handler will observe it: a
 * {@code damageSources().playerAttack(mockPlayer)} source whose {@code getWeaponItem()}
 * is the mock player's main-hand stack. This is deterministic and does not depend on
 * attack-cooldown / attribute-refresh plumbing that a never-ticked mock player lacks.
 *
 * <p>All tests share one structure, {@code secondshift:empty} — a 9x5x9 stone-floor box
 * ({@code data/secondshift/structure/empty.nbt}). {@code @PrefixGameTestTemplate(false)}
 * keeps the template name un-prefixed by the class name. Discovered under the
 * {@code secondshift} namespace; run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(SecondShift.MODID)
@PrefixGameTestTemplate(false)
public final class HarvesterGameTests {

    private static final BlockPos SPAWN = new BlockPos(4, 2, 4);
    private static final double DROP_SEARCH_RADIUS = 6.0D;
    private static final long SETTLE_TICKS = 10L;

    private HarvesterGameTests() {}

    // --- positive: one Harvester hit -> dead + exactly one Soul Fragment (RED until 02-02) ---

    @GameTest(template = "empty")
    public static void harvester_kill_villager_drops_one_fragment(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, SPAWN);
        hit(helper, villager, ModItems.HARVESTER.get());
        helper.succeedWhen(() -> assertReapedToOneFragment(helper, villager));
    }

    @GameTest(template = "empty")
    public static void harvester_kill_baby_villager_drops_one_fragment(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, SPAWN);
        villager.setBaby(true);
        hit(helper, villager, ModItems.HARVESTER.get());
        helper.succeedWhen(() -> assertReapedToOneFragment(helper, villager));
    }

    @GameTest(template = "empty")
    public static void harvester_kill_resistance_and_absorption_villager_still_one_shot(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, SPAWN);
        // Resistance V (amplifier 4) + a large absorption buffer — the reap must still be
        // lethal because 02-02 uses the post-mitigation LivingDamageEvent.Pre hook.
        villager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100000, 4));
        villager.setAbsorptionAmount(200.0F);
        hit(helper, villager, ModItems.HARVESTER.get());
        helper.succeedWhen(() -> assertReapedToOneFragment(helper, villager));
    }

    /**
     * WR-02 regression: the guaranteed Soul Fragment must survive {@code doMobLoot=false}
     * (which suppresses the whole {@code LivingDropsEvent} pipeline). The rule is restored
     * once the assertion passes so sibling tests are unaffected.
     */
    @GameTest(template = "empty")
    public static void harvester_kill_villager_drops_fragment_with_domobloot_false(GameTestHelper helper) {
        helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT)
                .set(false, helper.getLevel().getServer());
        Villager villager = helper.spawn(EntityType.VILLAGER, SPAWN);
        hit(helper, villager, ModItems.HARVESTER.get());
        helper.succeedWhen(() -> {
            assertReapedToOneFragment(helper, villager);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT)
                    .set(true, helper.getLevel().getServer());
        });
    }

    /**
     * CR-01 regression: the Soul Altar is {@code requiresCorrectToolForDrops()}, so it only
     * ever drops when a pickaxe is the "correct tool" — which requires membership in the
     * {@code minecraft:mineable/pickaxe} block tag. Without that tag the altar is
     * permanently non-recoverable once placed.
     */
    @GameTest(template = "empty")
    public static void soul_altar_needs_pickaxe_to_drop(GameTestHelper helper) {
        var altarState = ModBlocks.SOUL_ALTAR.get().defaultBlockState();
        helper.assertTrue(
                new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(altarState),
                "a pickaxe must be a correct tool for Soul Altar drops (CR-01)");
        helper.assertFalse(
                new ItemStack(Items.STICK).isCorrectToolForDrops(altarState),
                "a non-tool must not drop the Soul Altar (requiresCorrectToolForDrops still enforced)");
        helper.succeed();
    }

    // --- exclusions: no instakill, no Fragment (GREEN now and after 02-02) ---

    @GameTest(template = "empty")
    public static void harvester_hit_wandering_trader_no_fragment(GameTestHelper helper) {
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, SPAWN);
        float before = trader.getHealth();
        hit(helper, trader, ModItems.HARVESTER.get());
        helper.runAtTickTime(SETTLE_TICKS, () -> {
            helper.assertTrue(trader.isAlive(), "wandering trader must survive one Harvester hit (D-09 exclusion)");
            helper.assertTrue(trader.getHealth() < before, "wandering trader should take modest damage");
            helper.assertItemEntityNotPresent(ModItems.SOUL_FRAGMENT.get(), SPAWN, DROP_SEARCH_RADIUS);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void harvester_hit_zombie_villager_no_fragment(GameTestHelper helper) {
        ZombieVillager zombie = helper.spawn(EntityType.ZOMBIE_VILLAGER, SPAWN);
        hit(helper, zombie, ModItems.HARVESTER.get());
        helper.runAtTickTime(SETTLE_TICKS, () -> {
            helper.assertItemEntityNotPresent(ModItems.SOUL_FRAGMENT.get(), SPAWN, DROP_SEARCH_RADIUS);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void sword_kill_villager_no_fragment(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, SPAWN);
        hit(helper, villager, Items.IRON_SWORD, 40.0F);
        helper.runAtTickTime(SETTLE_TICKS, () -> {
            helper.assertTrue(villager.isDeadOrDying(), "villager should die to the (lethal) sword hit");
            helper.assertItemEntityNotPresent(ModItems.SOUL_FRAGMENT.get(), SPAWN, DROP_SEARCH_RADIUS);
            helper.succeed();
        });
    }

    // --- helpers ---

    private static void hit(GameTestHelper helper, LivingEntity target, Item weapon) {
        hit(helper, target, weapon, 4.0F);
    }

    /** Apply one player-attack damage event whose weapon item is {@code weapon}. */
    private static void hit(GameTestHelper helper, LivingEntity target, Item weapon, float amount) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon));
        player.moveTo(target.getX(), target.getY(), target.getZ());
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        target.hurt(source, amount);
    }

    private static void assertReapedToOneFragment(GameTestHelper helper, LivingEntity villager) {
        helper.assertTrue(villager.isDeadOrDying(), "one Harvester hit must kill the villager (D-08)");
        helper.assertItemEntityPresent(ModItems.SOUL_FRAGMENT.get(), SPAWN, DROP_SEARCH_RADIUS);
        int fragments = helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(DROP_SEARCH_RADIUS))
                .stream()
                .filter(e -> e.getItem().is(ModItems.SOUL_FRAGMENT.get()))
                .mapToInt(e -> e.getItem().getCount())
                .sum();
        helper.assertTrue(fragments == 1, "exactly one Soul Fragment must drop, got " + fragments);
    }
}
