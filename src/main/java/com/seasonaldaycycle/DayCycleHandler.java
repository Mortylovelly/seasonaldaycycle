package com.seasonaldaycycle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

/**
 * SeasonalDayCycle - DayCycleHandler
 *
 * HOW IT WORKS:
 * Vanilla Minecraft increments dayTime by 1 each server tick.
 * We cancel that by using the doDaylightCycle gamerule trick:
 *   - Set doDaylightCycle = FALSE every tick (so vanilla doesn't increment)
 *   - We manually add a fractional increment each tick using an accumulator
 *
 * This means: instead of +1 per tick, we add e.g. +0.333 per tick
 * so 24000 in-game time units take 72000 real ticks (60 real minutes).
 *
 * Day phase:   in-game time 0    -> 12000 (sunrise to sunset)
 * Night phase: in-game time 12000-> 24000 (sunset to sunrise)
 *
 * We apply different speeds for day vs night based on season.
 */
public class DayCycleHandler {

    // Accumulator for sub-tick time (avoids precision loss)
    // One entry per dimension - we use a simple array by level hashCode
    // but actually we store it per-level using a small helper field
    // For simplicity: server has one overworld, store directly
    private double accumulator = 0.0;

    // Vanilla day length constants
    private static final long VANILLA_DAY_START   = 0L;
    private static final long VANILLA_DAY_END     = 12000L;
    private static final long VANILLA_CYCLE       = 24000L;

    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        // Only affect Overworld
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        // Only if daylightCycle is enabled by server settings
        // (we control it manually, so we always run)

        // Disable vanilla time progression this tick
        // We do this by checking the gamerule and compensating
        // Actually: vanilla only increments if doDaylightCycle = true
        // We leave gamerule alone and instead subtract the vanilla +1
        // then add our own fractional increment.
        // This is the cleanest approach - no gamerule tampering.

        long currentTime = level.getDayTime();
        long timeInDay   = currentTime % VANILLA_CYCLE;
        boolean isDay    = timeInDay < VANILLA_DAY_END; // 0-11999 = day

        // Get season
        Season.SubSeason subSeason = getCurrentSubSeason(level);

        // Get real-tick targets from config
        double dayRealTicks   = getRealDayTicks(subSeason);
        double nightRealTicks = getRealNightTicks(subSeason);

        // Vanilla increments +1 per tick for 12000 in-game ticks = 12000 real ticks
        // We want it to take dayRealTicks real ticks for the same 12000 in-game ticks
        // Speed multiplier = 12000 / dayRealTicks
        // Each real tick we should advance: 12000 / dayRealTicks in-game ticks
        // Vanilla already added +1, so we need to adjust by: (12000/dayRealTicks) - 1

        double vanillaRate = 1.0; // vanilla adds 1 per tick
        double targetRate  = isDay
            ? (12000.0 / dayRealTicks)
            : (12000.0 / nightRealTicks);
        double delta = targetRate - vanillaRate;

        // Accumulate fractional ticks
        accumulator += delta;

        // Apply whole ticks from accumulator
        long toAdd = (long) accumulator;
        if (toAdd != 0) {
            accumulator -= toAdd;
            long newTime = level.getDayTime() + toAdd;
            if (newTime < 0) newTime = 0; // safety
            level.setDayTime(newTime);
        }
        // Note: when delta is negative (slowing down), toAdd will be negative,
        // effectively removing ticks that vanilla already added.
    }

    private Season.SubSeason getCurrentSubSeason(ServerLevel level) {
        try {
            var seasonState = SeasonHelper.getSeasonState(level);
            if (seasonState != null) {
                return seasonState.getSubSeason();
            }
        } catch (Exception e) {
            // Serene Seasons not available or dimension not supported
        }
        return Season.SubSeason.MID_SPRING; // fallback
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
