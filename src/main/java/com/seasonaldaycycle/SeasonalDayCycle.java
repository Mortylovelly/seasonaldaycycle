package com.seasonaldaycycle;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeasonalDayCycle implements ModInitializer {
    public static final String MODID = "seasonaldaycycle";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        ModConfig.load();
        ServerTickEvents.END_WORLD_TICK.register(DayCycleHandler::onWorldTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                DayCycleCommand.register(dispatcher));

        LOGGER.info("[SeasonalDayCycle] Loaded! Serene Seasons integration is optional.");
    }
}
