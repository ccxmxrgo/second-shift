package com.cxmxrgo.secondshift.content.block;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
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
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
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
     * D-01 / D-03 / D-11: one-way socket, now gated on {@link ProfessionResolver} BEFORE the
     * socket mutation runs — an unmapped/missing job block never consumes the player's Soul
     * Block. Gate on {@code stack.is(SOUL_BLOCK_ITEM)} AND {@code be.isEmpty()}; mutate the BE
     * only on the logical server; then {@code setChanged()} + {@code sendBlockUpdated(...)} so
     * the 02-05 renderer sees the charged state (PITFALLS §3); finally open the Binding Altar
     * screen in the same click (D-01, ALTAR-03, SC1).
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.is(ModItems.SOUL_BLOCK_ITEM.get()) || !be.isEmpty()) {
            // D-03/D-04: one-way — no overwrite; already-charged falls through to useWithoutItem's
            // reopen gate (Pitfall 10).
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            if (ProfessionResolver.fromAbove(level, pos).isEmpty()) {
                // D-11: unmapped/missing job block above an empty altar — do NOT socket.
                player.displayClientMessage(Component.translatable("message.secondshift.altar.not_a_workstation"), true);
                return ItemInteractionResult.CONSUME;
            }
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
            if (player instanceof ServerPlayer sp) {
                sp.openMenu(be, buf -> buf.writeBlockPos(pos)); // D-01
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * D-02 / D-04 / D-11: the single reopen gate. Falls through from {@link #useItemOn} whenever
     * that method returns {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} (empty hand, or a Soul Block
     * on an already-charged altar — Pitfall 10), so the "is the altar charged + does the job
     * block map to a profession" gate check lives exactly once, here.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (be.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.secondshift.altar.no_soul_block"), true); // D-11
            }
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (ProfessionResolver.fromAbove(level, pos).isEmpty()) {
                // D-11: distinguish "no job block at all" from "wrong/unmapped block" above.
                String key = PoiTypes.forState(level.getBlockState(pos.above())).isEmpty()
                        ? "message.secondshift.altar.no_job_block"
                        : "message.secondshift.altar.not_a_workstation";
                player.displayClientMessage(Component.translatable(key), true);
                return InteractionResult.CONSUME;
            }
            if (player instanceof ServerPlayer sp) {
                sp.openMenu(be, buf -> buf.writeBlockPos(pos)); // D-02 reopen
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * D-04 charged-altar break. Runs server-side before the BE is removed, and has the
     * {@link Player} ref. If the altar holds a Soul Block: spawn a visual-only
     * {@link LightningBolt} (flash + thunder, no fire, no collateral), deal exactly half a
     * heart of armour-bypassing magic damage to the breaking player only, mark the BE so
     * {@link #getDrops} suppresses everything, and clear the socketed stack (the Soul Block
     * is destroyed).
     *
     * <p>ALTAR-06 (a charged-altar break ALSO instakilling the bound employee) is Phase 6 —
     * no {@code EmployeeData} attachment exists yet; do not add it here.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be && !be.isEmpty()) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(pos));
                bolt.setVisualOnly(true);                               // flash + thunder only — no fire, no damage
                level.addFreshEntity(bolt);
            }
            player.hurt(level.damageSources().magic(), 1.0F);           // half a heart, breaking player only
            be.markBrokenWhileCharged();                                // read by getDrops off this same BE instance
            be.setHeldSoulBlock(ItemStack.EMPTY);                       // the Soul Block is destroyed
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
