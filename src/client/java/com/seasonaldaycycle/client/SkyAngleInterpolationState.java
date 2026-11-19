package com.seasonaldaycycle.client;

import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.SereneSeasonsCompat;
import net.minecraft.world.LunarWorldView;
import net.minecraft.world.World;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Continuous client sky clock driven by the exact configured length of each
 * phase. Integer time packets are used only for slow phase correction, never
 * for estimating instantaneous speed (which would oscillate at long cycles).
 */
public final class SkyAngleInterpolationState {
    private static final double DAY_LENGTH = 24000.0;
    private static final double HALF_DAY = 12000.0;
    private static final double SMALL_PACKET_ERROR = 2.0;
    private static final double LARGE_PACKET_ERROR = 200.0;
    private static final int SEASON_REFRESH_TICKS = 20;
    private static final int CORRECTION_TICKS = 80;
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
        if (!state.initialized) {
            initialize(state, serverTime, clientTick, world);
            return;
        }

        refreshSeason(state, world, clientTick);
        advanceWholeTicks(state, clientTick);
        double error = shortestPhaseDifference(serverTime, state.visualTime);

        // Integer rounding at the network boundary can be almost one game tick.
        if (Math.abs(error) <= SMALL_PACKET_ERROR) return;

        if (Math.abs(error) >= LARGE_PACKET_ERROR) {
            // A real /time set or other explicit discontinuity, not normal packet jitter.
            state.visualTime += error;
            state.correctionRemaining = 0.0;
        } else {
            state.correctionRemaining = error;
        }
    }

    public static synchronized double getVisualTime(LunarWorldView lunarWorld, long clientTick, float tickDelta) {
        State state = get(lunarWorld);
        if (!(lunarWorld instanceof World world) || !world.isClient()) {
            return lunarWorld.getLunarTime();
        }
        if (!state.initialized) initialize(state, world.getLunarTime(), clientTick, world);

        refreshSeason(state, world, clientTick);
        advanceWholeTicks(state, clientTick);
        double partial = Math.max(0.0, Math.min(1.0, tickDelta));
        return advance(state.visualTime, partial, state.subSeason, SeasonalDayCycleClient.getKnownCycleLengthTicks());
    }

    private static void initialize(State state, long time, long clientTick, World world) {
        state.initialized = true;
        state.visualTime = time;
        state.lastClientTick = clientTick;
        state.lastSeasonRefresh = clientTick;
        state.subSeason = readSubSeason(world);
        state.correctionRemaining = 0.0;
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
        state.visualTime = advance(state.visualTime, elapsed, state.subSeason, cycleTicks);

        if (state.correctionRemaining != 0.0) {
            double phaseSpeed = getPhaseSpeed(state.visualTime, state.subSeason, cycleTicks);
            // Keep the sky moving forward; phase corrections must not create a visible reversal.
            double maxStep = Math.max(0.000001, phaseSpeed * 0.15);
            double step = Math.max(-maxStep,
                    Math.min(maxStep, state.correctionRemaining / CORRECTION_TICKS));
            state.visualTime += step;
            state.correctionRemaining -= step;
            if (Math.abs(state.correctionRemaining) < 0.01) state.correctionRemaining = 0.0;
        }
        state.lastClientTick = clientTick;
    }

    private static double advance(double startTime, double clientTicks, String subSeason, long cycleTicks) {
        if (clientTicks <= 0.0) return startTime;
        double time = startTime;
        double remaining = clientTicks;

        // At normal settings only one or two boundaries are crossed per tick.
        // The guard protects against pathological configs with extremely short phases.
        for (int i = 0; remaining > 1.0E-9 && i < 8192; i++) {
            double phase = positiveModulo(time, DAY_LENGTH);
            boolean day = phase < HALF_DAY;
            double speed = getPhaseSpeed(time, subSeason, cycleTicks);
            double untilBoundary = day ? (HALF_DAY - phase) / speed : (DAY_LENGTH - phase) / speed;

            if (!Double.isFinite(untilBoundary) || untilBoundary <= 1.0E-9) {
                time += remaining * averageSpeed(subSeason, cycleTicks);
                return time;
            }

            double step = Math.min(remaining, untilBoundary);
            time += step * speed;
            remaining -= step;
        }

        if (remaining > 1.0E-9) time += remaining * averageSpeed(subSeason, cycleTicks);
        return time;
    }

    private static double getPhaseSpeed(double time, String subSeason, long cycleTicks) {
        long safeCycle = ModConfig.sanitizeCycleLengthTicks(cycleTicks);
        boolean day = positiveModulo(time, DAY_LENGTH) < HALF_DAY;
        double phaseTicks = day
                ? ModConfig.getDayTicksForSubSeason(subSeason, safeCycle)
                : ModConfig.getNightTicksForSubSeason(subSeason, safeCycle);
        if (!Double.isFinite(phaseTicks) || phaseTicks < 1.0) {
            return averageSpeed(subSeason, safeCycle);
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
    }
}
