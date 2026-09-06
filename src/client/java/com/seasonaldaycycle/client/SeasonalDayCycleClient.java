package com.seasonaldaycycle.client;

import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.network.DayCycleLengthSyncPayload;
import com.seasonaldaycycle.network.OpenDayCycleScreenPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

public final class SeasonalDayCycleClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(DayCycleLengthSyncPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                long ticks = ModConfig.sanitizeCycleLengthTicks(payload.ticks());
                ModConfig.setCycleLengthTicks(ticks);

                DayCycleScreen screen = DayCycleScreen.getActive();
                if (screen != null) {
                    screen.setCycleLengthFromServer(ticks);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenDayCycleScreenPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                if (!DayCycleScreen.isActive()) {
                    new DayCycleScreen();
                    client.mouse.unlockCursor();
                }
            });
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            DayCycleScreen screen = DayCycleScreen.getActive();
            if (screen == null) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            int mouseX = (int) Math.round(client.mouse.getX());
            int mouseY = (int) Math.round(client.mouse.getY());
            screen.renderOverlay(drawContext, mouseX, mouseY, 0.0f);
        });
    }
}
