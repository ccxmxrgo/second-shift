package com.cxmxrgo.secondshift.network;

import com.cxmxrgo.secondshift.content.blockentity.SoulAltarBlockEntity;
import com.cxmxrgo.secondshift.employee.EmployeeManager;
import com.cxmxrgo.secondshift.menu.BindingAltarMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side handler for {@link SelectTradesPayload} (Plan 05-05, GUI-02) — the security-critical
 * seam of the whole phase. Replaces Plan 04-03's throwaway {@code handleBindEmployee}.
 *
 * <p>Full trust-boundary discipline per 05-RESEARCH.md Finding 3: the sender's client-claimed
 * {@code indices} and {@code name} are never trusted directly. Every index is bounds/duplicate/
 * count-checked against the server's own {@link BindingAltarMenu#getCandidateOffers()} (never the
 * client's copy), a pool of &lt;=2 candidates is auto-selected server-side regardless of what the
 * client sent (PICK-04), and the name is stripped/capped/defaulted before ever being persisted.
 *
 * <p>T-05-11 (double-confirm race): {@code be.isEmployeeBound()} is checked as the FIRST statement
 * inside the atomic {@code access().execute(...)} lambda, and is only ever set {@code true} AFTER
 * {@link EmployeeManager#bind} returns successfully — never before, since a bind failure with the
 * flag already set would permanently soft-lock the altar (ALTAR-05/D-04 refuse to reopen a
 * {@code employeeBound == true} altar with no employee ever spawned).
 */
public final class ServerPayloadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ServerPayloadHandler() {}

    public static void handleSelectTrades(SelectTradesPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sp)) {
            return;
        }
        if (!(sp.containerMenu instanceof BindingAltarMenu menu)) {
            return; // no Binding Altar menu open server-side — stale/malicious send, silently ignore
        }
        if (!menu.stillValid(sp)) {
            return; // re-derived, not trusted — reuses the existing altar/job-block/proximity check
        }

        menu.access().execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof SoulAltarBlockEntity be)) {
                return; // altar gone
            }
            // T-05-11: the atomic occupancy guard, BEFORE any other check.
            if (be.isEmployeeBound()) {
                return;
            }
            if (be.isEmpty() || be.isJobItemEmpty()) {
                return; // nothing to bind, or a prior rapid confirm already consumed it
            }

            List<MerchantOffer> candidates = menu.getCandidateOffers();
            List<Integer> selected = validateIndices(payload.indices(), candidates.size());
            if (selected == null) {
                return; // whole payload rejected, no partial application
            }

            MerchantOffers chosen = new MerchantOffers();
            for (int i : selected) {
                chosen.add(candidates.get(i));
            }

            String name = sanitizeName(payload.name(), be.getDefaultName());

            Optional<VillagerProfession> profession = menu.getProfession();
            if (profession.isEmpty()) {
                return; // defensive — should be unreachable
            }

            // Consume both sockets BEFORE calling bind (matches T-4-02's existing discipline). The
            // sockets are consumed regardless of bind's eventual outcome; there is no repair
            // mechanic for a partially-applied bind, so a bind failure is handled by explicit
            // logging below, not by attempting to restore the already-consumed items.
            be.setHeldSoulBlock(ItemStack.EMPTY);
            be.setHeldJobItem(ItemStack.EMPTY);
            be.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);

            if (level instanceof ServerLevel serverLevel) {
                try {
                    EmployeeManager.bind(serverLevel, pos, profession.get(), chosen, name);
                    // Set employeeBound only AFTER a successful bind, inside this same atomic
                    // lambda, before sp.closeContainer() — never before (see class javadoc).
                    be.setEmployeeBound(true);
                    be.setChanged();
                    level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
                } catch (Exception e) {
                    LOGGER.error("EmployeeManager.bind failed for altar at {} — sockets already consumed, "
                            + "no employee spawned, employeeBound left false", pos, e);
                }
            }
        });

        sp.closeContainer();
    }

    /**
     * Bounds/duplicate/count-checks {@code raw} against {@code candidateCount}, per RESEARCH
     * Finding 3.1. Returns {@code null} to signal "reject the whole payload". When {@code
     * candidateCount <= 2}, the client's claimed selection content is ignored entirely and every
     * index {@code 0..candidateCount-1} is returned (PICK-04 auto-lock, re-derived server-side).
     */
    public static List<Integer> validateIndices(int[] raw, int candidateCount) {
        Set<Integer> deduped = new LinkedHashSet<>();
        for (int i : raw) {
            if (i < 0 || i >= candidateCount) {
                return null;
            }
            if (!deduped.add(i)) {
                return null; // duplicate index
            }
        }

        if (candidateCount <= 2) {
            List<Integer> all = new java.util.ArrayList<>();
            for (int i = 0; i < candidateCount; i++) {
                all.add(i);
            }
            return all;
        }

        if (deduped.size() > 2) {
            return null;
        }

        return List.copyOf(deduped);
    }

    /**
     * Strips section-sign and control characters, trims, and caps at 32 characters. Falls back to
     * {@code fallback} if {@code raw} is {@code null} or the sanitized result is blank.
     */
    public static String sanitizeName(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String cleaned = raw.replaceAll("[§\\p{Cc}]", "").trim();
        if (cleaned.isEmpty()) {
            return fallback;
        }
        return cleaned.substring(0, Math.min(32, cleaned.length()));
    }
}
