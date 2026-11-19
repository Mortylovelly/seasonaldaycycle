package com.seasonaldaycycle.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Method;
import java.nio.DoubleBuffer;

/** Optional bridge to AI Agent's public free-cursor API. */
public final class AiAgentCursorCompat {
    private static final String CURSOR_CLASS = "com.mortylovelly.minecraftai.client.AiFreeCursor";
    private static final Method IS_ENABLED = findEnabledMethod();
    private static final Method TOGGLE = findToggleMethod();

    private AiAgentCursorCompat() {
    }

    private static Method findEnabledMethod() {
        if (!FabricLoader.getInstance().isModLoaded("minecraft_ai_agent")) return null;
        try {
            return Class.forName(CURSOR_CLASS).getMethod("isEnabled");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Method findToggleMethod() {
        if (IS_ENABLED == null) return null;
        try {
            return Class.forName(CURSOR_CLASS).getMethod("toggle", MinecraftClient.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean isAvailable() {
        return IS_ENABLED != null;
    }

    public static boolean canControl() {
        return IS_ENABLED != null && TOGGLE != null;
    }

    public static boolean isEnabled() {
        if (IS_ENABLED == null) return false;
        try {
            return Boolean.TRUE.equals(IS_ENABLED.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /** Toggles AI Agent's cursor and returns its state after the call. */
    public static boolean toggle(MinecraftClient client) {
        if (TOGGLE == null || client == null) return false;
        try {
            TOGGLE.invoke(null, client);
            return isEnabled();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static Pointer getScaledPointer(MinecraftClient client) {
        if (client == null) return new Pointer(0.0, 0.0);
        Window window = client.getWindow();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window.getHandle(), x, y);
            return new Pointer(
                    x.get(0) * window.getScaledWidth() / Math.max(1.0, window.getWidth()),
                    y.get(0) * window.getScaledHeight() / Math.max(1.0, window.getHeight())
            );
        }
    }

    public record Pointer(double x, double y) {
    }
}
