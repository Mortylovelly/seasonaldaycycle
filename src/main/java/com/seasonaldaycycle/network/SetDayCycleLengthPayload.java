package com.seasonaldaycycle.network;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record SetDayCycleLengthPayload(int ticks) implements FabricPacket {
    public static final PacketType<SetDayCycleLengthPayload> TYPE =
            PacketType.create(
                    new Identifier(SeasonalDayCycle.MODID, "set_cycle_length"),
                    SetDayCycleLengthPayload::new
            );

    public SetDayCycleLengthPayload(PacketByteBuf buf) {
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
