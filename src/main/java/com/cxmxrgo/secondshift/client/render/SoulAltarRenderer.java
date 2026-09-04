package com.cxmxrgo.secondshift.client.render;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Charged Soul Altar renderer (D-05 / POL-03) — a client-only
 * {@link BlockEntityRenderer} that draws the socketed Soul Block embedded and
 * glowing in the altar top, eye-of-ender style.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@code be.isEmpty()} -&gt; render nothing extra (an empty altar is just the pedestal model).</li>
 *   <li>charged -&gt; render the {@code secondshift:soul_block} model recessed into the altar top
 *       plate at {@link LightTexture#FULL_BRIGHT} (emissive), scaled down so it reads as embedded,
 *       with <b>no</b> time-based rotation or bob (D-05).</li>
 *   <li>a slow, sparse {@link ParticleTypes#SOUL} wisp rises from just above the embedded block
 *       (throttled by a {@link RandomSource} roll so it stays occasional).</li>
 * </ul>
 *
 * <p><b>Client-class isolation (PITFALLS §9):</b> this class and everything it imports
 * ({@link PoseStack}, {@link MultiBufferSource}, {@link Minecraft}, {@link BlockEntityRenderer})
 * must never be referenced from {@code content/}, {@code event/}, or {@code registry/}. It is
 * registered only from {@code ClientModBusEvents.onRegisterRenderers}
 * ({@code EntityRenderersEvent.RegisterRenderers}, {@code Dist.CLIENT} mod bus). The phase's
 * mandatory {@code ./gradlew runServer} is the runtime proof of no leak.
 */
public class SoulAltarRenderer implements BlockEntityRenderer<SoulAltarBlockEntity> {

    /** Horizontal centre of the altar column / top plate (VoxelShape is symmetric about 0.5). */
    private static final double CENTRE = 0.5D;
    /** Vertical placement of the embedded block's centre — sits in the altar top plate (y 11..14 / 16). */
    private static final double EMBED_Y = 0.78D;
    /** Scale of the embedded Soul Block — small enough to read as recessed, not a full block. */
    private static final float EMBED_SCALE = 0.5F;
    /** Average frames between soul wisps (RandomSource roll) — keeps the wisp slow + sparse (D-05). */
    private static final int WISP_ROLL = 50;

    @SuppressWarnings("unused") // ctx matches the BlockEntityRendererProvider::new reference
    public SoulAltarRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(SoulAltarBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (be.isEmpty()) {
            return; // empty altar renders nothing beyond the pedestal model
        }

        BlockState soulBlock = ModBlocks.SOUL_BLOCK.get().defaultBlockState();
        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();

        pose.pushPose();
        // Centre on the altar top, scale down, then recentre the [0,1] block model on the origin.
        pose.translate(CENTRE, EMBED_Y, CENTRE);
        pose.scale(EMBED_SCALE, EMBED_SCALE, EMBED_SCALE);
        pose.translate(-0.5D, -0.5D, -0.5D);
        // FULL_BRIGHT packed light -> emissive, eye-of-ender style even in shadow (D-05). No bob / no spin.
        blockRenderer.renderSingleBlock(soulBlock, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();

        emitWisp(be);
    }

    /** Slow, sparse upward soul wisp just above the embedded block. */
    private static void emitWisp(SoulAltarBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        RandomSource random = level.getRandom();
        if (random.nextInt(WISP_ROLL) != 0) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        double x = pos.getX() + CENTRE + (random.nextDouble() - 0.5D) * 0.15D;
        double y = pos.getY() + EMBED_Y + 0.2D;
        double z = pos.getZ() + CENTRE + (random.nextDouble() - 0.5D) * 0.15D;
        level.addParticle(ParticleTypes.SOUL, x, y, z, 0.0D, 0.015D, 0.0D);
    }
}
