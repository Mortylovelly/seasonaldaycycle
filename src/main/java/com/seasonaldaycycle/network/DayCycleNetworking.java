package com.seasonaldaycycle.network;

import com.seasonaldaycycle.DayCycleWorldConfig;
import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.DayCycleHandler;
import com.seasonaldaycycle.SereneSeasonsCompat;
import com.seasonaldaycycle.SeamlessSleepCompat;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class DayCycleNetworking {
    private DayCycleNetworking() {}

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(SetDayCycleLengthPayload.TYPE, (payload, player, responseSender) -> {
            MinecraftServer server = player.getServer();
            if (server == null) return;
            if (!canEdit(player) || server.getOverworld() == null) {
                sendState(player);
                return;
            }

            // The day-cycle configuration belongs to the world save, not the
            // dimension the command happens to be opened from.
            DayCycleWorldConfig.setCycleLengthTicks(server.getOverworld(), payload.ticks());
            syncWorldState(server);
        });

        ServerPlayNetworking.registerGlobalReceiver(SetSeasonPayload.TYPE, (payload, player, responseSender) -> {
            if (player.getServer() == null) return;
            if (!canEdit(player)
                    || player.getServerWorld().getRegistryKey() != net.minecraft.world.World.OVERWORLD) {
                sendState(player);
                return;
            }

            if (SereneSeasonsCompat.setSeason(player.getServerWorld(), payload.seasonIndex())) {
                DayCycleHandler.invalidateSeasonCache(player.getServerWorld());
                syncWorldState(player.getServer());
            } else {
                sendState(player);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(SetSleepDurationPayload.TYPE, (payload, player, responseSender) -> {
            MinecraftServer server = player.getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!canEditSleepSettings(player)) {
                    sendSleepSettingsState(player);
                    return;
                }
                if (!SeamlessSleepCompat.setSleepAnimationDurationMultiplier(server, payload.multiplier())) {
                    sendSleepSettingsState(player);
                    return;
                }
                syncSleepSettingsState(server);
            });
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sendState(handler.player);
            sendDayOffset(handler.player);
        });
    }

    /** Sends the exact server calendar time to every connected player. */
    public static void syncDayOffset(ServerWorld world, long offsetDays) {
        if (world == null || world.getServer() == null) return;
        long safeOffset = DayCycleWorldConfig.sanitizeElapsedDayOffsetDays(offsetDays);
        long calendarTime = world.getTimeOfDay() + safeOffset * 24000L;
        CalendarProgressSyncPayload payload =
                new CalendarProgressSyncPayload(safeOffset, calendarTime);
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static void sendDayOffset(ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) return;
        ServerWorld overworld = player.getServer().getOverworld();
        if (overworld == null) return;
        long offset = DayCycleWorldConfig.getElapsedDayOffsetDays(overworld);
        long calendarTime = overworld.getTimeOfDay() + offset * 24000L;
        ServerPlayNetworking.send(player, new CalendarProgressSyncPayload(offset, calendarTime));
    }

    private static boolean canEdit(ServerPlayerEntity player) {
        return player.getServer() != null
                && (player.getServer().isSingleplayer() || player.hasPermissionLevel(2));
    }

    private static void sendState(ServerPlayerEntity player) {
        ServerWorld overworld = player.getServer() == null ? null : player.getServer().getOverworld();
        if (overworld == null) return;
        ServerPlayNetworking.send(player, new DayCycleLengthSyncPayload(
                (int) DayCycleWorldConfig.getCycleLengthTicks(overworld),
                canEdit(player),
                (float) ModConfig.getSpringDayRatio(),
                (float) ModConfig.getSummerDayRatio(),
                (float) ModConfig.getAutumnDayRatio(),
                (float) ModConfig.getWinterDayRatio()
        ));
        sendSleepSettingsState(player);
    }

    private static boolean canEditSleepSettings(ServerPlayerEntity player) {
        return player.getServer() != null
                && (player.getServer().isSingleplayer() || player.hasPermissionLevel(3));
    }

    private static void sendSleepSettingsState(ServerPlayerEntity player) {
        boolean available = SeamlessSleepCompat.isLoaded();
        float multiplier = available ? SeamlessSleepCompat.getSleepAnimationDurationMultiplier() : 1.0f;
        boolean canEdit = available && canEditSleepSettings(player);
        ServerPlayNetworking.send(player, new SleepSettingsSyncPayload(available, multiplier, canEdit));
    }

    private static void syncSleepSettingsState(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendSleepSettingsState(player);
        }
    }

    private static void syncWorldState(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendState(player);
        }
    }
}
