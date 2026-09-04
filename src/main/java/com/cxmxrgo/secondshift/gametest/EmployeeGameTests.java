package com.cxmxrgo.secondshift.gametest;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.employee.EmployeeData;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

/**
 * EMP-01 / EMP-02 / EMP-08 / EMP-09 GameTest suite — {@link EmployeeManager#bind} spawn
 * correctness.
 *
 * <p>Mirrors {@link HarvesterGameTests} / {@link BindingAltarGameTests} idiom exactly:
 * {@code helper.assertTrue}/{@code assertFalse}, {@code helper.succeed()}, no raw JUnit.
 * All tests share the {@code secondshift:empty} structure and a fixed relative altar position.
 */
@GameTestHolder(SecondShift.MODID)
@PrefixGameTestTemplate(false)
public final class EmployeeGameTests {

    private static final BlockPos ALTAR_POS = new BlockPos(4, 2, 4);
    private static final BlockPos WILD_VILLAGER_POS = new BlockPos(1, 2, 1);
    private static final Set<VillagerProfession> BINDABLE_PROFESSIONS =
            Set.of(VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.CLERIC);

    private EmployeeGameTests() {}

    @GameTest(template = "empty")
    public static void bind_spawns_villager_with_employee_data(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        Villager villager = EmployeeManager.bind(helper.getLevel(), absAltarPos);

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

        Villager villager = EmployeeManager.bind(helper.getLevel(), absAltarPos);

        AABB jobSiteAabb = new AABB(jobSitePos);
        helper.assertFalse(villager.getBoundingBox().intersects(jobSiteAabb),
                "bound villager's bounding box must not overlap the job-site block at altarPos.above() (CR-01), "
                        + "villager bb=" + villager.getBoundingBox() + " jobSite bb=" + jobSiteAabb);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bind_sets_villager_xp_at_least_one(GameTestHelper helper) {
        Villager villager = EmployeeManager.bind(helper.getLevel(), helper.absolutePos(ALTAR_POS));

        helper.assertTrue(villager.getVillagerXp() >= 1,
                "bound villager must have villagerXp >= 1 so ResetProfession never fires (EMP-02)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bind_sets_green_always_visible_name(GameTestHelper helper) {
        Villager villager = EmployeeManager.bind(helper.getLevel(), helper.absolutePos(ALTAR_POS));

        Component name = villager.getCustomName();
        helper.assertTrue(name != null, "bound villager must have a custom name (EMP-08)");
        helper.assertTrue(name.getStyle().getColor() != null
                        && name.getStyle().getColor().getValue() == ChatFormatting.GREEN.getColor(),
                "bound villager's name must be styled GREEN (EMP-08)");
        helper.assertTrue(villager.isCustomNameVisible(),
                "bound villager's name must be always-visible (EMP-08)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bind_profession_is_one_of_three_fixed(GameTestHelper helper) {
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        for (int i = 0; i < 20; i++) {
            Villager villager = EmployeeManager.bind(helper.getLevel(), absAltarPos);
            VillagerProfession profession = villager.getVillagerData().getProfession();
            helper.assertTrue(BINDABLE_PROFESSIONS.contains(profession),
                    "bound profession must be one of FARMER/LIBRARIAN/CLERIC, got " + profession);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wild_villager_unaffected_by_bind_in_same_world(GameTestHelper helper) {
        Villager wild = helper.spawn(EntityType.VILLAGER, WILD_VILLAGER_POS);

        EmployeeManager.bind(helper.getLevel(), helper.absolutePos(ALTAR_POS));

        helper.assertFalse(wild.hasData(ModAttachments.EMPLOYEE.get()),
                "wild villager must not carry the EmployeeData attachment (EMP-09)");
        helper.assertTrue(wild.getCustomName() == null,
                "wild villager must not have a custom name (EMP-09)");
        helper.assertTrue(wild.getVillagerXp() == 0,
                "wild villager's XP must be untouched by bind (EMP-09)");
        helper.succeed();
    }
}
