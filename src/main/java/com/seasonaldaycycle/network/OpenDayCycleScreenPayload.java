package com.seasonaldaycycle.network;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record OpenDayCycleScreenPayload() implements FabricPacket {
    public static final PacketType<OpenDayCycleScreenPayload> TYPE =
            PacketType.create(
                    new Identifier(SeasonalDayCycle.MODID, "open_daycycle_screen"),
                    OpenDayCycleScreenPayload::new
            );

    public OpenDayCycleScreenPayload(PacketByteBuf ignored) {
        this();
    }

    @Override
    public void write(PacketByteBuf buf) {
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
