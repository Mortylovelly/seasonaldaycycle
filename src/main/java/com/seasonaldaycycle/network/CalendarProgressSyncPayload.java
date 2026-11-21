package com.seasonaldaycycle.network;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Server-authoritative day offset used to restore calendar progress on new connections. */
public record CalendarProgressSyncPayload(long dayOffsetDays, boolean initialSync) implements FabricPacket {
    public static final PacketType<CalendarProgressSyncPayload> TYPE = PacketType.create(
            new Identifier(SeasonalDayCycle.MODID, "sync_calendar_progress"),
            CalendarProgressSyncPayload::new
    );

    public CalendarProgressSyncPayload(PacketByteBuf buf) {
        this(buf.readLong(), buf.readBoolean());
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeLong(dayOffsetDays);
        buf.writeBoolean(initialSync);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
