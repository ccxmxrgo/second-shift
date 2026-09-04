package com.cxmxrgo.secondshift.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Soul Block (D-13) — a real placeable block crafted from 4 Soul Fragments.
 *
 * <p>This class only owns the client-side ambient soul-wisp particles. The low light
 * level (~7) is set on the {@code BlockBehaviour.Properties} in {@code ModBlocks}, not
 * here. Blockstate / model / item model / loot table are authored in plan 02-03.
 *
 * <p>RESEARCH Assumptions Log A9 (resolved): the 1.21.1 ambient-particle hook is
 * {@code animateTick(BlockState, Level, BlockPos, RandomSource)} — verified against the
 * decompiled {@code net.minecraft.world.level.block.Block}.
 */
public class SoulBlock extends Block {

    public SoulBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(10) != 0) {
            return;
        }
        double x = pos.getX() + 0.2D + random.nextDouble() * 0.6D;
        double y = pos.getY() + 0.9D + random.nextDouble() * 0.3D;
        double z = pos.getZ() + 0.2D + random.nextDouble() * 0.6D;
        level.addParticle(ParticleTypes.SOUL, x, y, z, 0.0D, 0.02D + random.nextDouble() * 0.03D, 0.0D);
    }
}
