package com.cxmxrgo.secondshift.gametest;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.content.item.HarvesterItem;
import com.cxmxrgo.secondshift.employee.EmployeeData;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
import com.cxmxrgo.secondshift.event.EmployeeFiring;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Phase 6 GameTest suite (EMP-03/04/05/06/07, ECON-04, ALTAR-06) — see {@code 06-CONTEXT.md} for
 * the full design rationale each test below verifies.
 *
 * <p>Mirrors the rest of this project's GameTest idiom exactly — {@code helper.assertTrue}/{@code
 * assertFalse}/{@code succeed()}, no raw JUnit. Where a mechanic depends on real world ticks
 * (the periodic 40-tick check, the ALTAR-06 delayed smite), tests use {@code
 * helper.runAfterDelay}/{@code succeedWhen} rather than trying to invoke private handler methods
 * directly.
 */
@GameTestHolder(SecondShift.MODID)
@PrefixGameTestTemplate(false)
public final class EmployeeTraitsGameTests {

    private static final BlockPos ALTAR_POS = new BlockPos(4, 2, 4);

    private EmployeeTraitsGameTests() {}

    private static Villager bindEmployeeAt(GameTestHelper helper, BlockPos absAltarPos) {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 1, 1, 0.05F));
        return EmployeeManager.bind((net.minecraft.server.level.ServerLevel) helper.getLevel(),
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

    // --- EMP-03 / EMP-04: conversion immunity ---

    @GameTest(template = "empty")
    public static void employee_is_immune_to_zombie_conversion(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        helper.getLevel().addFreshEntity(employee);

        LivingConversionEvent.Pre event =
                new LivingConversionEvent.Pre(employee, EntityType.ZOMBIE_VILLAGER, ticks -> {});
        NeoForge.EVENT_BUS.post(event);

        helper.assertTrue(event.isCanceled(), "an employee's zombie conversion must be cancelled");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void employee_is_immune_to_witch_conversion(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        helper.getLevel().addFreshEntity(employee);

        LivingConversionEvent.Pre event =
                new LivingConversionEvent.Pre(employee, EntityType.WITCH, ticks -> {});
        NeoForge.EVENT_BUS.post(event);

        helper.assertTrue(event.isCanceled(), "an employee's witch conversion must be cancelled");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wild_villager_conversion_is_untouched(GameTestHelper helper) {
        Villager wild = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(wild != null, "villager creation must succeed");
        helper.getLevel().addFreshEntity(wild);

        LivingConversionEvent.Pre event =
                new LivingConversionEvent.Pre(wild, EntityType.ZOMBIE_VILLAGER, ticks -> {});
        NeoForge.EVENT_BUS.post(event);

        helper.assertFalse(event.isCanceled(), "EMP-09: a wild villager's conversion must never be touched by employee logic");
        helper.succeed();
    }

    // --- EMP-05: breeding prevention ---

    @GameTest(template = "empty")
    public static void employee_spawns_with_breeding_lock_age_and_cannot_breed(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);

        helper.assertTrue(employee.getAge() == EmployeeManager.BREEDING_LOCK_AGE,
                "bind() must set the breeding-lock age, got " + employee.getAge());
        helper.assertFalse(employee.canBreed(), "an employee at a positive age must never report canBreed() == true");
        helper.assertFalse(employee.isBaby(), "a positive age must never be mistaken for a baby");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breeding_lock_is_reasserted_after_decaying_below_the_floor(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        employee.setAge(500); // below the 1200-tick re-assert floor
        helper.getLevel().addFreshEntity(employee);

        // The periodic check runs every 40 ticks, keyed on the entity's own tickCount; give it a
        // full window plus margin.
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(employee.getAge() >= EmployeeManager.BREEDING_LOCK_AGE - 45,
                    "the periodic tick handler must re-assert the age back up once it decays below the floor, got "
                            + employee.getAge());
            helper.succeed();
        });
    }

    // --- EMP-07: the altar tether ---

    /**
     * Round-trip via a directly-posted {@code EntityTickEvent.Post} (same technique as the
     * conversion-immunity tests above) rather than {@code helper.runAfterDelay} — moving a test
     * entity 60+ blocks from the GameTest structure moved it out of the harness's own
     * loaded/ticking region, so it never received a real tick at all and the earlier version of
     * this test couldn't distinguish "correctly skipped" from "never ran". Posting the event
     * directly is deterministic and exercises the exact same handler.
     */
    @GameTest(template = "empty")
    public static void employee_far_past_the_hard_radius_is_teleported_home(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        employee.moveTo(absAltarPos.getX() + 60.0D, absAltarPos.getY(), absAltarPos.getZ(), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(employee);

        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(employee));

        double distSqr = employee.distanceToSqr(absAltarPos.getX() + 0.5D, absAltarPos.getY() + 2, absAltarPos.getZ() + 0.5D);
        helper.assertTrue(distSqr < 100.0D,
                "an employee past the hard tether radius must be teleported back near its altar, got distSqr=" + distSqr);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tether_is_skipped_when_the_altar_is_gone(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        // Deliberately do NOT place a Soul Altar at this position.
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        employee.moveTo(absAltarPos.getX() + 60.0D, absAltarPos.getY(), absAltarPos.getZ(), 0.0F, 0.0F);
        double startX = employee.getX();
        helper.getLevel().addFreshEntity(employee);

        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(employee));

        helper.assertTrue(Math.abs(employee.getX() - startX) < 5.0D,
                "with no real Soul Altar at the recorded position, the tether must not fire");
        helper.succeed();
    }

    // --- ECON-04: Harvester sneak-release ---

    @GameTest(template = "empty")
    public static void sneak_harvester_click_releases_the_employee(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        helper.getLevel().addFreshEntity(employee);
        SoulAltarBlockEntity be = setupBoundAltar(helper, absAltarPos, employee);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HARVESTER.get()));

        PlayerInteractEvent.EntityInteract event =
                new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, employee);
        NeoForge.EVENT_BUS.post(event);

        helper.assertTrue(event.isCanceled(), "the sneak-release gesture must cancel the normal trade-open interaction");
        helper.assertFalse(employee.isAlive(), "sneak + Harvester must instakill the employee, same as the reap");
        helper.assertFalse(be.isEmployeeBound(), "the altar must be released by the sneak-release path");

        List<ItemEntity> fragments = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(absAltarPos).inflate(6));
        long fragmentCount = fragments.stream().filter(e -> e.getItem().is(ModItems.SOUL_FRAGMENT.get())).count();
        helper.assertTrue(fragmentCount == 1, "sneak-release must yield exactly 1 Soul Fragment (ECON-04), got " + fragmentCount);
        helper.succeed();
    }

    // --- EMP-06: drop-recovery on every other death ---

    @GameTest(template = "empty")
    public static void non_harvester_death_drops_soul_block_and_slimeballs_and_releases_altar(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        helper.getLevel().addFreshEntity(employee);
        SoulAltarBlockEntity be = setupBoundAltar(helper, absAltarPos, employee);

        employee.hurt(helper.getLevel().damageSources().magic(), Float.MAX_VALUE);

        helper.assertFalse(employee.isAlive(), "the employee must actually die for this test to mean anything");
        helper.assertFalse(be.isEmployeeBound(), "a non-Harvester death must release the altar (EMP-06)");

        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(absAltarPos).inflate(6));
        long soulBlocks = drops.stream().filter(e -> e.getItem().is(ModItems.SOUL_BLOCK_ITEM.get())).count();
        long slimeballs = drops.stream().filter(e -> e.getItem().is(Items.SLIME_BALL)).mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(soulBlocks == 1, "a non-Harvester death must drop exactly 1 Soul Block, got " + soulBlocks);
        helper.assertTrue(slimeballs == 2, "a non-Harvester death must drop exactly 2 slimeballs, got " + slimeballs);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wild_villager_death_never_drops_recovery_items(GameTestHelper helper) {
        Villager wild = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(wild != null, "villager creation must succeed");
        wild.moveTo(0.5D, -60.0D, 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(wild);

        wild.hurt(helper.getLevel().damageSources().magic(), Float.MAX_VALUE);

        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(wild.blockPosition()).inflate(6));
        long soulBlocks = drops.stream().filter(e -> e.getItem().is(ModItems.SOUL_BLOCK_ITEM.get())).count();
        helper.assertTrue(soulBlocks == 0, "EMP-09: a wild villager's death must never drop a Soul Block, got " + soulBlocks);
        helper.succeed();
    }

    // --- ALTAR-06: altar destruction fires the employee ---

    @GameTest(template = "empty")
    public static void destroying_a_bound_altar_schedules_a_delayed_smite_that_drops_nothing(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager employee = bindEmployeeAt(helper, absAltarPos);
        helper.getLevel().addFreshEntity(employee);
        SoulAltarBlockEntity be = setupBoundAltar(helper, absAltarPos, employee);

        // GameTestHelper#destroyBlock has no player-aware overload (it calls Level#destroyBlock
        // with a null entity, which never reaches Block#playerWillDestroy at all) — call the real
        // production method directly, exactly as vanilla's own block-breaking pipeline would.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ModBlocks.SOUL_ALTAR.get().playerWillDestroy(helper.getLevel(), absAltarPos,
                helper.getLevel().getBlockState(absAltarPos), player);
        helper.getLevel().removeBlock(absAltarPos, false);

        helper.assertFalse(be.isEmployeeBound(), "playerWillDestroy must release the altar synchronously, before the delayed smite even fires");
        helper.assertTrue(employee.isAlive(), "the employee must still be alive immediately after the altar breaks — the smite is delayed");

        helper.runAfterDelay(EmployeeFiring.SMITE_DELAY_TICKS + 2, () -> {
            helper.assertFalse(employee.isAlive(), "the delayed smite must have killed the employee by now");
            helper.assertFalse(employee.hasData(ModAttachments.EMPLOYEE.get()),
                    "the attachment must be removed before the kill (so EMP-06 doesn't also fire)");

            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new net.minecraft.world.phys.AABB(absAltarPos).inflate(8));
            long soulBlocks = drops.stream().filter(e -> e.getItem().is(ModItems.SOUL_BLOCK_ITEM.get())).count();
            helper.assertTrue(soulBlocks == 0,
                    "ALTAR-06's firing kill must drop nothing (removing the attachment before death suppresses EMP-06), got "
                            + soulBlocks + " Soul Block(s)");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void empty_altar_break_does_not_schedule_a_smite(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get()); // empty, unbound — the pre-existing D-04 no-op path

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // No employee exists to check; this test's only job is confirming playerWillDestroy
        // doesn't throw for a completely empty/unbound altar now that its condition has an added
        // OR clause (widened from "holds a Soul Block" to "... OR has a bound employee").
        ModBlocks.SOUL_ALTAR.get().playerWillDestroy(helper.getLevel(), absAltarPos,
                helper.getLevel().getBlockState(absAltarPos), player);
        helper.succeed();
    }

    // --- D-01: the bidirectional link's identity guard ---

    @GameTest(template = "empty")
    public static void release_altar_does_not_clear_a_different_employees_link(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        SoulAltarBlockEntity be = (SoulAltarBlockEntity) helper.getLevel().getBlockEntity(absAltarPos);
        java.util.UUID realEmployeeId = java.util.UUID.randomUUID();
        be.setEmployeeBound(true);
        be.setEmployeeId(realEmployeeId);
        be.setChanged();

        EmployeeManager.releaseAltar((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                Optional.of(absAltarPos), java.util.UUID.randomUUID()); // a DIFFERENT, stale id

        helper.assertTrue(be.isEmployeeBound(), "a stale/mismatched employee id must never release an altar bound to a different, living employee");
        helper.assertTrue(realEmployeeId.equals(be.getEmployeeId()), "the real employee's link must be untouched");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void release_altar_with_a_null_stored_id_matches_any_employee(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        SoulAltarBlockEntity be = (SoulAltarBlockEntity) helper.getLevel().getBlockEntity(absAltarPos);
        be.setEmployeeBound(true);
        be.setEmployeeId(null); // pre-Phase-6 save shape
        be.setChanged();

        EmployeeManager.releaseAltar((net.minecraft.server.level.ServerLevel) helper.getLevel(),
                Optional.of(absAltarPos), java.util.UUID.randomUUID());

        helper.assertFalse(be.isEmployeeBound(), "a null stored id (pre-Phase-6 save) must release for any dying employee");
        helper.succeed();
    }
}
