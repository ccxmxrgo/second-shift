package com.cxmxrgo.secondshift.registry;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Block-entity-type registry holder (ALTAR-01).
 *
 * <p>Only {@code secondshift:soul_altar} — the {@link SoulAltarBlockEntity} type, built
 * from {@code BlockEntityType.Builder.of(SoulAltarBlockEntity::new, ModBlocks.SOUL_ALTAR)}.
 *
 * <p>Created in plan 02-01 Task 1 (rather than Task 2 as the plan scheduled) because
 * {@link SoulAltarBlockEntity}'s constructor references {@link #SOUL_ALTAR_BE} and Task 1
 * would not compile otherwise. Task 2 adds the {@code .register(modBus)} wiring and the
 * {@code ModRegistrySelfCheck} coverage.
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SecondShift.MODID);

    public static final Supplier<BlockEntityType<SoulAltarBlockEntity>> SOUL_ALTAR_BE =
            BLOCK_ENTITIES.register("soul_altar",
                    () -> BlockEntityType.Builder.of(SoulAltarBlockEntity::new, ModBlocks.SOUL_ALTAR.get()).build(null));

    private ModBlockEntities() {}
}
