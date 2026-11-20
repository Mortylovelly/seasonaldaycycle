package com.seasonaldaycycle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Optional, reflection-only integration with Seamless Sleep 2.5.2+. */
public final class SeamlessSleepCompat {
    public static final int WAKE_SETTLE_TICKS = 40;

    private static final String MOD_ID = "seamlesssleep";
    private static final String COMMON_CLASS = "net.aqualoco.sec.SeamlessSleepCommon";
    private static final String CONFIG_MANAGER_CLASS = "net.aqualoco.sec.config.SeamlessSleepServerConfigManager";
    private static final String CONFIG_SYNC_CLASS = "net.aqualoco.sec.network.ServerConfigSync";

    private static volatile boolean apiResolved;
    private static volatile boolean failureLogged;
    private static Field animationStateField;
    private static Method animationIsActive;
    private static Field animationDurationTicks;
    private static Field animationStartMillis;
    private static Method configGet;
    private static Method configSave;
    private static Method configClamp;
    private static Field durationMultiplierField;
    private static Method syncToAll;

    private SeamlessSleepCompat() {
    }

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    public static boolean isServerSleepAnimationActive(ServerWorld world) {
        if (world == null || world.getRegistryKey() != World.OVERWORLD || !isLoaded()) {
            return false;
        }
        try {
            resolveApi();
            Object state = animationStateField.get(null);
            return state != null && Boolean.TRUE.equals(animationIsActive.invoke(state));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailureOnce("Не удалось прочитать состояние сна Seamless Sleep. Временная интеграция отключена.", exception);
            return false;
        }
    }

    /**
     * True only during the extra wake-settling window appended to Seamless Sleep.
     * During this phase Seamless Sleep keeps players asleep while Vertex restores
     * the configured normal day speed.
     */
    public static boolean isServerSleepReleasePhase(ServerWorld world) {
        if (!isServerSleepAnimationActive(world)) return false;
        try {
            Object state = animationStateField.get(null);
            int durationTicks = Math.max(1, animationDurationTicks.getInt(state));
            long startMillis = animationStartMillis.getLong(state);
            long elapsedMillis = Math.max(0L, System.currentTimeMillis() - startMillis);
            int skipTicks = Math.max(1, durationTicks - WAKE_SETTLE_TICKS);
            return elapsedMillis >= skipTicks * 50L;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailureOnce("Не удалось определить фазу завершения сна Seamless Sleep.", exception);
            return false;
        }
    }

    public static float getSleepAnimationDurationMultiplier() {
        if (!isLoaded()) return 1.0f;
        try {
            resolveApi();
            Object config = configGet.invoke(null);
            return clampMultiplier((float) durationMultiplierField.getDouble(config));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailureOnce("Не удалось прочитать настройки Seamless Sleep.", exception);
            return 1.0f;
        }
    }

    /** Changes Seamless Sleep's own server setting and uses its own sync packet. */
    public static boolean setSleepAnimationDurationMultiplier(MinecraftServer server, float multiplier) {
        if (server == null || !isLoaded()) return false;
        try {
            resolveApi();
            Object config = configGet.invoke(null);
            durationMultiplierField.setDouble(config, clampMultiplier(multiplier));
            configClamp.invoke(config);
            configSave.invoke(null);
            syncToAll.invoke(null, server, config);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailureOnce("Не удалось изменить длительность перехода Seamless Sleep.", exception);
            return false;
        }
    }

    private static synchronized void resolveApi() throws ReflectiveOperationException {
        if (apiResolved) return;

        ClassLoader loader = SeamlessSleepCompat.class.getClassLoader();
        Class<?> common = Class.forName(COMMON_CLASS, true, loader);
        animationStateField = common.getField("OVERWORLD_SLEEP_ANIMATION");
        Object state = animationStateField.get(null);
        Class<?> stateClass = state.getClass();
        animationIsActive = stateClass.getMethod("isActive");
        animationDurationTicks = stateClass.getDeclaredField("durationTicks");
        animationStartMillis = stateClass.getDeclaredField("startMillis");
        animationDurationTicks.setAccessible(true);
        animationStartMillis.setAccessible(true);

        Class<?> manager = Class.forName(CONFIG_MANAGER_CLASS, true, loader);
        configGet = manager.getMethod("get");
        configSave = manager.getMethod("save");

        Object config = configGet.invoke(null);
        Class<?> configClass = config.getClass();
        durationMultiplierField = configClass.getField("sleepAnimationDurationMultiplier");
        configClamp = configClass.getMethod("clamp");

        Class<?> syncClass = Class.forName(CONFIG_SYNC_CLASS, true, loader);
        for (Method method : syncClass.getMethods()) {
            if (method.getName().equals("sendToAll")
                    && Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[0].isAssignableFrom(MinecraftServer.class)
                    && method.getParameterTypes()[1].isAssignableFrom(configClass)) {
                syncToAll = method;
                break;
            }
        }
        if (syncToAll == null) {
            throw new NoSuchMethodException(CONFIG_SYNC_CLASS + ".sendToAll(MinecraftServer, config)");
        }
        apiResolved = true;
    }

    public static float clampMultiplier(float value) {
        if (!Float.isFinite(value)) return 1.0f;
        return Math.max(0.25f, Math.min(8.0f, value));
    }

    private static void logFailureOnce(String message, Exception exception) {
        if (!failureLogged) {
            synchronized (SeamlessSleepCompat.class) {
                if (!failureLogged) {
                    SeasonalDayCycle.LOGGER.warn("[SeasonalDayCycle] " + message, exception);
                    failureLogged = true;
                }
            }
        }
    }
}
