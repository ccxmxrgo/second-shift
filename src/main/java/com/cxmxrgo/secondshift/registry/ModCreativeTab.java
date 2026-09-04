package com.cxmxrgo.secondshift.registry;

import com.cxmxrgo.secondshift.SecondShift;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Creative-tab registry holder (POL-01 / D-14).
 *
 * <p>One tab, {@code secondshift:main} ("Second Shift"), populated via {@code displayItems}
 * with every mod object this milestone introduces. All four are registered by plan 02-01,
 * so the tab is fully populated now. The {@code BuildCreativeModeTabContentsEvent}
 * game-bus path is intentionally not used ({@code displayItems} alone is sufficient —
 * RESEARCH Assumptions Log A10).
 */
public final class ModCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SecondShift.MODID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.secondshift.main"))
            .icon(() -> new ItemStack(ModItems.SOUL_BLOCK_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.HARVESTER.get());
                output.accept(ModItems.SOUL_FRAGMENT.get());
                output.accept(ModItems.SOUL_BLOCK_ITEM.get());
                output.accept(ModBlocks.SOUL_ALTAR.get());
            })
            .build());

    private ModCreativeTab() {}
}
