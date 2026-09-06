package com.seasonaldaycycle.network;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SetDayCycleLengthPayload(int ticks) implements CustomPayload {
    public static final CustomPayload.Id<SetDayCycleLengthPayload> ID =
            new CustomPayload.Id<>(Identifier.of(SeasonalDayCycle.MODID, "set_cycle_length"));

    public static final PacketCodec<RegistryByteBuf, SetDayCycleLengthPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            SetDayCycleLengthPayload::ticks,
            SetDayCycleLengthPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
