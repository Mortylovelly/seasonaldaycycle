package com.seasonaldaycycle.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-only persistent preferences for the in-world calendar HUD. */
public final class VertexHudConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("vertex-hud.json");
    private static final int CONFIG_VERSION = 1;
    public static final float MIN_SCALE = 0.35f;
    public static final float MAX_SCALE = 0.75f;
    private static final float DEFAULT_SCALE = 0.55f;
    private static Values values = new Values();

    private VertexHudConfig() {}

    public static void load() {
        try {
            Files.createDirectories(PATH.getParent());
            if (Files.exists(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH)) {
                    Values loaded = GSON.fromJson(reader, Values.class);
                    if (loaded != null) {
                        // Older versions used 75%-175%, where 75% was the largest
                        // on the new compact HUD. Reverse-map that range once.
                        if (loaded.version < CONFIG_VERSION) {
                            loaded.scale = remapLegacyScale(loaded.scale);
                            loaded.version = CONFIG_VERSION;
                        }
                        values = loaded;
                    }
                }
            }
        } catch (Exception ignored) {
            values = new Values();
        }
        sanitize();
        save();
    }

    public static boolean isEnabled() { return values.enabled; }
    public static int getPosition() { return values.position; }
    public static float getScale() { return values.scale; }

    public static void setEnabled(boolean enabled) {
        values.enabled = enabled;
        save();
    }

    public static void setPosition(int position) {
        values.position = Math.max(0, Math.min(5, position));
        save();
    }

    /** In-memory slider update; the file is written only when the drag ends. */
    public static void previewScale(float scale) {
        values.scale = clampScale(scale);
    }

    public static void setScale(float scale) {
        values.scale = clampScale(scale);
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(values, writer);
            }
        } catch (Exception ignored) {
            // A preference write must never prevent Minecraft from loading.
        }
    }

    private static void sanitize() {
        if (values == null) values = new Values();
        values.position = Math.max(0, Math.min(5, values.position));
        values.scale = clampScale(values.scale);
        values.version = CONFIG_VERSION;
    }

    private static float remapLegacyScale(float oldScale) {
        if (!Float.isFinite(oldScale)) return DEFAULT_SCALE;
        float oldClamped = Math.max(0.75f, Math.min(1.75f, oldScale));
        float normalized = oldClamped - 0.75f;
        return MAX_SCALE - normalized * (MAX_SCALE - MIN_SCALE);
    }

    private static float clampScale(float scale) {
        if (!Float.isFinite(scale)) return DEFAULT_SCALE;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    private static final class Values {
        int version = 0;
        boolean enabled = false;
        int position = 2;
        float scale = DEFAULT_SCALE;
    }
}
