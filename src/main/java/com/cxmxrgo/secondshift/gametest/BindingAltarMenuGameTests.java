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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GUI-02 GameTest suite — round-15 "career path" redesign: the player now picks one candidate
 * from the profession's full HIGHEST-tier pool, shown in a real scrollable list on the client and
 * routed to the server via vanilla's {@code clickMenuButton} RPC (see {@link BindingAltarMenu}'s
 * doc comment for the full rationale). Every trust-boundary property earlier rounds' payload/menu
 * code used to guard is still enforced inside {@link BindingAltarMenu#clickMenuButton}/{@link
 * BindingAltarMenu#clicked}, exercised here directly the way a real click/button packet would be.
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

    /**
     * Regression test (originally written for the fixed-3-row design, kept for the new
     * variable-count one): every reserved candidate slot up to {@link
     * BindingAltarMenu#MAX_CANDIDATE_SLOTS} must be reachable via {@code getSlot} without
     * throwing — {@code BindingAltarScreen}'s real client-side rendering scans exactly this range
     * to build its candidate list.
     */
    @GameTest(template = "empty")
    public static void every_reserved_candidate_slot_is_reachable(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);

        for (int i = 0; i < BindingAltarMenu.MAX_CANDIDATE_SLOTS; i++) {
            // Must not throw IndexOutOfBoundsException — that's the entire point of this test.
            menu.getSlot(BindingAltarMenu.CANDIDATE_SLOT_BASE + i);
        }
        helper.assertTrue(!menu.getDisplayedCandidates().isEmpty(),
                "a fully-socketed Librarian altar must roll at least one displayed candidate");
        helper.succeed();
    }

    /**
     * An Enchanted Book's real item name is always the generic "Enchanted Book" — vanilla only
     * ever shows WHICH enchantment as a separate tooltip line, never in the name itself. Forces a
     * synthetic enchanted-book candidate directly onto the BE (Librarian's own MAX-tier pool
     * doesn't happen to include one — see {@code VillagerTrades}, tier 5 is Name Tag only) to
     * assert {@link BindingAltarMenu} resolves the real enchantment for display regardless of
     * which profession/tier a book candidate actually came from.
     */
    @GameTest(template = "empty")
    public static void an_enchanted_book_candidate_shows_the_real_enchantment(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);

        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        net.minecraft.world.item.enchantment.ItemEnchantments.Mutable mutableEnchantments =
                new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                        net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        mutableEnchantments.set(helper.getLevel().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 3);
        book.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, mutableEnchantments.toImmutable());
        be.setCandidateOffers(java.util.List.of(new net.minecraft.world.item.trading.MerchantOffer(
                new net.minecraft.world.item.trading.ItemCost(Items.EMERALD), book, 1, 1, 0.05F)));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);

        String genericName = new ItemStack(Items.ENCHANTED_BOOK).getHoverName().getString();
        ItemStack candidate = menu.getSlot(BindingAltarMenu.CANDIDATE_SLOT_BASE).getItem();
        helper.assertFalse(candidate.getHoverName().getString().startsWith(genericName),
                "an Enchanted Book candidate's display name must show the real enchantment, not the generic '"
                        + genericName + "' name, got '" + candidate.getHoverName().getString() + "'");
        helper.succeed();
    }

    /** Baking the cost into the display name means every candidate's row text differs from just
     * its bare item name. */
    @GameTest(template = "empty")
    public static void candidate_display_name_includes_its_cost(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);

        ItemStack candidate = menu.getSlot(BindingAltarMenu.CANDIDATE_SLOT_BASE).getItem();
        String displayName = candidate.getHoverName().getString();
        helper.assertTrue(displayName.contains("(") && displayName.contains(")"),
                "a candidate's display name must bake in the cost (parenthesized), got '" + displayName + "'");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clicking_a_candidate_via_click_menu_button_binds_exactly_one_employee(GameTestHelper helper) {
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

        helper.assertTrue(menu.clickMenuButton(player, 0), "clickMenuButton must accept a valid candidate index");

        helper.assertTrue(employeeCount(helper, absAltarPos) == 1,
                "exactly one employee must exist after picking candidate 0, got " + employeeCount(helper, absAltarPos));
        helper.assertTrue(be.isEmployeeBound(), "the altar must be marked employeeBound after a successful bind");
        helper.assertTrue(be.isEmpty() && be.isJobItemEmpty(), "both sockets must be empty after a successful bind");
        helper.succeed();
    }

    /**
     * Double-confirm race (T-05-11 / ALTAR-05), re-verified against the round-15 career-path
     * bind path: two rapid picks (same or different candidate index) must never spawn a second
     * employee — the atomic {@code employeeBound} guard inside {@code attemptBind} must reject the
     * second one.
     */
    @GameTest(template = "empty")
    public static void double_pick_does_not_spawn_a_second_employee(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        menu.clickMenuButton(player, 0);
        menu.clickMenuButton(player, 0);
        if (menu.getDisplayedCandidates().size() > 1) {
            menu.clickMenuButton(player, 1);
        }

        helper.assertTrue(employeeCount(helper, absAltarPos) == 1,
                "exactly one employee must exist after multiple rapid picks on the same altar, got "
                        + employeeCount(helper, absAltarPos));
        helper.assertTrue(be.isEmployeeBound(), "the altar must be marked employeeBound after the first successful bind");
        helper.succeed();
    }

    /** An out-of-range candidate index (beyond the real rolled pool) must be a safe no-op — no
     * bind, no crash, and {@code clickMenuButton} reports it as unhandled ({@code false}). */
    @GameTest(template = "empty")
    public static void picking_an_out_of_range_index_is_a_safe_no_op(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        int outOfRange = BindingAltarMenu.MAX_CANDIDATE_SLOTS; // always beyond any real pool
        helper.assertFalse(menu.clickMenuButton(player, outOfRange),
                "an out-of-range candidate index must be reported as unhandled");

        helper.assertFalse(be.isEmployeeBound(), "an out-of-range pick must never bind an employee");
        helper.assertTrue(!be.isEmpty() && !be.isJobItemEmpty(), "both altar sockets must remain intact");
        helper.assertTrue(employeeCount(helper, absAltarPos) == 0,
                "no employee must spawn from an out-of-range pick, got " + employeeCount(helper, absAltarPos));
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

        helper.assertTrue(menu.quickMoveStack(player, BindingAltarMenu.CANDIDATE_SLOT_BASE).isEmpty(),
                "D-06: shift-clicking a candidate slot in this read-only menu must never move a real item");
        helper.succeed();
    }

    /** The candidate slots are locked against direct slot-click pickup too (defensive — they're
     * off-screen and unreachable via a real mouse click, but a forged packet could still target
     * them directly). */
    @GameTest(template = "empty")
    public static void candidate_slots_are_locked_against_direct_slot_clicks(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        menu.clicked(BindingAltarMenu.CANDIDATE_SLOT_BASE, 0, ClickType.PICKUP, player);

        helper.assertFalse(be.isEmployeeBound(), "a direct slot click on a candidate slot must never bind an employee");
        helper.assertTrue(menu.getCarried().isEmpty(), "a direct slot click on a candidate slot must never populate the cursor");
        helper.succeed();
    }

    /**
     * Regression test for the live-user-reported "empty pool" bug (see
     * .planning/debug/resolved/binding-altar-empty-pool.md): the actual root cause was purely
     * client-side (BindingAltarScreen#init() reading the client menu's candidate slots before the
     * separate container-content sync packet populates them — see that class's doc comment) and
     * is therefore not directly reproducible from a server-only GameTest (no real client
     * connection/screen exists in this harness). This test instead exercises the REAL two-click
     * socket-fill sequence through {@link com.cxmxrgo.secondshift.content.block.SoulAltarBlock
     * SoulAltarBlock}'s actual {@code useItemOn} interaction path (not the direct BE-setter
     * shortcut {@link #setupFullySocketedAltar} uses) for BOTH fill orders, confirming the
     * server-side roll this bug's investigation ruled out as the cause is — and stays — correct
     * end-to-end via the real interaction, not just the end state.
     */
    @GameTest(template = "empty")
    public static void real_two_click_soul_then_job_rolls_a_non_empty_pool(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.SOUL_BLOCK_ITEM.get()));
        helper.useBlock(ALTAR_POS, player);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(Blocks.LECTERN.asItem()));
        // The second click's socket-fill mutation (setHeldJobItem/setChanged) runs and completes
        // BEFORE SoulAltarBlock#openIfBothSocketsFilled's sp.openMenu(...) call — the roll itself
        // (inside BindingAltarMenu's constructor, invoked by createMenu()) also completes before
        // vanilla attempts to actually send the open-screen packet. Only that final packet-send
        // step fails here (GameTestHelper's mock player has no real client connection to receive
        // it) — harmless for what this test verifies (the real interaction's socket + roll state).
        try {
            helper.useBlock(ALTAR_POS, player);
        } catch (Exception expectedMockPlayerPacketSendFailure) {
            // Expected — see comment above. The BE mutation already happened synchronously.
        }

        helper.assertTrue(
                helper.getLevel().getBlockEntity(absAltarPos) instanceof SoulAltarBlockEntity be
                        && be.bothSocketsFilled() && be.candidatesRolled() && !be.getCandidateOffers().isEmpty(),
                "socketing Soul Block THEN job item via the real useItemOn path must roll a non-empty pool");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void real_two_click_job_then_soul_rolls_a_non_empty_pool(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(Blocks.LECTERN.asItem()));
        helper.useBlock(ALTAR_POS, player);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.SOUL_BLOCK_ITEM.get()));
        // See the mirror-order test above for why this is expected to throw on packet-send only,
        // after the real socket-fill + roll already completed synchronously.
        try {
            helper.useBlock(ALTAR_POS, player);
        } catch (Exception expectedMockPlayerPacketSendFailure) {
            // Expected — see comment above.
        }

        helper.assertTrue(
                helper.getLevel().getBlockEntity(absAltarPos) instanceof SoulAltarBlockEntity be
                        && be.bothSocketsFilled() && be.candidatesRolled() && !be.getCandidateOffers().isEmpty(),
                "socketing the job item THEN Soul Block via the real useItemOn path must roll a non-empty pool");
        helper.succeed();
    }

    // --- receipt slots (unchanged from the round-13 design) ---

    @GameTest(template = "empty")
    public static void receipt_slots_show_socketed_items_and_are_locked(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        helper.assertTrue(menu.getSlot(0).getItem().is(ModItems.SOUL_BLOCK_ITEM.get()),
                "receipt slot 0 must show a copy of the socketed Soul Block, got " + menu.getSlot(0).getItem());
        helper.assertTrue(menu.getSlot(1).getItem().is(Blocks.LECTERN.asItem()),
                "receipt slot 1 must show a copy of the socketed job item, got " + menu.getSlot(1).getItem());

        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(1, 1, ClickType.PICKUP, player);

        helper.assertTrue(menu.getSlot(0).getItem().is(ModItems.SOUL_BLOCK_ITEM.get()),
                "clicking receipt slot 0 must never remove its display item");
        helper.assertTrue(menu.getSlot(1).getItem().is(Blocks.LECTERN.asItem()),
                "clicking receipt slot 1 must never remove its display item");
        helper.assertTrue(menu.getCarried().isEmpty(), "locked receipt slots must never populate the cursor's carried item");
        helper.succeed();
    }

    /**
     * Guards against the exact duplication bug the receipt-slot design has to avoid: {@code
     * EnchantmentMenu.removed()} unconditionally drops whatever is in its "item to enchant"/"lapis"
     * slots back into the world on close — since those now hold COPIES of already-consumed altar
     * materials (not real held items), letting that run unmodified would duplicate them.
     */
    @GameTest(template = "empty")
    public static void closing_the_menu_does_not_duplicate_receipt_items(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);

        long itemEntitiesBefore = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(absAltarPos).inflate(8)).size();

        menu.removed(player);

        long itemEntitiesAfter = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(absAltarPos).inflate(8)).size();
        helper.assertTrue(itemEntitiesAfter == itemEntitiesBefore,
                "closing the menu must not drop the receipt slots' display copies as item entities, before="
                        + itemEntitiesBefore + " after=" + itemEntitiesAfter);
        helper.succeed();
    }
}
