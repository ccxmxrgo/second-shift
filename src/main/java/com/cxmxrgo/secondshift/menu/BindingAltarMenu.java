package com.cxmxrgo.secondshift.menu;

import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Binding Altar container menu (D-05 / D-06 / D-16 / GUI-01) — the load-bearing half of the HARD
 * GATE. Owns the player inventory plus the altar's single display-only {@link SoulSlot}, and is
 * the {@code AbstractContainerMenu} the prior draft's unregistered {@code MenuType} crashed
 * before this ever compiled.
 *
 * <p>No trade/employee data, no shift-click routing (D-06) — those arrive in later phases. The
 * {@code player} field is stored now for Plan 02 Task 3's D-12 forced-close messaging; not used
 * yet.
 */
public class BindingAltarMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final Player player;

    /** Client ctor — bound by {@code IMenuTypeExtension.create(BindingAltarMenu::new)}. */
    public BindingAltarMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInv, extraData.readBlockPos());
    }

    /** Convenience ctor — server-side open with only the altar's position known. */
    public BindingAltarMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, ContainerLevelAccess.create(playerInv.player.level(), pos), pos);
    }

    /** Core ctor — both client and server ultimately land here. */
    public BindingAltarMenu(int containerId, Inventory playerInv, ContainerLevelAccess access, BlockPos pos) {
        super(ModMenus.BINDING_ALTAR.get(), containerId);
        this.access = access;
        this.player = playerInv.player;

        AltarSoulContainer container = new AltarSoulContainer(playerInv.player.level(), pos);
        this.addSlot(new SoulSlot(container, 0, 80, 35));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new net.minecraft.world.inventory.Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new net.minecraft.world.inventory.Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(this.access, player, ModBlocks.SOUL_ALTAR.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // display-only slot, no shift-click routing needed (D-06)
    }
}
