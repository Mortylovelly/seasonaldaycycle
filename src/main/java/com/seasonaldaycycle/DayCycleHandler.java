package com.seasonaldaycycle;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

public class DayCycleHandler {

    private double accumulator = 0.0;

    private static final long VANILLA_DAY_END = 12000L;
    private static final long VANILLA_CYCLE   = 24000L;

    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;

        long timeInDay = level.getDayTime() % VANILLA_CYCLE;
        boolean isDay  = timeInDay < VANILLA_DAY_END;

        Season.SubSeason subSeason = getCurrentSubSeason(level);

        double dayRealTicks   = getRealDayTicks(subSeason);
        double nightRealTicks = getRealNightTicks(subSeason);

        double targetRate = isDay
            ? (12000.0 / dayRealTicks)
            : (12000.0 / nightRealTicks);

        // Ваниль уже добавила +1, мы добавляем разницу
        accumulator += (targetRate - 1.0);

        long toAdd = (long) accumulator;
        if (toAdd != 0) {
            accumulator -= toAdd;
            long newTime = level.getDayTime() + toAdd;
            // Правильный wrap вместо обрезки до 0
            if (newTime < 0) newTime = ((newTime % VANILLA_CYCLE) + VANILLA_CYCLE) % VANILLA_CYCLE;
            level.setDayTime(newTime);
        }
    }

    private Season.SubSeason getCurrentSubSeason(ServerLevel level) {
        try {
            var seasonState = SeasonHelper.getSeasonState(level);
            if (seasonState != null) return seasonState.getSubSeason();
        } catch (Exception ignored) {}
        return Season.SubSeason.MID_SPRING;
    }

    private double getRealDayTicks(Season.SubSeason sub) {
        if (sub == null) return ModConfig.SPRING_DAY_TICKS.get();
        return switch (sub) {
            case EARLY_SPRING, MID_SPRING, LATE_SPRING -> ModConfig.SPRING_DAY_TICKS.get();
            case EARLY_SUMMER, MID_SUMMER, LATE_SUMMER -> ModConfig.SUMMER_DAY_TICKS.get();
            case EARLY_AUTUMN, MID_AUTUMN, LATE_AUTUMN -> ModConfig.AUTUMN_DAY_TICKS.get();
            case EARLY_WINTER, MID_WINTER, LATE_WINTER -> ModConfig.WINTER_DAY_TICKS.get();
            default -> ModConfig.SPRING_DAY_TICKS.get();
        };
    }

    private double getRealNightTicks(Season.SubSeason sub) {
        if (sub == null) return ModConfig.SPRING_NIGHT_TICKS.get();
        return switch (sub) {
            case EARLY_SPRING, MID_SPRING, LATE_SPRING -> ModConfig.SPRING_NIGHT_TICKS.get();
            case EARLY_SUMMER, MID_SUMMER, LATE_SUMMER -> ModConfig.SUMMER_NIGHT_TICKS.get();
            case EARLY_AUTUMN, MID_AUTUMN, LATE_AUTUMN -> ModConfig.AUTUMN_NIGHT_TICKS.get();
            case EARLY_WINTER, MID_WINTER, LATE_WINTER -> ModConfig.WINTER_NIGHT_TICKS.get();
            default -> ModConfig.SPRING_NIGHT_TICKS.get();
        };
    }
}
