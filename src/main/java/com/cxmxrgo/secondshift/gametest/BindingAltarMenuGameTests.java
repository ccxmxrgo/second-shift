package com.cxmxrgo.secondshift.gametest;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GUI-02 GameTest suite — round-12 redesign, replacing round-10/11's selection-toggle tests with
 * the "click a row, bind immediately" mechanic that comes from reusing vanilla's real enchanting
 * table menu (see {@link BindingAltarMenu}'s doc comment for the full rationale). Every trust-
 * boundary property earlier rounds' payload/menu code used to guard is still enforced inside
 * {@link BindingAltarMenu#clicked}, exercised here directly the way a real click packet would.
 *
 * <p>Mirrors {@link BindingAltarGameTests}'s idiom exactly — {@code helper.assertTrue}/{@code
 * assertFalse}/{@code succeed()}, no raw JUnit assertions.
 */
@GameTestHolder(SecondShift.MODID)
@PrefixGameTestTemplate(false)
public final class BindingAltarMenuGameTests {

    private static final BlockPos ALTAR_POS = new BlockPos(4, 2, 4);

    private BindingAltarMenuGameTests() {}

    private static SoulAltarBlockEntity setupFullySocketedAltar(GameTestHelper helper, BlockPos absAltarPos) {
        SoulAltarBlockEntity be = (SoulAltarBlockEntity) helper.getLevel().getBlockEntity(absAltarPos);
        be.setHeldJobItem(new ItemStack(Blocks.LECTERN.asItem()));
        be.setHeldSoulBlock(new ItemStack(ModItems.SOUL_BLOCK_ITEM.get()));
        be.setChanged();
        return be;
    }

    private static int employeeCount(GameTestHelper helper, BlockPos absAltarPos) {
        int count = 0;
        for (Villager villager : helper.getLevel().getEntitiesOfClass(Villager.class,
                new net.minecraft.world.phys.AABB(absAltarPos).inflate(6))) {
            if (villager.hasData(ModAttachments.EMPLOYEE.get())) {
                count++;
            }
        }
        return count;
    }

    @GameTest(template = "empty")
    public static void clicking_a_trade_row_binds_exactly_one_employee(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        helper.assertTrue(!menu.getDisplayedCandidates().isEmpty(),
                "a fully-socketed Librarian altar must roll at least one displayed candidate");

        menu.clicked(BindingAltarMenu.TRADE_SLOT_BASE, 0, ClickType.PICKUP, player);

        helper.assertTrue(employeeCount(helper, absAltarPos) == 1,
                "exactly one employee must exist after clicking row 0 once, got "
                        + employeeCount(helper, absAltarPos));
        helper.assertTrue(be.isEmployeeBound(), "the altar must be marked employeeBound after a successful bind");
        helper.assertTrue(be.isEmpty() && be.isJobItemEmpty(), "both sockets must be empty after a successful bind");
        helper.succeed();
    }

    /**
     * Double-confirm race (T-05-11 / ALTAR-05), re-verified against the round-12 immediate-bind
     * path: two rapid clicks on the same (or different) trade row must never spawn a second
     * employee — the atomic {@code employeeBound} guard inside {@code attemptBind} must reject the
     * second one, exactly like every prior round's equivalent test.
     */
    @GameTest(template = "empty")
    public static void double_click_does_not_spawn_a_second_employee(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        menu.clicked(BindingAltarMenu.TRADE_SLOT_BASE, 0, ClickType.PICKUP, player);
        menu.clicked(BindingAltarMenu.TRADE_SLOT_BASE, 0, ClickType.PICKUP, player);
        // Also try a second row, in case a naive implementation only guarded per-row rather than
        // altar-wide.
        if (menu.getDisplayedCandidates().size() > 1) {
            menu.clicked(BindingAltarMenu.TRADE_SLOT_BASE + 1, 0, ClickType.PICKUP, player);
        }

        helper.assertTrue(employeeCount(helper, absAltarPos) == 1,
                "exactly one employee must exist after multiple rapid clicks on the same altar, got "
                        + employeeCount(helper, absAltarPos));
        helper.assertTrue(be.isEmployeeBound(), "the altar must be marked employeeBound after the first successful bind");
        helper.succeed();
    }

    /**
     * Clicking a row beyond the real candidate count (an empty/inactive row, when the rolled pool
     * is smaller than {@link BindingAltarMenu#OPTION_COUNT}) must be a safe no-op — no bind, no
     * crash.
     */
    @GameTest(template = "empty")
    public static void clicking_an_empty_row_is_a_safe_no_op(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);
        // Force a 1-candidate pool (this also short-circuits the menu's own lazy roll — see
        // candidatesRolled()) so rows 1 and 2 are guaranteed empty, independent of any real
        // profession's actual tier-1 pool size.
        be.setCandidateOffers(java.util.List.of(new net.minecraft.world.item.trading.MerchantOffer(
                new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.EMERALD),
                new ItemStack(net.minecraft.world.item.Items.BREAD), 1, 1, 0.05F)));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        int emptyRow = menu.getDisplayedCandidates().size(); // first row past the real candidates
        helper.assertTrue(emptyRow < BindingAltarMenu.OPTION_COUNT,
                "expected a forced 1-candidate pool to leave an empty row, got "
                        + menu.getDisplayedCandidates().size() + " displayed candidates");

        menu.clicked(BindingAltarMenu.TRADE_SLOT_BASE + emptyRow, 0, ClickType.PICKUP, player);

        helper.assertFalse(be.isEmployeeBound(), "clicking an empty row must never bind an employee");
        helper.assertTrue(!be.isEmpty() && !be.isJobItemEmpty(), "both altar sockets must remain intact");
        helper.assertTrue(employeeCount(helper, absAltarPos) == 0,
                "no employee must spawn from clicking an empty row, got " + employeeCount(helper, absAltarPos));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void quick_move_stack_is_always_a_no_op(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);

        helper.assertTrue(menu.quickMoveStack(player, BindingAltarMenu.TRADE_SLOT_BASE).isEmpty(),
                "D-06: shift-clicking a trade row in this read-only menu must never move a real item");
        helper.succeed();
    }

    /** clickMenuButton is deliberately disabled (round-12) — vanilla's mouseClicked would try it
     * first, and it must always fall through to normal slot-click routing instead. */
    @GameTest(template = "empty")
    public static void click_menu_button_is_always_disabled(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);

        helper.assertFalse(menu.clickMenuButton(player, 0),
                "clickMenuButton must always return false so EnchantmentScreen's mouseClicked falls through to normal slot clicks");
        helper.succeed();
    }
}
