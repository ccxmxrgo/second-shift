package com.cxmxrgo.secondshift.network;

import com.cxmxrgo.secondshift.SecondShift;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server "Confirm Hire" trigger (Plan 04-03, D-01, T-4-01 mitigation).
 *
 * <p>Deliberately a zero-field record — the bind position is never sent over the wire. The
 * server re-derives it exclusively from the sending player's currently-open
 * {@link com.cxmxrgo.secondshift.menu.BindingAltarMenu} server-side state
 * ({@link ServerPayloadHandler#handleBindEmployee}), so there is no client-supplied position to
 * spoof.
 */
public record BindEmployeePayload() implements CustomPacketPayload {

    public static final Type<BindEmployeePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SecondShift.MODID, "bind_employee"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BindEmployeePayload> STREAM_CODEC =
            StreamCodec.unit(new BindEmployeePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
