package com.cxmxrgo.secondshift.employee;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

/**
 * The employee record attached to a bound {@code minecraft:villager} (EMP-01, D-01/D-03/D-04).
 *
 * <p>This is the single data contract every later plan in this phase (spawn logic, network
 * bind trigger, {@code EmployeeManager}, {@code ServerPayloadHandler},
 * {@code ClientEmployeeSyncDebug}) reads and writes — copy this shape verbatim rather than
 * re-deriving it.
 *
 * <p>Deliberately does NOT yet contain happiness/timer fields — those belong to the
 * happiness/upkeep system (Phase 8/9). The only forward-looking piece of infrastructure here
 * is {@link #version()}, kept per CONTEXT.md's discretion note so a future schema change has
 * a migration seam without needing a breaking attachment-type rename.
 *
 * <p><b>Phase 6 addition (06-CONTEXT.md D-01):</b> {@link #altarPos()} is the employee's half of
 * the bidirectional altar↔employee link — every Phase 6 death/removal path needs to answer
 * "which altar hired this villager?", which the Phase 5 {@code employeeBound} boolean cannot
 * answer. {@code Optional} (not a sentinel position) because {@link #EMPTY} has no altar.
 *
 * <p><b>A hard constraint discovered during Phase 6 research:</b> {@link StreamCodec#composite}
 * in 1.21.1 has overloads for 1 through 6 components and no more — {@code altarPos} takes the
 * sixth and last slot. Any future field on this record (happiness, timers — Phase 9) cannot be
 * added by extending this composite chain; it will need a nested sub-record or a hand-written
 * {@code StreamCodec}.
 */
public record EmployeeData(
        int version,
        String name,
        ResourceLocation profession,
        int tier,
        MerchantOffers offers,
        Optional<BlockPos> altarPos
) {

    public static final EmployeeData EMPTY = new EmployeeData(
            1, "", ResourceLocation.withDefaultNamespace("none"), 0, new MerchantOffers(), Optional.empty());

    public static final Codec<EmployeeData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("version").forGetter(EmployeeData::version),
            Codec.STRING.fieldOf("name").forGetter(EmployeeData::name),
            ResourceLocation.CODEC.fieldOf("profession").forGetter(EmployeeData::profession),
            Codec.INT.fieldOf("tier").forGetter(EmployeeData::tier),
            MerchantOffers.CODEC.fieldOf("offers").forGetter(EmployeeData::offers),
            BlockPos.CODEC.optionalFieldOf("altarPos").forGetter(EmployeeData::altarPos)
    ).apply(inst, EmployeeData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EmployeeData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EmployeeData::version,
                    ByteBufCodecs.STRING_UTF8, EmployeeData::name,
                    ResourceLocation.STREAM_CODEC, EmployeeData::profession,
                    ByteBufCodecs.VAR_INT, EmployeeData::tier,
                    MerchantOffers.STREAM_CODEC, EmployeeData::offers,
                    ByteBufCodecs.optional(BlockPos.STREAM_CODEC.cast()), EmployeeData::altarPos,
                    EmployeeData::new);
}
