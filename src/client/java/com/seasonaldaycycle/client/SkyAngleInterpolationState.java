package com.seasonaldaycycle.client;

import net.minecraft.world.LunarWorldView;

import java.util.Map;
import java.util.WeakHashMap;

public final class SkyAngleInterpolationState {
    private static final long DAY_LENGTH = 24000L;
    private static final long EXTERNAL_CHANGE_THRESHOLD = 200L;

    private static final Map<LunarWorldView, State> STATES = new WeakHashMap<>();

    private SkyAngleInterpolationState() {}

    public static synchronized State get(LunarWorldView world) {
        return STATES.computeIfAbsent(world, ignored -> new State());
    }

    public static long normalizeDifference(long difference) {
        if (difference > DAY_LENGTH / 2L) {
            return difference - DAY_LENGTH;
        }
        if (difference < -DAY_LENGTH / 2L) {
            return difference + DAY_LENGTH;
        }
        return difference;
    }

    public static boolean isExternalChange(long difference) {
        return Math.abs(difference) > EXTERNAL_CHANGE_THRESHOLD;
    }

    public static final class State {
        public boolean initialized;
        public long previousTime;
        public long currentTime;
    }
}
