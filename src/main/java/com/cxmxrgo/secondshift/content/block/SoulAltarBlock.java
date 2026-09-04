package com.cxmxrgo.secondshift.content.block;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
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
 * Soul Altar (D-01 / D-02 / ALTAR-01) — an {@link EntityBlock} pedestal that sockets one
 * Soul Block.
 *
 * <p><b>Socket path authored in plan 02-04.</b> Socketing is one-way (D-03): right-click a
 * charged-empty altar holding a Soul Block consumes exactly one into the BE slot. No
 * empty-hand retrieval, no hopper/dropper access (the BE exposes no {@code Capability} /
 * {@code IItemHandler}).
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
     * D-03: one-way socket. Gate on {@code stack.is(SOUL_BLOCK_ITEM)} AND {@code be.isEmpty()};
     * mutate the BE only on the logical server; then {@code setChanged()} +
     * {@code sendBlockUpdated(...)} so the 02-05 renderer sees the charged state (PITFALLS §3).
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.is(ModItems.SOUL_BLOCK_ITEM.get()) || !be.isEmpty()) {
            // D-03: one-way — no overwrite, no retrieval, empty hand does nothing.
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
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
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** D-01 / D-03: no menu, no retrieval this phase. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return InteractionResult.PASS;
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
     * D-04 drop suppression. When the BE was broken while charged, the altar is lost
     * entirely — nothing drops (not the Soul Block, not the altar block). Otherwise defer to
     * the drops-self loot table (ALTAR-07, empty-altar path).
     *
     * <p>The {@code getOptionalParameter(BLOCK_ENTITY)} value is the same instance
     * {@code playerWillDestroy} flagged: the break pipeline captures it before block removal.
     */
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
