package com.seasonaldaycycle;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("seasonaldaycycle")
public class SeasonalDayCycle {

    public static final String MODID = "seasonaldaycycle";
    public static final Logger LOGGER = LogManager.getLogger();

    public SeasonalDayCycle() {
        ModConfig.register();

        FMLJavaModLoadingContext.get().getModEventBus()
            .addListener(ModConfig::onLoad);

        MinecraftForge.EVENT_BUS.register(new DayCycleHandler());

        LOGGER.info("[SeasonalDayCycle] Loaded! Day cycle tied to Serene Seasons.");
    }
}
