package com.cxmxrgo.secondshift.registry;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.content.item.HarvesterItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registry holder (ECON-01 / ECON-02 / POL-01).
 *
 * <p>Every real item this milestone introduces so far:
 * <ul>
 *   <li>{@code secondshift:harvester} — the scythe (custom {@link HarvesterItem}).</li>
 *   <li>{@code secondshift:soul_fragment} — the harvest drop + crafting ingredient.</li>
 *   <li>{@code secondshift:soul_block} — the {@code BlockItem} for {@link ModBlocks#SOUL_BLOCK}.</li>
 * </ul>
 *
 * <p>Forward-references {@link ModBlocks}; both registers attach from the same
 * {@code SecondShift} constructor block so the ordering is safe.
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SecondShift.MODID);

    // D-08: instakill is a handler decision, not a stat. The attack attribute only governs
    // the "modest ~3-4 damage" the Harvester deals to non-villagers. Tiers.STONE contributes
    // +1.0 attack-damage bonus, +2 here => 3 on the item, ~4 shown with the player base.
    // Durability 250 (D-07) also makes the item table-enchantable (Item#isEnchantable).
    public static final DeferredItem<HarvesterItem> HARVESTER = ITEMS.registerItem(
            "harvester",
            HarvesterItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .durability(250)
                    .attributes(SwordItem.createAttributes(Tiers.STONE, 2, -2.8F)));

    // D-12: subtle enchant-style foil so a dropped Fragment is easy to spot. No light.
    public static final DeferredItem<Item> SOUL_FRAGMENT = ITEMS.registerSimpleItem(
            "soul_fragment",
            new Item.Properties().component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

    // D-13: real placeable block. registerSimpleBlockItem wires the BlockItem to the block.
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SOUL_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("soul_block", ModBlocks.SOUL_BLOCK, new Item.Properties());

    private ModItems() {}
}
