package com.seasonaldaycycle.client;

import com.seasonaldaycycle.SeasonalDayCycle;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Client-only reflection bridge for Seamless Sleep's own smooth transition. */
public final class SeamlessSleepClientCompat {
    private static final String MOD_ID = "seamlesssleep";
    private static volatile boolean resolved;
    private static volatile boolean failureLogged;
    private static Field animationStateField;
    private static Method animationIsActive;

    private SeamlessSleepClientCompat() {
    }

    public static boolean isSleepAnimationActive() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return false;
        try {
            resolve();
            Object state = animationStateField.get(null);
            return state != null && Boolean.TRUE.equals(animationIsActive.invoke(state));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!failureLogged) {
                synchronized (SeamlessSleepClientCompat.class) {
                    if (!failureLogged) {
                        SeasonalDayCycle.LOGGER.warn(
                                "[SeasonalDayCycle] Could not read Seamless Sleep client animation state.", exception);
                        failureLogged = true;
                    }
                }
            }
            return false;
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
        animationIsActive = state.getClass().getMethod("isActive");
        resolved = true;
    }
}
