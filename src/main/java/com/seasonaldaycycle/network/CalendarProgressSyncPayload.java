package com.seasonaldaycycle.network;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * Server-authoritative calendar offset and absolute visual time.
 * Sending the absolute value lets connected clients re-anchor without phase-only
 * corrections accidentally moving the calendar across a day boundary.
 */
public record CalendarProgressSyncPayload(long dayOffsetDays, long calendarTime) implements FabricPacket {
    public static final PacketType<CalendarProgressSyncPayload> TYPE = PacketType.create(
            new Identifier(SeasonalDayCycle.MODID, "sync_calendar_progress"),
            CalendarProgressSyncPayload::new
    );

    public CalendarProgressSyncPayload(PacketByteBuf buf) {
        this(buf.readLong(), buf.readLong());
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeLong(dayOffsetDays);
        buf.writeLong(calendarTime);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
