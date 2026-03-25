package com.seasonaldaycycle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

public class DayCycleHandler {

    private double accumulator = 0.0;
    private long lastKnownTime = -1;

    private static final long VANILLA_DAY_END            = 12000L;
    private static final long VANILLA_CYCLE              = 24000L;
    private static final long EXTERNAL_CHANGE_THRESHOLD  = 200L;

    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) return;

        long currentTime = level.getDayTime();

        // Детекция внешнего изменения (/time set, другой мод)
        if (lastKnownTime >= 0) {
            long diff = Math.abs(currentTime - lastKnownTime);
            if (diff > EXTERNAL_CHANGE_THRESHOLD && diff < VANILLA_CYCLE - EXTERNAL_CHANGE_THRESHOLD) {
                accumulator = 0.0;
                lastKnownTime = currentTime;
                return;
            }
        }

        long timeInDay = currentTime % VANILLA_CYCLE;
        boolean isDay  = timeInDay < VANILLA_DAY_END;

        Season.SubSeason subSeason = getCurrentSubSeason(level);

        double dayRealTicks   = getRealDayTicks(subSeason);
        double nightRealTicks = getRealNightTicks(subSeason);

        // Ваниль уже добавила +1 этот тик
        // Нам нужно чтобы за dayRealTicks тиков прошло 12000 игрового времени
        // Значит за 1 тик нужно: 12000 / dayRealTicks
        // Ваниль уже дала +1, значит нам нужно добавить: (12000/dayRealTicks) - 1
        // Если dayRealTicks > 12000 (медленный день) — delta отрицательная, мы вычитаем
        // Если dayRealTicks < 12000 (быстрый день) — delta положительная, мы добавляем

        double targetRate = isDay
            ? (12000.0 / dayRealTicks)
            : (12000.0 / nightRealTicks);

        double delta = targetRate - 1.0;
        accumulator += delta;

        // Важно: применяем только когда накопилось целое число
        // Для медленного дня (delta ~ -0.67): каждые ~1.5 тика вычитаем 1
        // Это значит: ваниль +1, мы -1 = 0 два раза из трёх, один раз = +1
        // Итого: 1 тик из 3 реальных = 1 игровой тик — правильно для 3x замедления
        if (Math.abs(accumulator) >= 1.0) {
            long toAdd = (long) accumulator;
            accumulator -= toAdd;

            long newTime = currentTime + toAdd;
            if (newTime < 0) newTime = ((newTime % VANILLA_CYCLE) + VANILLA_CYCLE) % VANILLA_CYCLE;
            level.setDayTime(newTime);
            lastKnownTime = newTime;
        } else {
            lastKnownTime = currentTime;
        }
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
