package com.seasonaldaycycle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;

public final class DayCycleWorldConfig {
    private static final String FILE_NAME = ".seasonaldaycycle.json";
    private static final Map<ServerWorld, Long> CACHE = new WeakHashMap<>();

    private DayCycleWorldConfig() {}

    public static long getCycleLengthTicks(ServerWorld world) {
        if (world == null) {
            return ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;
        }

        Long cached = CACHE.get(world);
        if (cached != null) {
            return cached;
        }

        long value = load(world.getServer());
        CACHE.put(world, value);
        return value;
    }

    public static void setCycleLengthTicks(ServerWorld world, long ticks) {
        if (world == null) return;

        long value = ModConfig.sanitizeCycleLengthTicks(ticks);
        CACHE.put(world, value);
        save(world.getServer(), value);
    }

    public static void clear(ServerWorld world) {
        if (world != null) {
            CACHE.remove(world);
        }
    }

    private static Path getPath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(FILE_NAME);
    }

    private static long load(MinecraftServer server) {
        if (server == null) {
            return ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;
        }

        Path path = getPath(server);
        if (!Files.exists(path)) {
            long defaultValue = ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;
            save(server, defaultValue);
            return defaultValue;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            Config config = ModConfigGson.getGson().fromJson(reader, Config.class);
            if (config != null) {
                return ModConfig.sanitizeCycleLengthTicks(config.cycleLengthTicks);
            }
        } catch (Exception exception) {
            SeasonalDayCycle.LOGGER.error("[SeasonalDayCycle] Failed to load world cycle config, using default.", exception);
        }

        return ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;
    }

    private static void save(MinecraftServer server, long ticks) {
        if (server == null) return;

        Path path = getPath(server);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                ModConfigGson.getGson().toJson(new Config(ModConfig.sanitizeCycleLengthTicks(ticks)), writer);
            }
        } catch (IOException exception) {
            SeasonalDayCycle.LOGGER.error("[SeasonalDayCycle] Failed to save world cycle config.", exception);
        }
    }

    private static final class Config {
        long cycleLengthTicks;

        Config(long cycleLengthTicks) {
            this.cycleLengthTicks = cycleLengthTicks;
        }
    }
}
