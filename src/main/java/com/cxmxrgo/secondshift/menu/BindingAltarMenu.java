package com.cxmxrgo.secondshift.menu;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.employee.EmployeeNames;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModMenus;
import com.cxmxrgo.secondshift.trade.ProfessionResolver;
import com.cxmxrgo.secondshift.trade.TradePoolCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;
import java.util.Optional;

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

    /**
     * Round-6 UI redesign (2026-09-07, per user-provided mockup): Confirm button now sits at the
     * very top of the screen, the Soul Block + profession-item sockets sit side by side below it,
     * the scrollable trade list sits to the right of those two sockets at the same height, and the
     * profession name / Happiness placeholder sit in the narrow column directly under the two
     * sockets. These constants are the single source of truth shared with {@code
     * BindingAltarScreen} so the two files never drift out of sync again (the exact bug this
     * comment replaces — see the prior INVENTORY_Y_SHIFT-only approach's git history).
     */
    public static final int SOUL_SLOT_X = 8;
    public static final int SOUL_SLOT_Y = 32;
    public static final int JOB_SLOT_X = 30;
    public static final int JOB_SLOT_Y = 32;
    public static final int INVENTORY_LABEL_Y = 76;
    public static final int INVENTORY_ROW1_Y = 86;
    public static final int INVENTORY_ROW_HEIGHT = 18;
    public static final int INVENTORY_HOTBAR_Y = 144;

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

        AltarSoulContainer soulContainer = new AltarSoulContainer(playerInv.player.level(), pos);
        this.addSlot(new SoulSlot(soulContainer, 0, SOUL_SLOT_X, SOUL_SLOT_Y));

        AltarJobItemContainer jobContainer = new AltarJobItemContainer(playerInv.player.level(), pos);
        this.addSlot(new SoulSlot(jobContainer, 0, JOB_SLOT_X, JOB_SLOT_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new net.minecraft.world.inventory.Slot(playerInv, col + row * 9 + 9, 8 + col * 18, INVENTORY_ROW1_Y + row * INVENTORY_ROW_HEIGHT));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new net.minecraft.world.inventory.Slot(playerInv, col, 8 + col * 18, INVENTORY_HOTBAR_Y));
        }

        // Plan 05-04 (PICK-02/04/07/08): one-time tier-1 candidate + default-name materialization.
        // Only the server-side branch ever rolls or reads real candidate data (threat T-05-06) —
        // the client-side menu construction never sees the BE at all in this code path.
        if (!playerInv.player.level().isClientSide()
                && playerInv.player.level().getBlockEntity(pos) instanceof SoulAltarBlockEntity be
                && !be.isEmployeeBound() && be.bothSocketsFilled() && !be.candidatesRolled()) {
            Optional<VillagerProfession> profession = ProfessionResolver.fromItem(be.getHeldJobItem());
            if (profession.isPresent() && playerInv.player.level() instanceof ServerLevel serverLevel) {
                be.setCandidateOffers(TradePoolCache.rollTier1Candidates(serverLevel, pos, profession.get()));
                be.setDefaultName(EmployeeNames.pickRandom(serverLevel.getRandom()));
            } else {
                // Defensive — should be unreachable given bothSocketsFilled implies a valid job
                // item was accepted at socket time. Still flips candidatesRolled() true so this
                // branch does not re-attempt on every reopen.
                be.setCandidateOffers(List.of());
            }
        }
    }

    /**
     * Plan 05-04 (GUI-03): re-resolves the block entity fresh on every call, mirroring {@code
     * AltarSoulContainer}'s "no caching" pattern — never caches the BE reference across calls.
     */
    private SoulAltarBlockEntity resolveBlockEntity() {
        return this.access.evaluate((level, pos) ->
                level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be ? be : null).orElse(null);
    }

    /** The materialized tier-1 candidate offers, or an empty list if not yet rolled/unresolvable. */
    public List<MerchantOffer> getCandidateOffers() {
        SoulAltarBlockEntity be = resolveBlockEntity();
        return be == null ? List.of() : be.getCandidateOffers();
    }

    /** {@code true} when the tier-1 pool has 2 or fewer candidates (auto-lock, still shows all). */
    public boolean isAutoLocked() {
        return getCandidateOffers().size() <= 2;
    }

    /** The profession resolved from the altar's socketed job item, or empty if unresolvable. */
    public Optional<VillagerProfession> getProfession() {
        SoulAltarBlockEntity be = resolveBlockEntity();
        return be == null ? Optional.empty() : ProfessionResolver.fromItem(be.getHeldJobItem());
    }

    /** Always 1 this phase; PROG-04's tier advancement is Phase 7. */
    public int getTier() {
        return 1;
    }

    /** The generated default employee name, or {@code ""} if unresolvable/not yet rolled. */
    public String getDefaultName() {
        SoulAltarBlockEntity be = resolveBlockEntity();
        if (be == null) {
            return "";
        }
        String name = be.getDefaultName();
        return name == null ? "" : name;
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
                if (level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be && be.isJobItemEmpty()) {
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

    /**
     * Plan 04-03 (T-4-01 mitigation): the server-side bind handler's ONLY source of the altar
     * position. Never trust a client-supplied position — re-derive it here, from this menu's own
     * {@link ContainerLevelAccess}, which was itself constructed server-side when the menu opened.
     */
    public ContainerLevelAccess access() {
        return this.access;
    }
}
