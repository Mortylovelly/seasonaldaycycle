package com.seasonaldaycycle.client;

import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.network.DayCycleLengthSyncPayload;
import com.seasonaldaycycle.network.OpenDayCycleScreenPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

public final class SeasonalDayCycleClient implements ClientModInitializer {
    private static long knownCycleLengthTicks = ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;

    @Override
    public void onInitializeClient() {
        VertexHudConfig.load();
        VertexUiConfig.load();
        ClientTickEvents.END_CLIENT_TICK.register(VertexPanelController::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(SeasonalDayCycleClient::resetForWorldExit));

        ClientPlayNetworking.registerGlobalReceiver(DayCycleLengthSyncPayload.TYPE, (payload, player, responseSender) -> {
            long ticks = ModConfig.sanitizeCycleLengthTicks(payload.ticks());
            knownCycleLengthTicks = ticks;
            ModConfig.setCycleLengthTicks(ticks);
            ModConfig.setSyncedSeasonDayRatios(
                    payload.springDayRatio(), payload.summerDayRatio(),
                    payload.autumnDayRatio(), payload.winterDayRatio()
            );
            DayCycleScreen screen = DayCycleScreen.getActive();
            if (screen != null) screen.setCycleLengthFromServer(ticks, payload.canEdit());
            else DayCycleScreen.updateCanEdit(payload.canEdit());
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenDayCycleScreenPayload.TYPE, (payload, player, responseSender) ->
                VertexPanelController.openMain(MinecraftClient.getInstance()));

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();
            AiAgentCursorCompat.Pointer pointer = AiAgentCursorCompat.getScaledPointer(client);
            if (VertexPanelController.isActive()) {
                VertexPanelController.render(drawContext,
                        (int) Math.round(pointer.x()), (int) Math.round(pointer.y()), 0.0f);
            } else {
                VertexHudOverlay.render(drawContext, width, height);
            }
        });
    }

    private static void resetForWorldExit() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (VertexPanelController.isActive()) VertexPanelController.close(client);
        else {
            DayCycleScreen screen = DayCycleScreen.getActive();
            if (screen != null) screen.close();
        }
        knownCycleLengthTicks = ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;
        ModConfig.setCycleLengthTicks(ModConfig.DEFAULT_CYCLE_LENGTH_TICKS);
        ModConfig.clearSyncedSeasonDayRatios();
        SkyAngleInterpolationState.clearAll();
        VertexHudOverlay.reset();
    }

    public static long getKnownCycleLengthTicks() { return knownCycleLengthTicks; }

    public static void setKnownCycleLengthTicks(long ticks) {
        knownCycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(ticks);
        ModConfig.setCycleLengthTicks(knownCycleLengthTicks);
    }
}
