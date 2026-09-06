package com.seasonaldaycycle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("seasonaldaycycle.json");
    private static Config values = new Config();

    private ModConfig() {}

    public static void load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    Config loaded = GSON.fromJson(reader, Config.class);
                    if (loaded != null) values = loaded;
                }
            } else {
                save();
            }
        } catch (Exception exception) {
            SeasonalDayCycle.LOGGER.error("[SeasonalDayCycle] Failed to load config, using defaults.", exception);
            values = new Config();
        }
        values.sanitize();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(values, writer);
            }
        } catch (Exception exception) {
            SeasonalDayCycle.LOGGER.error("[SeasonalDayCycle] Failed to save config.", exception);
        }
    }

    public static double getSpringDayTicks() { return values.spring.dayTicks; }
    public static double getSpringNightTicks() { return values.spring.nightTicks; }
    public static double getSummerDayTicks() { return values.summer.dayTicks; }
    public static double getSummerNightTicks() { return values.summer.nightTicks; }
    public static double getAutumnDayTicks() { return values.autumn.dayTicks; }
    public static double getAutumnNightTicks() { return values.autumn.nightTicks; }
    public static double getWinterDayTicks() { return values.winter.dayTicks; }
    public static double getWinterNightTicks() { return values.winter.nightTicks; }

    private static final class Config {
        SeasonConfig spring = new SeasonConfig(36000, 36000);
        SeasonConfig summer = new SeasonConfig(42000, 30000);
        SeasonConfig autumn = new SeasonConfig(36000, 36000);
        SeasonConfig winter = new SeasonConfig(30000, 42000);

        void sanitize() {
            if (spring == null) spring = new SeasonConfig(36000, 36000);
            if (summer == null) summer = new SeasonConfig(42000, 30000);
            if (autumn == null) autumn = new SeasonConfig(36000, 36000);
            if (winter == null) winter = new SeasonConfig(30000, 42000);
            spring.sanitize(); summer.sanitize(); autumn.sanitize(); winter.sanitize();
        }
    }

    private static final class SeasonConfig {
        double dayTicks;
        double nightTicks;
        SeasonConfig(double dayTicks, double nightTicks) { this.dayTicks = dayTicks; this.nightTicks = nightTicks; }
        void sanitize() {
            if (!Double.isFinite(dayTicks) || dayTicks < 1000) dayTicks = 36000;
            if (!Double.isFinite(nightTicks) || nightTicks < 1000) nightTicks = 36000;
            dayTicks = Math.min(dayTicks, 500000);
            nightTicks = Math.min(nightTicks, 500000);
        }
    }
}
