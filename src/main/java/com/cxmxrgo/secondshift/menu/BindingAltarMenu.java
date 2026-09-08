package com.cxmxrgo.secondshift.menu;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
import com.cxmxrgo.secondshift.employee.EmployeeNames;
import com.cxmxrgo.secondshift.registry.ModBlocks;
import com.cxmxrgo.secondshift.registry.ModItems;
import com.cxmxrgo.secondshift.registry.ModMenus;
import com.cxmxrgo.secondshift.trade.ProfessionResolver;
import com.cxmxrgo.secondshift.trade.TradePoolCache;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Binding Altar container menu — round-12 redesign (2026-09-08): reuses vanilla's real
 * enchanting-table menu/screen ({@link EnchantmentMenu} / the client-side {@code
 * EnchantmentScreen}) per the user's explicit request, instead of the generic {@code ChestMenu}
 * used in round-10.
 *
 * <p><b>Why extend {@code EnchantmentMenu} specifically:</b> the vanilla {@code EnchantmentScreen}
 * class is hard-typed to {@code AbstractContainerScreen<EnchantmentMenu>} — reusing that screen
 * (its background texture and slot positions) requires our menu to actually BE an {@code
 * EnchantmentMenu}, the same constraint that made {@code ChestMenu} the anchor for the round-10
 * design.
 *
 * <p><b>Why the mechanic changed from "pick 2 of N, then confirm" to "pick 1 of up to 3,
 * immediately":</b> {@code EnchantmentMenu} has no concept of a toggleable multi-select plus a
 * separate confirm action — its entire menu-button system (see the now-disabled {@link
 * #clickMenuButton}) is built around exactly 3 options where clicking one immediately performs
 * the action. Reusing vanilla's real slot/click machinery instead of that button system (see
 * below) lets the picker still work through completely standard, well-tested vanilla code paths
 * — just with the accepted rule change to "1 of up to 3" rather than trying to bolt a
 * pick-2-then-confirm flow onto a widget that was never built for it.
 *
 * <p><b>How the 3 trade rows actually work:</b> rather than driving {@code EnchantmentMenu}'s own
 * {@code costs}/{@code enchantClue}/{@code levelClue} arrays (which only exist to describe a real
 * enchantment, and whose tooltip in vanilla's screen does a REAL enchantment-registry lookup — see
 * {@code costs} handling below for why that matters), this class adds 3 ordinary real {@link Slot}s
 * at the exact same screen coordinates vanilla uses for its enchant-option rows (x=60,
 * y=14+19*row, matching {@code EnchantmentMenu}'s own {@code EnchantingTableBlock}-derived layout
 * exactly), backed by a small container this class owns. Because they are ordinary slots holding
 * ordinary {@link ItemStack}s (the real trade result, with cost info attached as {@link
 * DataComponents#LORE}), vanilla's own generic per-slot rendering, hover-highlight, and
 * item-tooltip machinery in {@code AbstractContainerScreen} handles the icon, the hover overlay,
 * and the tooltip completely for free — genuinely zero custom tooltip code, unlike the vanilla
 * enchant rows' scrambled-rune-text + wrong-registry-lookup problem this design sidesteps
 * entirely. The client-side screen ({@code BindingAltarScreen}) only needs to draw each row's
 * enabled/disabled parchment-bar background sprite.
 *
 * <p>{@link #costs} (inherited, public, mutable) is deliberately left at its default all-zero
 * state forever — {@code EnchantmentScreen.render()}'s own inline tooltip loop (which performs
 * the real, unrelated {@code Registries.ENCHANTMENT} lookup) is guarded by {@code costs[row] > 0},
 * so leaving it at 0 permanently and unconditionally suppresses that broken vanilla codepath
 * without touching it at all. {@link #slotsChanged} is overridden to a no-op specifically so a
 * curious player fiddling with the two "receipt" input/lapis slots (see {@link #REROLL_SLOT}'s
 * neighbors below) can never trigger vanilla's real bookshelf-scanning enchant-cost calculation,
 * which would otherwise repopulate {@code costs[]} with a nonzero value and revive that same
 * broken tooltip.
 *
 * <p><b>Round-13 (user feedback pass, same day):</b> the two real slots {@code EnchantmentMenu}'s
 * own constructor adds (its "item to enchant" and "lapis" slots, at 15,47 and 35,47) now show a
 * read-only, locked "receipt" of what's actually socketed on the altar — a copy of {@link
 * SoulAltarBlockEntity#getHeldSoulBlock()} and {@link SoulAltarBlockEntity#getHeldJobItem()}
 * respectively, since the real items are already consumed by the block-interaction socketing step
 * before this menu ever opens (there is no drag-to-socket flow in this GUI — see {@link #clicked}
 * for the lock). A third real slot ({@link #REROLL_SLOT}, in the enchanting table's now-otherwise-
 * empty book area) lets the player spend one real {@code minecraft:soul_fragment} from their own
 * inventory to re-roll the 3 displayed trades without closing the menu (see {@link #attemptReroll}).
 */
public class BindingAltarMenu extends EnchantmentMenu {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** How many of the rolled tier-1 candidates are ever shown/pickable at once (vanilla's fixed row count). */
    public static final int OPTION_COUNT = 3;

    /**
     * The slot index of trade row 0 — 2 (EnchantmentMenu's own input+lapis slots) + 27 (player
     * inventory) + 9 (hotbar) = 38, added by {@code EnchantmentMenu}'s constructor before this
     * class's own constructor body runs. Verified against the decompiled {@code
     * EnchantmentMenu} constructor and defensively asserted in this class's own constructor
     * below — if a future NeoForge/vanilla update changes that layout, the assertion fails loudly
     * instead of silently misrouting clicks.
     */
    public static final int TRADE_SLOT_BASE = 38;

    /** The reroll button's slot index — right after the 3 trade rows. */
    public static final int REROLL_SLOT = TRADE_SLOT_BASE + OPTION_COUNT;

    /** Screen position of the reroll slot — centered between the two receipt slots (15,47 / 35,47),
     * well clear of the title text above it. */
    private static final int REROLL_SLOT_X = 25;
    private static final int REROLL_SLOT_Y = 20;

    private final ContainerLevelAccess access;
    private final Container tradeSlots = new SimpleContainer(OPTION_COUNT);
    private final Container rerollSlot = new SimpleContainer(1);
    private List<MerchantOffer> displayedCandidates;

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

        if (this.slots.size() != TRADE_SLOT_BASE) {
            throw new IllegalStateException("EnchantmentMenu's slot layout changed — expected "
                    + TRADE_SLOT_BASE + " slots before the trade rows, found " + this.slots.size());
        }

        // Round-13: the "item to enchant" / "lapis" slots become a locked, read-only receipt of
        // what's actually socketed on the altar below — never populated on the client (server-only
        // BE read; the real stacks sync to the client the same way the trade rows do).
        if (!playerInv.player.level().isClientSide()
                && playerInv.player.level().getBlockEntity(pos) instanceof SoulAltarBlockEntity receiptBe) {
            this.getSlot(0).set(receiptBe.getHeldSoulBlock().copy());
            this.getSlot(1).set(receiptBe.getHeldJobItem().copy());
        }

        refreshTradeSlots();
        for (int row = 0; row < OPTION_COUNT; row++) {
            this.addSlot(new Slot(tradeSlots, row, 60, 14 + 19 * row));
        }

        rerollSlot.setItem(0, buildRerollDisplay());
        this.addSlot(new Slot(rerollSlot, 0, REROLL_SLOT_X, REROLL_SLOT_Y));
    }

    /** (Re)writes all {@link #OPTION_COUNT} trade-row slot CONTENTS from {@link
     * #displayedCandidates} — does not touch the menu's slot list; the 3 trade {@link Slot}s
     * themselves are added exactly once, in the constructor. */
    private void refreshTradeSlots() {
        for (int row = 0; row < OPTION_COUNT; row++) {
            ItemStack display = row < displayedCandidates.size()
                    ? buildCandidateDisplay(displayedCandidates.get(row))
                    : ItemStack.EMPTY;
            tradeSlots.setItem(row, display);
        }
    }

    /**
     * Resolves (and, server-side only, rolls-once-and-persists — PICK-02/04/07/08) the candidate
     * trade pool, then caps it down to {@link #OPTION_COUNT} for display via {@link
     * #sampleUpToOptionCount}.
     *
     * <p>On the CLIENT, this always returns an empty list purely for slot-count bookkeeping —
     * vanilla's own container-sync protocol fills in the real per-slot item stacks immediately
     * after open, exactly like {@link #TRADE_SLOT_BASE}'s doc comment describes.
     */
    private static List<MerchantOffer> resolveDisplayedCandidates(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be)
                || be.isEmployeeBound() || !be.bothSocketsFilled()) {
            return List.of();
        }

        if (!be.candidatesRolled()) {
            rollFreshCandidates(be, (ServerLevel) level, pos);
        }

        return sampleUpToOptionCount(be.getCandidateOffers(), (ServerLevel) level);
    }

    private static void rollFreshCandidates(SoulAltarBlockEntity be, ServerLevel level, BlockPos pos) {
        Optional<VillagerProfession> profession = ProfessionResolver.fromItem(be.getHeldJobItem());
        if (profession.isPresent()) {
            be.setCandidateOffers(TradePoolCache.rollTier1Candidates(level, pos, profession.get()));
            be.setDefaultName(EmployeeNames.pickRandom(level.getRandom()));
        } else {
            be.setCandidateOffers(List.of()); // defensive — should be unreachable
        }
    }

    /** A shuffled sample of at most {@link #OPTION_COUNT} offers, so a profession with more than 3
     * tier-1 listings doesn't always show the same 3 (vanilla's tier1 array order is fixed). */
    private static List<MerchantOffer> sampleUpToOptionCount(List<MerchantOffer> all, ServerLevel level) {
        if (all.size() <= OPTION_COUNT) {
            return all;
        }
        List<MerchantOffer> shuffled = new ArrayList<>(all);
        RandomSource random = level.getRandom();
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            MerchantOffer tmp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, tmp);
        }
        return List.copyOf(shuffled.subList(0, OPTION_COUNT));
    }

    private static ItemStack buildCandidateDisplay(MerchantOffer offer) {
        ItemStack display = offer.getResult().copy();
        List<Component> lore = new ArrayList<>();
        lore.add(costLine(offer.getCostA()));
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

    private static ItemStack buildRerollDisplay() {
        ItemStack display = new ItemStack(ModItems.SOUL_FRAGMENT.get());
        display.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.secondshift.binding_altar.reroll")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("gui.secondshift.binding_altar.reroll_cost").withStyle(ChatFormatting.GRAY));
        display.set(DataComponents.LORE, new ItemLore(lore));
        return display;
    }

    /**
     * Disables vanilla's real enchant-button mechanism entirely. {@code EnchantmentScreen}'s
     * inherited {@code mouseClicked} always tries this FIRST for a click inside a row's bounding
     * box, before ever falling through to the normal slot-click path that would route to our own
     * {@link #clicked}. Returning {@code false} unconditionally makes every row-click fall through
     * to that normal path instead, where {@link #clicked} does the real work.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        return false;
    }

    /**
     * No-op: this altar has no real enchantment cost to compute. Overridden specifically to stop
     * vanilla's own bookshelf-scanning enchant-cost logic from ever running against the two
     * receipt input/lapis slots (see class doc) — that logic would repopulate {@link #costs} with
     * a nonzero value and revive {@code EnchantmentScreen}'s broken enchantment-registry tooltip
     * lookup, which {@link #costs} being permanently 0 otherwise suppresses. It also happens to be
     * exactly what we want anyway, since we drive those slots' contents ourselves via {@code
     * Slot#set} in the constructor and never expect vanilla's own change-tracking to fire for them.
     */
    @Override
    public void slotsChanged(Container inventory) {
        // Intentionally empty.
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Drag operations fire clicked() once per slot the drag passes over — never treat an
        // incidental drag-through as a deliberate bind/reroll click (mirrors the round-10/11 guard).
        if (clickType == ClickType.QUICK_CRAFT) {
            super.clicked(slotId, button, clickType, player);
            return;
        }

        // Round-13: lock the two receipt slots — they show already-consumed altar materials, not
        // anything the player can take back.
        if (slotId == 0 || slotId == 1) {
            return;
        }

        if (slotId == REROLL_SLOT) {
            if (player instanceof ServerPlayer sp) {
                attemptReroll(sp);
            }
            return;
        }

        int row = slotId - TRADE_SLOT_BASE;
        if (row >= 0 && row < OPTION_COUNT) {
            if (row < displayedCandidates.size() && player instanceof ServerPlayer sp) {
                attemptBind(sp, displayedCandidates.get(row));
            }
            return; // no-op for an empty/inactive row, or any non-ServerPlayer caller
        }

        super.clicked(slotId, button, clickType, player);
    }

    /**
     * The bind attempt — runs entirely server-side. Preserves every safety property from the
     * round-10/11 implementation: the atomic occupancy guard first, consume-both-sockets-before-
     * bind, {@code employeeBound} set ONLY after a successful {@link EmployeeManager#bind} call,
     * and a caught/logged failure path that leaves the altar in a recoverable (if item-losing)
     * state rather than crashing.
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
                    EmployeeManager.bind(serverLevel, pos, profession.get(), chosen, name);
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

    /**
     * Round-13: spends one real {@code minecraft:soul_fragment} from the player's own inventory
     * (not a menu slot — the reroll button is a display, not a payment slot) to re-roll the tier-1
     * pool and refresh the 3 displayed trade rows in place, without closing the menu. Rejects with
     * a themed message if the player doesn't have a Soul Fragment on hand; never touches the altar
     * sockets or {@code employeeBound}.
     */
    private void attemptReroll(ServerPlayer sp) {
        if (!stillValid(sp)) {
            return;
        }

        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be) || be.isEmployeeBound()
                    || !(level instanceof ServerLevel serverLevel)) {
                return;
            }

            if (sp.getInventory().countItem(ModItems.SOUL_FRAGMENT.get()) < 1) {
                sp.displayClientMessage(Component.translatable("message.secondshift.altar.reroll_not_enough_fragments"), true);
                return;
            }
            sp.getInventory().clearOrCountMatchingItems(
                    stack -> stack.is(ModItems.SOUL_FRAGMENT.get()), 1, new SimpleContainer(0));

            rollFreshCandidates(be, serverLevel, pos);
            this.displayedCandidates = sampleUpToOptionCount(be.getCandidateOffers(), serverLevel);
            refreshTradeSlots();

            level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.8F,
                    0.9F + serverLevel.getRandom().nextFloat() * 0.2F);
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

    /** Read-only trade/reroll rows, and no shift-click routing for them — full override of
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

    /** The (already capped-to-{@link #OPTION_COUNT}) candidate offers actually shown this open. */
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
