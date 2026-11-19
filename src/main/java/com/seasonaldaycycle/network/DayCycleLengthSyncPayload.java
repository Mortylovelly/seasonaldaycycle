package com.seasonaldaycycle.network;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record DayCycleLengthSyncPayload(int ticks, boolean canEdit,
                                        float springDayRatio, float summerDayRatio,
                                        float autumnDayRatio, float winterDayRatio) implements FabricPacket {
    public static final PacketType<DayCycleLengthSyncPayload> TYPE =
            PacketType.create(
                    new Identifier(SeasonalDayCycle.MODID, "sync_cycle_length"),
                    DayCycleLengthSyncPayload::new
            );

    public DayCycleLengthSyncPayload(PacketByteBuf buf) {
        this(buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeVarInt(ticks);
        buf.writeBoolean(canEdit);
        buf.writeFloat(springDayRatio);
        buf.writeFloat(summerDayRatio);
        buf.writeFloat(autumnDayRatio);
        buf.writeFloat(winterDayRatio);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
