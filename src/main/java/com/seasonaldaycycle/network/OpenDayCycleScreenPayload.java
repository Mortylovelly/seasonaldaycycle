package com.seasonaldaycycle.network;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenDayCycleScreenPayload() implements CustomPayload {
    public static final CustomPayload.Id<OpenDayCycleScreenPayload> ID =
            new CustomPayload.Id<>(Identifier.of(SeasonalDayCycle.MODID, "open_daycycle_screen"));

    public static final PacketCodec<RegistryByteBuf, OpenDayCycleScreenPayload> CODEC =
            PacketCodec.unit(new OpenDayCycleScreenPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
