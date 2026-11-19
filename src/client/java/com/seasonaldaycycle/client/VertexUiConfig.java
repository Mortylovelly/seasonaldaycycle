package com.seasonaldaycycle.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent scale shared by every Vertex panel. */
public final class VertexUiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("vertex-ui.json");
    private static final float MIN_SCALE = 0.55f;
    private static final float MAX_SCALE = 1.40f;
    private static Values values = new Values();

    private VertexUiConfig() {
    }

    public static void load() {
        try {
            Files.createDirectories(PATH.getParent());
            if (Files.exists(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH)) {
                    Values loaded = GSON.fromJson(reader, Values.class);
                    if (loaded != null) values = loaded;
                }
            }
        } catch (Exception ignored) {
            values = new Values();
        }
        if (values == null) values = new Values();
        values.scale = clamp(values.scale);
        save();
    }

    public static float getScale() {
        return values.scale;
    }

    public static void adjustScale(float delta) {
        values.scale = clamp(values.scale + delta);
        save();
    }

    private static float clamp(float scale) {
        if (!Float.isFinite(scale)) return 0.85f;
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
        float scale = 0.85f;
    }
}
