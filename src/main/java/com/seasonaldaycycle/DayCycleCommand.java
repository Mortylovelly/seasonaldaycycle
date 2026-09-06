package com.seasonaldaycycle;

import com.mojang.brigadier.CommandDispatcher;
import com.seasonaldaycycle.network.OpenDayCycleScreenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public final class DayCycleCommand {
    private DayCycleCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("daycycle")
                .then(CommandManager.literal("info")
                        .executes(context -> executeInfo(context.getSource())))
                .then(CommandManager.literal("gui")
                        .executes(context -> executeGui(context.getSource()))));
    }

    private static int executeGui(ServerCommandSource source) {
        if (!(source.getEntity() instanceof net.minecraft.server.network.ServerPlayerEntity player)) {
            source.sendError(Text.literal("[SeasonalDayCycle] Эта команда доступна только игроку."));
            return 0;
        }

        ServerPlayNetworking.send(player, new OpenDayCycleScreenPayload());
        return 1;
    }

    private static int executeInfo(ServerCommandSource source) {
        ServerWorld level = source.getWorld();
        if (level.getRegistryKey() != World.OVERWORLD) {
            source.sendError(Text.literal("[SeasonalDayCycle] Команда работает только в Overworld!"));
            return 0;
        }

        String subSeason = DayCycleHandler.getCurrentSubSeason(level);
        double dayTicks = DayCycleHandler.getRealDayTicks(subSeason);
        double nightTicks = DayCycleHandler.getRealNightTicks(subSeason);
        long cycleTicks = ModConfig.getCycleLengthTicks();
        long timeInDay = Math.floorMod(level.getTimeOfDay(), 24000L);
        boolean isDay = timeInDay < 12000L;
        String phase = isDay ? "День" : "Ночь";

        double ticksPerGameTick = isDay ? 12000.0 / dayTicks : 12000.0 / nightTicks;
        long gameTicksLeft = isDay ? 12000L - timeInDay : 24000L - timeInDay;
        long realSecondsLeft = (long) (gameTicksLeft / ticksPerGameTick / 20.0);
        long minLeft = realSecondsLeft / 60;
        long secLeft = realSecondsLeft % 60;

        source.sendFeedback(() -> Text.literal("[SeasonalDayCycle]\n" +
                "Сезон: " + getSeasonName(subSeason) + "\n" +
                "Фаза: " + phase + "\n" +
                "Полные сутки: " + formatMinutes(cycleTicks) + "\n" +
                "Длина дня: " + formatMinutes(dayTicks) + "\n" +
                "Длина ночи: " + formatMinutes(nightTicks) + "\n" +
                "До смены фазы: " + minLeft + "м " + secLeft + "с"), false);
        return 1;
    }

    private static String getSeasonName(String sub) {
        if (sub == null) return "Не используется";
        return switch (sub) {
            case "EARLY_SPRING" -> "Ранняя весна";
            case "MID_SPRING" -> "Середина весны";
            case "LATE_SPRING" -> "Поздняя весна";
            case "EARLY_SUMMER" -> "Раннее лето";
            case "MID_SUMMER" -> "Середина лета";
            case "LATE_SUMMER" -> "Позднее лето";
            case "EARLY_AUTUMN" -> "Ранняя осень";
            case "MID_AUTUMN" -> "Середина осени";
            case "LATE_AUTUMN" -> "Поздняя осень";
            case "EARLY_WINTER" -> "Ранняя зима";
            case "MID_WINTER" -> "Середина зимы";
            case "LATE_WINTER" -> "Поздняя зима";
            default -> "Неизвестно";
        };
    }

    private static String formatMinutes(double ticks) {
        long totalSeconds = (long) (ticks / 20.0);
        return (totalSeconds / 60) + "м " + (totalSeconds % 60) + "с";
    }
}
