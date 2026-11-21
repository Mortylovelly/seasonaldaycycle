package com.seasonaldaycycle;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;
import net.minecraft.server.world.ServerWorld;

/**
 * Calendar progress stored by Minecraft's world PersistentStateManager.
 * The offset is saved as part of the normal world-save cycle instead of a separate
 * file, keeping it aligned with the world's saved time-of-day.
 */
public final class VertexCalendarState extends PersistentState {
    private static final String SAVE_ID = "vertex_calendar";
    private static final String KEY_VERSION = "version";
    private static final String KEY_DAY_OFFSET = "dayOffsetDays";
    private static final int VERSION = 1;

    private long dayOffsetDays;

    private VertexCalendarState(long dayOffsetDays, boolean dirty) {
        this.dayOffsetDays = DayCycleWorldConfig.sanitizeElapsedDayOffsetDays(dayOffsetDays);
        if (dirty) markDirty();
    }

    public static VertexCalendarState get(ServerWorld world, long legacyOffsetDays) {
        if (world == null) return new VertexCalendarState(legacyOffsetDays, false);
        return world.getPersistentStateManager().getOrCreate(
                nbt -> fromNbt(nbt, legacyOffsetDays),
                () -> new VertexCalendarState(legacyOffsetDays, true),
                SAVE_ID
        );
    }

    public long getDayOffsetDays() {
        return dayOffsetDays;
    }

    public void setDayOffsetDays(long value) {
        long safeValue = DayCycleWorldConfig.sanitizeElapsedDayOffsetDays(value);
        if (dayOffsetDays == safeValue) return;
        dayOffsetDays = safeValue;
        markDirty();
    }

    private static VertexCalendarState fromNbt(NbtCompound nbt, long legacyOffsetDays) {
        long offset = nbt.contains(KEY_DAY_OFFSET)
                ? nbt.getLong(KEY_DAY_OFFSET)
                : legacyOffsetDays;
        return new VertexCalendarState(offset, false);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putInt(KEY_VERSION, VERSION);
        nbt.putLong(KEY_DAY_OFFSET, dayOffsetDays);
        return nbt;
    }
}
