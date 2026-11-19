package com.seasonaldaycycle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Optional runtime bridge to Serene Seasons. The mod deliberately has no compile-time
 * dependency on Serene Seasons, so SeasonalDayCycle also works without it installed.
 */
public final class SereneSeasonsCompat {
    private static final String MOD_ID = "sereneseasons";
    private static final String HELPER_CLASS = "sereneseasons.api.season.SeasonHelper";
    private static final String SEASON_TIME_CLASS = "sereneseasons.season.SeasonTime";
    private static final String SEASON_HANDLER_CLASS = "sereneseasons.season.SeasonHandler";
    private static volatile boolean failureLogged;

    private SereneSeasonsCompat() {}

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    public static boolean isBigGlobeLoaded() {
        return FabricLoader.getInstance().isModLoaded("bigglobe");
    }

    public static SeasonInfo getSeasonInfo(World world) {
        if (world == null || !isLoaded()) return SeasonInfo.unavailable();
        try {
            Class<?> helper = Class.forName(HELPER_CLASS);
            Method getSeasonState = findWorldMethod(helper, "getSeasonState", world);
            Object state = getSeasonState.invoke(null, world);
            if (state == null) return SeasonInfo.unavailable();

            Object subSeason = state.getClass().getMethod("getSubSeason").invoke(state);
            if (!(subSeason instanceof Enum<?> seasonEnum)) return SeasonInfo.unavailable();

            int day = ((Number) state.getClass().getMethod("getDay").invoke(state)).intValue();
            int dayDuration = ((Number) state.getClass().getMethod("getDayDuration").invoke(state)).intValue();
            int subSeasonDuration = ((Number) state.getClass().getMethod("getSubSeasonDuration").invoke(state)).intValue();
            int seasonDuration = ((Number) state.getClass().getMethod("getSeasonDuration").invoke(state)).intValue();

            int daysPerSubSeason = dayDuration > 0 ? Math.max(1, subSeasonDuration / dayDuration) : 8;
            int daysPerSeason = dayDuration > 0 ? Math.max(1, seasonDuration / dayDuration) : daysPerSubSeason * 3;
            return new SeasonInfo(true, seasonEnum.ordinal(), seasonEnum.name(), day,
                    daysPerSubSeason, daysPerSeason);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailureOnce("Could not read Serene Seasons state through its public season API.", exception);
            return SeasonInfo.unavailable();
        }
    }

    /**
     * Sets the middle sub-season of the selected season. Serene Seasons then broadcasts
     * its normal update packet and its own snow/ice/color handlers perform the transition.
     *
     * @param seasonIndex 0=spring, 1=summer, 2=autumn, 3=winter
     */
    public static boolean setSeason(ServerWorld world, int seasonIndex) {
        if (world == null || seasonIndex < 0 || seasonIndex > 3 || !isLoaded()) return false;
        try {
            Class<?> handler = Class.forName(SEASON_HANDLER_CLASS);
            Method getSavedData = findWorldMethod(handler, "getSeasonSavedData", world);
            Object savedData = getSavedData.invoke(null, world);
            if (savedData == null) return false;

            Class<?> seasonTime = Class.forName(SEASON_TIME_CLASS);
            Field zeroField = seasonTime.getField("ZERO");
            Object zero = zeroField.get(null);
            int subSeasonDuration = ((Number) seasonTime.getMethod("getSubSeasonDuration").invoke(zero)).intValue();

            // Select the middle of each three-part season, not an arbitrary transition edge.
            int middleSubSeasonOrdinal = seasonIndex * 3 + 1;
            Field cycleTicks = savedData.getClass().getField("seasonCycleTicks");
            cycleTicks.setInt(savedData, middleSubSeasonOrdinal * subSeasonDuration);
            markSavedDataDirty(savedData);

            findWorldMethod(handler, "sendSeasonUpdate", world).invoke(null, world);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailureOnce("Could not change the Serene Seasons season through its saved-data API.", exception);
            return false;
        }
    }

    private static Method findWorldMethod(Class<?> owner, String name, World world) throws NoSuchMethodException {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name)
                    && Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(world.getClass())) {
                return method;
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name + "(World)");
    }

    private static void markSavedDataDirty(Object savedData) throws ReflectiveOperationException {
        for (String name : new String[]{"setDirty", "markDirty"}) {
            try {
                savedData.getClass().getMethod(name).invoke(savedData);
                return;
            } catch (NoSuchMethodException ignored) {
                // Mojang and Yarn mappings use different names for SavedData's dirty marker.
            }
        }
        throw new NoSuchMethodException("SavedData dirty marker (setDirty/markDirty)");
    }

    private static void logFailureOnce(String message, Exception exception) {
        if (!failureLogged) {
            synchronized (SereneSeasonsCompat.class) {
                if (!failureLogged) {
                    SeasonalDayCycle.LOGGER.warn("[SeasonalDayCycle] " + message, exception);
                    failureLogged = true;
                }
            }
        }
    }

    public record SeasonInfo(
            boolean available,
            int subSeasonOrdinal,
            String subSeasonName,
            int seasonCycleDay,
            int daysPerSubSeason,
            int daysPerSeason
    ) {
        public static SeasonInfo unavailable() {
            return new SeasonInfo(false, -1, "", 0, 0, 0);
        }
    }
}
