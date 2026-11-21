package com.seasonaldaycycle.client;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Client-only reflection bridge for Seamless Sleep's sleep transition. */
public final class SeamlessSleepClientCompat {
    private static final String MOD_ID = "seamlesssleep";
    private static final int WAKE_SETTLE_TICKS = 40;

    private static volatile boolean resolved;
    private static volatile boolean failureLogged;
    private static Field animationStateField;
    private static Field animationActiveField;
    private static Field animationDurationTicksField;
    private static Field animationStartMillisField;
    private static Field replayCompatModeField;
    private static Method animationIsActive;

    private SeamlessSleepClientCompat() {
    }

    /**
     * True only while Seamless Sleep is still animating the accelerated night skip.
     * During the final wake-settling window, Vertex resumes its own smooth world clock.
     */
    public static boolean isSleepAnimationActive() {
        Object state = getAnimationState();
        if (state == null) return false;

        try {
            // The client must follow Seamless Sleep's world clock through the
            // complete normal-speed resting phase, not just the accelerated skip.
            return Boolean.TRUE.equals(animationIsActive.invoke(state));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailureOnce(exception);
            return false;
        }
    }

    /**
     * Returns animation timing for the HUD. Replay timelines are intentionally
     * omitted because their clock is not tied to wall time.
     */
    public static SleepProgress getSleepAnimationProgress() {
        Object state = getAnimationState();
        if (state == null) return null;

        try {
            if (!Boolean.TRUE.equals(animationIsActive.invoke(state))) return null;
            if (replayCompatModeField.getBoolean(state)) return null;

            int durationTicks = Math.max(1, animationDurationTicksField.getInt(state));
            long elapsedMillis = Math.max(0L,
                    System.currentTimeMillis() - animationStartMillisField.getLong(state));
            return new SleepProgress(Math.min(durationTicks, elapsedMillis / 50.0), durationTicks);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailureOnce(exception);
            return null;
        }
    }

    private static Object getAnimationState() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return null;
        try {
            resolve();
            Object state = animationStateField.get(null);
            if (state == null || !animationActiveField.getBoolean(state)) return null;
            return state;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailureOnce(exception);
            return null;
        }
    }

    private static synchronized void resolve() throws ReflectiveOperationException {
        if (resolved) return;
        Class<?> stateClass = Class.forName(
                "net.aqualoco.sec.client.SeamlessSleepClientState",
                true,
                SeamlessSleepClientCompat.class.getClassLoader()
        );
        animationStateField = stateClass.getField("SLEEP_ANIMATION");
        Object state = animationStateField.get(null);
        Class<?> animationClass = state.getClass();

        animationIsActive = animationClass.getMethod("isActive");
        animationActiveField = animationClass.getDeclaredField("active");
        animationDurationTicksField = animationClass.getDeclaredField("durationTicks");
        animationStartMillisField = animationClass.getDeclaredField("startMillis");
        replayCompatModeField = animationClass.getDeclaredField("replayCompatMode");

        animationActiveField.setAccessible(true);
        animationDurationTicksField.setAccessible(true);
        animationStartMillisField.setAccessible(true);
        replayCompatModeField.setAccessible(true);
        resolved = true;
    }

    private static void logFailureOnce(Exception exception) {
        if (!failureLogged) {
            synchronized (SeamlessSleepClientCompat.class) {
                if (!failureLogged) {
                    SeasonalDayCycle.LOGGER.warn(
                            "[SeasonalDayCycle] Could not read Seamless Sleep client animation state.", exception);
                    failureLogged = true;
                }
            }
        }
    }

    public record SleepProgress(double elapsedTicks, double durationTicks) {
    }
}
