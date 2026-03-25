package com.seasonaldaycycle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

public class DayCycleHandler {

    // Дробная часть времени — хранится отдельно как в Better Days
    private double timeDecimalAccumulator = 0.0;

    private long lastKnownTime = -1;
    private static final long EXTERNAL_CHANGE_THRESHOLD = 200L;
    private static final long VANILLA_DAY_END = 12000L;
    private static final long VANILLA_CYCLE   = 24000L;

    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        // Better Days использует START фазу для vanillaTimeCompensation
        // и END фазу для своего tickTime — мы делаем то же самое

        if (!(event.level instanceof ServerLevel level)) return;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) return;

        if (event.phase == TickEvent.Phase.START) {
            // Точно как Better Days: отменяем ванильный +1 который уже был добавлен
            level.setDayTime(level.getDayTime() - 1);
            return;
        }

        // Дальше — END фаза

        long currentTime = level.getDayTime();

        // Детекция /time set или другого мода
        if (lastKnownTime >= 0) {
            long diff = Math.abs(currentTime - lastKnownTime);
            if (diff > EXTERNAL_CHANGE_THRESHOLD && diff < VANILLA_CYCLE - EXTERNAL_CHANGE_THRESHOLD) {
                timeDecimalAccumulator = 0.0;
                lastKnownTime = currentTime;
                return;
            }
        }

        long timeInDay = currentTime % VANILLA_CYCLE;
        boolean isDay  = timeInDay < VANILLA_DAY_END;

        Season.SubSeason subSeason = getCurrentSubSeason(level);

        double dayRealTicks   = getRealDayTicks(subSeason);
        double nightRealTicks = getRealNightTicks(subSeason);

        // speed = сколько игровых тиков за 1 реальный тик
        // vanilla = 1.0, медленнее = меньше 1.0, быстрее = больше 1.0
        double speed = isDay
            ? (12000.0 / dayRealTicks)
            : (12000.0 / nightRealTicks);

        // Добавляем speed к дробному накопителю (точно как Better Days)
        timeDecimalAccumulator += speed;

        // Берём целую часть — это сколько тиков добавить
        long toAdd = (long) timeDecimalAccumulator;
        timeDecimalAccumulator -= toAdd;

        // Двигаем время только вперёд
        if (toAdd > 0) {
            long newTime = currentTime + toAdd;
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
