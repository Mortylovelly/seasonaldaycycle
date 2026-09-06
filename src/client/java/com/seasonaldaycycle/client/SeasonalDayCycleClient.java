package com.seasonaldaycycle.client;

import net.fabricmc.api.ClientModInitializer;

public final class SeasonalDayCycleClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Visual sky interpolation is handled by the client mixins.
        // The client world time is left untouched so it remains the normal
        // vanilla client-side simulation state.
    }
}
