package com.cxmxrgo.secondshift.registry;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.employee.EmployeeData;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Data-attachment registry holder (EMP-01 / D-08 / D-10).
 *
 * <p>Only {@code secondshift:employee} — an {@link EmployeeData} attachment, serialized via
 * {@link EmployeeData#CODEC} and synced via {@link EmployeeData#STREAM_CODEC} per the verified
 * builder surface documented in STACK.md §4. This is the 6th {@code DeferredRegister} the mod
 * wires up; it must be registered in {@code SecondShift}'s constructor and guarded by
 * {@code ModRegistrySelfCheck} exactly like the other five.
 */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SecondShift.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EmployeeData>> EMPLOYEE =
            ATTACHMENT_TYPES.register("employee", () ->
                    AttachmentType.builder(() -> EmployeeData.EMPTY)
                            .serialize(EmployeeData.CODEC)
                            .sync(EmployeeData.STREAM_CODEC)
                            .build());

    /**
     * Phase 8 (STOCK-01/02/03): the last real game-time (in ticks) an employee's trades were
     * restocked. Deliberately a SEPARATE attachment from {@link #EMPLOYEE}, not a 7th field on
     * {@link EmployeeData} — that record's {@code StreamCodec.composite} chain is already at its
     * documented 6-component ceiling (Phase 6). No {@code .sync(...)} — this is pure
     * server-authoritative bookkeeping the client never needs to render. Default {@code 0L} is
     * intentionally "long ago" rather than a sentinel: an employee attachment-loaded from a
     * pre-Phase-8 save simply restocks once on its next periodic check (matching success
     * criterion 4 — at most one restock, never a burst) and then behaves normally forever after.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> RESTOCK_TIMER =
            ATTACHMENT_TYPES.register("restock_timer", () ->
                    AttachmentType.builder(() -> 0L)
                            .serialize(Codec.LONG)
                            .build());

    /**
     * Phase 9 (HAPP-03, 09-CONTEXT.md D-03): a 0-100 happiness meter, moved gradually toward 100
     * when quarters+food conditions are met and toward 0 when they aren't (see {@code
     * Happiness#fromMeter} for the discrete band mapping). Default 50 (OK) — a freshly bound
     * employee with no quarters set up yet isn't instantly branded Unhappy. No sync: happiness is
     * currently surfaced only via a chat/action-bar message on altar interaction (see
     * {@code SoulAltarBlock#useWithoutItem}), not a synced screen widget.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> HAPPINESS =
            ATTACHMENT_TYPES.register("happiness", () ->
                    AttachmentType.builder(() -> 50)
                            .serialize(Codec.INT)
                            .build());

    /**
     * Phase 9 (HAPP-06, 09-CONTEXT.md D-04): ticks spent continuously at the Unhappy band, reset
     * to 0 the moment happiness rises out of Unhappy. Once this reaches {@code
     * ModConfig#HAPPINESS_QUIT_THRESHOLD_TICKS}, the employee quits.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> UNHAPPY_STREAK_TICKS =
            ATTACHMENT_TYPES.register("unhappy_streak_ticks", () ->
                    AttachmentType.builder(() -> 0)
                            .serialize(Codec.INT)
                            .build());

    private ModAttachments() {}
}
