package com.cxmxrgo.secondshift;

import com.cxmxrgo.secondshift.registry.ModBlockEntities;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModCreativeTab;
import com.cxmxrgo.secondshift.registry.ModItems;
import com.cxmxrgo.secondshift.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

import java.util.List;

/**
 * Second Shift entrypoint.
 *
 * <p>The constructor is the single wiring point for the whole mod: every
 * {@code DeferredRegister} is attached to the mod bus here, in one visible block.
 * Keeping that wiring in one place is what structurally prevents the unbound-registry
 * crash that killed the prior draft.
 */
@Mod(SecondShift.MODID)
public class SecondShift {

    public static final String MODID = "secondshift";

    private static final Logger LOGGER = LogUtils.getLogger();

    public SecondShift(IEventBus modBus, ModContainer container) {
        LOGGER.info("[SecondShift] loading {} on NeoForge", container.getModInfo().getVersion());

        // Register every DeferredRegister on the mod bus here, in one visible block
        // (D-08/D-10). Each new registry/Mod* class MUST be added here and to
        // ModRegistrySelfCheck.
        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        ModMenus.MENUS.register(modBus);

        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            List<String> ids = ModItems.ITEMS.getEntries().stream()
                    .map(holder -> holder.getId().toString())
                    .sorted()
                    .toList();
            LOGGER.info("[SecondShift] common setup - {} item(s), {} block(s), {} block-entity type(s), {} creative tab(s) registered: {}",
                    ids.size(),
                    ModBlocks.BLOCKS.getEntries().size(),
                    ModBlockEntities.BLOCK_ENTITIES.getEntries().size(),
                    ModCreativeTab.TABS.getEntries().size(),
                    ids);
            LOGGER.info("[SecondShift] menu registered: {}",
                    BuiltInRegistries.MENU.getKey(ModMenus.BINDING_ALTAR.get()));
        });
    }
}
