package com.seasonaldaycycle.client;

import net.minecraft.client.MinecraftClient;

/**
 * Temporarily provides a visible pointer while a Vertex menu is open.
 * AI Agent is optional: its public cursor API is used when available;
 * otherwise Vertex unlocks and relocks vanilla Minecraft's mouse directly.
 */
public final class VertexCursorController {
    private static boolean unlockedByVertex;
    private static boolean aiCursorEnabledByVertex;

    private VertexCursorController() {
    }

    public static void acquire(MinecraftClient client) {
        if (client == null) return;

        if (AiAgentCursorCompat.canControl()) {
            if (!AiAgentCursorCompat.isEnabled()) {
                aiCursorEnabledByVertex = AiAgentCursorCompat.toggle(client);
            }
            return;
        }

        if (!unlockedByVertex) {
            client.mouse.unlockCursor();
            unlockedByVertex = true;
        }
    }

    public static void ensureCursorAvailable(MinecraftClient client) {
        if (client == null) return;

        if (AiAgentCursorCompat.canControl()) {
            if (!AiAgentCursorCompat.isEnabled()) {
                aiCursorEnabledByVertex =
                        AiAgentCursorCompat.toggle(client) || aiCursorEnabledByVertex;
            }
            return;
        }

        if (!unlockedByVertex) {
            client.mouse.unlockCursor();
            unlockedByVertex = true;
        }
    }

    /** Restores only the cursor state that Vertex itself changed. */
    public static void release(MinecraftClient client) {
        if (AiAgentCursorCompat.canControl()) {
            if (aiCursorEnabledByVertex && AiAgentCursorCompat.isEnabled()) {
                AiAgentCursorCompat.toggle(client);
            }
            aiCursorEnabledByVertex = false;
            return;
        }

        if (unlockedByVertex && client != null) {
            client.mouse.lockCursor();
        }
        unlockedByVertex = false;
    }

    public static Pointer getScaledPointer(MinecraftClient client) {
        if (client == null) return new Pointer(0.0, 0.0);
        AiAgentCursorCompat.Pointer pointer = AiAgentCursorCompat.getScaledPointer(client);
        return new Pointer(pointer.x(), pointer.y());
    }

    public static Pointer toScaledPointer(MinecraftClient client, double rawX, double rawY) {
        if (client == null) return new Pointer(0.0, 0.0);
        var window = client.getWindow();
        return new Pointer(
                rawX * window.getScaledWidth() / Math.max(1.0, window.getWidth()),
                rawY * window.getScaledHeight() / Math.max(1.0, window.getHeight())
        );
    }

    public record Pointer(double x, double y) {
    }
}
