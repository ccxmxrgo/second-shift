package com.cxmxrgo.secondshift.registry;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Menu-type registry holder (GUI-01 / D-14).
 *
 * <p>Only {@code secondshift:binding_altar} — built via
 * {@link IMenuTypeExtension#create(net.neoforged.neoforge.network.IContainerFactory)} so the
 * client ctor can read the altar's {@code BlockPos} out of the open-screen buffer. This is the
 * exact registry the prior draft never attached to the mod bus, crashing with "Trying to access
 * unbound value: ResourceKey[minecraft:menu / secondshift:binding_altar]" — see
 * {@code SecondShift}'s constructor and {@code ModRegistrySelfCheck} for the two-part structural
 * fix (register-in-constructor + guardrail coverage).
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SecondShift.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<BindingAltarMenu>> BINDING_ALTAR =
            MENUS.register("binding_altar",
                    () -> IMenuTypeExtension.create(BindingAltarMenu::new));

    private ModMenus() {}
}
