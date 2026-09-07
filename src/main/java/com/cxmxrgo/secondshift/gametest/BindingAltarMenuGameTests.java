package com.cxmxrgo.secondshift.gametest;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.menu.BindingAltarContainer;
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

import java.util.Set;

/**
 * GUI-02 GameTest suite — round-10 redesign replacement for the deleted {@code
 * ServerPayloadHandlerGameTests} (see {@link BindingAltarMenu}'s doc comment for the full
 * rationale). Every trust-boundary property the old {@code SelectTradesPayload}/{@code
 * ServerPayloadHandler} pair used to guard is now enforced inside {@link
 * BindingAltarMenu#clicked}, which runs via vanilla's own server-authoritative
 * {@code ServerboundContainerClickPacket} → {@code AbstractContainerMenu#clicked} path — these
 * tests exercise that method directly, exactly the way a real click packet would.
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

    // --- selection toggle ---

    @GameTest(template = "empty")
    public static void clicking_candidate_slot_toggles_selection(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        helper.assertTrue(menu.getCandidateOffers().size() > 2,
                "this test requires a non-auto-locked (>2 candidate) pool to exercise real toggling, got "
                        + menu.getCandidateOffers().size());

        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getSelectedIndices().contains(0), "clicking an unselected candidate slot must select it");

        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(!menu.getSelectedIndices().contains(0), "clicking an already-selected candidate slot must deselect it");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void selecting_a_third_candidate_is_rejected(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        helper.assertTrue(menu.getCandidateOffers().size() > 2,
                "this test requires a non-auto-locked (>2 candidate) pool, got " + menu.getCandidateOffers().size());

        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(1, 0, ClickType.PICKUP, player);
        menu.clicked(2, 0, ClickType.PICKUP, player);

        helper.assertTrue(menu.getSelectedIndices().equals(Set.of(0, 1)),
                "a third candidate click must be a no-op once 2 are already selected (PICK-03), got "
                        + menu.getSelectedIndices());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void small_pool_is_pre_selected_and_frozen(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        // Roll once, then force a 2-candidate (auto-locked) pool BEFORE constructing the menu —
        // the menu snapshots candidates from the BE once, at construction time (ChestMenu's slots
        // are fixed for the menu's lifetime), so mutating the BE after construction has no effect
        // on an already-open menu.
        new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        be.setCandidateOffers(be.getCandidateOffers().subList(0, 2));

        BindingAltarMenu menu = new BindingAltarMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        helper.assertTrue(menu.isAutoLocked(), "a 2-candidate pool must report auto-locked");
        helper.assertTrue(menu.getSelectedIndices().equals(Set.of(0, 1)),
                "an auto-locked pool must start pre-selected on both indices, got " + menu.getSelectedIndices());

        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getSelectedIndices().equals(Set.of(0, 1)),
                "clicking a candidate slot in an auto-locked pool must be a no-op, got " + menu.getSelectedIndices());
        helper.succeed();
    }

    // --- confirm / bind ---

    @GameTest(template = "empty")
    public static void confirm_with_two_selected_binds_exactly_one_employee(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        int n = Math.min(2, menu.getCandidateOffers().size());
        for (int i = 0; i < n; i++) {
            menu.clicked(i, 0, ClickType.PICKUP, player);
        }

        menu.clicked(BindingAltarContainer.CONFIRM_SLOT, 0, ClickType.PICKUP, player);
        // Double-confirm race (T-05-11 / ALTAR-05): a second rapid confirm must not spawn a
        // second employee — the atomic employeeBound guard inside attemptBind must reject it.
        menu.clicked(BindingAltarContainer.CONFIRM_SLOT, 0, ClickType.PICKUP, player);

        helper.assertTrue(employeeCount(helper, absAltarPos) == 1,
                "exactly one employee must exist after two rapid confirms against the same altar, got "
                        + employeeCount(helper, absAltarPos));
        helper.assertTrue(be.isEmployeeBound(), "the altar must be marked employeeBound after a successful bind");
        helper.assertTrue(be.isEmpty() && be.isJobItemEmpty(), "both sockets must be empty after a successful bind");
        helper.succeed();
    }

    /**
     * Round-3 regression (Phase 5 checkpoint debug, "employee never spawns" symptom), re-verified
     * against the round-10 menu-native confirm path: a confirm attempted with the wrong selection
     * count (0, on a >2-candidate pool) must NOT bind, must NOT consume the sockets, and must leave
     * the menu itself perfectly reusable — the player can select properly and retry.
     */
    @GameTest(template = "empty")
    public static void confirm_with_wrong_selection_count_does_not_bind(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        helper.assertTrue(menu.getCandidateOffers().size() > 2,
                "this regression requires a non-auto-locked (>2 candidate) pool, got "
                        + menu.getCandidateOffers().size());

        // Confirm clicked with nothing ever selected — the exact real-world trigger.
        menu.clicked(BindingAltarContainer.CONFIRM_SLOT, 0, ClickType.PICKUP, player);

        helper.assertFalse(be.isEmployeeBound(), "a rejected confirm must never bind an employee");
        helper.assertTrue(!be.isEmpty() && !be.isJobItemEmpty(), "both altar sockets must remain intact after a rejected confirm");
        helper.assertTrue(employeeCount(helper, absAltarPos) == 0,
                "no employee must spawn from a rejected confirm, got " + employeeCount(helper, absAltarPos));

        // And the player can now retry properly.
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(1, 0, ClickType.PICKUP, player);
        menu.clicked(BindingAltarContainer.CONFIRM_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(be.isEmployeeBound(), "a properly-selected retry after a rejected confirm must succeed");
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

        helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(),
                "D-06: shift-clicking any slot in this read-only menu must never move a real item");
        helper.succeed();
    }
}
