package com.cxmxrgo.secondshift.network;

import com.cxmxrgo.secondshift.SecondShift;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Client-to-server "confirm the chosen trades" payload (Plan 05-05, GUI-02) — replaces Plan
 * 04-03's zero-field {@code BindEmployeePayload}.
 *
 * <p>Carries the client's claimed selection ({@code indices}, into the server's own materialized
 * candidate pool) and the client's typed {@code name}. Both are untrusted input: {@link
 * ServerPayloadHandler#handleSelectTrades} re-validates every index against {@code
 * BindingAltarMenu#getCandidateOffers()} and sanitizes/caps the name server-side before ever
 * touching persistent state (RESEARCH.md Finding 3).
 */
public record SelectTradesPayload(int[] indices, String name) implements CustomPacketPayload {

    public static final Type<SelectTradesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SecondShift.MODID, "select_trades"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectTradesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.<RegistryFriendlyByteBuf, Integer, List<Integer>>collection(
                                    ArrayList::new, ByteBufCodecs.VAR_INT)
                            .map(list -> list.stream().mapToInt(Integer::intValue).toArray(),
                                    arr -> Arrays.stream(arr).boxed().toList()),
                    SelectTradesPayload::indices,
                    // Generous wire-level cap (defense in depth) — the real 32-char semantic cap is
                    // enforced server-side by ServerPayloadHandler#sanitizeName.
                    ByteBufCodecs.stringUtf8(64),
                    SelectTradesPayload::name,
                    SelectTradesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
