package com.seasonaldaycycle.client;

import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.network.DayCycleLengthSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
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

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("daycycle")
                        .then(ClientCommandManager.literal("gui")
                                .executes(context -> {
                                    MinecraftClient.getInstance().setScreen(new DayCycleScreen(null));
                                    return 1;
                                }))));
    }
}
