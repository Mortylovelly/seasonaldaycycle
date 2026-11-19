package com.seasonaldaycycle.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

/** Provides an independent visible mouse cursor while a Vertex overlay is open. */
public final class VertexCursorController {
    private static boolean ownsCursor;
    private static long windowHandle;
    private static int previousCursorMode = GLFW.GLFW_CURSOR_DISABLED;
    private static boolean aiCursorWasEnabled;

    private VertexCursorController() {
    }

    public static void acquire(MinecraftClient client) {
        if (client == null) return;
        long handle = client.getWindow().getHandle();
        if (ownsCursor && windowHandle == handle) {
            setCursorNormal(handle);
            return;
        }
        if (ownsCursor) release(client);
        windowHandle = handle;
        previousCursorMode = GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR);
        aiCursorWasEnabled = AiAgentCursorCompat.isEnabled();
        ownsCursor = true;
        setCursorNormal(handle);
    }

    public static void ensureCursorAvailable(MinecraftClient client) {
        if (client == null) return;
        long handle = client.getWindow().getHandle();
        if (!ownsCursor || windowHandle != handle) acquire(client);
        else setCursorNormal(handle);
    }

    public static void release(MinecraftClient client) {
        if (!ownsCursor) return;
        if (windowHandle != 0L && !AiAgentCursorCompat.isEnabled()) {
            int restoreMode = aiCursorWasEnabled ? GLFW.GLFW_CURSOR_DISABLED : previousCursorMode;
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, restoreMode);
        }
        ownsCursor = false;
        windowHandle = 0L;
        aiCursorWasEnabled = false;
    }

    public static Pointer getScaledPointer(MinecraftClient client) {
        if (client == null) return new Pointer(0.0, 0.0);
        Window window = client.getWindow();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window.getHandle(), x, y);
            return toScaledPointer(client, x.get(0), y.get(0));
        }
    }

    public static Pointer toScaledPointer(MinecraftClient client, double rawX, double rawY) {
        if (client == null) return new Pointer(0.0, 0.0);
        Window window = client.getWindow();
        return new Pointer(
                rawX * window.getScaledWidth() / Math.max(1.0, window.getWidth()),
                rawY * window.getScaledHeight() / Math.max(1.0, window.getHeight())
        );
    }

    private static void setCursorNormal(long handle) {
        if (GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL) {
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    public record Pointer(double x, double y) {
    }
}
