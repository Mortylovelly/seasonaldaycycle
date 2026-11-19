package com.seasonaldaycycle.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Method;
import java.nio.DoubleBuffer;

/** Optional reflective bridge to AI Agent; that mod remains sole cursor owner. */
public final class AiAgentCursorCompat {
    private static final String CURSOR_CLASS = "com.mortylovelly.minecraftai.client.AiFreeCursor";
    private static final Method IS_ENABLED = findEnabledMethod();

    private AiAgentCursorCompat() {}

    private static Method findEnabledMethod() {
        if (!FabricLoader.getInstance().isModLoaded("minecraft_ai_agent")) return null;
        try {
            return Class.forName(CURSOR_CLASS).getMethod("isEnabled");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean isAvailable() { return IS_ENABLED != null; }

    public static boolean isEnabled() {
        if (IS_ENABLED == null) return false;
        try {
            return Boolean.TRUE.equals(IS_ENABLED.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /** GLFW coordinates stay current while AI Agent consumes Minecraft Mouse callbacks. */
    public static Pointer getScaledPointer(MinecraftClient client) {
        if (client == null) return new Pointer(0.0, 0.0);
        Window window = client.getWindow();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window.getHandle(), x, y);
            return new Pointer(x.get(0) * window.getScaledWidth() / Math.max(1.0, window.getWidth()),
                    y.get(0) * window.getScaledHeight() / Math.max(1.0, window.getHeight()));
        }
    }

    public record Pointer(double x, double y) {}
}
