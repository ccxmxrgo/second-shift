package com.cxmxrgo.secondshift.menu;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.employee.EmployeeData;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
import com.cxmxrgo.secondshift.registry.ModAttachments;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModMenus;
import com.cxmxrgo.secondshift.trade.TradePoolCache;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 7 (07-CONTEXT.md D-04/D-05): the Promotion Ritual — opened on a bound altar (empty-hand
 * right-click, see {@code SoulAltarBlock#useWithoutItem}) once its employee has out-leveled its
 * last officially installed tier ({@code EmployeeData.tier() < villager's vanilla level}).
 *
 * <p>Deliberately extends {@link EnchantmentMenu} for exactly the same reason {@link
 * BindingAltarMenu} does — free player-inventory slots at the enchanting-table texture's exact
 * coordinates, reused by {@code PromotionRitualScreen}. The two receipt slots (0, 1) are simply
 * never populated here (no socketed items to show for a promotion) and are locked the same way
 * {@code BindingAltarMenu} locks them.
 *
 * <p><b>"Pick 2" without a new network payload:</b> selection is entirely client-side
 * ({@code TradeCandidateList}'s toggle mode) — this menu's {@link #clickMenuButton} only ever
 * receives ONE call, the Confirm button's, with the two chosen indices packed into a single int
 * via {@link #encodeConfirm} (decoded inline in {@code clickMenuButton}). No new {@code
 * CustomPacketPayload} needed, matching {@code BindingAltarMenu}'s own established precedent.
 */
public class PromotionRitualMenu extends EnchantmentMenu {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Same reservation shape as {@link BindingAltarMenu#MAX_CANDIDATE_SLOTS} — a single tier's
     * pool is always small, but this stays generous for the same forward-compatibility reason. */
    public static final int MAX_CANDIDATE_SLOTS = 24;
    public static final int CANDIDATE_SLOT_BASE = 38;

    /** Index-packing base for {@link #encodeConfirm}/{@link #decodeConfirm} — {@code
     * MAX_CANDIDATE_SLOTS} (24) comfortably fits under 32, and 31 is reserved as the
     * "no second pick" sentinel for a 1-pick ritual. */
    private static final int PACK_BASE = 32;
    private static final int NO_SECOND_PICK = 31;

    private final ContainerLevelAccess access;
    private final Container candidateSlots = new SimpleContainer(MAX_CANDIDATE_SLOTS);
    private final List<MerchantOffer> tierPool;
    private final int pickCount;
    private final int targetTier;
    private boolean forcedCloseMessageSent = false;

    @Override
    public MenuType<?> getType() {
        return ModMenus.PROMOTION_RITUAL.get();
    }

    public PromotionRitualMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInv, extraData.readBlockPos());
    }

    public PromotionRitualMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, ContainerLevelAccess.create(playerInv.player.level(), pos), pos);
    }

    public PromotionRitualMenu(int containerId, Inventory playerInv, ContainerLevelAccess access, BlockPos pos) {
        super(containerId, playerInv, access);
        this.access = access;

        RollResult roll = rollForPromotion(playerInv, pos);
        this.tierPool = roll.pool;
        this.targetTier = roll.targetTier;
        this.pickCount = Math.min(2, Math.max(this.tierPool.size(), 1));

        if (this.slots.size() != CANDIDATE_SLOT_BASE) {
            throw new IllegalStateException("EnchantmentMenu's slot layout changed — expected "
                    + CANDIDATE_SLOT_BASE + " slots before the candidate slots, found " + this.slots.size());
        }

        for (int i = 0; i < MAX_CANDIDATE_SLOTS; i++) {
            ItemStack display = i < this.tierPool.size()
                    ? buildCandidateDisplay(this.tierPool.get(i))
                    : ItemStack.EMPTY;
            candidateSlots.setItem(i, display);
            this.addSlot(new Slot(candidateSlots, i, -2000, -2000));
        }
    }

    private record RollResult(List<MerchantOffer> pool, int targetTier) {}

    /**
     * Server-side only: resolves the bound employee via {@code SoulAltarBlockEntity#getEmployeeId()}
     * (the same {@code ServerLevel#getEntity(UUID)} technique {@code EmployeeFiring} already uses),
     * and rolls its NEXT tier's pool (its live vanilla level — always {@code > data.tier()} by the
     * time {@code SoulAltarBlock} opens this menu at all). Returns an empty pool/tier 0 on the
     * client or on any resolution failure — defensive, should be unreachable given the caller's own
     * promotable check.
     */
    private static RollResult rollForPromotion(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be) || !be.isEmployeeBound()
                || be.getEmployeeId() == null) {
            return new RollResult(List.of(), 0);
        }
        if (!(serverLevel.getEntity(be.getEmployeeId()) instanceof Villager employee)
                || !employee.hasData(ModAttachments.EMPLOYEE.get())) {
            return new RollResult(List.of(), 0);
        }
        EmployeeData data = employee.getData(ModAttachments.EMPLOYEE.get());
        int vanillaLevel = employee.getVillagerData().getLevel();
        if (vanillaLevel <= data.tier()) {
            return new RollResult(List.of(), 0); // not actually promotable — defensive
        }
        VillagerProfession profession = employee.getVillagerData().getProfession();
        List<MerchantOffer> pool = TradePoolCache.rollCandidatesForTier(serverLevel, pos, profession, vanillaLevel);
        return new RollResult(pool, vanillaLevel);
    }

    private static ItemStack buildCandidateDisplay(MerchantOffer offer) {
        ItemStack display = offer.getResult().copy();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("gui.secondshift.binding_altar.cost_line",
                offer.getCostA().getCount(), offer.getCostA().getHoverName()));
        if (!offer.getCostB().isEmpty()) {
            lore.add(Component.translatable("gui.secondshift.binding_altar.cost_line",
                    offer.getCostB().getCount(), offer.getCostB().getHoverName()));
        }
        display.set(DataComponents.LORE, new ItemLore(lore));
        return display;
    }

    /** Packs one or two candidate indices into a single int for the Confirm button's {@link
     * #clickMenuButton} call — the second slot becomes {@link #NO_SECOND_PICK} when only one index
     * is given. Public so {@code PromotionRitualScreen} can call it without duplicating the
     * packing scheme or the sentinel value. */
    public static int encodeConfirm(Collection<Integer> indices) {
        Iterator<Integer> it = indices.iterator();
        int idxA = it.hasNext() ? it.next() : NO_SECOND_PICK;
        int idxB = it.hasNext() ? it.next() : NO_SECOND_PICK;
        return idxA * PACK_BASE + idxB;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        int idxA = id / PACK_BASE;
        int idxB = id % PACK_BASE;
        Set<Integer> chosen = new LinkedHashSet<>();
        if (idxA >= 0 && idxA < tierPool.size()) {
            chosen.add(idxA);
        }
        if (idxB != NO_SECOND_PICK && idxB >= 0 && idxB < tierPool.size()) {
            chosen.add(idxB);
        }
        if (chosen.isEmpty() || chosen.size() != pickCount) {
            return false; // malformed/short selection — never install a partial promotion
        }
        if (player instanceof ServerPlayer sp) {
            List<MerchantOffer> chosenOffers = chosen.stream().map(tierPool::get).toList();
            confirmPromotion(sp, chosenOffers);
        }
        return true;
    }

    private void confirmPromotion(ServerPlayer sp, List<MerchantOffer> chosenOffers) {
        if (!stillValid(sp)) {
            return;
        }
        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be) || !be.isEmployeeBound()
                    || be.getEmployeeId() == null || !(level instanceof ServerLevel serverLevel)) {
                return;
            }
            if (!(serverLevel.getEntity(be.getEmployeeId()) instanceof Villager employee)
                    || !employee.hasData(ModAttachments.EMPLOYEE.get())) {
                return;
            }
            EmployeeData data = employee.getData(ModAttachments.EMPLOYEE.get());
            int vanillaLevel = employee.getVillagerData().getLevel();
            if (vanillaLevel != targetTier || data.tier() >= targetTier) {
                return; // stale ritual (already promoted, or leveled again since this menu opened)
            }
            try {
                EmployeeManager.installPromotion(employee, data, targetTier, chosenOffers);

                // POL-05: promotion feedback — a brighter, more triumphant burst than the bind FX
                // (enchant-table sparkle + a level-up-flavored sound) at the employee's own
                // position, since this is about THEM advancing, not the altar itself.
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                        employee.getX(), employee.getY() + 1.0D, employee.getZ(), 30, 0.4D, 0.6D, 0.4D, 0.5D);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                        employee.getX(), employee.getY() + 1.8D, employee.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.0D);
                serverLevel.playSound(null, employee.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F, 1.4F);

                sp.displayClientMessage(Component.translatable(
                        "message.secondshift.employee.promoted", employee.getCustomName(), targetTier), true);
                sp.closeContainer();
            } catch (Exception e) {
                LOGGER.error("Promotion Ritual install failed for employee {} at altar {}",
                        be.getEmployeeId(), pos, e);
            }
        });
    }

    @Override
    public void slotsChanged(Container inventory) {
        // No-op — matches BindingAltarMenu; nothing here uses EnchantmentMenu's bookshelf scan.
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.QUICK_CRAFT) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (slotId == 0 || slotId == 1) {
            return; // unused receipt slots — locked
        }
        if (slotId >= CANDIDATE_SLOT_BASE && slotId < CANDIDATE_SLOT_BASE + MAX_CANDIDATE_SLOTS) {
            return; // off-screen, forged-packet-only
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void removed(Player player) {
        this.getSlot(0).set(ItemStack.EMPTY);
        this.getSlot(1).set(ItemStack.EMPTY);
        super.removed(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        boolean valid = AbstractContainerMenu.stillValid(this.access, player, ModBlocks.SOUL_ALTAR.get());
        if (!valid && !forcedCloseMessageSent && !player.level().isClientSide()) {
            player.displayClientMessage(
                    Component.translatable("message.secondshift.altar.closed.altar_gone"), true);
            forcedCloseMessageSent = true;
        }
        return valid;
    }

    // --- Screen-facing accessors ---

    public List<MerchantOffer> getTierPool() {
        return tierPool;
    }

    public int getPickCount() {
        return pickCount;
    }

    public int getTargetTier() {
        return targetTier;
    }
}
