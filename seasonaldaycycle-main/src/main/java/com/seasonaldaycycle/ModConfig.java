package com.seasonaldaycycle;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public class ModConfig {

    public static final ForgeConfigSpec SPEC;

    public static ForgeConfigSpec.IntValue SPRING_DAY_TICKS;
    public static ForgeConfigSpec.IntValue SPRING_NIGHT_TICKS;
    public static ForgeConfigSpec.IntValue SUMMER_DAY_TICKS;
    public static ForgeConfigSpec.IntValue SUMMER_NIGHT_TICKS;
    public static ForgeConfigSpec.IntValue AUTUMN_DAY_TICKS;
    public static ForgeConfigSpec.IntValue AUTUMN_NIGHT_TICKS;
    public static ForgeConfigSpec.IntValue WINTER_DAY_TICKS;
    public static ForgeConfigSpec.IntValue WINTER_NIGHT_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("SeasonalDayCycle Configuration",
            "Values = real ticks (20 TPS) for day or night.",
            "Summer: longer day. Winter: longer night.");

        builder.push("spring");
        SPRING_DAY_TICKS   = builder.comment("Real ticks for spring daytime (36000 = 30min)")
            .defineInRange("day_ticks", 36000, 1000, 500000);
        SPRING_NIGHT_TICKS = builder.comment("Real ticks for spring nighttime (36000 = 30min)")
            .defineInRange("night_ticks", 36000, 1000, 500000);
        builder.pop();

        builder.push("summer");
        SUMMER_DAY_TICKS   = builder.comment("Real ticks for summer daytime (42000 = 35min)")
            .defineInRange("day_ticks", 42000, 1000, 500000);
        SUMMER_NIGHT_TICKS = builder.comment("Real ticks for summer nighttime (30000 = 25min)")
            .defineInRange("night_ticks", 30000, 1000, 500000);
        builder.pop();

        builder.push("autumn");
        AUTUMN_DAY_TICKS   = builder.comment("Real ticks for autumn daytime (36000 = 30min)")
            .defineInRange("day_ticks", 36000, 1000, 500000);
        AUTUMN_NIGHT_TICKS = builder.comment("Real ticks for autumn nighttime (36000 = 30min)")
            .defineInRange("night_ticks", 36000, 1000, 500000);
        builder.pop();

        builder.push("winter");
        WINTER_DAY_TICKS   = builder.comment("Real ticks for winter daytime (30000 = 25min)")
            .defineInRange("day_ticks", 30000, 1000, 500000);
        WINTER_NIGHT_TICKS = builder.comment("Real ticks for winter nighttime (42000 = 35min)")
            .defineInRange("night_ticks", 42000, 1000, 500000);
        builder.pop();

        SPEC = builder.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(Type.SERVER, SPEC, "seasonaldaycycle-server.toml");
    }

    public static void onLoad(ModConfigEvent event) {
        SeasonalDayCycle.LOGGER.info("[SeasonalDayCycle] Config loaded.");
    }
}
