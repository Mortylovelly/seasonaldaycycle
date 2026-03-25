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
    private boolean wasManaged = false;

    private static final long VANILLA_DAY_END            = 12000L;
    private static final long VANILLA_CYCLE              = 24000L;
    private static final long EXTERNAL_CHANGE_THRESHOLD  = 200L;

    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;

        // Если gamerule выключен кем-то другим — не вмешиваемся
        // но восстанавливаем его если мы сами выключали
        boolean gameruleOn = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        if (!gameruleOn) {
            wasManaged = false;
            accumulator = 0.0;
            lastKnownTime = -1;
            return;
        }

        // Выключаем ванильный тик времени — теперь МЫ двигаем время
        // setBoolean каждый тик дёшево, Forge это делает внутри
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT)
            .set(false, level.getServer());
        wasManaged = true;

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

        // Теперь ваниль НЕ добавляет +1, мы двигаем время полностью сами
        // targetRate = сколько игровых тиков добавить за 1 реальный тик
        double targetRate = isDay
            ? (12000.0 / dayRealTicks)
            : (12000.0 / nightRealTicks);

        accumulator += targetRate;

        // Берём целую часть и двигаем время только ВПЕРЁД
        // Никогда не вычитаем — солнце всегда идёт в одну сторону
        long toAdd = (long) accumulator;
        accumulator -= toAdd;

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
