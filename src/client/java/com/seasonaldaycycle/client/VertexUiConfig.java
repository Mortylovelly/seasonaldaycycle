package com.seasonaldaycycle.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared panel scale using the same range and resize behaviour as AI Agent's Logs HUD.
 */
public final class VertexUiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("vertex-ui.json");
    private static final int CONFIG_VERSION = 2;
    private static final float MIN_SCALE = 0.25f;
    private static final float MAX_SCALE = 1.6f;
    private static final float DEFAULT_SCALE = 300.0f / 360.0f;
    private static Values values = new Values();

    private VertexUiConfig() {
    }

    public static void load() {
        try {
            Files.createDirectories(PATH.getParent());
            if (Files.exists(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH)) {
                    Values loaded = GSON.fromJson(reader, Values.class);
                    if (loaded != null) {
                        if (loaded.version < CONFIG_VERSION) {
                            loaded.scale = DEFAULT_SCALE;
                            loaded.version = CONFIG_VERSION;
                        }
                        values = loaded;
                    }
                }
            }
        } catch (Exception ignored) {
            values = new Values();
        }
        if (values == null) values = new Values();
        values.scale = clamp(values.scale);
        values.version = CONFIG_VERSION;
        save();
    }

    public static float getScale() {
        return values.scale;
    }

    public static void setScale(float scale) {
        previewScale(scale);
        commitScale();
    }

    public static void previewScale(float scale) {
        values.scale = clamp(scale);
    }

    public static void commitScale() {
        values.scale = clamp(values.scale);
        values.version = CONFIG_VERSION;
        save();
    }

    public static void adjustScale(float delta) {
        setScale(values.scale + delta);
    }

    private static float clamp(float scale) {
        if (!Float.isFinite(scale)) return DEFAULT_SCALE;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(values, writer);
            }
        } catch (Exception ignored) {
            // A client preference write must never prevent the game from loading.
        }
    }

    private static final class Values {
        int version = 0;
        float scale = DEFAULT_SCALE;
    }
}
