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
            if (player.getServer() == null) return;

            if (!canEdit(player)) {
                sendState(player);
                return;
            }

            DayCycleWorldConfig.setCycleLengthTicks(player.getServerWorld(), payload.ticks());
            syncWorldState(player);
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
                syncWorldState(player);
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
            sendDayOffset(handler.player, true);
        });
    }

    /** Sends the current calendar offset to every player on this server. */
    public static void syncDayOffset(ServerWorld world, long offsetDays) {
        if (world == null || world.getServer() == null) return;
        long safeOffset = DayCycleWorldConfig.sanitizeElapsedDayOffsetDays(offsetDays);
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new CalendarProgressSyncPayload(safeOffset, false));
        }
    }

    private static void sendDayOffset(ServerPlayerEntity player, boolean initialSync) {
        if (player == null || player.getServer() == null) return;
        long offset = DayCycleWorldConfig.getElapsedDayOffsetDays(player.getServerWorld());
        ServerPlayNetworking.send(player, new CalendarProgressSyncPayload(offset, initialSync));
    }

    private static boolean canEdit(ServerPlayerEntity player) {
        return player.getServer() != null
                && (player.getServer().isSingleplayer() || player.hasPermissionLevel(2));
    }

    private static void sendState(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new DayCycleLengthSyncPayload(
                (int) DayCycleWorldConfig.getCycleLengthTicks(player.getServerWorld()),
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

    private static void syncWorldState(ServerPlayerEntity source) {
        if (source.getServer() == null) return;
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            sendState(player);
        }
    }
}
