package com.seasonaldaycycle.network;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record DayCycleLengthSyncPayload(int ticks) implements FabricPacket {
    public static final PacketType<DayCycleLengthSyncPayload> TYPE =
            PacketType.create(
                    new Identifier(SeasonalDayCycle.MODID, "sync_cycle_length"),
                    DayCycleLengthSyncPayload::new
            );

    public DayCycleLengthSyncPayload(PacketByteBuf buf) {
        this(buf.readVarInt());
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeVarInt(ticks);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
