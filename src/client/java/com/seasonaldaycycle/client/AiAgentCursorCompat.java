package com.seasonaldaycycle.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Optional bridge to Minecraft AI Agent 0.3.20's shared mouse event bus.
 *
 * Vertex does not install its own Mouse mixin and never changes cursor mode.
 * AI Agent owns the only Mouse mixin, the G toggle, and scaled pointer coordinates.
 */
public final class AiAgentCursorCompat {
    private static final String EVENTS_CLASS = "com.mortylovelly.minecraftai.client.AiCursorEvents";
    private static final String CURSOR_CLASS = "com.mortylovelly.minecraftai.client.AiFreeCursor";
    private static final Method IS_ENABLED = findCursorEnabledMethod();

    private static volatile boolean registered;
    private static volatile boolean pointerKnown;
    private static volatile double pointerX;
    private static volatile double pointerY;

    private AiAgentCursorCompat() {
    }

    private static Method findCursorEnabledMethod() {
        if (!FabricLoader.getInstance().isModLoaded("minecraft_ai_agent")) return null;
        try {
            return Class.forName(CURSOR_CLASS).getMethod("isEnabled");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean isAvailable() {
        return IS_ENABLED != null && registered;
    }

    public static boolean isEnabled() {
        if (IS_ENABLED == null) return false;
        try {
            return Boolean.TRUE.equals(IS_ENABLED.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Registers Vertex with the one MouseMixin already owned by AI Agent.
     * Reflection keeps the AI Agent dependency optional at compile time.
     */
    public static void registerPointerHandlers() {
        if (registered || !FabricLoader.getInstance().isModLoaded("minecraft_ai_agent")) return;
        try {
            Class<?> events = Class.forName(EVENTS_CLASS);
            Method addButton = events.getMethod("addMouseButtonListener", Consumer.class);
            Method addMove = events.getMethod("addCursorMoveListener", Consumer.class);
            Method addScroll = events.getMethod("addMouseScrollListener", Consumer.class);

            Consumer<double[]> buttonListener = event -> {
                if (event == null || event.length < 4) return;
                pointerX = event[0];
                pointerY = event[1];
                pointerKnown = true;
                if (!VertexPanelController.isActive()) return;
                VertexPanelController.handleMouseButton(
                        event[0], event[1], (int) event[2], (int) event[3]);
            };
            Consumer<double[]> moveListener = event -> {
                if (event == null || event.length < 2) return;
                pointerX = event[0];
                pointerY = event[1];
                pointerKnown = true;
                if (VertexPanelController.isActive()) {
                    VertexPanelController.handleMouseMove(pointerX, pointerY);
                }
            };
            Consumer<double[]> scrollListener = event -> {
                if (event == null || event.length < 4 || !VertexPanelController.isActive()) return;
                VertexPanelController.handleMouseScroll(event[0], event[1], event[3]);
            };

            addButton.invoke(null, buttonListener);
            addMove.invoke(null, moveListener);
            addScroll.invoke(null, scrollListener);
            registered = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Missing/older AI Agent means Vertex stays visible but deliberately cannot be clicked.
            registered = false;
        }
    }

    /** Pointer coordinates are sourced only from AI Agent's MouseMixin events. */
    public static Pointer getScaledPointer(MinecraftClient client) {
        if (!registered || !isEnabled() || !pointerKnown) return new Pointer(0.0, 0.0);
        return new Pointer(pointerX, pointerY);
    }

    public record Pointer(double x, double y) {
    }
}
