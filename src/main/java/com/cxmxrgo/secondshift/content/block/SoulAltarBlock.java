package com.cxmxrgo.secondshift.content.block;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.employee.EmployeeData;
import com.cxmxrgo.secondshift.event.EmployeeFiring;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import com.cxmxrgo.secondshift.registry.ModItems;
import com.cxmxrgo.secondshift.trade.ProfessionResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Soul Altar (D-01 / D-02 / D-11 / ALTAR-01 / ALTAR-03) — an {@link EntityBlock} pedestal that
 * sockets one Soul Block and opens the Binding Altar screen.
 *
 * <p><b>Socket path authored in plan 02-04; open-trigger wiring authored in plan 03-02.</b>
 * Socketing is one-way (D-03): right-click an empty altar holding a Soul Block, with a
 * profession-mapped job block above, sockets exactly one Soul Block into the BE slot AND opens
 * the screen in the same click (D-01). Empty-hand right-click on an already-charged altar with a
 * mapped job block re-opens the screen (D-02). Every invalid path (no job block, unmapped block,
 * no Soul Block in hand on an empty altar) sends a themed action-bar message and never opens a
 * menu or crashes (D-11). No empty-hand retrieval, no hopper/dropper access (the BE exposes no
 * {@code Capability} / {@code IItemHandler}).
 *
 * <p>1.21.1 interaction signatures (RESEARCH Assumptions Log A4 — verified against the
 * decompiled {@code net.minecraft.world.level.block.state.BlockBehaviour}):
 * <ul>
 *   <li>{@code protected ItemInteractionResult useItemOn(ItemStack, BlockState, Level,
 *       BlockPos, Player, InteractionHand, BlockHitResult)} — the odd-one-out result type.</li>
 *   <li>{@code protected InteractionResult useWithoutItem(BlockState, Level, BlockPos,
 *       Player, BlockHitResult)} — returns {@code PASS} (no retrieval this phase).</li>
 * </ul>
 *
 * <p><b>Still authored in plan 02-04 Task 2:</b> {@code playerWillDestroy} (charged-altar
 * break: visual-only {@link net.minecraft.world.entity.LightningBolt}, {@code
 * player.hurt(magic(), 1.0F)}, clear the BE stack) and {@code getDrops} (empty list when
 * the BE was broken while charged, otherwise the drops-self loot table).
 */
public class SoulAltarBlock extends Block implements EntityBlock {

    // D-02: waist-high carved-stone pedestal — lectern / enchanting-table silhouette.
    // No facing property (RESEARCH Open Question 5, resolved): symmetric, single variant.
    // Keep in visual agreement with assets/secondshift/models/block/soul_altar.json.
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2.0D, 0.0D, 2.0D, 14.0D, 2.0D, 14.0D),   // base slab
            Block.box(5.0D, 2.0D, 5.0D, 11.0D, 11.0D, 11.0D),  // column
            Block.box(3.0D, 11.0D, 3.0D, 13.0D, 14.0D, 13.0D)  // top plate
    );

    public SoulAltarBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SoulAltarBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Plan 05-01 (G-2 / D-04 / ALTAR-05 / PICK-01 / PICK-08): dual item-socket interaction,
     * replacing the "place a real job-site block on top" mechanic. Branch order (server-only
     * mutation, client only mirrors via {@code sidedSuccess}):
     * <ol>
     *   <li>Occupied altar ({@code employeeBound}) — themed no-op, never touches a socket.</li>
     *   <li>Soul Block in hand + Soul Block slot empty — socket it (unchanged sound/particles).</li>
     *   <li>Job item in hand ({@link ProfessionResolver#fromItem} resolves) + job slot empty —
     *       socket it.</li>
     *   <li>An unmapped {@link BlockItem} — themed rejection, no socket mutation (PICK-08).</li>
     *   <li>Anything else — fall through to {@link #useWithoutItem}.</li>
     * </ol>
     * After either successful socket, opens the Binding Altar screen the instant both sockets are
     * filled, regardless of fill order.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (be.isEmployeeBound()) {
            // D-04/ALTAR-05: occupied altar — never mutates a socket regardless of held item.
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.secondshift.altar.occupied"), true);
            }
            return ItemInteractionResult.CONSUME;
        }

        if (stack.is(ModItems.SOUL_BLOCK_ITEM.get()) && be.isEmpty()) {
            if (!level.isClientSide) {
                be.setHeldSoulBlock(stack.copyWithCount(1));
                stack.consume(1, player);                                   // respects creative mode
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL); // push getUpdatePacket to trackers
                level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.8F, 1.0F);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SOUL,
                            pos.getX() + 0.5D, pos.getY() + 0.95D, pos.getZ() + 0.5D,
                            8, 0.18D, 0.05D, 0.18D, 0.01D);
                }
                openIfBothSocketsFilled(be, pos, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (ProfessionResolver.fromItem(stack).isPresent() && be.isJobItemEmpty()) {
            if (!level.isClientSide) {
                be.setHeldJobItem(stack.copyWithCount(1));
                stack.consume(1, player);
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
                openIfBothSocketsFilled(be, pos, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (stack.getItem() instanceof BlockItem && ProfessionResolver.fromItem(stack).isEmpty()) {
            // PICK-08: an invalid job item — reject, no socket mutation.
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.secondshift.altar.not_a_workstation"), true);
            }
            return ItemInteractionResult.CONSUME;
        }

        // Covers a Soul Block on an already-soul-socketed altar, or any other stack.
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Server-only: opens the Binding Altar screen the instant both sockets are filled. */
    private static void openIfBothSocketsFilled(SoulAltarBlockEntity be, BlockPos pos, Player player) {
        if (be.bothSocketsFilled() && player instanceof ServerPlayer sp) {
            sp.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
    }

    /**
     * Plan 05-01 (D-02 / D-04): the single reopen gate. Falls through from {@link #useItemOn}
     * whenever that method returns {@code PASS_TO_DEFAULT_BLOCK_INTERACTION}.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be)) {
            return InteractionResult.PASS;
        }

        if (be.isEmployeeBound()) {
            // Phase 7 (07-CONTEXT.md D-06): empty-hand right-click on a bound altar opens the
            // Promotion Ritual instead of the plain "occupied" message, IF the bound employee has
            // out-leveled its last officially installed tier. Holding any item keeps the plain
            // "occupied" behavior (useItemOn's own branch, unchanged) — this trigger is
            // empty-hand-only by design.
            if (!level.isClientSide && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp
                    && be.getEmployeeId() != null
                    && serverLevel.getEntity(be.getEmployeeId()) instanceof Villager employee
                    && employee.hasData(ModAttachments.EMPLOYEE.get())) {
                EmployeeData data = employee.getData(ModAttachments.EMPLOYEE.get());
                if (employee.getVillagerData().getLevel() > data.tier()) {
                    be.requestPromotionRitual();
                    sp.openMenu(be, buf -> buf.writeBlockPos(pos));
                    return InteractionResult.sidedSuccess(false);
                }
                player.displayClientMessage(Component.translatable("message.secondshift.altar.no_promotion_pending"), true);
                return InteractionResult.CONSUME;
            }
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.secondshift.altar.occupied"), true);
            }
            return InteractionResult.CONSUME;
        }

        if (be.bothSocketsFilled()) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                sp.openMenu(be, buf -> buf.writeBlockPos(pos)); // D-02 reopen
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            String key = be.isJobItemEmpty()
                    ? "message.secondshift.altar.no_job_block"
                    : "message.secondshift.altar.no_soul_block";
            player.displayClientMessage(Component.translatable(key), true);
        }
        return InteractionResult.PASS;
    }

    /**
     * D-04 / Phase 6 ALTAR-06 charged-altar break. Runs server-side before the BE is removed, and
     * has the {@link Player} ref. Fires whenever the altar holds a Soul Block OR has a bound
     * employee (widened from D-04's original "holds a Soul Block" — the Soul Block was already
     * consumed at bind time on a bound altar, so without this widening, breaking a bound altar
     * was completely free): spawn a visual-only {@link LightningBolt} (flash + thunder, no fire,
     * no collateral), deal exactly half a heart of armour-bypassing magic damage to the breaking
     * player only, mark the BE so {@link #getDrops} suppresses everything, clear the socketed
     * stack (the Soul Block is destroyed if present), and — if the altar had a bound employee —
     * schedule a second, delayed strike that instakills it (06-CONTEXT.md D-07).
     *
     * <p>The delay (see {@link EmployeeFiring#SMITE_DELAY_TICKS}) is real, not cosmetic sugar: it
     * is what makes the two strikes read as cause and effect rather than one event.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be
                && (!be.isEmpty() || be.isEmployeeBound())) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(pos));
                bolt.setVisualOnly(true);                               // flash + thunder only — no fire, no damage
                level.addFreshEntity(bolt);
            }
            player.hurt(level.damageSources().magic(), 1.0F);           // half a heart, breaking player only
            be.markBrokenWhileCharged();                                // read by getDrops off this same BE instance
            be.setHeldSoulBlock(ItemStack.EMPTY);                       // the Soul Block is destroyed, if present

            if (be.isEmployeeBound() && be.getEmployeeId() != null && level instanceof ServerLevel serverLevel) {
                EmployeeFiring.schedule(serverLevel, be.getEmployeeId());
            }
            be.setEmployeeBound(false);
            be.setEmployeeId(null);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * WR-03 safety net for non-player removal. {@link #playerWillDestroy} only runs for a
     * player break, so any other removal path (modded block breakers, {@code
     * Level.destroyBlock} from other mods, tooling) would drop the altar via the loot table
     * while silently voiding the socketed Soul Block held in the BE. Here we drop that stack
     * as an {@link net.minecraft.world.entity.item.ItemEntity} for every removal that is
     * <em>not</em> the deliberate D-04 charged-break suppression ({@code wasBrokenWhileCharged()}),
     * then clear it so a re-entrant removal cannot double-drop.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be
                && !be.isEmpty() && !be.wasBrokenWhileCharged()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.getHeldSoulBlock());
            be.setHeldSoulBlock(ItemStack.EMPTY);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * D-04 drop suppression. When the BE was broken while charged, the altar is lost
     * entirely — nothing drops (not the Soul Block, not the altar block). Otherwise defer to
     * the drops-self loot table (ALTAR-07, empty-altar path).
     *
     * <p>The {@code getOptionalParameter(BLOCK_ENTITY)} value is the same instance
     * {@code playerWillDestroy} flagged: the break pipeline captures it before block removal.
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof SoulAltarBlockEntity altar && altar.wasBrokenWhileCharged()) {
            // >>> D-04 FALLBACK: change the next line to `return super.getDrops(state, params);`
            // >>> to make a charged break drop the altar block (only the Soul Block is destroyed).
            return List.of();
        }
        return super.getDrops(state, params);
    }
}
