package com.seasonaldaycycle;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.Map;
import java.util.WeakHashMap;

public final class DayCycleHandler {
    private static final Map<ServerWorld, State> STATES = new WeakHashMap<>();
    private static final long VANILLA_DAY_END = 12000L;
    private static final long VANILLA_CYCLE = 24000L;
    private static final long EXTERNAL_CHANGE_THRESHOLD = 200L;
    private static final int SPEED_TRANSITION_TICKS = 40;

    private DayCycleHandler() {}

    public static void onWorldTick(ServerWorld level) {
        if (level.getRegistryKey() != World.OVERWORLD) return;
        if (!level.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) return;

        State state = STATES.computeIfAbsent(level, ignored -> new State());
        long currentTime = level.getTimeOfDay();

        if (!state.seasonInitialized || ++state.seasonRefreshTicks >= 20) {
            state.cachedSubSeason = getCurrentSubSeason(level);
            state.seasonInitialized = true;
            state.seasonRefreshTicks = 0;
        }

        // Seamless Sleep owns the clock for the complete animation. Its own state
        // now applies one continuous curve which reaches the configured normal
        // day speed before the player is released from bed.
        if (SeamlessSleepCompat.isServerSleepAnimationActive(level)) {
            state.sleepAnimationWasActive = true;
            state.timeDecimalAccumulator = 0.0;
            state.lastKnownTime = currentTime;
            return;
        }

        if (state.sleepAnimationWasActive) {
            state.sleepAnimationWasActive = false;
            state.timeDecimalAccumulator = 0.0;

            // Seamless Sleep suppresses vanilla's daylight tick while its animation
            // is active. On this handoff tick there is therefore no vanilla +1 to
            // cancel: continue from the exact final sleep time without subtracting
            // a tick, which previously caused a tiny backwards snap on wake-up.
            double resumeSpeed = getTargetSpeed(currentTime, state.cachedSubSeason,
                    DayCycleWorldConfig.getCycleLengthTicks(level));
            state.currentSpeed = Math.max(1.0E-9, resumeSpeed);
            state.transitionStartSpeed = state.currentSpeed;
            state.targetSpeed = state.currentSpeed;
            state.speedTransitionElapsed = SPEED_TRANSITION_TICKS;
            state.speedTransitionDurationTicks = SPEED_TRANSITION_TICKS;
            state.speedInitialized = true;
            state.forceSpeedTransition = false;

            state.timeDecimalAccumulator += state.currentSpeed;
            long toAdd = (long) Math.floor(state.timeDecimalAccumulator);
            state.timeDecimalAccumulator -= toAdd;
            long resumedTime = currentTime + toAdd;
            if (resumedTime < 0L) resumedTime = Math.floorMod(resumedTime, VANILLA_CYCLE);
            level.setTimeOfDay(resumedTime);
            state.lastKnownTime = resumedTime;
            return;
        }

        double targetSpeed = getTargetSpeed(currentTime, state.cachedSubSeason,
                DayCycleWorldConfig.getCycleLengthTicks(level));

        // Don't mistake a naturally fast cycle (down to one real second) for /time set.
        // Compare the observed advance with the speed we actually applied last tick.
        if (state.lastKnownTime >= 0L && state.speedInitialized) {
            long observedAdvance = Math.abs(currentTime - state.lastKnownTime);
            long expectedAdvance = Math.max(1L, Math.round(state.currentSpeed) + 1L);
            long deviation = Math.abs(observedAdvance - expectedAdvance);
            if (observedAdvance > EXTERNAL_CHANGE_THRESHOLD
                    && observedAdvance < VANILLA_CYCLE - EXTERNAL_CHANGE_THRESHOLD
                    && deviation > EXTERNAL_CHANGE_THRESHOLD) {
                state.timeDecimalAccumulator = 0.0;
                state.lastKnownTime = currentTime;
                return;
            }
        }

        updateSpeed(state, targetSpeed);

        // Cancel vanilla's one-tick step; our fractional accumulator owns the clock.
        level.setTimeOfDay(currentTime - 1L);
        currentTime--;

        state.timeDecimalAccumulator += state.currentSpeed;
        long toAdd = (long) Math.floor(state.timeDecimalAccumulator);
        state.timeDecimalAccumulator -= toAdd;

        long newTime = currentTime + toAdd;
        if (newTime < 0L) newTime = Math.floorMod(newTime, VANILLA_CYCLE);

        level.setTimeOfDay(newTime);
        state.lastKnownTime = newTime;
    }

    /** Returns the configured normal world-time speed at an explicit time of day. */
    public static double getConfiguredSpeed(ServerWorld level, long timeOfDay) {
        if (level == null || !level.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) {
            return 0.0;
        }
        State state = STATES.get(level);
        String subSeason = state != null && state.seasonInitialized
                ? state.cachedSubSeason
                : getCurrentSubSeason(level);
        return getTargetSpeed(timeOfDay, subSeason, DayCycleWorldConfig.getCycleLengthTicks(level));
    }

    private static double getTargetSpeed(long currentTime, String subSeason, long cycleLengthTicks) {
        long timeInDay = Math.floorMod(currentTime, VANILLA_CYCLE);
        boolean isDay = timeInDay < VANILLA_DAY_END;
        double phaseTicks = isDay
                ? getRealDayTicks(subSeason, cycleLengthTicks)
                : getRealNightTicks(subSeason, cycleLengthTicks);
        return 12000.0 / Math.max(1.0, phaseTicks);
    }

    private static void updateSpeed(State state, double targetSpeed) {
        if (!Double.isFinite(targetSpeed) || targetSpeed <= 0.0) {
            targetSpeed = 1.0 / 24000.0;
        }

        if (!state.speedInitialized) {
            state.speedInitialized = true;
            state.currentSpeed = targetSpeed;
            state.transitionStartSpeed = targetSpeed;
            state.targetSpeed = targetSpeed;
            state.speedTransitionElapsed = SPEED_TRANSITION_TICKS;
            state.speedTransitionDurationTicks = SPEED_TRANSITION_TICKS;
            return;
        }

        double difference = Math.abs(targetSpeed - state.targetSpeed);
        double threshold = Math.max(1.0E-9, Math.abs(targetSpeed) * 1.0E-5);
        if (state.forceSpeedTransition || difference > threshold) {
            // Restart the ramp from the current speed. Rapid slider changes therefore
            // never reset the world clock or snap the sun to a new position.
            state.transitionStartSpeed = state.currentSpeed;
            state.targetSpeed = targetSpeed;
            state.speedTransitionElapsed = 0;
            state.speedTransitionDurationTicks = SPEED_TRANSITION_TICKS;
            state.forceSpeedTransition = false;
        }

        int transitionTicks = Math.max(1, state.speedTransitionDurationTicks);
        if (state.speedTransitionElapsed < transitionTicks) {
            state.speedTransitionElapsed++;
            double progress = state.speedTransitionElapsed / (double) transitionTicks;
            double eased = smootherStep(progress);
            state.currentSpeed = state.transitionStartSpeed
                    + (state.targetSpeed - state.transitionStartSpeed) * eased;
        } else {
            state.currentSpeed = state.targetSpeed;
        }

        state.currentSpeed = Math.max(1.0E-9, state.currentSpeed);
    }

    private static double smootherStep(double value) {
        double x = Math.max(0.0, Math.min(1.0, value));
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    public static String getCurrentSubSeason(ServerWorld level) {
        SereneSeasonsCompat.SeasonInfo info = SereneSeasonsCompat.getSeasonInfo(level);
        return info.available() ? info.subSeasonName() : null;
    }

    public static void invalidateSeasonCache(ServerWorld level) {
        State state = STATES.get(level);
        if (state != null) state.seasonInitialized = false;
    }

    public static double getRealDayTicks(String subSeason) {
        return getRealDayTicks(subSeason, ModConfig.getCycleLengthTicks());
    }

    public static double getRealDayTicks(String subSeason, long cycleLengthTicks) {
        if (subSeason == null) return ModConfig.getSpringDayTicks(cycleLengthTicks);
        return switch (subSeason) {
            case "EARLY_SPRING", "MID_SPRING", "LATE_SPRING" -> ModConfig.getSpringDayTicks(cycleLengthTicks);
            case "EARLY_SUMMER", "MID_SUMMER", "LATE_SUMMER" -> ModConfig.getSummerDayTicks(cycleLengthTicks);
            case "EARLY_AUTUMN", "MID_AUTUMN", "LATE_AUTUMN" -> ModConfig.getAutumnDayTicks(cycleLengthTicks);
            case "EARLY_WINTER", "MID_WINTER", "LATE_WINTER" -> ModConfig.getWinterDayTicks(cycleLengthTicks);
            default -> ModConfig.getSpringDayTicks(cycleLengthTicks);
        };
    }

    public static double getRealNightTicks(String subSeason) {
        return getRealNightTicks(subSeason, ModConfig.getCycleLengthTicks());
    }

    public static double getRealNightTicks(String subSeason, long cycleLengthTicks) {
        if (subSeason == null) return ModConfig.getSpringNightTicks(cycleLengthTicks);
        return switch (subSeason) {
            case "EARLY_SPRING", "MID_SPRING", "LATE_SPRING" -> ModConfig.getSpringNightTicks(cycleLengthTicks);
            case "EARLY_SUMMER", "MID_SUMMER", "LATE_SUMMER" -> ModConfig.getSummerNightTicks(cycleLengthTicks);
            case "EARLY_AUTUMN", "MID_AUTUMN", "LATE_AUTUMN" -> ModConfig.getAutumnNightTicks(cycleLengthTicks);
            case "EARLY_WINTER", "MID_WINTER", "LATE_WINTER" -> ModConfig.getWinterNightTicks(cycleLengthTicks);
            default -> ModConfig.getSpringNightTicks(cycleLengthTicks);
        };
    }

    private static final class State {
        double timeDecimalAccumulator = 0.0;
        long lastKnownTime = -1L;
        String cachedSubSeason;
        int seasonRefreshTicks;
        boolean seasonInitialized;
        boolean sleepAnimationWasActive;

        boolean speedInitialized;
        double currentSpeed;
        double transitionStartSpeed;
        double targetSpeed;
        int speedTransitionElapsed;
        int speedTransitionDurationTicks = SPEED_TRANSITION_TICKS;
        boolean forceSpeedTransition;
    }
}
