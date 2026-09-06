package com.seasonaldaycycle.network;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record DayCycleLengthSyncPayload(int ticks) implements CustomPayload {
    public static final CustomPayload.Id<DayCycleLengthSyncPayload> ID =
            CustomPayload.id(SeasonalDayCycle.MODID + ":sync_cycle_length");

    public static final PacketCodec<RegistryByteBuf, DayCycleLengthSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            DayCycleLengthSyncPayload::ticks,
            DayCycleLengthSyncPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
