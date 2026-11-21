package com.seasonaldaycycle.client;

import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.SereneSeasonsCompat;
import net.minecraft.world.LunarWorldView;
import net.minecraft.world.World;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Continuous client sky clock. The displayed sky follows the server's configured
 * day/night proportions, but speed changes are ramped independently from time.
 */
public final class SkyAngleInterpolationState {
    private static final double DAY_LENGTH = 24000.0;
    private static final double HALF_DAY = 12000.0;
    private static final double SMALL_PACKET_ERROR = 2.0;
    private static final double LARGE_PACKET_ERROR = 200.0;
    private static final int SEASON_REFRESH_TICKS = 20;
    private static final int CORRECTION_TICKS = 80;
    private static final int SPEED_TRANSITION_TICKS = 40;
    private static final int SLEEP_RESUME_TRANSITION_TICKS = 28;
    private static final long MAX_CATCH_UP_TICKS = 2400L;
    private static final Map<LunarWorldView, State> STATES = new WeakHashMap<>();

    private SkyAngleInterpolationState() {}

    public static synchronized State get(LunarWorldView world) {
        return STATES.computeIfAbsent(world, ignored -> new State());
    }

    public static synchronized void clearAll() {
        STATES.clear();
    }

    public static synchronized void acceptServerTime(LunarWorldView lunarWorld, long serverTime, long clientTick) {
        if (!(lunarWorld instanceof World world) || !world.isClient()) return;
        State state = get(lunarWorld);

        if (SeamlessSleepClientCompat.isSleepAnimationActive()) {
            synchronizeDuringSleep(state, world, world.getTimeOfDay(), clientTick);
            return;
        }
        if (!state.initialized) {
            initialize(state, serverTime, clientTick, world);
            return;
        }

        refreshSeason(state, world, clientTick);
        resumeAfterSleepIfNeeded(state);
        advanceWholeTicks(state, clientTick);
        double error = shortestPhaseDifference(serverTime, state.visualTime);

        // Normal packet rounding is ignored; small drift is corrected without reversing time.
        if (Math.abs(error) <= SMALL_PACKET_ERROR) return;

        if (Math.abs(error) >= LARGE_PACKET_ERROR) {
            // Explicit /time changes are real discontinuities, unlike speed-slider changes.
            state.visualTime += error;
            state.correctionRemaining = 0.0;
        } else {
            state.correctionRemaining = error;
        }
    }

    /** Returns the configured normal world-time speed at an explicit time of day. */
    public static synchronized double getConfiguredSpeed(LunarWorldView lunarWorld, long timeOfDay) {
        if (!(lunarWorld instanceof World world) || !world.isClient()) return 1.0D;
        String subSeason = readSubSeason(world);
        return getPhaseSpeed(timeOfDay, subSeason, SeasonalDayCycleClient.getKnownCycleLengthTicks());
    }

    public static synchronized double getVisualTime(LunarWorldView lunarWorld, long clientTick, float tickDelta) {
        State state = get(lunarWorld);
        if (!(lunarWorld instanceof World world) || !world.isClient()) {
            return lunarWorld.getLunarTime();
        }

        if (SeamlessSleepClientCompat.isSleepAnimationActive()) {
            double sleepTime = lunarWorld.getLunarTime();
            synchronizeDuringSleep(state, world, sleepTime, clientTick);
            return sleepTime;
        }

        if (!state.initialized) initialize(state, world.getLunarTime(), clientTick, world);
        refreshSeason(state, world, clientTick);
        resumeAfterSleepIfNeeded(state);
        advanceWholeTicks(state, clientTick);

        double partial = Math.max(0.0, Math.min(1.0, tickDelta));
        return state.visualTime + state.currentSpeed * partial;
    }

    private static void initialize(State state, long time, long clientTick, World world) {
        state.initialized = true;
        state.visualTime = time;
        state.lastClientTick = clientTick;
        state.lastSeasonRefresh = clientTick;
        state.subSeason = readSubSeason(world);
        state.correctionRemaining = 0.0;

        double initialSpeed = getPhaseSpeed(time, state.subSeason,
                SeasonalDayCycleClient.getKnownCycleLengthTicks());
        state.speedInitialized = true;
        state.currentSpeed = initialSpeed;
        state.transitionStartSpeed = initialSpeed;
        state.targetSpeed = initialSpeed;
        state.speedTransitionElapsed = SPEED_TRANSITION_TICKS;
        state.speedTransitionDurationTicks = SPEED_TRANSITION_TICKS;
        state.sleepResumeTransition = false;
        state.sleepNaturalReleasePending = false;
    }

    private static void synchronizeDuringSleep(State state, World world, double sleepTime, long clientTick) {
        state.sleepAnimationWasActive = true;
        if (!state.initialized) {
            initialize(state, (long) Math.floor(sleepTime), clientTick, world);
            state.visualTime = sleepTime;
            return;
        }

        // Seamless Sleep owns the world clock during its own eased transition.
        // Keep the exact fractional position and resume from there when it finishes.
        state.visualTime = sleepTime;
        state.lastClientTick = clientTick;
        state.lastSeasonRefresh = clientTick;
        state.subSeason = readSubSeason(world);
        state.correctionRemaining = 0.0;
    }

    /**
     * Anchors the client visual clock to the exact sunrise before leaving
     * Seamless Sleep's clock ownership and beginning Vertex's normal-speed ramp.
     */
    public static synchronized void finishSleepTransition(
            LunarWorldView lunarWorld, long sunriseTime, long clientTick) {
        if (!(lunarWorld instanceof World world) || !world.isClient()) return;

        State state = get(lunarWorld);
        if (!state.initialized) {
            initialize(state, sunriseTime, clientTick, world);
        }
        state.visualTime = sunriseTime;
        state.lastClientTick = clientTick;
        state.lastSeasonRefresh = clientTick;
        state.subSeason = readSubSeason(world);
        state.correctionRemaining = 0.0;
        state.sleepAnimationWasActive = true;
        state.sleepNaturalReleasePending = true;
    }

    private static void resumeAfterSleepIfNeeded(State state) {
        if (!state.sleepAnimationWasActive) return;
        state.sleepAnimationWasActive = false;
        state.sleepNaturalReleasePending = false;

        // The sleep curve already reaches the configured normal velocity exactly.
        // Continue with that same velocity instead of restarting from near zero.
        double target = getPhaseSpeed(state.visualTime, state.subSeason,
                SeasonalDayCycleClient.getKnownCycleLengthTicks());
        state.currentSpeed = Math.max(1.0E-9, target);
        state.transitionStartSpeed = state.currentSpeed;
        state.targetSpeed = state.currentSpeed;
        state.speedTransitionElapsed = SPEED_TRANSITION_TICKS;
        state.speedTransitionDurationTicks = SPEED_TRANSITION_TICKS;
        state.speedInitialized = true;
        state.sleepResumeTransition = false;
    }

    private static void refreshSeason(State state, World world, long clientTick) {
        if (state.subSeason == null || clientTick - state.lastSeasonRefresh >= SEASON_REFRESH_TICKS) {
            state.subSeason = readSubSeason(world);
            state.lastSeasonRefresh = clientTick;
        }
    }

    private static String readSubSeason(World world) {
        SereneSeasonsCompat.SeasonInfo info = SereneSeasonsCompat.getSeasonInfo(world);
        return info.available() ? info.subSeasonName() : null;
    }

    private static void advanceWholeTicks(State state, long clientTick) {
        long elapsed = clientTick - state.lastClientTick;
        if (elapsed <= 0L) return;

        long cycleTicks = SeasonalDayCycleClient.getKnownCycleLengthTicks();
        long ticksToSimulate = Math.min(elapsed, MAX_CATCH_UP_TICKS);
        for (long i = 0L; i < ticksToSimulate; i++) {
            advanceOneTick(state, cycleTicks);
        }

        // A long frame stall must not freeze or rewind the visual clock.
        if (elapsed > ticksToSimulate) {
            state.visualTime += (elapsed - ticksToSimulate) * state.currentSpeed;
        }
        state.lastClientTick = clientTick;
    }

    private static void advanceOneTick(State state, long cycleTicks) {
        double target = getPhaseSpeed(state.visualTime, state.subSeason, cycleTicks);
        updateSpeed(state, target);
        state.visualTime += state.currentSpeed;

        if (state.correctionRemaining != 0.0) {
            // Corrections can alter speed slightly, but can never stop or reverse the clock.
            double maxStep = Math.max(1.0E-9, state.currentSpeed * 0.10);
            double step = Math.max(-maxStep,
                    Math.min(maxStep, state.correctionRemaining / CORRECTION_TICKS));
            state.visualTime += step;
            state.correctionRemaining -= step;
            if (Math.abs(state.correctionRemaining) < 0.01) state.correctionRemaining = 0.0;
        }
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
            state.sleepResumeTransition = false;
            return;
        }

        double difference = Math.abs(targetSpeed - state.targetSpeed);
        double threshold = Math.max(1.0E-9, Math.abs(targetSpeed) * 1.0E-5);
        if (difference > threshold) {
            // If the slider moves again mid-ramp, continue from the current velocity.
            state.transitionStartSpeed = state.currentSpeed;
            state.targetSpeed = targetSpeed;
            state.speedTransitionElapsed = 0;
            if (!state.sleepResumeTransition) {
                state.speedTransitionDurationTicks = SPEED_TRANSITION_TICKS;
            }
        }

        int transitionTicks = Math.max(1, state.speedTransitionDurationTicks);
        if (state.speedTransitionElapsed < transitionTicks) {
            state.speedTransitionElapsed++;
            double progress = state.speedTransitionElapsed / (double) transitionTicks;
            double eased = smootherStep(progress);
            state.currentSpeed = state.transitionStartSpeed
                    + (state.targetSpeed - state.transitionStartSpeed) * eased;
            if (state.speedTransitionElapsed >= transitionTicks && state.sleepResumeTransition) {
                state.sleepResumeTransition = false;
                state.speedTransitionDurationTicks = SPEED_TRANSITION_TICKS;
            }
        } else {
            state.currentSpeed = state.targetSpeed;
        }

        state.currentSpeed = Math.max(1.0E-9, state.currentSpeed);
    }

    private static double smootherStep(double value) {
        double x = Math.max(0.0, Math.min(1.0, value));
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    private static double getPhaseSpeed(double time, String subSeason, long cycleTicks) {
        long safeCycle = ModConfig.sanitizeCycleLengthTicks(cycleTicks);
        boolean day = positiveModulo(time, DAY_LENGTH) < HALF_DAY;
        double phaseTicks = day
                ? ModConfig.getDayTicksForSubSeason(subSeason, safeCycle)
                : ModConfig.getNightTicksForSubSeason(subSeason, safeCycle);
        if (!Double.isFinite(phaseTicks) || phaseTicks < 1.0) {
            double average = averageSpeed(subSeason, safeCycle);
            return Double.isFinite(average) && average > 0.0 ? average : 1.0 / safeCycle;
        }
        return HALF_DAY / phaseTicks;
    }

    private static double averageSpeed(String subSeason, long cycleTicks) {
        long safeCycle = ModConfig.sanitizeCycleLengthTicks(cycleTicks);
        double dayTicks = ModConfig.getDayTicksForSubSeason(subSeason, safeCycle);
        double nightTicks = ModConfig.getNightTicksForSubSeason(subSeason, safeCycle);
        double total = dayTicks + nightTicks;
        return total > 0.0 && Double.isFinite(total) ? DAY_LENGTH / total : DAY_LENGTH / safeCycle;
    }

    private static double shortestPhaseDifference(double serverTime, double visualTime) {
        double difference = (serverTime - visualTime) % DAY_LENGTH;
        if (difference > HALF_DAY) difference -= DAY_LENGTH;
        if (difference < -HALF_DAY) difference += DAY_LENGTH;
        return difference;
    }

    private static double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0 ? result + modulus : result;
    }

    public static long normalizeDifference(long difference) {
        if (difference > DAY_LENGTH / 2.0) return difference - (long) DAY_LENGTH;
        if (difference < -DAY_LENGTH / 2.0) return difference + (long) DAY_LENGTH;
        return difference;
    }

    public static final class State {
        private boolean initialized;
        private double visualTime;
        private long lastClientTick;
        private long lastSeasonRefresh;
        private String subSeason;
        private double correctionRemaining;

        private boolean speedInitialized;
        private double currentSpeed;
        private double transitionStartSpeed;
        private double targetSpeed;
        private int speedTransitionElapsed;
        private int speedTransitionDurationTicks = SPEED_TRANSITION_TICKS;
        private boolean sleepAnimationWasActive;
        private boolean sleepNaturalReleasePending;
        private boolean sleepResumeTransition;
    }
}
