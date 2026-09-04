package com.cxmxrgo.secondshift.registry;

import com.cxmxrgo.secondshift.SecondShift;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * D-07 throwaway marker — its only job is to give the self-check something to bind
 * and to exercise the detach-a-register abort test. Delete when ModItems gets real
 * content in Phase 2. Do NOT add Harvester / Soul Fragment / Soul Block here.
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SecondShift.MODID);

    public static final DeferredItem<Item> DEBUG_MARKER =
            ITEMS.registerSimpleItem("debug_marker", new Item.Properties());

    private ModItems() {}
}
