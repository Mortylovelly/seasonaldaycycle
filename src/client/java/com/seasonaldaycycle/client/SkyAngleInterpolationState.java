package com.seasonaldaycycle.client;

import net.minecraft.world.LunarWorldView;

import java.util.Map;
import java.util.WeakHashMap;

public final class SkyAngleInterpolationState {
    private static final long DAY_LENGTH = 24000L;

    private static final Map<LunarWorldView, State> STATES = new WeakHashMap<>();

    private SkyAngleInterpolationState() {}

    public static synchronized State get(LunarWorldView world) {
        return STATES.computeIfAbsent(world, ignored -> new State());
    }

    public static synchronized void acceptServerTime(LunarWorldView world, long serverTime, long clientTick) {
        State state = get(world);

        if (!state.initialized) {
            state.initialized = true;
            state.previousServerTime = serverTime;
            state.currentServerTime = serverTime;
            state.serverInterval = 20.0;
            state.anchorClientTick = clientTick;
            return;
        }

        long rawDifference = serverTime - state.currentServerTime;
        long difference = normalizeDifference(rawDifference);

        long elapsedClientTicks = clientTick - state.anchorClientTick;
        if (elapsedClientTicks <= 0L || difference == 0L) {
            return;
        }

        // Detect an actual external /time-like jump by comparing the received
        // movement against the speed expected from the configured real-time cycle.
        // This keeps high-speed cycles smooth while still snapping true external
        // time changes instead of animating them as if they were normal progression.
        double expectedPerClientTick = getExpectedGameTicksPerClientTick();
        double expectedDifference = expectedPerClientTick * elapsedClientTicks;
        boolean looksExternal = expectedDifference > 0.0
                && (Math.abs(difference) < expectedDifference * 0.35
                || Math.abs(difference) > expectedDifference * 2.5);

        if (looksExternal && Math.abs(difference) > 200L) {
            state.previousServerTime = serverTime;
            state.currentServerTime = serverTime;
            state.serverInterval = 20.0;
            state.anchorClientTick = clientTick;
            return;
        }

        state.previousServerTime = state.currentServerTime;
        state.currentServerTime = serverTime;
        state.serverInterval = elapsedClientTicks;
        state.anchorClientTick = clientTick;
    }

    private static double getExpectedGameTicksPerClientTick() {
        long cycleTicks = SeasonalDayCycleClient.getKnownCycleLengthTicks();
        if (cycleTicks <= 0L) {
            cycleTicks = 72000L;
        }

        // cycleTicks is real client/server ticks for one complete Minecraft day.
        // 24000 game-time ticks occur during one complete cycle.
        return 24000.0 / cycleTicks;
    }

    public static synchronized double getVisualTime(LunarWorldView world, long clientTick, float tickDelta) {
        State state = get(world);

        if (!state.initialized) {
            long currentTime = world.getLunarTime();
            state.initialized = true;
            state.previousServerTime = currentTime;
            state.currentServerTime = currentTime;
            state.serverInterval = 20.0;
            state.anchorClientTick = clientTick;
        }

        double elapsed = Math.max(0.0, (clientTick - state.anchorClientTick) + tickDelta);
        double speed = 0.0;

        long serverDifference = normalizeDifference(state.currentServerTime - state.previousServerTime);
        if (state.serverInterval > 0.0) {
            speed = serverDifference / state.serverInterval;
        }

        return state.currentServerTime + elapsed * speed;
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

    public static final class State {
        private boolean initialized;
        private long previousServerTime;
        private long currentServerTime;
        private double serverInterval;
        private long anchorClientTick;
    }
}
