package com.seasonaldaycycle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

public final class DayCycleHandler {
    private static final Map<ServerWorld, State> STATES = new WeakHashMap<>();
    private static final long VANILLA_DAY_END = 12000L;
    private static final long VANILLA_CYCLE = 24000L;
    private static final long EXTERNAL_CHANGE_THRESHOLD = 200L;

    private static final String SERENE_SEASONS_MOD_ID = "sereneseasons";
    private static final String SEASON_HELPER_CLASS = "sereneseasons.api.season.SeasonHelper";

    private DayCycleHandler() {}

    public static void onWorldTick(ServerWorld level) {
        if (level.getRegistryKey() != World.OVERWORLD) return;
        if (!level.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) return;

        State state = STATES.computeIfAbsent(level, ignored -> new State());
        long currentTime = level.getTimeOfDay();

        if (state.lastKnownTime >= 0) {
            long diff = Math.abs(currentTime - state.lastKnownTime);
            if (diff > EXTERNAL_CHANGE_THRESHOLD && diff < VANILLA_CYCLE - EXTERNAL_CHANGE_THRESHOLD) {
                state.timeDecimalAccumulator = 0.0;
                state.lastKnownTime = currentTime;
                return;
            }
        }

        level.setTimeOfDay(currentTime - 1L);
        currentTime--;

        long timeInDay = Math.floorMod(currentTime, VANILLA_CYCLE);
        boolean isDay = timeInDay < VANILLA_DAY_END;
        String subSeason = getCurrentSubSeason(level);

        double realDayTicks = getRealDayTicks(subSeason);
        double realNightTicks = getRealNightTicks(subSeason);
        double speed = isDay ? 12000.0 / realDayTicks : 12000.0 / realNightTicks;

        state.timeDecimalAccumulator += speed;
        long toAdd = (long) Math.floor(state.timeDecimalAccumulator);
        state.timeDecimalAccumulator -= toAdd;

        long newTime = currentTime + toAdd;
        if (newTime < 0) newTime = Math.floorMod(newTime, VANILLA_CYCLE);

        level.setTimeOfDay(newTime);
        state.lastKnownTime = newTime;
    }

    /**
     * Returns the current Serene Seasons sub-season when Serene Seasons is installed.
     * Returns null when Serene Seasons is absent or its API cannot be accessed.
     * Reflection is intentional: Serene Seasons is optional and must not be required
     * for Seasonal Day Cycle to load or run.
     */
    public static String getCurrentSubSeason(ServerWorld level) {
        if (!FabricLoader.getInstance().isModLoaded(SERENE_SEASONS_MOD_ID)) {
            return null;
        }

        try {
            Class<?> helperClass = Class.forName(SEASON_HELPER_CLASS);
            Method getSeasonState = helperClass.getMethod("getSeasonState", ServerWorld.class);
            Object seasonState = getSeasonState.invoke(null, level);
            if (seasonState == null) return null;

            Method getSubSeason = seasonState.getClass().getMethod("getSubSeason");
            Object subSeason = getSubSeason.invoke(seasonState);
            return subSeason == null ? null : subSeason.toString();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static double getRealDayTicks(String subSeason) {
        if (subSeason == null) return ModConfig.getSpringDayTicks();
        return switch (subSeason) {
            case "EARLY_SPRING", "MID_SPRING", "LATE_SPRING" -> ModConfig.getSpringDayTicks();
            case "EARLY_SUMMER", "MID_SUMMER", "LATE_SUMMER" -> ModConfig.getSummerDayTicks();
            case "EARLY_AUTUMN", "MID_AUTUMN", "LATE_AUTUMN" -> ModConfig.getAutumnDayTicks();
            case "EARLY_WINTER", "MID_WINTER", "LATE_WINTER" -> ModConfig.getWinterDayTicks();
            default -> ModConfig.getSpringDayTicks();
        };
    }

    public static double getRealNightTicks(String subSeason) {
        if (subSeason == null) return ModConfig.getSpringNightTicks();
        return switch (subSeason) {
            case "EARLY_SPRING", "MID_SPRING", "LATE_SPRING" -> ModConfig.getSpringNightTicks();
            case "EARLY_SUMMER", "MID_SUMMER", "LATE_SUMMER" -> ModConfig.getSummerNightTicks();
            case "EARLY_AUTUMN", "MID_AUTUMN", "LATE_AUTUMN" -> ModConfig.getAutumnNightTicks();
            case "EARLY_WINTER", "MID_WINTER", "LATE_WINTER" -> ModConfig.getWinterNightTicks();
            default -> ModConfig.getSpringNightTicks();
        };
    }

    private static final class State {
        double timeDecimalAccumulator = 0.0;
        long lastKnownTime = -1L;
    }
}
