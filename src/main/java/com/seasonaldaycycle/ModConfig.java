package com.seasonaldaycycle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
    public static final long DEFAULT_CYCLE_LENGTH_TICKS = 72000L;
    public static final long MIN_CYCLE_LENGTH_TICKS = 1200L;
    public static final long MAX_CYCLE_LENGTH_TICKS = 432000L;
    public static final long CYCLE_LENGTH_STEP_TICKS = 20L;

    private static final double DEFAULT_TOTAL_BASE_TICKS = 72000.0;
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

    public static long getCycleLengthTicks() {
        return values.cycleLengthTicks;
    }

    /**
     * Changes the in-memory value only. World saves are handled by DayCycleWorldConfig,
     * so a GUI change cannot leak from one world save into another.
     */
    public static void setCycleLengthTicks(long ticks) {
        values.cycleLengthTicks = sanitizeCycleLengthTicks(ticks);
    }

    public static long sanitizeCycleLengthTicks(long ticks) {
        long clamped = Math.max(MIN_CYCLE_LENGTH_TICKS, Math.min(MAX_CYCLE_LENGTH_TICKS, ticks));
        long rounded = Math.round((double) clamped / CYCLE_LENGTH_STEP_TICKS) * CYCLE_LENGTH_STEP_TICKS;
        return Math.max(MIN_CYCLE_LENGTH_TICKS, Math.min(MAX_CYCLE_LENGTH_TICKS, rounded));
    }

    public static double getCycleScale() {
        return getCycleScale(getCycleLengthTicks());
    }

    public static double getCycleScale(long cycleLengthTicks) {
        return sanitizeCycleLengthTicks(cycleLengthTicks) / DEFAULT_TOTAL_BASE_TICKS;
    }

    public static double getSpringDayTicks() {
        return getSpringDayTicks(getCycleLengthTicks());
    }

    public static double getSpringNightTicks() {
        return getSpringNightTicks(getCycleLengthTicks());
    }

    public static double getSummerDayTicks() {
        return getSummerDayTicks(getCycleLengthTicks());
    }

    public static double getSummerNightTicks() {
        return getSummerNightTicks(getCycleLengthTicks());
    }

    public static double getAutumnDayTicks() {
        return getAutumnDayTicks(getCycleLengthTicks());
    }

    public static double getAutumnNightTicks() {
        return getAutumnNightTicks(getCycleLengthTicks());
    }

    public static double getWinterDayTicks() {
        return getWinterDayTicks(getCycleLengthTicks());
    }

    public static double getWinterNightTicks() {
        return getWinterNightTicks(getCycleLengthTicks());
    }

    public static double getSpringDayTicks(long cycleLengthTicks) {
        return 36000.0 * getCycleScale(cycleLengthTicks);
    }

    public static double getSpringNightTicks(long cycleLengthTicks) {
        return 36000.0 * getCycleScale(cycleLengthTicks);
    }

    public static double getSummerDayTicks(long cycleLengthTicks) {
        return 42000.0 * getCycleScale(cycleLengthTicks);
    }

    public static double getSummerNightTicks(long cycleLengthTicks) {
        return 30000.0 * getCycleScale(cycleLengthTicks);
    }

    public static double getAutumnDayTicks(long cycleLengthTicks) {
        return 36000.0 * getCycleScale(cycleLengthTicks);
    }

    public static double getAutumnNightTicks(long cycleLengthTicks) {
        return 36000.0 * getCycleScale(cycleLengthTicks);
    }

    public static double getWinterDayTicks(long cycleLengthTicks) {
        return 30000.0 * getCycleScale(cycleLengthTicks);
    }

    public static double getWinterNightTicks(long cycleLengthTicks) {
        return 42000.0 * getCycleScale(cycleLengthTicks);
    }

    private static final class Config {
        long cycleLengthTicks = DEFAULT_CYCLE_LENGTH_TICKS;
        SeasonConfig spring = new SeasonConfig(36000, 36000);
        SeasonConfig summer = new SeasonConfig(42000, 30000);
        SeasonConfig autumn = new SeasonConfig(36000, 36000);
        SeasonConfig winter = new SeasonConfig(30000, 42000);

        void sanitize() {
            cycleLengthTicks = sanitizeCycleLengthTicks(cycleLengthTicks);
            if (spring == null) spring = new SeasonConfig(36000, 36000);
            if (summer == null) summer = new SeasonConfig(42000, 30000);
            if (autumn == null) autumn = new SeasonConfig(36000, 36000);
            if (winter == null) winter = new SeasonConfig(30000, 42000);
            spring.sanitize();
            summer.sanitize();
            autumn.sanitize();
            winter.sanitize();
        }
    }

    private static final class SeasonConfig {
        double dayTicks;
        double nightTicks;

        SeasonConfig(double dayTicks, double nightTicks) {
            this.dayTicks = dayTicks;
            this.nightTicks = nightTicks;
        }

        void sanitize() {
            if (!Double.isFinite(dayTicks) || dayTicks < 1000) dayTicks = 36000;
            if (!Double.isFinite(nightTicks) || nightTicks < 1000) nightTicks = 36000;
            dayTicks = Math.min(dayTicks, 500000);
            nightTicks = Math.min(nightTicks, 500000);
        }
    }
}
