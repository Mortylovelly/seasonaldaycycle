package com.seasonaldaycycle.network;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Client request to set spring/summer/autumn/winter on the server. */
public record SetSeasonPayload(int seasonIndex) implements FabricPacket {
    public static final PacketType<SetSeasonPayload> TYPE =
            PacketType.create(
                    new Identifier(SeasonalDayCycle.MODID, "set_season"),
                    SetSeasonPayload::new
            );

    public SetSeasonPayload(PacketByteBuf buf) {
        this(buf.readVarInt());
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeVarInt(seasonIndex);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
