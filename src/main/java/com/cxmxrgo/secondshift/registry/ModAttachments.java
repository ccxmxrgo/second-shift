package com.cxmxrgo.secondshift.registry;

import com.cxmxrgo.secondshift.SecondShift;
import com.cxmxrgo.secondshift.employee.EmployeeData;
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

    private ModAttachments() {}
}
