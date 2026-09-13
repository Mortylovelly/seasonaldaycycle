package com.seasonaldaycycle.network;

import com.seasonaldaycycle.DayCycleWorldConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class DayCycleNetworking {
    private DayCycleNetworking() {}

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(SetDayCycleLengthPayload.TYPE, (payload, player, responseSender) -> {
            if (player.getServer() == null) return;

            boolean allowed = player.getServer().isSingleplayer() || player.hasPermissionLevel(2);
            if (!allowed) {
                ServerPlayNetworking.send(player,
                        new DayCycleLengthSyncPayload((int) DayCycleWorldConfig.getCycleLengthTicks(player.getServerWorld())));
                return;
            }

            DayCycleWorldConfig.setCycleLengthTicks(player.getServerWorld(), payload.ticks());
            int ticks = (int) DayCycleWorldConfig.getCycleLengthTicks(player.getServerWorld());
            DayCycleLengthSyncPayload sync = new DayCycleLengthSyncPayload(ticks);

            for (ServerPlayerEntity serverPlayer : player.getServer().getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(serverPlayer, sync);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayNetworking.send(
                    handler.player,
                    new DayCycleLengthSyncPayload(
                            (int) DayCycleWorldConfig.getCycleLengthTicks(handler.player.getServerWorld())
                    )
            );
        });
    }
}
