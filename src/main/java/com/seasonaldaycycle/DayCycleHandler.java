package com.seasonaldaycycle;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

import java.util.Map;
import java.util.WeakHashMap;

public final class DayCycleHandler {
    private static final Map<ServerWorld, State> STATES = new WeakHashMap<>();
    private static final long VANILLA_DAY_END = 12000L;
    private static final long VANILLA_CYCLE = 24000L;
    private static final long EXTERNAL_CHANGE_THRESHOLD = 200L;

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
        Season.SubSeason subSeason = getCurrentSubSeason(level);

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

    public static Season.SubSeason getCurrentSubSeason(ServerWorld level) {
        try {
            var seasonState = SeasonHelper.getSeasonState(level);
            if (seasonState != null) return seasonState.getSubSeason();
        } catch (Exception ignored) {}
        return Season.SubSeason.MID_SPRING;
    }

    public static double getRealDayTicks(Season.SubSeason sub) {
        if (sub == null) return ModConfig.getSpringDayTicks();
        return switch (sub) {
            case EARLY_SPRING, MID_SPRING, LATE_SPRING -> ModConfig.getSpringDayTicks();
            case EARLY_SUMMER, MID_SUMMER, LATE_SUMMER -> ModConfig.getSummerDayTicks();
            case EARLY_AUTUMN, MID_AUTUMN, LATE_AUTUMN -> ModConfig.getAutumnDayTicks();
            case EARLY_WINTER, MID_WINTER, LATE_WINTER -> ModConfig.getWinterDayTicks();
            default -> ModConfig.getSpringDayTicks();
        };
    }

    public static double getRealNightTicks(Season.SubSeason sub) {
        if (sub == null) return ModConfig.getSpringNightTicks();
        return switch (sub) {
            case EARLY_SPRING, MID_SPRING, LATE_SPRING -> ModConfig.getSpringNightTicks();
            case EARLY_SUMMER, MID_SUMMER, LATE_SUMMER -> ModConfig.getSummerNightTicks();
            case EARLY_AUTUMN, MID_AUTUMN, LATE_AUTUMN -> ModConfig.getAutumnNightTicks();
            case EARLY_WINTER, MID_WINTER, LATE_WINTER -> ModConfig.getWinterNightTicks();
            default -> ModConfig.getSpringNightTicks();
        };
    }

    private static final class State {
        double timeDecimalAccumulator = 0.0;
        long lastKnownTime = -1L;
    }
}
