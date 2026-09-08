package com.cxmxrgo.secondshift.content.blockentity;

import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import com.cxmxrgo.secondshift.registry.ModBlockEntities;
import com.cxmxrgo.secondshift.registry.ModItems;
import com.cxmxrgo.secondshift.trade.ProfessionResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Soul Altar block entity (D-01 / D-03 / ALTAR-01).
 *
 * <p>Holds exactly one thing: the socketed Soul Block {@link ItemStack}. No professions,
 * no employees, no binding state — those arrive in later phases. Deliberately exposes no
 * item-handler capability (D-03: player interaction only, no hopper/dropper automation) —
 * the held stack is a plain field with hand-rolled save/load.
 *
 * <p>Skeleton for plan 02-01 — the socket / break / retrieve logic lives in the block
 * class and lands in plan 02-04. This class already carries full persistence + client
 * sync so 02-04 and the renderer (02-05) can rely on it.
 *
 * <p>RESEARCH Assumptions Log A5 (resolved): 1.21.1 {@link ItemStack} persistence is
 * codec-driven — {@code ItemStack#save(HolderLookup.Provider)} returns a {@code Tag},
 * {@code ItemStack#parse(HolderLookup.Provider, Tag)} returns {@code Optional<ItemStack>}.
 * Verified against the decompiled {@code net.minecraft.world.item.ItemStack}.
 *
 * <p><b>Plan 03-01 addition:</b> now {@link MenuProvider} — {@link #createMenu} constructs a
 * {@link BindingAltarMenu} against this BE's position (GUI-01). No block-interaction trigger is
 * wired yet (that is Plan 02 / ALTAR-03); this only makes the BE a valid open target.
 */
public class SoulAltarBlockEntity extends BlockEntity implements MenuProvider {

    /** Bump when the persisted NBT shape changes; read back for future migrations (D-17). */
    private static final int DATA_VERSION = 2;
    private static final String KEY_SOUL_BLOCK = "SoulBlock";
    private static final String KEY_DATA_VERSION = "DataVersion";
    private static final String KEY_JOB_ITEM = "JobItem";
    private static final String KEY_EMPLOYEE_BOUND = "EmployeeBound";
    private static final String KEY_EMPLOYEE_ID = "EmployeeId";

    private ItemStack heldSoulBlock = ItemStack.EMPTY;

    /**
     * Plan 05-01 (G-2): the second held-item socket — the job-site item right-clicked onto the
     * altar. Mirrors {@link #heldSoulBlock}'s exact persistence/getter/setter idiom. No item-type
     * validation here (validated at the {@code SoulAltarBlock} call site — same division of
     * responsibility as the Soul Block slot).
     */
    private ItemStack heldJobItem = ItemStack.EMPTY;

    /** Plan 05-01 (D-04 / ALTAR-05): one employee per altar. Persisted unconditionally. */
    private boolean employeeBound = false;

    /**
     * Phase 6 (06-CONTEXT.md D-01): the altar's half of the bidirectional altar↔employee link —
     * {@code null} means either no employee was ever bound, or (for a pre-Phase-6 save) one was
     * bound before this field existed. Nullable rather than {@code Optional} because block
     * entity fields follow the codebase's existing null-means-absent idiom (see
     * {@link #heldSoulBlock}'s ItemStack.EMPTY equivalent) — {@code Optional} is reserved for
     * {@link com.cxmxrgo.secondshift.employee.EmployeeData#altarPos()}, the record side of the
     * same link, per that class's own doc comment.
     */
    private UUID employeeId = null;

    /**
     * Plan 05-01: transient (never persisted) session fields for the trade-candidate roll that
     * later plans in this phase build against. {@code null} candidateOffers means "not yet
     * rolled this session" — regenerated if the BE reloads before a bind completes.
     */
    private transient List<MerchantOffer> candidateOffers = null;
    private transient String defaultName = null;

    /**
     * Transient (never persisted): set by {@code SoulAltarBlock#playerWillDestroy} when the
     * altar is broken while charged, read back moments later by {@code SoulAltarBlock#getDrops}
     * off the same BE instance (the break pipeline captures the BE before block removal and
     * passes it through {@code LootContextParams.BLOCK_ENTITY}). D-04 drop suppression.
     */
    private transient boolean brokenWhileCharged = false;

    public SoulAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOUL_ALTAR_BE.get(), pos, state);
    }

    public boolean isEmpty() {
        return heldSoulBlock.isEmpty();
    }

    public boolean wasBrokenWhileCharged() {
        return brokenWhileCharged;
    }

    public void markBrokenWhileCharged() {
        this.brokenWhileCharged = true;
    }

    public ItemStack getHeldSoulBlock() {
        return heldSoulBlock;
    }

    /**
     * CR-01: validated at the point where the stack enters persistent state — not only at the
     * {@link com.cxmxrgo.secondshift.menu.SoulSlot} UI layer. Accepts {@code null}/empty or
     * exactly a {@link ModItems#SOUL_BLOCK_ITEM} stack; anything else (e.g. an arbitrary item
     * written via a non-click-routed {@code Container} write, such as the creative-mode
     * set-slot packet) is silently rejected so the "always empty or exactly one Soul Block"
     * invariant holds for every current and future caller of this method, not just
     * {@code SoulAltarBlock#useItemOn}.
     */
    public void setHeldSoulBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            this.heldSoulBlock = ItemStack.EMPTY;
            resetRolledCandidates(); // round-3 fix: a socket emptying invalidates any prior roll
            return;
        }
        if (!stack.is(ModItems.SOUL_BLOCK_ITEM.get())) {
            return; // reject anything that isn't empty or a Soul Block
        }
        this.heldSoulBlock = stack;
    }

    public boolean isJobItemEmpty() {
        return heldJobItem.isEmpty();
    }

    public ItemStack getHeldJobItem() {
        return heldJobItem;
    }

    /**
     * No item-type validation here — validated at the {@code SoulAltarBlock} call site (same
     * division of responsibility as {@link #setHeldSoulBlock}).
     */
    public void setHeldJobItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            this.heldJobItem = ItemStack.EMPTY;
            resetRolledCandidates(); // round-3 fix: a socket emptying invalidates any prior roll
            return;
        }
        this.heldJobItem = stack;
    }

    /** True once both the Soul Block and job-item sockets are filled (G-2 open trigger). */
    public boolean bothSocketsFilled() {
        return !isEmpty() && !isJobItemEmpty();
    }

    public boolean isEmployeeBound() {
        return employeeBound;
    }

    public void setEmployeeBound(boolean bound) {
        this.employeeBound = bound;
    }

    /** Phase 6: the UUID of the villager this altar is currently bound to, or {@code null}. */
    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    /** {@code true} once {@link #candidateOffers} has been rolled for this session. */
    public boolean candidatesRolled() {
        return candidateOffers != null;
    }

    public List<MerchantOffer> getCandidateOffers() {
        return candidateOffers == null ? List.of() : candidateOffers;
    }

    public void setCandidateOffers(List<MerchantOffer> offers) {
        this.candidateOffers = offers;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public void setDefaultName(String name) {
        this.defaultName = name;
    }

    /**
     * Round-3 checkpoint fix: {@link #candidatesRolled()} treats ANY non-null
     * {@link #candidateOffers} — including an empty {@code List.of()} — as "already rolled,
     * never re-roll" (Plan 05-01's "roll once per bind session" design). Without this reset, a BE
     * instance whose very first roll ever happened to land on an empty list (e.g. a transient
     * failure during an earlier, since-fixed code path) would be stuck showing "Nothing to Offer"
     * forever, even after being fully unsocketed and re-filled with a job item that has a perfectly
     * good real trade pool. Clearing both transient fields whenever either socket transitions to
     * empty ensures a fresh re-socketing always starts a genuinely new roll session instead of
     * reusing a stale result left over from a previous occupant of this BE instance.
     */
    private void resetRolledCandidates() {
        this.candidateOffers = null;
        this.defaultName = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(KEY_DATA_VERSION, DATA_VERSION);
        if (!heldSoulBlock.isEmpty()) {
            tag.put(KEY_SOUL_BLOCK, heldSoulBlock.save(registries));
        }
        if (!heldJobItem.isEmpty()) {
            tag.put(KEY_JOB_ITEM, heldJobItem.save(registries));
        }
        tag.putBoolean(KEY_EMPLOYEE_BOUND, employeeBound);
        if (employeeId != null) {
            tag.putUUID(KEY_EMPLOYEE_ID, employeeId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(KEY_SOUL_BLOCK)) {
            heldSoulBlock = ItemStack.parse(registries, tag.getCompound(KEY_SOUL_BLOCK)).orElse(ItemStack.EMPTY);
        } else {
            heldSoulBlock = ItemStack.EMPTY;
        }
        if (tag.contains(KEY_JOB_ITEM)) {
            heldJobItem = ItemStack.parse(registries, tag.getCompound(KEY_JOB_ITEM)).orElse(ItemStack.EMPTY);
        } else {
            heldJobItem = ItemStack.EMPTY;
        }
        // D-17: pre-Phase-5 saves lack this key — getBoolean defaults to false, backward compatible.
        employeeBound = tag.getBoolean(KEY_EMPLOYEE_BOUND);
        // Phase 6: pre-Phase-6 saves lack this key — null means "bound before the link existed";
        // EmployeeManager.releaseAltar treats a null stored id as matching any dying employee.
        employeeId = tag.hasUUID(KEY_EMPLOYEE_ID) ? tag.getUUID(KEY_EMPLOYEE_ID) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Round-11 intuitiveness pass: names the open screen after the resolved profession (e.g.
     * "Binding Altar - Librarian") whenever the socketed job item resolves to one, instead of a
     * bare "Binding Altar" that gives no hint what role the player is about to staff.
     */
    @Override
    public Component getDisplayName() {
        Optional<VillagerProfession> profession = ProfessionResolver.fromItem(heldJobItem);
        if (profession.isEmpty()) {
            return Component.translatable("container.secondshift.binding_altar");
        }
        String path = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession.get()).getPath();
        return Component.translatable("container.secondshift.binding_altar_titled",
                Component.translatable("entity.minecraft.villager." + path));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new BindingAltarMenu(containerId, playerInv,
                ContainerLevelAccess.create(this.getLevel(), this.getBlockPos()), this.getBlockPos());
    }
}
