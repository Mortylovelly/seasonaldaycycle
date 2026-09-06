package com.seasonaldaycycle.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.GameRules;

public final class SeasonalDayCycleClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.isPaused()) return;
            if (!client.world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) return;

            // ClientWorld advances time by one tick on its own.
            // The server-side DayCycleHandler is authoritative, so cancel that
            // local vanilla increment and let server time updates move the target.
            client.world.getLevelProperties().setTimeOfDay(client.world.getTimeOfDay() - 1L);
        });
    }
}
