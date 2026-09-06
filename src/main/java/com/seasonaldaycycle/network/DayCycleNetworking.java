package com.seasonaldaycycle.network;

import com.seasonaldaycycle.ModConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class DayCycleNetworking {
    private DayCycleNetworking() {}

    public static void init() {
        PayloadTypeRegistry.playC2S().register(
                SetDayCycleLengthPayload.ID,
                SetDayCycleLengthPayload.CODEC
        );

        PayloadTypeRegistry.playS2C().register(
                DayCycleLengthSyncPayload.ID,
                DayCycleLengthSyncPayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(SetDayCycleLengthPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (player.getServer() == null) return;

            boolean allowed = player.getServer().isSingleplayer() || player.hasPermissionLevel(2);
            if (!allowed) {
                ServerPlayNetworking.send(player,
                        new DayCycleLengthSyncPayload((int) ModConfig.getCycleLengthTicks()));
                return;
            }

            ModConfig.setCycleLengthTicks(payload.ticks());
            ServerPlayNetworking.send(player,
                    new DayCycleLengthSyncPayload((int) ModConfig.getCycleLengthTicks()));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayNetworking.send(handler.player,
                    new DayCycleLengthSyncPayload((int) ModConfig.getCycleLengthTicks()));
        });
    }
}
