package com.seasonaldaycycle.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Optional bridge to Minecraft AI Agent 0.3.20+'s shared mouse event bus.
 *
 * Vertex does not install its own Mouse mixin and never changes cursor mode.
 * AI Agent owns the only Mouse mixin, the G toggle, and scaled pointer coordinates.
 */
public final class AiAgentCursorCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vertex/Cursor");
    private static final String MOD_ID = "minecraft_ai_agent";
    private static final String EVENTS_CLASS = "com.mortylovelly.minecraftai.client.AiCursorEvents";
    private static final String CURSOR_CLASS = "com.mortylovelly.minecraftai.client.AiFreeCursor";

    private static volatile Method isEnabledMethod;
    private static volatile boolean registrationAttempted;
    private static volatile boolean registered;
    private static volatile boolean pointerKnown;
    private static volatile double pointerX;
    private static volatile double pointerY;
    private static volatile boolean statusMessagePending;
    private static volatile String statusMessage;
    private static volatile boolean firstPanelClickReported;

    private AiAgentCursorCompat() {
    }

    public static boolean isAvailable() {
        return isEnabledMethod != null && registered;
    }

    public static boolean isEnabled() {
        Method method = isEnabledMethod;
        if (method == null) return false;
        try {
            return Boolean.TRUE.equals(method.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Called from the first client tick, after all mod client initializers have run.
     * This avoids relying on the relative initialization order of Vertex and AI Agent.
     */
    public static void tick(MinecraftClient client) {
        registerPointerHandlers();

        if (statusMessagePending && client.player != null) {
            client.player.sendMessage(Text.literal(statusMessage), true);
            statusMessagePending = false;
        }
    }

    /**
     * Registers Vertex with the one MouseMixin already owned by AI Agent.
     * Reflection keeps the AI Agent dependency optional at compile time.
     */
    public static synchronized void registerPointerHandlers() {
        if (registered || registrationAttempted) return;
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            registrationAttempted = true;
            queueStatus("[Vertex] Minecraft AI Agent не найден. Меню будет видно, но клики работать не будут.");
            LOGGER.warn("Minecraft AI Agent is not installed; Vertex mouse input is unavailable.");
            return;
        }

        String agentVersion = getAgentVersion();
        try {
            // Resolve the API only after Fabric has completed all mod initialization.
            Class<?> cursorClass = Class.forName(CURSOR_CLASS);
            Method enabledMethod = cursorClass.getMethod("isEnabled");
            Class<?> eventsClass = Class.forName(EVENTS_CLASS);
            Method addButton = eventsClass.getMethod("addMouseButtonListener", Consumer.class);
            Method addMove = eventsClass.getMethod("addCursorMoveListener", Consumer.class);
            Method addScroll = eventsClass.getMethod("addMouseScrollListener", Consumer.class);

            Consumer<double[]> buttonListener = event -> {
                if (event == null || event.length < 4) return;
                pointerX = event[0];
                pointerY = event[1];
                pointerKnown = true;

                if ((int) event[2] != GLFW.GLFW_MOUSE_BUTTON_LEFT
                        || (int) event[3] != GLFW.GLFW_PRESS
                        || !VertexPanelController.isActive()) {
                    if (VertexPanelController.isActive()
                            && (int) event[3] == GLFW.GLFW_RELEASE) {
                        VertexPanelController.handleMouseButton(
                                event[0], event[1], (int) event[2], (int) event[3]);
                    }
                    return;
                }

                boolean handled = VertexPanelController.handleMouseButton(
                        event[0], event[1], (int) event[2], (int) event[3]);

                if (!firstPanelClickReported) {
                    firstPanelClickReported = true;
                    String result = handled
                            ? "Первый клик получен и передан меню."
                            : "Клик получен, но координаты оказались вне области меню.";
                    LOGGER.info("AI Agent mouse input reached Vertex: x={}, y={}, handled={}",
                            event[0], event[1], handled);
                    queueStatus("[Vertex] " + result);
                }
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

            isEnabledMethod = enabledMethod;
            registered = true;
            registrationAttempted = true;
            queueStatus("[Vertex] Подключён курсор Minecraft AI Agent " + agentVersion + ". Нажми G и проверь клик.");
            LOGGER.info("Connected to Minecraft AI Agent {} cursor event API.", agentVersion);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            registrationAttempted = true;
            String message = "[Vertex] Не найден API мыши AI Agent " + agentVersion
                    + ". Нужен Minecraft AI Agent 0.3.20 или новее.";
            queueStatus(message);
            LOGGER.error("Could not connect to Minecraft AI Agent {} cursor API. Install AI Agent 0.3.20 or newer.",
                    agentVersion, exception);
        }
    }

    private static String getAgentVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown version");
    }

    private static void queueStatus(String message) {
        statusMessage = message;
        statusMessagePending = true;
    }

    /** Pointer coordinates are sourced only from AI Agent's MouseMixin events. */
    public static Pointer getScaledPointer(MinecraftClient client) {
        if (!registered || !isEnabled() || !pointerKnown) return new Pointer(0.0, 0.0);
        return new Pointer(pointerX, pointerY);
    }

    public record Pointer(double x, double y) {
    }
}
