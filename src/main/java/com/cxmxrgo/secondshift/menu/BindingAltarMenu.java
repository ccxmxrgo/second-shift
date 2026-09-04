package com.cxmxrgo.secondshift.menu;

import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModMenus;
import com.cxmxrgo.secondshift.trade.ProfessionResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
 * {@code owningPlayer} field is stored now for Plan 02 Task 3's D-12 forced-close messaging; not
 * used yet. Named distinctly from {@link #stillValid(Player)}'s {@code player} parameter so the
 * two are never ambiguous within the same scope (WR-01).
 */
public class BindingAltarMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final Player owningPlayer;

    /** D-12: guards forced-close messaging so a menu that fails {@code stillValid} across
     * multiple ticks (before the client processes the close packet) sends exactly one
     * action-bar message, not one per failing tick. */
    private boolean forcedCloseMessageSent = false;

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
        this.owningPlayer = playerInv.player;

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

    /**
     * D-12 forced-close messaging: on the tick {@code stillValid} first flips to {@code false}
     * (altar broken, job block removed, or player > ~8 blocks away — SC4), send exactly one
     * themed action-bar message naming the reason before the container closes.
     */
    @Override
    public boolean stillValid(Player player) {
        boolean valid = AbstractContainerMenu.stillValid(this.access, player, ModBlocks.SOUL_ALTAR.get());
        if (!valid && !forcedCloseMessageSent && !player.level().isClientSide()) {
            String key = this.access.evaluate((level, pos) -> {
                if (!level.getBlockState(pos).is(ModBlocks.SOUL_ALTAR.get())) {
                    return "message.secondshift.altar.closed.altar_gone";
                }
                if (ProfessionResolver.fromAbove(level, pos).isEmpty()) {
                    return "message.secondshift.altar.closed.job_gone";
                }
                return "message.secondshift.altar.closed.too_far";
            }, "message.secondshift.altar.closed.altar_gone");
            player.displayClientMessage(Component.translatable(key), true);
            forcedCloseMessageSent = true;
        }
        return valid;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // display-only slot, no shift-click routing needed (D-06)
    }
}
