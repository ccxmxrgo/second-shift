package com.cxmxrgo.secondshift.content.blockentity;

import com.cxmxrgo.secondshift.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
 */
public class SoulAltarBlockEntity extends BlockEntity {

    /** Bump when the persisted NBT shape changes; read back for future migrations (D-17). */
    private static final int DATA_VERSION = 1;
    private static final String KEY_SOUL_BLOCK = "SoulBlock";
    private static final String KEY_DATA_VERSION = "DataVersion";

    private ItemStack heldSoulBlock = ItemStack.EMPTY;

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

    public void setHeldSoulBlock(ItemStack stack) {
        this.heldSoulBlock = stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(KEY_DATA_VERSION, DATA_VERSION);
        if (!heldSoulBlock.isEmpty()) {
            tag.put(KEY_SOUL_BLOCK, heldSoulBlock.save(registries));
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
}
