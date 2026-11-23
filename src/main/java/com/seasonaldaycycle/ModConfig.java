package com.seasonaldaycycle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
    public static final long DEFAULT_CYCLE_LENGTH_TICKS = 144000L;
    public static final long MIN_CYCLE_LENGTH_TICKS = 20L;
    public static final long MAX_CYCLE_LENGTH_TICKS = 1_728_000L;
    public static final long CYCLE_LENGTH_STEP_TICKS = 20L;

    private static final double DEFAULT_TOTAL_BASE_TICKS = 144000.0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("seasonaldaycycle.json");
    private static Config values = new Config();
    private static Double syncedSpringDayRatio;
    private static Double syncedSummerDayRatio;
    private static Double syncedAutumnDayRatio;
    private static Double syncedWinterDayRatio;

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

    public static double getSpringDayRatio() { return syncedSpringDayRatio != null ? syncedSpringDayRatio : getDayRatio(values.spring); }
    public static double getSummerDayRatio() { return syncedSummerDayRatio != null ? syncedSummerDayRatio : getDayRatio(values.summer); }
    public static double getAutumnDayRatio() { return syncedAutumnDayRatio != null ? syncedAutumnDayRatio : getDayRatio(values.autumn); }
    public static double getWinterDayRatio() { return syncedWinterDayRatio != null ? syncedWinterDayRatio : getDayRatio(values.winter); }

    /** Receives server-authoritative proportions for an online world; these are not written to the local config file. */
    public static void setSyncedSeasonDayRatios(float spring, float summer, float autumn, float winter) {
        syncedSpringDayRatio = sanitizeRatio(spring);
        syncedSummerDayRatio = sanitizeRatio(summer);
        syncedAutumnDayRatio = sanitizeRatio(autumn);
        syncedWinterDayRatio = sanitizeRatio(winter);
    }

    public static void clearSyncedSeasonDayRatios() {
        syncedSpringDayRatio = null;
        syncedSummerDayRatio = null;
        syncedAutumnDayRatio = null;
        syncedWinterDayRatio = null;
    }

    private static double sanitizeRatio(double ratio) {
        if (!Double.isFinite(ratio)) return 0.5;
        return Math.max(0.001, Math.min(0.999, ratio));
    }

    private static double getDayRatio(SeasonConfig season) {
        double total = season.dayTicks + season.nightTicks;
        return total > 0.0 ? season.dayTicks / total : 0.5;
    }

    public static double getDayRatioForSubSeason(String subSeason) {
        if (subSeason == null) return getSpringDayRatio();
        return switch (subSeason) {
            case "EARLY_SPRING", "MID_SPRING", "LATE_SPRING" -> getSpringDayRatio();
            case "EARLY_SUMMER", "MID_SUMMER", "LATE_SUMMER" -> getSummerDayRatio();
            case "EARLY_AUTUMN", "MID_AUTUMN", "LATE_AUTUMN" -> getAutumnDayRatio();
            case "EARLY_WINTER", "MID_WINTER", "LATE_WINTER" -> getWinterDayRatio();
            default -> getSpringDayRatio();
        };
    }

    public static double getSpringDayTicks() { return getSpringDayTicks(getCycleLengthTicks()); }
    public static double getSpringNightTicks() { return getSpringNightTicks(getCycleLengthTicks()); }
    public static double getSummerDayTicks() { return getSummerDayTicks(getCycleLengthTicks()); }
    public static double getSummerNightTicks() { return getSummerNightTicks(getCycleLengthTicks()); }
    public static double getAutumnDayTicks() { return getAutumnDayTicks(getCycleLengthTicks()); }
    public static double getAutumnNightTicks() { return getAutumnNightTicks(getCycleLengthTicks()); }
    public static double getWinterDayTicks() { return getWinterDayTicks(getCycleLengthTicks()); }
    public static double getWinterNightTicks() { return getWinterNightTicks(getCycleLengthTicks()); }

    public static double getSpringDayTicks(long cycleLengthTicks) { return sanitizeCycleLengthTicks(cycleLengthTicks) * getSpringDayRatio(); }
    public static double getSpringNightTicks(long cycleLengthTicks) { return sanitizeCycleLengthTicks(cycleLengthTicks) * (1.0 - getSpringDayRatio()); }
    public static double getSummerDayTicks(long cycleLengthTicks) { return sanitizeCycleLengthTicks(cycleLengthTicks) * getSummerDayRatio(); }
    public static double getSummerNightTicks(long cycleLengthTicks) { return sanitizeCycleLengthTicks(cycleLengthTicks) * (1.0 - getSummerDayRatio()); }
    public static double getAutumnDayTicks(long cycleLengthTicks) { return sanitizeCycleLengthTicks(cycleLengthTicks) * getAutumnDayRatio(); }
    public static double getAutumnNightTicks(long cycleLengthTicks) { return sanitizeCycleLengthTicks(cycleLengthTicks) * (1.0 - getAutumnDayRatio()); }
    public static double getWinterDayTicks(long cycleLengthTicks) { return sanitizeCycleLengthTicks(cycleLengthTicks) * getWinterDayRatio(); }
    public static double getWinterNightTicks(long cycleLengthTicks) { return sanitizeCycleLengthTicks(cycleLengthTicks) * (1.0 - getWinterDayRatio()); }

    public static double getDayTicksForSubSeason(String subSeason, long cycleLengthTicks) {
        if (subSeason == null) return getSpringDayTicks(cycleLengthTicks);
        return switch (subSeason) {
            case "EARLY_SPRING", "MID_SPRING", "LATE_SPRING" -> getSpringDayTicks(cycleLengthTicks);
            case "EARLY_SUMMER", "MID_SUMMER", "LATE_SUMMER" -> getSummerDayTicks(cycleLengthTicks);
            case "EARLY_AUTUMN", "MID_AUTUMN", "LATE_AUTUMN" -> getAutumnDayTicks(cycleLengthTicks);
            case "EARLY_WINTER", "MID_WINTER", "LATE_WINTER" -> getWinterDayTicks(cycleLengthTicks);
            default -> getSpringDayTicks(cycleLengthTicks);
        };
    }

    public static double getNightTicksForSubSeason(String subSeason, long cycleLengthTicks) {
        if (subSeason == null) return getSpringNightTicks(cycleLengthTicks);
        return switch (subSeason) {
            case "EARLY_SPRING", "MID_SPRING", "LATE_SPRING" -> getSpringNightTicks(cycleLengthTicks);
            case "EARLY_SUMMER", "MID_SUMMER", "LATE_SUMMER" -> getSummerNightTicks(cycleLengthTicks);
            case "EARLY_AUTUMN", "MID_AUTUMN", "LATE_AUTUMN" -> getAutumnNightTicks(cycleLengthTicks);
            case "EARLY_WINTER", "MID_WINTER", "LATE_WINTER" -> getWinterNightTicks(cycleLengthTicks);
            default -> getSpringNightTicks(cycleLengthTicks);
        };
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
            spring.sanitize(); summer.sanitize(); autumn.sanitize(); winter.sanitize();
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
