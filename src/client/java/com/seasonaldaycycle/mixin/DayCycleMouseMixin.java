package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.client.AiAgentCursorCompat;
import com.seasonaldaycycle.client.VertexPanelController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes pointer input to Vertex using the same AI Agent free cursor and GLFW
 * coordinate conversion as Minecraft AI Agent 0.3.19.
 *
 * This mixin never changes cursor mode. AiFreeCursor is the only cursor owner.
 */
@Mixin(value = Mouse.class, priority = 1100)
public abstract class DayCycleMouseMixin {
    @Inject(method = "method_1601", at = @At("HEAD"), cancellable = true, remap = false)
    private void seasonaldaycycle$onMouseButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null
                || !VertexPanelController.isActive()
                || !AiAgentCursorCompat.shouldHandleInput(client)) {
            return;
        }

        AiAgentCursorCompat.Pointer pointer = AiAgentCursorCompat.getScaledPointer(client);
        VertexPanelController.handleMouseButton(pointer.x(), pointer.y(), button, action);

        // AI Agent owns the pointer while G mode is active: never click through into the world.
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE) {
            ci.cancel();
        }
    }

    @Inject(method = "method_1600", at = @At("HEAD"), cancellable = true, remap = false)
    private void seasonaldaycycle$onCursorPos(long window, double cursorX, double cursorY, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null
                || !VertexPanelController.isActive()
                || !AiAgentCursorCompat.shouldHandleInput(client)) {
            return;
        }

        AiAgentCursorCompat.Pointer pointer =
                AiAgentCursorCompat.toScaledPointer(client, cursorX, cursorY);
        VertexPanelController.handleMouseMove(pointer.x(), pointer.y());
        ci.cancel();
    }

    @Inject(method = "method_1598", at = @At("HEAD"), cancellable = true, remap = false)
    private void seasonaldaycycle$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null
                || !VertexPanelController.isActive()
                || !AiAgentCursorCompat.shouldHandleInput(client)) {
            return;
        }

        AiAgentCursorCompat.Pointer pointer = AiAgentCursorCompat.getScaledPointer(client);
        VertexPanelController.handleMouseScroll(pointer.x(), pointer.y(), vertical);
        ci.cancel();
    }
}
