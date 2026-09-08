package com.cxmxrgo.secondshift.gametest;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModItems;
import com.cxmxrgo.secondshift.trade.ProfessionResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * SC4 / ALTAR-03 / GUI-01 GameTest suite — the Binding Altar interaction loop's server-side
 * safety net.
 *
 * <p>Plan 05-01 (G-2) rework: every test that used to place a real job-site block above the
 * altar now sockets a job-item {@link ItemStack} via {@code helper.useBlock} instead, since
 * {@link BindingAltarMenu#stillValid} reads the job-item slot off the block entity rather than
 * the block above (D-04, ALTAR-05, PICK-01, PICK-08).
 *
 * <p>All tests share the {@code secondshift:empty} structure, mirroring {@link
 * HarvesterGameTests}'s structure and idiom exactly — {@code helper.assertTrue}/{@code
 * assertFalse}/{@code succeed()}, no raw JUnit assertions.
 */
@GameTestHolder(SecondShift.MODID)
@PrefixGameTestTemplate(false)
public final class BindingAltarGameTests {

    private static final BlockPos ALTAR_POS = new BlockPos(4, 2, 4);

    private BindingAltarGameTests() {}

    /** Sockets a job item (default: a cartography table) into the altar via a real interaction. */
    private static void socketJobItem(GameTestHelper helper, ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.CARTOGRAPHY_TABLE.asItem()));
        helper.useBlock(ALTAR_POS, player);
    }

    @GameTest(template = "empty")
    public static void binding_altar_menu_open_stillvalid_true(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());

        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        socketJobItem(helper, player);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);

        helper.assertTrue(menu.stillValid(player),
                "stillValid must be true while altar + job item + proximity are all valid");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void binding_altar_stillvalid_false_after_altar_break(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());

        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        socketJobItem(helper, player);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);

        helper.destroyBlock(ALTAR_POS);

        helper.assertFalse(menu.stillValid(player),
                "stillValid must be false after the altar block is broken (SC4)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void binding_altar_stillvalid_false_when_far(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());

        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        socketJobItem(helper, player);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);

        player.teleportTo(absAltarPos.getX() + 40.0D, absAltarPos.getY(), absAltarPos.getZ() + 40.0D);

        helper.assertFalse(menu.stillValid(player),
                "stillValid must be false once the player walks more than ~8 blocks away (SC4)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void binding_altar_no_job_block_no_menu_no_crash(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOUL_BLOCK_ITEM.get()));

        helper.useBlock(ALTAR_POS, player);

        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        helper.assertTrue(
                helper.getLevel().getBlockEntity(absAltarPos) instanceof SoulAltarBlockEntity be
                        && !be.isEmpty() && be.isJobItemEmpty(),
                "the Soul Block sockets but the altar stays unbound with no job item present (D-11/PICK-08)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void profession_resolver_from_item_maps_real_job_site(GameTestHelper helper) {
        Optional<VillagerProfession> resolved =
                ProfessionResolver.fromItem(new ItemStack(Blocks.CARTOGRAPHY_TABLE.asItem()));

        helper.assertTrue(resolved.equals(Optional.of(VillagerProfession.CARTOGRAPHER)),
                "a real job-site item must resolve to its mapped profession, got " + resolved);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void profession_resolver_from_item_ignores_non_poi_block(GameTestHelper helper) {
        Optional<VillagerProfession> resolved =
                ProfessionResolver.fromItem(new ItemStack(Blocks.STONE.asItem()));

        helper.assertTrue(resolved.isEmpty(),
                "a non-POI BlockItem must resolve to no profession, got " + resolved);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void binding_altar_occupied_altar_refuses_to_open(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());

        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        // Set up an already-bound altar directly on the BE (avoids exercising a real
        // openMenu packet against the mock player, which the test harness cannot deliver).
        helper.assertTrue(
                helper.getLevel().getBlockEntity(absAltarPos) instanceof SoulAltarBlockEntity,
                "altar must have a SoulAltarBlockEntity");
        SoulAltarBlockEntity be = (SoulAltarBlockEntity) helper.getLevel().getBlockEntity(absAltarPos);
        be.setHeldJobItem(new ItemStack(Blocks.CARTOGRAPHY_TABLE.asItem()));
        be.setHeldSoulBlock(new ItemStack(ModItems.SOUL_BLOCK_ITEM.get()));
        be.setEmployeeBound(true);
        be.setChanged();

        // No item in hand — exercises the useWithoutItem occupied-gate path.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.useBlock(ALTAR_POS, player);

        helper.assertTrue(!(player.containerMenu instanceof BindingAltarMenu),
                "an occupied altar must never open a BindingAltarMenu, regardless of interaction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void soul_altar_be_persists_job_item_and_occupancy(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);

        helper.assertTrue(
                helper.getLevel().getBlockEntity(absAltarPos) instanceof SoulAltarBlockEntity,
                "altar must have a SoulAltarBlockEntity");

        SoulAltarBlockEntity be = (SoulAltarBlockEntity) helper.getLevel().getBlockEntity(absAltarPos);
        be.setHeldJobItem(new ItemStack(Blocks.CARTOGRAPHY_TABLE.asItem()));
        be.setEmployeeBound(true);
        be.setChanged();

        CompoundTag saved = be.saveWithFullMetadata(helper.getLevel().registryAccess());

        SoulAltarBlockEntity reloaded = new SoulAltarBlockEntity(absAltarPos, be.getBlockState());
        reloaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        helper.assertTrue(!reloaded.isJobItemEmpty(),
                "reloaded BE must still hold the socketed job item after a save/load round trip");
        helper.assertTrue(reloaded.isEmployeeBound(),
                "reloaded BE must still be employee-bound after a save/load round trip");
        helper.succeed();
    }

    /**
     * Plan 05-04 (PICK-02/04/07/08, GUI-03): sets up a fully-socketed, unbound altar directly on
     * the BE (both sockets filled, not yet employee-bound) — avoids driving the real two-socket
     * interaction sequence, which triggers a real {@code openMenu} packet the GameTest mock player
     * cannot receive (same rationale as {@link #binding_altar_occupied_altar_refuses_to_open}).
     * Uses {@code Blocks.LECTERN} (Librarian job site) since Librarian has a real, >2-sized tier-1
     * pool.
     */
    private static SoulAltarBlockEntity setupFullySocketedAltar(GameTestHelper helper, BlockPos absAltarPos) {
        SoulAltarBlockEntity be = (SoulAltarBlockEntity) helper.getLevel().getBlockEntity(absAltarPos);
        be.setHeldJobItem(new ItemStack(Blocks.LECTERN.asItem()));
        be.setHeldSoulBlock(new ItemStack(ModItems.SOUL_BLOCK_ITEM.get()));
        be.setChanged();
        return be;
    }

    private static boolean offersEqual(MerchantOffer a, MerchantOffer b) {
        return ItemStack.matches(a.getResult(), b.getResult())
                && ItemStack.matches(a.getCostA(), b.getCostA())
                && ItemStack.matches(a.getCostB(), b.getCostB())
                && a.getMaxUses() == b.getMaxUses()
                && a.getXp() == b.getXp();
    }

    @GameTest(template = "empty")
    public static void binding_altar_menu_construction_rolls_candidates_and_default_name(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);

        helper.assertTrue(!be.getCandidateOffers().isEmpty(),
                "constructing the menu against a fully-socketed altar must roll a non-empty tier-1 pool");
        helper.assertTrue(be.getDefaultName() != null && !be.getDefaultName().isBlank(),
                "constructing the menu must also roll a non-blank default name");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void binding_altar_menu_reconstruction_does_not_reroll(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        List<MerchantOffer> firstRoll = List.copyOf(be.getCandidateOffers());

        // Second construction against the same BE/pos must not re-roll.
        new BindingAltarMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        List<MerchantOffer> secondRoll = be.getCandidateOffers();

        helper.assertTrue(firstRoll.size() == secondRoll.size(),
                "reopening the menu must not change the stored candidate list size");
        for (int i = 0; i < firstRoll.size(); i++) {
            helper.assertTrue(offersEqual(firstRoll.get(i), secondRoll.get(i)),
                    "reopening the menu must not change candidate list contents at index " + i);
        }
        helper.succeed();
    }

    /**
     * Round-12 note: {@link BindingAltarMenu} snapshots (and, when the real pool exceeds {@link
     * BindingAltarMenu#OPTION_COUNT}, shuffles-and-caps) its displayed candidate list from the
     * block entity once, at construction time — mirroring round-10's "menu snapshots once" note,
     * now with an explicit cap since the enchanting-table-style UI only ever shows 3 rows.
     */
    @GameTest(template = "empty")
    public static void binding_altar_displayed_candidates_capped_at_option_count(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        MerchantOffer offerA = new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 1, 1, 0.05F);
        MerchantOffer offerB = new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.PAPER), 1, 1, 0.05F);

        be.setCandidateOffers(List.of(offerA, offerB));
        BindingAltarMenu twoCandidateMenu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        helper.assertTrue(twoCandidateMenu.getDisplayedCandidates().size() == 2,
                "a 2-candidate real pool must show all 2, got " + twoCandidateMenu.getDisplayedCandidates().size());

        MerchantOffer offerC = new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BOOK), 1, 1, 0.05F);
        MerchantOffer offerD = new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.MAP), 1, 1, 0.05F);
        MerchantOffer offerE = new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.COMPASS), 1, 1, 0.05F);
        be.setCandidateOffers(List.of(offerA, offerB, offerC, offerD, offerE));
        BindingAltarMenu fiveCandidateMenu = new BindingAltarMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        helper.assertTrue(fiveCandidateMenu.getDisplayedCandidates().size() == BindingAltarMenu.OPTION_COUNT,
                "a 5-candidate real pool must be capped down to " + BindingAltarMenu.OPTION_COUNT + ", got "
                        + fiveCandidateMenu.getDisplayedCandidates().size());
        helper.succeed();
    }

    /**
     * Bug C investigation (Phase 5 checkpoint debug): exercises the REAL {@code
     * SoulAltarBlock#useItemOn} interaction path (mirrors {@link #socketJobItem}) with a Lectern
     * item specifically, instead of the direct BE field injection every other Lectern-based test
     * in this file uses ({@link #setupFullySocketedAltar}). Only {@code Blocks.CARTOGRAPHY_TABLE}
     * had ever been exercised through the real path before this test.
     */
    @GameTest(template = "empty")
    public static void binding_altar_real_interaction_sockets_lectern_job_item(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());

        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.LECTERN.asItem()));
        helper.useBlock(ALTAR_POS, player);

        helper.assertTrue(
                helper.getLevel().getBlockEntity(absAltarPos) instanceof SoulAltarBlockEntity be
                        && !be.isJobItemEmpty(),
                "a Lectern item right-clicked onto the altar must socket via the real useItemOn path, "
                        + "exactly like Blocks.CARTOGRAPHY_TABLE does");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void binding_altar_getprofession_and_gettier(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        setupFullySocketedAltar(helper, absAltarPos);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);

        helper.assertTrue(menu.getProfession().equals(Optional.of(VillagerProfession.LIBRARIAN)),
                "menu.getProfession() must resolve the Librarian profession from the socketed Lectern, got "
                        + menu.getProfession());
        helper.assertTrue(menu.getTier() == 1, "menu.getTier() must always be 1 this phase");
        helper.succeed();
    }

    /**
     * Round-3 checkpoint fix (Phase 5 checkpoint debug, "Nothing to Offer" for a Librarian bind):
     * {@code candidatesRolled()} treats any non-null {@code candidateOffers} — including an empty
     * {@code List.of()} — as "already rolled". Simulates a BE instance whose first-ever roll (for
     * whatever reason) landed on an empty list, then clears and re-fills a socket exactly like a
     * real unsocket/re-socket would, and confirms the altar rolls a genuinely fresh, non-stale,
     * non-empty pool afterward instead of staying stuck showing nothing forever.
     */
    @GameTest(template = "empty")
    public static void soul_altar_reset_candidates_on_socket_empty_allows_fresh_reroll(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);
        SoulAltarBlockEntity be = setupFullySocketedAltar(helper, absAltarPos);

        // Simulate a stale, permanently-stuck empty roll from an earlier session on this same BE.
        be.setCandidateOffers(List.of());
        be.setDefaultName("StaleName");
        helper.assertTrue(be.candidatesRolled(), "an empty list must still count as rolled (pre-fix behavior)");

        // Clear both sockets exactly like a real unsocket path would.
        be.setHeldJobItem(ItemStack.EMPTY);
        be.setHeldSoulBlock(ItemStack.EMPTY);
        helper.assertTrue(!be.candidatesRolled(),
                "clearing a socket must reset candidatesRolled() so a re-socketing always re-rolls");

        // Re-fill both sockets with a real Librarian job item and construct a fresh menu.
        be.setHeldJobItem(new ItemStack(Blocks.LECTERN.asItem()));
        be.setHeldSoulBlock(new ItemStack(ModItems.SOUL_BLOCK_ITEM.get()));
        be.setChanged();

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);

        helper.assertTrue(!be.getCandidateOffers().isEmpty(),
                "re-socketing after a socket-empty transition must roll a fresh, non-empty tier-1 pool, "
                        + "not reuse the stale empty roll");
        helper.assertTrue(be.getDefaultName() != null && !"StaleName".equals(be.getDefaultName()),
                "re-socketing must also roll a fresh default name, not reuse the stale one");
        helper.succeed();
    }
}
