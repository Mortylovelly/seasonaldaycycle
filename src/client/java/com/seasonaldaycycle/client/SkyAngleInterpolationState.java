package com.seasonaldaycycle.client;

import net.minecraft.world.LunarWorldView;

import java.util.Map;
import java.util.WeakHashMap;

public final class SkyAngleInterpolationState {
    private static final long DAY_LENGTH = 24000L;
    private static final double SPEED_TRANSITION_TICKS = 10.0;

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
            state.visualSpeed = getExpectedGameTicksPerClientTick();
            state.targetSpeed = state.visualSpeed;
            return;
        }

        long rawDifference = serverTime - state.currentServerTime;
        long difference = normalizeDifference(rawDifference);
        long elapsedClientTicks = clientTick - state.anchorClientTick;

        if (elapsedClientTicks <= 0L) {
            return;
        }

        double expectedPerClientTick = getExpectedGameTicksPerClientTick();
        double expectedDifference = expectedPerClientTick * elapsedClientTicks;

        // At very high speed the server can advance a full 24000-tick day (or more)
        // between two client time-update packets, so normalized packet differences
        // can legitimately become 0 or otherwise wrap. In that case use the known
        // configured cycle speed instead of mistaking the wrap for a teleport.
        double newTargetSpeed = expectedPerClientTick;
        boolean packetCanMeasureSpeed = difference != 0L
                && expectedDifference > 0.0
                && Math.abs(expectedDifference) < DAY_LENGTH * 0.75
                && Math.abs(difference) <= DAY_LENGTH / 2L;

        if (packetCanMeasureSpeed) {
            double observedSpeed = (double) difference / (double) elapsedClientTicks;
            double lower = Math.max(0.0, expectedPerClientTick * 0.20);
            double upper = expectedPerClientTick * 5.0;
            if (observedSpeed >= lower && observedSpeed <= upper) {
                newTargetSpeed = observedSpeed;
            }
        }

        state.previousServerTime = state.currentServerTime;
        state.currentServerTime = serverTime;
        state.serverInterval = elapsedClientTicks;
        state.anchorClientTick = clientTick;
        state.targetSpeed = newTargetSpeed;
    }

    private static double getExpectedGameTicksPerClientTick() {
        long cycleTicks = SeasonalDayCycleClient.getKnownCycleLengthTicks();
        if (cycleTicks <= 0L) {
            cycleTicks = 72000L;
        }

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
            state.visualSpeed = getExpectedGameTicksPerClientTick();
            state.targetSpeed = state.visualSpeed;
        }

        double elapsed = Math.max(0.0, (clientTick - state.anchorClientTick) + tickDelta);
        double progress = Math.min(1.0, elapsed / SPEED_TRANSITION_TICKS);
        double eased = progress * progress * (3.0 - 2.0 * progress);
        double speed = state.visualSpeed + (state.targetSpeed - state.visualSpeed) * eased;

        if (progress >= 1.0) {
            state.visualSpeed = state.targetSpeed;
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
        private double visualSpeed;
        private double targetSpeed;
    }
}
