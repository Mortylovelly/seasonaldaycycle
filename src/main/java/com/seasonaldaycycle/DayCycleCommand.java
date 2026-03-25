package com.seasonaldaycycle;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import sereneseasons.api.season.Season;

public class DayCycleCommand {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new DayCycleCommand());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("daycycle")
                .then(Commands.literal("info")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        ServerLevel level = source.getLevel();

                        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                            source.sendFailure(Component.literal(
                                "[SeasonalDayCycle] Команда работает только в Overworld!"
                            ));
                            return 0;
                        }

                        Season.SubSeason sub = DayCycleHandler.getCurrentSubSeason(level);
                        double dayTicks   = DayCycleHandler.getRealDayTicks(sub);
                        double nightTicks = DayCycleHandler.getRealNightTicks(sub);

                        long timeInDay  = level.getDayTime() % 24000L;
                        boolean isDay   = timeInDay < 12000L;
                        String phase    = isDay ? "День" : "Ночь";

                        // Считаем сколько реального времени осталось до смены фазы
                        double ticksPerGameTick = isDay
                            ? (12000.0 / dayTicks)
                            : (12000.0 / nightTicks);
                        long gameTicksLeft = isDay
                            ? (12000L - timeInDay)
                            : (24000L - timeInDay);
                        long realSecondsLeft = (long)(gameTicksLeft / ticksPerGameTick / 20.0);
                        long minLeft = realSecondsLeft / 60;
                        long secLeft = realSecondsLeft % 60;

                        String seasonName = getSeasonName(sub);

                        source.sendSuccess(() -> Component.literal(
                            "§6[SeasonalDayCycle]§r\n" +
                            "§eСезон:§r " + seasonName + "\n" +
                            "§eФаза:§r " + phase + "\n" +
                            "§eДлина дня:§r " + formatMinutes(dayTicks) + "\n" +
                            "§eДлина ночи:§r " + formatMinutes(nightTicks) + "\n" +
                            "§eДо смены фазы:§r " + minLeft + "м " + secLeft + "с"
                        ), false);

                        return 1;
                    })
                )
        );
    }

    private static String getSeasonName(Season.SubSeason sub) {
        if (sub == null) return "Неизвестно";
        return switch (sub) {
            case EARLY_SPRING -> "Ранняя весна";
            case MID_SPRING   -> "Середина весны";
            case LATE_SPRING  -> "Поздняя весна";
            case EARLY_SUMMER -> "Раннее лето";
            case MID_SUMMER   -> "Середина лета";
            case LATE_SUMMER  -> "Позднее лето";
            case EARLY_AUTUMN -> "Ранняя осень";
            case MID_AUTUMN   -> "Середина осени";
            case LATE_AUTUMN  -> "Поздняя осень";
            case EARLY_WINTER -> "Ранняя зима";
            case MID_WINTER   -> "Середина зимы";
            case LATE_WINTER  -> "Поздняя зима";
            default           -> "Неизвестно";
        };
    }

    private static String formatMinutes(double ticks) {
        long totalSeconds = (long)(ticks / 20.0);
        long min = totalSeconds / 60;
        long sec = totalSeconds % 60;
        return min + "м " + sec + "с";
    }
}
