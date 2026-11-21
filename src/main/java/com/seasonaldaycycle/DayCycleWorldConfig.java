package com.seasonaldaycycle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * World-save settings for Vertex. The file is written atomically and a last-known-good
 * copy is kept so an interrupted write cannot silently erase the world's settings.
 */
public final class DayCycleWorldConfig {
    private static final String FILE_NAME = ".seasonaldaycycle.json";
    private static final String BACKUP_SUFFIX = ".bak";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final int CONFIG_VERSION = 2;
    private static final long MAX_DAY_OFFSET_DAYS = 1_000_000_000_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<ServerWorld, Config> CACHE = new WeakHashMap<>();

    private DayCycleWorldConfig() {}

    public static long getCycleLengthTicks(ServerWorld world) {
        return getConfig(world).cycleLengthTicks;
    }

    public static long getElapsedDayOffsetDays(ServerWorld world) {
        return getConfig(world).elapsedDayOffsetDays;
    }

    public static void setCycleLengthTicks(ServerWorld world, long ticks) {
        if (world == null) return;
        Config config = getConfig(world);
        long value = ModConfig.sanitizeCycleLengthTicks(ticks);
        if (config.cycleLengthTicks == value) return;
        config.cycleLengthTicks = value;
        save(world.getServer(), config);
    }

    public static void setElapsedDayOffsetDays(ServerWorld world, long offsetDays) {
        if (world == null) return;
        Config config = getConfig(world);
        long value = sanitizeElapsedDayOffsetDays(offsetDays);
        if (config.elapsedDayOffsetDays == value) return;
        config.elapsedDayOffsetDays = value;
        save(world.getServer(), config);
    }

    public static long sanitizeElapsedDayOffsetDays(long offsetDays) {
        return Math.max(-MAX_DAY_OFFSET_DAYS, Math.min(MAX_DAY_OFFSET_DAYS, offsetDays));
    }

    public static void clear(ServerWorld world) {
        if (world != null) CACHE.remove(world);
    }

    public static void clearAll() {
        CACHE.clear();
    }

    private static Config getConfig(ServerWorld world) {
        if (world == null) return new Config();

        Config cached = CACHE.get(world);
        if (cached != null) return cached;

        Config loaded = load(world.getServer());
        CACHE.put(world, loaded);
        return loaded;
    }

    private static Path getPath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(FILE_NAME);
    }

    private static Config load(MinecraftServer server) {
        Config defaults = new Config();
        if (server == null) return defaults;

        Path path = getPath(server);
        Path backup = path.resolveSibling(path.getFileName() + BACKUP_SUFFIX);

        Config primary = tryLoad(path);
        if (primary != null) {
            primary.sanitize();
            return primary;
        }

        Config recovered = tryLoad(backup);
        if (recovered != null) {
            recovered.sanitize();
            SeasonalDayCycle.LOGGER.warn(
                    "[Vertex] World settings were recovered from the backup file: {}", backup);
            return recovered;
        }

        if (Files.exists(path) || Files.exists(backup)) {
            SeasonalDayCycle.LOGGER.error(
                    "[Vertex] Both the world settings file and its backup are unreadable. "
                            + "Using safe defaults without deleting either file.");
        } else {
            save(server, defaults);
        }
        return defaults;
    }

    private static Config tryLoad(Path path) {
        if (!Files.exists(path)) return null;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Config config = GSON.fromJson(reader, Config.class);
            if (config == null || config.cycleLengthTicks <= 0L) return null;
            return config;
        } catch (Exception exception) {
            SeasonalDayCycle.LOGGER.warn("[Vertex] Could not read world settings from {}.", path, exception);
            return null;
        }
    }

    private static boolean isValidConfigFile(Path path) {
        Config config = tryLoad(path);
        return config != null
                && config.cycleLengthTicks >= ModConfig.MIN_CYCLE_LENGTH_TICKS
                && config.cycleLengthTicks <= ModConfig.MAX_CYCLE_LENGTH_TICKS;
    }

    private static void save(MinecraftServer server, Config config) {
        if (server == null || config == null) return;

        Path path = getPath(server);
        Path backup = path.resolveSibling(path.getFileName() + BACKUP_SUFFIX);
        Path temporary = path.resolveSibling(path.getFileName() + TEMP_SUFFIX);

        try {
            Files.createDirectories(path.getParent());
            config.sanitize();

            Files.writeString(
                    temporary,
                    GSON.toJson(config),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            // Never replace a good backup with a corrupted or partial primary file.
            if (isValidConfigFile(path)) {
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                Files.move(temporary, path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SeasonalDayCycle.LOGGER.error(
                    "[Vertex] Failed to save world settings. The previous file and backup were kept.", exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupException) {
                SeasonalDayCycle.LOGGER.warn("[Vertex] Could not remove temporary settings file.", cleanupException);
            }
        }
    }

    private static final class Config {
        int version = CONFIG_VERSION;
        long cycleLengthTicks = ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;
        long elapsedDayOffsetDays = 0L;

        void sanitize() {
            if (cycleLengthTicks <= 0L) {
                cycleLengthTicks = ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;
            } else {
                cycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(cycleLengthTicks);
            }
            elapsedDayOffsetDays = sanitizeElapsedDayOffsetDays(elapsedDayOffsetDays);
            version = CONFIG_VERSION;
        }
    }
}
