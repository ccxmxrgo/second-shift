package com.cxmxrgo.secondshift.menu;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
import com.cxmxrgo.secondshift.employee.EmployeeNames;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModMenus;
import com.cxmxrgo.secondshift.trade.ProfessionResolver;
import com.cxmxrgo.secondshift.trade.TradePoolCache;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Binding Altar container menu — round-10 redesign (2026-09-08).
 *
 * <p><b>Why this exists:</b> rounds 6-9 built a fully custom {@code Screen} subclass with a
 * hand-generated background texture and hand-computed pixel coordinates for every element. Every
 * one of those rounds shipped a real, distinct bug (a title/button collision, a Java
 * constructor-init-order gotcha with {@code inventoryLabelY}, coordinate math that didn't account
 * for actual rendered proportions) despite each fix being individually verified. The custom-layout
 * approach was the common thread across all of them.
 *
 * <p>This redesign eliminates that entire class of risk by extending {@link ChestMenu} directly —
 * the exact same menu class every vanilla chest, barrel, and shulker box uses — and binding it to
 * vanilla's own {@code ContainerScreen} (see {@code ClientModBusEvents}). Zero custom rendering
 * code, zero custom texture, zero hand-computed pixel coordinates. The materialized trade
 * candidates are shown as real items in slots 0..N-1 (via {@link BindingAltarContainer}), selection
 * is toggled by clicking a candidate slot (shown via an enchantment-glint overlay — a real vanilla
 * per-item visual, not custom-drawn), and confirming is a click on the dedicated
 * {@link BindingAltarContainer#CONFIRM_SLOT}. All of this happens inside {@link #clicked}, which
 * runs via vanilla's own server-authoritative slot-click protocol
 * ({@code ServerboundContainerClickPacket}) — the same mechanism every container in the game
 * already uses securely, which is also why the {@code SelectTradesPayload}/{@code
 * ServerPayloadHandler} network layer and its index-validation logic (the exact code that caused
 * Bug D) no longer exist: there is no client-supplied index list to validate anymore.
 *
 * <p>The name field is still removed (per round-6's decision, D-03 superseded in 05-CONTEXT.md) —
 * the employee always gets its {@link EmployeeNames}-pool-generated default name. GUI-03's
 * "shows profession" requirement is satisfied via the container's title (see
 * {@link SoulAltarBlockEntity#getDisplayName()}), computed once at menu-open time from the
 * socketed job item — no custom text rendering needed.
 */
public class BindingAltarMenu extends ChestMenu {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ContainerLevelAccess access;
    private final BindingAltarContainer altarContainer;

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
        super(ModMenus.BINDING_ALTAR.get(), containerId, playerInv,
                resolveContainer(playerInv, access, pos), BindingAltarContainer.ROWS);
        this.access = access;
        this.altarContainer = (BindingAltarContainer) this.getContainer();
    }

    /**
     * Resolves (and, server-side only, rolls-once-and-persists — PICK-02/04/07/08, mirroring
     * Plan 05-04's original one-time materialization) the candidate trade pool, then builds the
     * backing {@link BindingAltarContainer}.
     *
     * <p>On the CLIENT, this always returns an empty-candidates container purely for correct
     * sizing — vanilla's own container-sync protocol ({@code ClientboundContainerSetContentPacket})
     * fills in the real item stacks immediately after open, exactly like any vanilla chest. The
     * client's own local candidate list is never used for anything authoritative.
     */
    private static BindingAltarContainer resolveContainer(Inventory playerInv, ContainerLevelAccess access, BlockPos pos) {
        List<MerchantOffer> candidates = List.of();
        Level level = playerInv.player.level();

        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be
                && !be.isEmployeeBound() && be.bothSocketsFilled()) {
            if (!be.candidatesRolled()) {
                Optional<VillagerProfession> profession = ProfessionResolver.fromItem(be.getHeldJobItem());
                if (profession.isPresent() && level instanceof ServerLevel serverLevel) {
                    be.setCandidateOffers(TradePoolCache.rollTier1Candidates(serverLevel, pos, profession.get()));
                    be.setDefaultName(EmployeeNames.pickRandom(serverLevel.getRandom()));
                } else {
                    // Defensive — should be unreachable given bothSocketsFilled implies a valid
                    // job item was accepted at socket time.
                    be.setCandidateOffers(List.of());
                }
            }
            candidates = be.getCandidateOffers();
        }

        Set<Integer> selected = new LinkedHashSet<>();
        if (candidates.size() <= 2) {
            // PICK-04 auto-lock: pre-select (and, via the clicked() guard below, freeze) every
            // candidate when the pool is too small to require a real choice.
            for (int i = 0; i < candidates.size(); i++) {
                selected.add(i);
            }
        }
        return new BindingAltarContainer(candidates, selected);
    }

    /**
     * Vanilla's own server-authoritative slot-click entry point (fires from
     * {@code ServerboundContainerClickPacket} — the same mechanism every container in the game
     * already uses). Candidate slots (0..N-1) toggle selection; {@link
     * BindingAltarContainer#CONFIRM_SLOT} attempts the bind; everything else (the player's own
     * inventory, added by {@link ChestMenu}'s constructor at indices &gt;= {@link
     * BindingAltarContainer#SIZE}) falls through to vanilla's default behavior.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        List<MerchantOffer> candidates = altarContainer.getCandidates();
        Set<Integer> selected = altarContainer.getSelected();
        boolean autoLocked = candidates.size() <= 2;

        if (slotId >= 0 && slotId < candidates.size()) {
            if (autoLocked) {
                return; // pre-selected and frozen — no-op click, matches D-04's "no-op, don't
                        // disable-gray-out" tone for locked state.
            }
            if (selected.contains(slotId)) {
                selected.remove(slotId);
                altarContainer.refreshCandidateDisplay(slotId);
            } else if (selected.size() < 2) {
                selected.add(slotId);
                altarContainer.refreshCandidateDisplay(slotId);
            } else if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.secondshift.altar.select_exactly_two"), true);
            }
            return;
        }

        if (slotId == BindingAltarContainer.CONFIRM_SLOT) {
            if (player instanceof ServerPlayer sp) {
                attemptBind(sp, candidates, selected, autoLocked);
            }
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    /**
     * The bind attempt — runs entirely server-side (only ever called with a {@link ServerPlayer}).
     * Preserves every safety property from the prior {@code ServerPayloadHandler} implementation:
     * the atomic occupancy guard first, consume-both-sockets-before-bind, {@code employeeBound} set
     * ONLY after a successful {@link EmployeeManager#bind} call (never before — a bind failure with
     * the flag already set would permanently soft-lock the altar), and a caught/logged failure path
     * that leaves the altar in a recoverable (if item-losing) state rather than crashing.
     */
    private void attemptBind(ServerPlayer sp, List<MerchantOffer> candidates, Set<Integer> selected, boolean autoLocked) {
        if (!autoLocked && selected.size() != 2) {
            sp.displayClientMessage(Component.translatable("message.secondshift.altar.select_exactly_two"), true);
            return;
        }
        if (!stillValid(sp)) {
            return;
        }

        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be)) {
                return; // altar gone
            }
            if (be.isEmployeeBound()) {
                return; // T-05-11: atomic occupancy guard, before any other check
            }
            if (be.isEmpty() || be.isJobItemEmpty()) {
                return; // nothing to bind, or a prior rapid confirm already consumed it
            }

            MerchantOffers chosen = new MerchantOffers();
            for (int i : selected) {
                chosen.add(candidates.get(i));
            }

            Optional<VillagerProfession> profession = ProfessionResolver.fromItem(be.getHeldJobItem());
            if (profession.isEmpty()) {
                return; // defensive — should be unreachable
            }
            String name = be.getDefaultName() == null ? "Employee" : be.getDefaultName();

            // Consume both sockets BEFORE calling bind. The sockets are consumed regardless of
            // bind's eventual outcome; there is no repair mechanic for a partially-applied bind,
            // so a bind failure is handled by explicit logging below, not by attempting to
            // restore the already-consumed items.
            be.setHeldSoulBlock(ItemStack.EMPTY);
            be.setHeldJobItem(ItemStack.EMPTY);
            be.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);

            if (level instanceof ServerLevel serverLevel) {
                try {
                    EmployeeManager.bind(serverLevel, pos, profession.get(), chosen, name);
                    // Set employeeBound only AFTER a successful bind, inside this same atomic
                    // lambda — never before (see class javadoc).
                    be.setEmployeeBound(true);
                    be.setChanged();
                    level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
                    sp.closeContainer();
                } catch (Exception e) {
                    LOGGER.error("EmployeeManager.bind failed for altar at {} — sockets already consumed, "
                            + "no employee spawned, employeeBound left false", pos, e);
                }
            }
        });
    }

    /** Read-only container — no shift-click routing in either direction (D-06). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
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

    // --- GUI-03 / test-facing accessors (kept for GameTest compatibility and API parity) ---

    /** The materialized tier-1 candidate offers, or an empty list if not yet rolled/unresolvable. */
    public List<MerchantOffer> getCandidateOffers() {
        return altarContainer.getCandidates();
    }

    /** {@code true} when the tier-1 pool has 2 or fewer candidates (auto-lock, still shows all). */
    public boolean isAutoLocked() {
        return altarContainer.getCandidates().size() <= 2;
    }

    /** The currently-selected candidate indices (mutable live view — do not cache). */
    public Set<Integer> getSelectedIndices() {
        return altarContainer.getSelected();
    }

    /** The profession resolved from the altar's socketed job item, or empty if unresolvable. */
    public Optional<VillagerProfession> getProfession() {
        return this.access.evaluate((level, pos) ->
                level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be
                        ? ProfessionResolver.fromItem(be.getHeldJobItem())
                        : Optional.<VillagerProfession>empty()).orElse(Optional.empty());
    }

    /** Always 1 this phase; PROG-04's tier advancement is Phase 7. */
    public int getTier() {
        return 1;
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
