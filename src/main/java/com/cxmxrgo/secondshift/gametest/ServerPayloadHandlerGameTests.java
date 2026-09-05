package com.cxmxrgo.secondshift.gametest;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.employee.EmployeeData;
import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import com.cxmxrgo.secondshift.network.ServerPayloadHandler;
import com.cxmxrgo.secondshift.network.SelectTradesPayload;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * GUI-02 GameTest suite — proves 05-RESEARCH.md Finding 3's full trust-boundary checklist against
 * {@link ServerPayloadHandler}'s package-... (now public, see Plan 05-05 SUMMARY) static helpers,
 * plus one end-to-end double-confirm race test.
 *
 * <p>Mirrors {@link BindingAltarGameTests}'s idiom exactly — {@code helper.assertTrue}/{@code
 * assertFalse}/{@code succeed()}, no raw JUnit assertions.
 */
@GameTestHolder(SecondShift.MODID)
@PrefixGameTestTemplate(false)
public final class ServerPayloadHandlerGameTests {

    private static final BlockPos ALTAR_POS = new BlockPos(4, 2, 4);

    private ServerPayloadHandlerGameTests() {}

    // --- validateIndices ---

    @GameTest(template = "empty")
    public static void validate_indices_rejects_out_of_range(GameTestHelper helper) {
        List<Integer> result = ServerPayloadHandler.validateIndices(new int[] {0, 5}, 3);
        helper.assertTrue(result == null, "an out-of-range index must reject the whole payload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void validate_indices_rejects_duplicate(GameTestHelper helper) {
        List<Integer> result = ServerPayloadHandler.validateIndices(new int[] {1, 1}, 3);
        helper.assertTrue(result == null, "a duplicate index must reject the whole payload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void validate_indices_rejects_more_than_two(GameTestHelper helper) {
        List<Integer> result = ServerPayloadHandler.validateIndices(new int[] {0, 1, 2}, 5);
        helper.assertTrue(result == null, "3 distinct valid indices against a >2 pool must reject (exceeds the 2-selection cap)");
        helper.succeed();
    }

    /**
     * Bug D regression (Phase 5 checkpoint debug): PICK-03 requires exactly 2 selections against a
     * pool bigger than 2 (not auto-locked). Before this fix, {@code validateIndices} only enforced
     * an upper bound, silently accepting 0 or 1 valid indices — which let a player confirm with no
     * trades selected (e.g. the Confirm button's default {@code new int[0]}) and spawn an employee
     * with an empty/near-empty {@code MerchantOffers}, which then fails vanilla
     * {@code Villager#mobInteract}'s {@code getOffers().isEmpty()} gate and never opens the trade
     * screen at all.
     */
    @GameTest(template = "empty")
    public static void validate_indices_rejects_fewer_than_two(GameTestHelper helper) {
        List<Integer> resultZero = ServerPayloadHandler.validateIndices(new int[] {}, 5);
        helper.assertTrue(resultZero == null,
                "0 selected indices against a >2 pool must reject (PICK-03 requires exactly 2), got " + resultZero);

        List<Integer> resultOne = ServerPayloadHandler.validateIndices(new int[] {2}, 5);
        helper.assertTrue(resultOne == null,
                "1 selected index against a >2 pool must reject (PICK-03 requires exactly 2), got " + resultOne);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void validate_indices_auto_locks_small_pool(GameTestHelper helper) {
        List<Integer> resultEmpty = ServerPayloadHandler.validateIndices(new int[] {}, 2);
        helper.assertTrue(resultEmpty != null && resultEmpty.size() == 2
                        && resultEmpty.contains(0) && resultEmpty.contains(1),
                "an empty selection against a 2-candidate pool must auto-lock to List.of(0, 1), got " + resultEmpty);

        List<Integer> resultPartial = ServerPayloadHandler.validateIndices(new int[] {1}, 2);
        helper.assertTrue(resultPartial != null && resultPartial.size() == 2
                        && resultPartial.contains(0) && resultPartial.contains(1),
                "a single-index selection against a 2-candidate pool must still auto-lock to both indices, got "
                        + resultPartial);
        helper.succeed();
    }

    // --- sanitizeName ---

    @GameTest(template = "empty")
    public static void sanitize_name_strips_formatting_and_control_chars(GameTestHelper helper) {
        String raw = "§cBadName";
        String result = ServerPayloadHandler.sanitizeName(raw, "Fallback");
        helper.assertTrue(result.equals("cBadName"),
                "sanitizeName must strip section-sign and control characters and trim, got '" + result + "'");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sanitize_name_falls_back_when_blank(GameTestHelper helper) {
        String result = ServerPayloadHandler.sanitizeName("   ", "Fallback");
        helper.assertTrue(result.equals("Fallback"),
                "sanitizeName must fall back to the default when the sanitized result is blank, got '" + result + "'");

        String resultStrippedToBlank = ServerPayloadHandler.sanitizeName("§  ", "Fallback");
        helper.assertTrue(resultStrippedToBlank.equals("Fallback"),
                "sanitizeName must fall back when only section-signs/control-chars/whitespace remain, got '"
                        + resultStrippedToBlank + "'");
        helper.succeed();
    }

    // --- end-to-end double-confirm race (T-05-11 / ALTAR-05) ---

    /** Minimal {@link IPayloadContext} stub — handleSelectTrades only ever calls {@code player()}. */
    private static final class FakeContext implements IPayloadContext {
        private final Player player;

        FakeContext(Player player) {
            this.player = player;
        }

        @Override
        public ICommonPacketListener listener() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Player player() {
            return player;
        }

        @Override
        public CompletableFuture<Void> enqueueWork(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<T> enqueueWork(java.util.function.Supplier<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PacketFlow flow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void handle(CustomPacketPayload payload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void finishCurrentTask(ConfigurationTask.Type type) {
            throw new UnsupportedOperationException();
        }
    }

    @GameTest(template = "empty")
    public static void double_confirm_spawns_exactly_one_employee(GameTestHelper helper) {
        helper.setBlock(ALTAR_POS, ModBlocks.SOUL_ALTAR.get());
        BlockPos absAltarPos = helper.absolutePos(ALTAR_POS);

        SoulAltarBlockEntity be = (SoulAltarBlockEntity) helper.getLevel().getBlockEntity(absAltarPos);
        be.setHeldJobItem(new ItemStack(Blocks.LECTERN.asItem()));
        be.setHeldSoulBlock(new ItemStack(ModItems.SOUL_BLOCK_ITEM.get()));
        be.setChanged();

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(absAltarPos.getX() + 0.5D, absAltarPos.getY(), absAltarPos.getZ() + 0.5D);

        BindingAltarMenu menu = new BindingAltarMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absAltarPos), absAltarPos);
        player.containerMenu = menu;

        int n = Math.min(2, menu.getCandidateOffers().size());
        int[] indices = java.util.stream.IntStream.range(0, n).toArray();
        SelectTradesPayload payload = new SelectTradesPayload(indices, "Double Confirm Test");
        FakeContext context = new FakeContext(player);

        ServerPayloadHandler.handleSelectTrades(payload, context);
        ServerPayloadHandler.handleSelectTrades(payload, context);

        int employeeCount = 0;
        for (Villager villager : helper.getLevel().getEntitiesOfClass(Villager.class,
                new net.minecraft.world.phys.AABB(absAltarPos).inflate(6))) {
            if (villager.hasData(ModAttachments.EMPLOYEE.get())) {
                employeeCount++;
            }
        }
        helper.assertTrue(employeeCount == 1,
                "exactly one employee must exist after two rapid confirms against the same altar, got "
                        + employeeCount);

        helper.assertTrue(be.isEmployeeBound(), "the altar must be marked employeeBound after a successful bind");
        helper.assertTrue(be.isEmpty() && be.isJobItemEmpty(),
                "both sockets must be empty after a successful bind");
        helper.succeed();
    }
}
