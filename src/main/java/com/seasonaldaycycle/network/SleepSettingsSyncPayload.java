package com.seasonaldaycycle.network;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Server-authoritative availability and settings for the optional sleep integration. */
public record SleepSettingsSyncPayload(boolean available, float multiplier, boolean canEdit)
        implements FabricPacket {
    public static final PacketType<SleepSettingsSyncPayload> TYPE = PacketType.create(
            new Identifier(SeasonalDayCycle.MODID, "sync_sleep_settings"),
            SleepSettingsSyncPayload::new
    );

    public SleepSettingsSyncPayload(PacketByteBuf buf) {
        this(buf.readBoolean(), buf.readFloat(), buf.readBoolean());
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeBoolean(available);
        buf.writeFloat(multiplier);
        buf.writeBoolean(canEdit);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
