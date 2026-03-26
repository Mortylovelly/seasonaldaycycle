package com.seasonaldaycycle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

public class DayCycleHandler {

    private double timeDecimalAccumulator = 0.0;
    private long lastKnownTime = -1;
    private int debugCounter = 0;

    private static final long VANILLA_DAY_END           = 12000L;
    private static final long VANILLA_CYCLE             = 24000L;
    private static final long EXTERNAL_CHANGE_THRESHOLD = 200L;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;

        // Каждые 100 тиков пишем в лог что происходит
        debugCounter++;
        if (debugCounter % 100 == 0) {
            boolean gamerule = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
            SeasonalDayCycle.LOGGER.info("[SDC DEBUG] tick={} time={} gamerule={} accumulator={}",
                debugCounter, level.getDayTime(), gamerule, timeDecimalAccumulator);
        }

        if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            SeasonalDayCycle.LOGGER.warn("[SDC DEBUG] gamerule doDaylightCycle = FALSE, мод остановлен!");
            return;
        }

        long currentTime = level.getDayTime();

        if (lastKnownTime >= 0) {
            long diff = Math.abs(currentTime - lastKnownTime);
            if (diff > EXTERNAL_CHANGE_THRESHOLD && diff < VANILLA_CYCLE - EXTERNAL_CHANGE_THRESHOLD) {
                timeDecimalAccumulator = 0.0;
                lastKnownTime = currentTime;
                return;
            }
        }

        // Отменяем ванильный +1
        level.setDayTime(currentTime - 1);
        currentTime = currentTime - 1;

        long timeInDay = currentTime % VANILLA_CYCLE;
        boolean isDay  = timeInDay < VANILLA_DAY_END;

        Season.SubSeason subSeason = getCurrentSubSeason(level);
        double dayRealTicks   = getRealDayTicks(subSeason);
        double nightRealTicks = getRealNightTicks(subSeason);

        double speed = isDay
            ? (12000.0 / dayRealTicks)
            : (12000.0 / nightRealTicks);

        timeDecimalAccumulator += speed;

        long toAdd = (long) timeDecimalAccumulator;
        timeDecimalAccumulator -= toAdd;

        long newTime = currentTime + toAdd;
        if (newTime < 0) newTime = 0;
        level.setDayTime(newTime);
        lastKnownTime = newTime;
    }

    public static Season.SubSeason getCurrentSubSeason(ServerLevel level) {
        try {
            var seasonState = SeasonHelper.getSeasonState(level);
            if (seasonState != null) return seasonState.getSubSeason();
        } catch (Exception ignored) {}
        return Season.SubSeason.MID_SPRING;
    }

    public static double getRealDayTicks(Season.SubSeason sub) {
        if (sub == null) return ModConfig.SPRING_DAY_TICKS.get();
        return switch (sub) {
            case EARLY_SPRING, MID_SPRING, LATE_SPRING -> ModConfig.SPRING_DAY_TICKS.get();
            case EARLY_SUMMER, MID_SUMMER, LATE_SUMMER -> ModConfig.SUMMER_DAY_TICKS.get();
            case EARLY_AUTUMN, MID_AUTUMN, LATE_AUTUMN -> ModConfig.AUTUMN_DAY_TICKS.get();
            case EARLY_WINTER, MID_WINTER, LATE_WINTER -> ModConfig.WINTER_DAY_TICKS.get();
            default -> ModConfig.SPRING_DAY_TICKS.get();
        };
    }

    public static double getRealNightTicks(Season.SubSeason sub) {
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
