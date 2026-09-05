package com.cxmxrgo.secondshift.trade;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Runtime POI -> {@link VillagerProfession} resolver (D-08 / D-09 / D-10).
 *
 * <p>Deliberately contains no hardcoded {@code Block}/{@code ResourceLocation} list (D-08):
 * every lookup goes through {@link PoiTypes#forState(net.minecraft.world.level.block.state.BlockState)}
 * and a live iteration of {@link BuiltInRegistries#VILLAGER_PROFESSION}, so any vanilla OR
 * modded profession's job-site block is picked up "for free" with zero code changes here.
 *
 * <p>Used ONLY as a yes/no "is the block on top a real job site" gate this phase (D-09) — the
 * resolved {@link VillagerProfession} is not stored or used for binding. Phase 5 reuses this
 * same resolver for ALTAR-02's real target-profession behaviour.
 *
 * <p>Uses {@link VillagerProfession#heldJobSite()} rather than {@code acquirableJobSite()} —
 * for every vanilla profession the two predicates are constructed identically, and {@code
 * heldJobSite} is the semantically correct choice here: it mirrors vanilla's own {@code
 * ResetProfession} brain behavior's "is this POI type still this profession's job site" check,
 * not the "can an unemployed villager acquire this" check (RESEARCH.md Pattern 5).
 */
public final class ProfessionResolver {

    private ProfessionResolver() {}

    /**
     * Plan 05-01 (G-2): resolves the profession (if any) mapped to the job-site block backing a
     * {@link BlockItem} stack — replaces {@code fromAbove}, which resolved from the block placed
     * directly above the altar (retired: the item-socket mechanic supersedes "block on top").
     */
    public static Optional<VillagerProfession> fromItem(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return Optional.empty();
        }
        return PoiTypes.forState(blockItem.getBlock().defaultBlockState()).flatMap(ProfessionResolver::fromPoi);
    }

    /** Resolves the profession (if any) whose held job site matches the given POI holder. */
    public static Optional<VillagerProfession> fromPoi(Holder<PoiType> poi) {
        return BuiltInRegistries.VILLAGER_PROFESSION.stream()
                .filter(p -> p != VillagerProfession.NONE && p != VillagerProfession.NITWIT)
                .filter(p -> p.heldJobSite().test(poi))
                .findFirst();
    }
}
