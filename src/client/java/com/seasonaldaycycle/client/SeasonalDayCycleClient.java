package com.seasonaldaycycle.client;

import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.network.DayCycleLengthSyncPayload;
import com.seasonaldaycycle.network.OpenDayCycleScreenPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

public final class SeasonalDayCycleClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(DayCycleLengthSyncPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                long ticks = ModConfig.sanitizeCycleLengthTicks(payload.ticks());
                ModConfig.setCycleLengthTicks(ticks);
                if (client.currentScreen instanceof DayCycleScreen screen) {
                    screen.setCycleLengthFromServer(ticks);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenDayCycleScreenPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> client.setScreen(new DayCycleScreen(null)));
        });
    }
}
