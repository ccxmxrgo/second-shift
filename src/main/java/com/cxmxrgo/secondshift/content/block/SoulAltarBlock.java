package com.cxmxrgo.secondshift.content.block;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Soul Altar (D-01 / D-02 / ALTAR-01) — an {@link EntityBlock} pedestal that sockets one
 * Soul Block.
 *
 * <p><b>Skeleton for plan 02-01.</b> This class only wires the block entity and the
 * non-full-cube pedestal shape. The interaction / break contract below is authored in
 * <b>plan 02-04</b>. 1.21.1 signatures (RESEARCH Assumptions Log A4 — the compiler
 * catches a mismatch because every one carries {@code @Override}):
 *
 * <ul>
 *   <li>{@code protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
 *       Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)}
 *       — socket a Soul Block (D-03: one-way, gate on {@code stack.is(SOUL_BLOCK_ITEM)}
 *       AND {@code be.isEmpty()}; mutate BE only when {@code !level.isClientSide}; then
 *       {@code be.setChanged()} + {@code level.sendBlockUpdated(pos, state, state,
 *       Block.UPDATE_ALL)}; play {@code SoundEvents.SOUL_ESCAPE}).</li>
 *   <li>{@code protected InteractionResult useWithoutItem(BlockState state, Level level,
 *       BlockPos pos, Player player, BlockHitResult hit)} — return
 *       {@code InteractionResult.PASS} (no retrieval this phase, D-01/D-03).</li>
 *   <li>{@code public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState
 *       state, Player player)} — charged-altar break (D-04): visual-only
 *       {@code LightningBolt}, {@code player.hurt(damageSources().magic(), 1.0F)}, clear
 *       the BE stack, then {@code super.playerWillDestroy(...)}.</li>
 *   <li>{@code protected List<ItemStack> getDrops(BlockState state, LootParams.Builder
 *       params)} — read {@code params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)};
 *       return {@code List.of()} when the BE was charged, otherwise
 *       {@code super.getDrops(...)}. Structure it so the D-04 fallback ("altar still
 *       drops, only the Soul Block is lost") is a one-line change.</li>
 * </ul>
 */
public class SoulAltarBlock extends Block implements EntityBlock {

    // D-02: waist-high carved-stone pedestal — lectern / enchanting-table silhouette.
    // No facing property (RESEARCH Open Question 5, resolved): symmetric, single variant.
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
}
