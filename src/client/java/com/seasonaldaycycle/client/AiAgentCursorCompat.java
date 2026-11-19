package com.seasonaldaycycle.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Method;
import java.nio.DoubleBuffer;

/**
 * Read-only bridge to the public cursor state owned by Minecraft AI Agent 0.3.19.
 *
 * Vertex never unlocks, locks, or toggles the cursor. When AI Agent is absent or its
 * free cursor (G) is disabled, Vertex deliberately does not process pointer input.
 */
public final class AiAgentCursorCompat {
    private static final String CURSOR_CLASS = "com.mortylovelly.minecraftai.client.AiFreeCursor";
    private static final Method IS_ENABLED = findEnabledMethod();

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

    public static boolean isAvailable() {
        return IS_ENABLED != null;
    }

    public static boolean isEnabled() {
        if (IS_ENABLED == null) return false;
        try {
            return Boolean.TRUE.equals(IS_ENABLED.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean shouldHandleInput(MinecraftClient client) {
        if (!isEnabled() || client == null || client.player == null || client.world == null) {
            return false;
        }
        long window = client.getWindow().getHandle();
        return GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED;
    }

    /** Uses the exact GLFW-to-scaled-window conversion used by AI Agent's MouseMixin. */
    public static Pointer getScaledPointer(MinecraftClient client) {
        if (!shouldHandleInput(client)) return new Pointer(0.0, 0.0);
        Window window = client.getWindow();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer cursorX = stack.mallocDouble(1);
            DoubleBuffer cursorY = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window.getHandle(), cursorX, cursorY);
            return new Pointer(
                    scaledX(cursorX.get(0), window),
                    scaledY(cursorY.get(0), window)
            );
        }
    }

    /** Converts a cursor callback's raw GLFW coordinates exactly like AI Agent. */
    public static Pointer toScaledPointer(MinecraftClient client, double rawX, double rawY) {
        if (client == null) return new Pointer(0.0, 0.0);
        Window window = client.getWindow();
        return new Pointer(scaledX(rawX, window), scaledY(rawY, window));
    }

    private static double scaledX(double rawX, Window window) {
        return rawX * window.getScaledWidth() / (double) window.getWidth();
    }

    private static double scaledY(double rawY, Window window) {
        return rawY * window.getScaledHeight() / (double) window.getHeight();
    }

    public record Pointer(double x, double y) {
    }
}
