package com.seasonaldaycycle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

public class DayCycleHandler {

    private double accumulator = 0.0;

    // Последнее известное время — для детекции внешних изменений (/time set, другие моды)
    private long lastKnownTime = -1;

    private static final long VANILLA_DAY_END = 12000L;
    private static final long VANILLA_CYCLE   = 24000L;

    // Максимальный прыжок времени за один тик который мы считаем "нормальным"
    // Если прыжок больше — значит кто-то снаружи изменил время
    private static final long EXTERNAL_CHANGE_THRESHOLD = 200L;

    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;

        // Если gamerule doDaylightCycle выключен другим модом — не вмешиваемся
        if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) return;

        long currentTime = level.getDayTime();

        // Детекция внешнего изменения времени (/time set, другой мод, команда оператора)
        if (lastKnownTime >= 0) {
            long diff = Math.abs(currentTime - lastKnownTime);
            // Нормальный тик = максимум ~3 тика времени (при очень быстром дне)
            // Если разница огромная — кто-то снаружи изменил время
            if (diff > EXTERNAL_CHANGE_THRESHOLD && diff < VANILLA_CYCLE - EXTERNAL_CHANGE_THRESHOLD) {
                // Принимаем новое время, сбрасываем аккумулятор
                accumulator = 0.0;
                lastKnownTime = currentTime;
                return; // пропускаем этот тик, начинаем заново со следующего
            }
        }

        long timeInDay = currentTime % VANILLA_CYCLE;
        boolean isDay  = timeInDay < VANILLA_DAY_END;

        Season.SubSeason subSeason = getCurrentSubSeason(level);

        double dayRealTicks   = getRealDayTicks(subSeason);
        double nightRealTicks = getRealNightTicks(subSeason);

        double targetRate = isDay
            ? (12000.0 / dayRealTicks)
            : (12000.0 / nightRealTicks);

        accumulator += (targetRate - 1.0);

        long toAdd = (long) accumulator;
        if (toAdd != 0) {
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
