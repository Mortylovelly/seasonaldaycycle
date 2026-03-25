package com.seasonaldaycycle;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public class ModConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // --- Spring ---
    public static ForgeConfigSpec.IntValue SPRING_DAY_TICKS;
    public static ForgeConfigSpec.IntValue SPRING_NIGHT_TICKS;

    // --- Summer ---
    public static ForgeConfigSpec.IntValue SUMMER_DAY_TICKS;
    public static ForgeConfigSpec.IntValue SUMMER_NIGHT_TICKS;

    // --- Autumn ---
    public static ForgeConfigSpec.IntValue AUTUMN_DAY_TICKS;
    public static ForgeConfigSpec.IntValue AUTUMN_NIGHT_TICKS;

    // --- Winter ---
    public static ForgeConfigSpec.IntValue WINTER_DAY_TICKS;
    public static ForgeConfigSpec.IntValue WINTER_NIGHT_TICKS;

    static {
        BUILDER.comment("SeasonalDayCycle Configuration",
            "Values represent how many REAL ticks (at 20 TPS) the day or night lasts.",
            "Default: 60-minute full cycle (36000 day + 36000 night = 72000 total).",
            "Summer: longer day. Winter: longer night.");

        BUILDER.push("spring");
        SPRING_DAY_TICKS   = BUILDER.comment("Real ticks for spring daytime (default 36000 = 30min)")
            .defineInRange("day_ticks", 36000, 1000, 500000);
        SPRING_NIGHT_TICKS = BUILDER.comment("Real ticks for spring nighttime (default 36000 = 30min)")
            .defineInRange("night_ticks", 36000, 1000, 500000);
        BUILDER.pop();

        BUILDER.push("summer");
        SUMMER_DAY_TICKS   = BUILDER.comment("Real ticks for summer daytime (default 42000 = 35min)")
            .defineInRange("day_ticks", 42000, 1000, 500000);
        SUMMER_NIGHT_TICKS = BUILDER.comment("Real ticks for summer nighttime (default 30000 = 25min)")
            .defineInRange("night_ticks", 30000, 1000, 500000);
        BUILDER.pop();

        BUILDER.push("autumn");
        AUTUMN_DAY_TICKS   = BUILDER.comment("Real ticks for autumn daytime (default 36000 = 30min)")
            .defineInRange("day_ticks", 36000, 1000, 500000);
        AUTUMN_NIGHT_TICKS = BUILDER.comment("Real ticks for autumn nighttime (default 36000 = 30min)")
            .defineInRange("night_ticks", 36000, 1000, 500000);
        BUILDER.pop();

        BUILDER.push("winter");
        WINTER_DAY_TICKS   = BUILDER.comment("Real ticks for winter daytime (default 30000 = 25min)")
            .defineInRange("day_ticks", 30000, 1000, 500000);
        WINTER_NIGHT_TICKS = BUILDER.comment("Real ticks for winter nighttime (default 42000 = 35min)")
            .defineInRange("night_ticks", 42000, 1000, 500000);
        BUILDER.pop();

        SPEC = BUILDER.build();
        ModLoadingContext.get().registerConfig(Type.SERVER, SPEC, "seasonaldaycycle-server.toml");
    }

    public static void onLoad(ModConfigEvent event) {
        SeasonalDayCycle.LOGGER.info("[SeasonalDayCycle] Config loaded.");
    }
}
