package com.seasonaldaycycle.network;

import com.seasonaldaycycle.DayCycleWorldConfig;
import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.DayCycleHandler;
import com.seasonaldaycycle.SereneSeasonsCompat;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

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
            if (!canEdit(player)) {
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

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendState(handler.player));
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
    }

    private static void syncWorldState(ServerPlayerEntity source) {
        if (source.getServer() == null) return;
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            sendState(player);
        }
    }
}
