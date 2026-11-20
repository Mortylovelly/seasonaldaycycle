package com.seasonaldaycycle.network;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Client request to change Seamless Sleep's server-side transition duration. */
public record SetSleepDurationPayload(float multiplier) implements FabricPacket {
    public static final PacketType<SetSleepDurationPayload> TYPE = PacketType.create(
            new Identifier(SeasonalDayCycle.MODID, "set_sleep_duration"),
            SetSleepDurationPayload::new
    );

    public SetSleepDurationPayload(PacketByteBuf buf) {
        this(buf.readFloat());
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeFloat(multiplier);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
