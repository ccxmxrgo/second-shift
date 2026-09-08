package com.cxmxrgo.secondshift.menu;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
import com.cxmxrgo.secondshift.employee.EmployeeNames;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModMenus;
import com.cxmxrgo.secondshift.trade.ProfessionResolver;
import com.cxmxrgo.secondshift.trade.TradePoolCache;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
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
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Binding Altar container menu — reuses vanilla's real enchanting-table menu ({@link
 * EnchantmentMenu}) per the user's explicit request to make the altar "work with the enchanting
 * table GUI", instead of the generic {@code ChestMenu} used in an earlier redesign round.
 *
 * <p><b>Why extend {@code EnchantmentMenu} specifically, even though {@code BindingAltarScreen} no
 * longer extends its screen counterpart ({@code EnchantmentScreen}, dropped — see that class's
 * doc comment for why):</b> its constructor already adds exactly the two receipt slots (the "item
 * to enchant" / "lapis" slots at 15,47 and 35,47) at the precise coordinates the real
 * {@code enchanting_table.png} texture bakes those slot outlines into — reusing it is the cheapest
 * way to get those positions right without re-deriving them.
 *
 * <p><b>Round-15 redesign (user-requested "career path" mechanic):</b> instead of showing 3 rolled
 * tier-1 candidates with a Soul-Fragment-cost reroll, the picker shows a full listing pool in a
 * real scrollable list ({@code BindingAltarScreen}'s {@code TradeCandidateList} widget) instead of
 * a fixed 3-row layout. There is no reroll anymore: the whole pool is already visible via
 * scrolling, so there's nothing to reroll.
 *
 * <p><b>Phase 7 change (07-CONTEXT.md D-01):</b> this bind-time picker now rolls the profession's
 * TIER 1 pool (was: the HIGHEST tier's pool, with the chosen trade granted immediately as an
 * interim stopgap — see the round-15 comment this replaces, and the memory note on the original
 * "grant only at max tier" balancing idea). Now that Phase 7 builds real employee progression, the
 * honest version of that same idea is fully general rather than a special case: the employee
 * starts with a real, earned tier-1 trade chosen here, and {@code PromotionRitualMenu} re-opens
 * this exact same picker pattern at every LATER tier the employee actually earns through real
 * vanilla trade XP — including the profession's highest tier, which is never reachable except by
 * playing there. See {@code EmployeeManager#installPromotion} and {@code EmployeeEvents}'s
 * periodic tier check.
 *
 * <p><b>Why a scrollable list instead of driving vanilla's {@code costs}/{@code enchantClue}
 * button system:</b> that system is hardwired to a real enchantment-registry tooltip lookup (see
 * the {@link #clickMenuButton} doc) and fixed at exactly 3 options — neither fits an
 * open-ended, scrollable "show everything" list. Instead:
 * <ul>
 *   <li>Every rolled candidate gets a real, off-screen {@link Slot} (backed by {@link
 *   #candidateSlots}, added after the reserved {@link #MAX_CANDIDATE_SLOTS} count so client and
 *   server always agree on slot layout regardless of how many real candidates exist — see that
 *   field's doc). This reuses the exact same server-authoritative sync mechanism (vanilla's
 *   container-content sync calling {@code Slot#set}/{@code Container#setItem}) that already
 *   proved out for the fixed-3-row design, just decoupled from any specific on-screen position.</li>
 *   <li>The screen's {@code TradeCandidateList} widget reads those synced slots directly to build
 *   its visible rows (icon + name + cost, via this class's {@link #buildCandidateDisplay}), and
 *   routes a row click through {@code Minecraft#gameMode#handleInventoryButtonClick} — vanilla's
 *   own existing "non-slot button inside a container menu" RPC, arriving here as {@link
 *   #clickMenuButton}, which this class re-enables (with a real implementation) specifically for
 *   this purpose. No new network payload needed.</li>
 * </ul>
 */
public class BindingAltarMenu extends EnchantmentMenu {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * A generous fixed reservation for candidate-display slots, added after {@code
     * EnchantmentMenu}'s own 38 (2 input/lapis + 27 inventory + 9 hotbar). Client and server must
     * agree on the TOTAL slot count at construction time (before any network round-trip can tell
     * the client how many real candidates there are), so both always reserve exactly this many —
     * unused ones just hold {@code ItemStack.EMPTY}. Vanilla professions' single-tier pools are
     * small (2-3 real listings is typical, per {@code VillagerTrades}); this is set well above
     * that to comfortably absorb whatever other mods' {@code VillagerTradesEvent} listeners might
     * add to the pool. Public so {@code BindingAltarScreen} knows how many slots to scan when
     * building its candidate list.
     */
    public static final int MAX_CANDIDATE_SLOTS = 24;

    /** The slot index of candidate 0 — right after {@code EnchantmentMenu}'s own 38 slots.
     * Verified against the decompiled {@code EnchantmentMenu} constructor and defensively
     * asserted in this class's own constructor below. */
    public static final int CANDIDATE_SLOT_BASE = 38;

    private final ContainerLevelAccess access;
    private final Container candidateSlots = new SimpleContainer(MAX_CANDIDATE_SLOTS);
    private final List<MerchantOffer> displayedCandidates;

    /** D-12: guards forced-close messaging so a menu failing {@code stillValid} across multiple
     * ticks sends exactly one action-bar message, not one per failing tick. */
    private boolean forcedCloseMessageSent = false;

    /**
     * Overrides {@code AbstractContainerMenu}'s {@code getType()} (not {@code final} — verified
     * against the decompiled source). {@code EnchantmentMenu}'s constructor hardcodes {@code
     * super(MenuType.ENCHANTMENT, containerId)} with no way to inject a different {@code
     * MenuType}, which would otherwise make the open-screen packet select VANILLA's own
     * registered {@code EnchantmentScreen} instead of {@code BindingAltarScreen} — and, far worse,
     * registering {@code BindingAltarScreen} directly under {@code MenuType.ENCHANTMENT} to
     * compensate would hijack every real enchanting table in the world and crash the moment one
     * is opened (its menu is a plain {@code EnchantmentMenu}, not a {@code BindingAltarMenu}).
     * Overriding this getter instead redirects only OUR instances to {@code
     * ModMenus.BINDING_ALTAR}'s own registration, leaving {@code MenuType.ENCHANTMENT} and every
     * real enchanting table in the game completely untouched.
     */
    @Override
    public MenuType<?> getType() {
        return ModMenus.BINDING_ALTAR.get();
    }

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
        super(containerId, playerInv, access);
        this.access = access;
        this.displayedCandidates = resolveDisplayedCandidates(playerInv, pos);

        if (this.slots.size() != CANDIDATE_SLOT_BASE) {
            throw new IllegalStateException("EnchantmentMenu's slot layout changed — expected "
                    + CANDIDATE_SLOT_BASE + " slots before the candidate slots, found " + this.slots.size());
        }
        if (displayedCandidates.size() > MAX_CANDIDATE_SLOTS) {
            LOGGER.warn("Binding Altar rolled {} candidates, truncating to MAX_CANDIDATE_SLOTS={}",
                    displayedCandidates.size(), MAX_CANDIDATE_SLOTS);
        }

        // Round-13: the "item to enchant" / "lapis" slots become a locked, read-only receipt of
        // what's actually socketed on the altar below — never populated on the client (server-only
        // BE read; the real stacks sync to the client the same way the candidate slots do).
        if (!playerInv.player.level().isClientSide()
                && playerInv.player.level().getBlockEntity(pos) instanceof SoulAltarBlockEntity receiptBe) {
            this.getSlot(0).set(receiptBe.getHeldSoulBlock().copy());
            this.getSlot(1).set(receiptBe.getHeldJobItem().copy());
        }

        for (int i = 0; i < MAX_CANDIDATE_SLOTS; i++) {
            ItemStack display = i < displayedCandidates.size()
                    ? buildCandidateDisplay(displayedCandidates.get(i))
                    : ItemStack.EMPTY;
            candidateSlots.setItem(i, display);
            // Off-screen — these exist purely to sync candidate data to the client via vanilla's
            // normal slot-content sync; TradeCandidateList reads them to build its visible rows.
            this.addSlot(new Slot(candidateSlots, i, -2000, -2000));
        }
    }

    /**
     * Resolves (and, server-side only, rolls-once-and-persists — PICK-02/04/07/08) the candidate
     * trade pool from the profession's HIGHEST tier (the full "career path" set).
     *
     * <p>On the CLIENT, this always returns an empty list — vanilla's own container-sync protocol
     * fills in the real per-slot item stacks immediately after open, exactly like {@link
     * #CANDIDATE_SLOT_BASE}'s doc comment describes.
     */
    private static List<MerchantOffer> resolveDisplayedCandidates(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be)
                || be.isEmployeeBound() || !be.bothSocketsFilled()) {
            return List.of();
        }

        if (!be.candidatesRolled()) {
            Optional<VillagerProfession> profession = ProfessionResolver.fromItem(be.getHeldJobItem());
            if (profession.isPresent() && level instanceof ServerLevel serverLevel) {
                // Phase 7 (07-CONTEXT.md D-01): tier 1, not the profession's max tier — see this
                // class's doc comment for why the "grant only at max tier" idea generalizes cleanly
                // into the Promotion Ritual instead of a one-off gate on this bind-time roll.
                be.setCandidateOffers(TradePoolCache.rollCandidatesForTier(serverLevel, pos, profession.get(), 1));
                be.setDefaultName(EmployeeNames.pickRandom(serverLevel.getRandom()));
            } else {
                be.setCandidateOffers(List.of()); // defensive — should be unreachable
            }
        }

        List<MerchantOffer> all = be.getCandidateOffers();
        return all.size() <= MAX_CANDIDATE_SLOTS ? all : List.copyOf(all.subList(0, MAX_CANDIDATE_SLOTS));
    }

    /**
     * Bakes the trade's cost into the display item's own {@code CUSTOM_NAME} (read by {@code
     * BindingAltarScreen}'s {@code TradeCandidateList} row rendering) so the player never needs to
     * hover to see the price, and resolves an Enchanted Book's real enchantment name (see {@link
     * #resolveDisplayName}) instead of the generic "Enchanted Book".
     */
    private static ItemStack buildCandidateDisplay(MerchantOffer offer) {
        ItemStack display = offer.getResult().copy();

        Component costA = costLine(offer.getCostA());
        display.set(DataComponents.CUSTOM_NAME, Component.empty()
                .append(resolveDisplayName(display))
                .append(Component.literal(" ("))
                .append(costA)
                .append(Component.literal(")")));

        List<Component> lore = new ArrayList<>();
        lore.add(costA);
        if (!offer.getCostB().isEmpty()) {
            lore.add(costLine(offer.getCostB()));
        }
        lore.add(Component.empty());
        lore.add(Component.translatable("gui.secondshift.binding_altar.trade_bind_hint").withStyle(ChatFormatting.GREEN));
        display.set(DataComponents.LORE, new ItemLore(lore));
        return display;
    }

    private static Component costLine(ItemStack cost) {
        return Component.translatable("gui.secondshift.binding_altar.cost_line",
                cost.getCount(), cost.getHoverName()).withStyle(ChatFormatting.YELLOW);
    }

    /**
     * An Enchanted Book's (or any pre-enchanted item's) real item name is always the generic
     * "Enchanted Book" — the actual enchantment only ever shows as a separate tooltip line vanilla
     * adds via {@code ItemEnchantments}' own {@code TooltipProvider}, which a display item's own
     * {@code getHoverName()} never includes. Since a player picking between candidates needs to
     * know WHICH enchantment a given "Enchanted Book" row actually is without hovering, this reads
     * {@code DataComponents#STORED_ENCHANTMENTS} (books) / {@code ENCHANTMENTS} (already-applied,
     * for completeness) directly and builds the real "Sharpness III"-style name vanilla's own
     * {@link Enchantment#getFullname} produces — falling back to the plain item name for anything
     * unenchanted.
     */
    private static Component resolveDisplayName(ItemStack result) {
        ItemEnchantments stored = result.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) {
            return enchantmentsToName(stored);
        }
        ItemEnchantments applied = result.get(DataComponents.ENCHANTMENTS);
        if (applied != null && !applied.isEmpty()) {
            return enchantmentsToName(applied);
        }
        return result.getHoverName();
    }

    private static Component enchantmentsToName(ItemEnchantments enchantments) {
        MutableComponent combined = Component.empty();
        boolean first = true;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            if (!first) {
                combined.append(", ");
            }
            combined.append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
            first = false;
        }
        return combined;
    }

    /**
     * Round-15: re-enabled (was permanently disabled while the picker used real, on-screen,
     * clickable candidate slots) — {@code id} is a candidate index, sent by {@code
     * TradeCandidateList}'s row click handler via {@code Minecraft#gameMode#handleInventoryButtonClick},
     * vanilla's existing RPC for exactly this "non-slot button inside a container menu" case (the
     * same one {@code EnchantmentScreen} itself uses for its 3 real enchant options).
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= displayedCandidates.size()) {
            return false;
        }
        if (player instanceof ServerPlayer sp) {
            attemptBind(sp, displayedCandidates.get(id));
        }
        return true;
    }

    /** No-op: this altar has no real enchantment cost to compute, and nothing here uses {@code
     * EnchantmentMenu}'s own bookshelf-scanning cost calculation. Overridden specifically to stop
     * a player fiddling with the two receipt input/lapis slots from ever triggering it. */
    @Override
    public void slotsChanged(Container inventory) {
        // Intentionally empty.
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Drag operations fire clicked() once per slot the drag passes over.
        if (clickType == ClickType.QUICK_CRAFT) {
            super.clicked(slotId, button, clickType, player);
            return;
        }

        // Round-13: lock the two receipt slots — they show already-consumed altar materials, not
        // anything the player can take back.
        if (slotId == 0 || slotId == 1) {
            return;
        }

        // The candidate slots are off-screen and only ever reachable via a forged packet (never
        // via a real mouse click) — lock them the same way, defensively.
        if (slotId >= CANDIDATE_SLOT_BASE && slotId < CANDIDATE_SLOT_BASE + MAX_CANDIDATE_SLOTS) {
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    /**
     * The bind attempt — runs entirely server-side. Preserves every safety property from earlier
     * rounds: the atomic occupancy guard first, consume-both-sockets-before-bind, {@code
     * employeeBound} set ONLY after a successful {@link EmployeeManager#bind} call, and a
     * caught/logged failure path that leaves the altar in a recoverable (if item-losing) state
     * rather than crashing.
     */
    private void attemptBind(ServerPlayer sp, MerchantOffer chosenOffer) {
        if (!stillValid(sp)) {
            return;
        }

        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be)) {
                return; // altar gone
            }
            if (be.isEmployeeBound()) {
                return; // atomic occupancy guard, before any other check
            }
            if (be.isEmpty() || be.isJobItemEmpty()) {
                return; // nothing to bind, or a prior rapid confirm already consumed it
            }

            MerchantOffers chosen = new MerchantOffers();
            chosen.add(chosenOffer);

            Optional<VillagerProfession> profession = ProfessionResolver.fromItem(be.getHeldJobItem());
            if (profession.isEmpty()) {
                return; // defensive — should be unreachable
            }
            String name = be.getDefaultName() == null ? "Employee" : be.getDefaultName();

            be.setHeldSoulBlock(ItemStack.EMPTY);
            be.setHeldJobItem(ItemStack.EMPTY);
            be.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);

            if (level instanceof ServerLevel serverLevel) {
                try {
                    net.minecraft.world.entity.npc.Villager employee =
                            EmployeeManager.bind(serverLevel, pos, profession.get(), chosen, name);
                    be.setEmployeeBound(true);
                    be.setEmployeeId(employee.getUUID()); // Phase 6 D-01: altar's half of the bidirectional link
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

    /**
     * Prevents {@code EnchantmentMenu.removed()} (called via {@code super.removed()} right below)
     * from dropping the receipt slots' display copies back into the world on close — those are
     * copies of already-consumed altar materials, not real held items, and {@code
     * EnchantmentMenu.removed()} unconditionally drops whatever is in its "item to enchant"/"lapis"
     * slots. Emptying them first makes that drop a safe no-op (it only drops non-empty stacks).
     */
    @Override
    public void removed(Player player) {
        this.getSlot(0).set(ItemStack.EMPTY);
        this.getSlot(1).set(ItemStack.EMPTY);
        super.removed(player);
    }

    /** Read-only candidate slots, and no shift-click routing for them — full override of
     * {@code EnchantmentMenu}'s own version to keep the two receipt slots' quirky default
     * shift-click behavior from ever reaching our own slots. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

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

    // --- GUI-03 / test-facing accessors ---

    /** The full career-path candidate pool actually shown this open (server-side truth — on the
     * client, read the synced slots via {@link #getSlot} instead, e.g. from {@code
     * CANDIDATE_SLOT_BASE}). */
    public List<MerchantOffer> getDisplayedCandidates() {
        return displayedCandidates;
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

    public ContainerLevelAccess access() {
        return this.access;
    }
}
