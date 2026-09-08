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

    private ModAttachments() {}
}
