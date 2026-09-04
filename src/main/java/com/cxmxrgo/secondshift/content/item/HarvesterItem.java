package com.cxmxrgo.secondshift.content.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Harvester scythe (ECON-01 / D-07 / D-08).
 *
 * <p>Deliberately a plain {@link Item} and <b>never</b> a {@code SwordItem}: the vanilla
 * sweep attack is innate to {@code SwordItem} instances only, so a non-sword item never
 * sweeps (D-07 "single target only"). All harvest behaviour — the villager instakill and
 * the guaranteed Soul Fragment drop — lives in {@code event/HarvesterEvents} (added in
 * plan 02-02), keyed on {@code DamageSource#getWeaponItem() instanceof HarvesterItem}.
 *
 * <p>Attack stats (~3-4 damage, no tier mining behaviour) come from a reused
 * {@code SwordItem.createAttributes(...)} component set on the {@code Item.Properties} in
 * {@code ModItems}; durability (250) is also set there.
 *
 * <p><b>Enchantability (RESEARCH Assumptions Log A3, resolved):</b> NeoForge 21.1.248 /
 * MC 1.21.1 has neither {@code Item.Properties#enchantable(int)} nor
 * {@code DataComponents.ENCHANTABLE} — both arrived in 1.21.2. A durability item is
 * already enchantable at a table ({@code Item#isEnchantable} is true when max stack size
 * is 1 and the stack has {@code MAX_DAMAGE}); overriding {@link #getEnchantmentValue()}
 * only raises the enchant <em>quality</em> to iron-tier so Unbreaking/Mending are
 * meaningful (D-07). This is not an attack override and does not enable the sweep.
 * The {@code ItemStack}-sensitive NeoForge overload is used (the no-arg vanilla one is
 * deprecated for removal).
 */
public class HarvesterItem extends Item {

    public HarvesterItem(Properties props) {
        super(props);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }
}
