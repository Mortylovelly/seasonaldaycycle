package com.seasonaldaycycle.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameRules;

public final class SeasonalDayCycleClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.isPaused()) return;
            if (!client.world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) return;
            client.world.getLevelProperties().setTimeOfDay(client.world.getTimeOfDay() - 1L);
        });

        WorldRenderEvents.START.register(context ->
                ClientTimeInterpolator.onRenderTick(MinecraftClient.getInstance()));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                ClientTimeInterpolator.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientTimeInterpolator.reset());
    }
}
