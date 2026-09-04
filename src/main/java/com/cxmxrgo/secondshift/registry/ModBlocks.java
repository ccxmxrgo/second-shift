package com.cxmxrgo.secondshift.registry;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.content.block.SoulAltarBlock;
import com.cxmxrgo.secondshift.content.block.SoulBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registry holder (ALTAR-01 / D-13).
 *
 * <ul>
 *   <li>{@code secondshift:soul_block} — {@link SoulBlock}, low light (7), soul-soil sound.</li>
 *   <li>{@code secondshift:soul_altar} — {@link SoulAltarBlock}, non-full-cube pedestal.</li>
 * </ul>
 *
 * <p>RESEARCH Assumptions Log A9 sibling (resolved): the 1.21.1 builder method is
 * {@code BlockBehaviour.Properties#lightLevel(ToIntFunction<BlockState>)} (it stores into
 * {@code lightEmission}) — verified against the decompiled {@code BlockBehaviour}.
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SecondShift.MODID);

    public static final DeferredBlock<SoulBlock> SOUL_BLOCK = BLOCKS.registerBlock(
            "soul_block",
            SoulBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(2.0F)
                    .lightLevel(state -> 7)
                    .sound(SoundType.SOUL_SOIL));

    public static final DeferredBlock<SoulAltarBlock> SOUL_ALTAR = BLOCKS.registerBlock(
            "soul_altar",
            SoulAltarBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.DEEPSLATE_BRICKS));

    private ModBlocks() {}
}
