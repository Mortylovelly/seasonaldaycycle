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

/** Routes UI input through Minecraft AI Agent's G-key free cursor. */
@Mixin(value = Mouse.class, priority = 1100)
public abstract class DayCycleMouseMixin {
    @Inject(method = "method_1601", at = @At("HEAD"), cancellable = true, remap = false)
    private void seasonaldaycycle$onMouseButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || !VertexPanelController.isActive()) return;
        if (AiAgentCursorCompat.isEnabled()) {
            AiAgentCursorCompat.Pointer pointer = AiAgentCursorCompat.getScaledPointer(client);
            VertexPanelController.handleMouseButton(pointer.x(), pointer.y(), button, action);
        }
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE) ci.cancel();
    }

    @Inject(method = "method_1600", at = @At("HEAD"), cancellable = true, remap = false)
    private void seasonaldaycycle$onCursorPos(long window, double cursorX, double cursorY, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || !VertexPanelController.isActive()) return;
        if (AiAgentCursorCompat.isEnabled()) {
            double x = cursorX * client.getWindow().getScaledWidth() / Math.max(1.0, client.getWindow().getWidth());
            double y = cursorY * client.getWindow().getScaledHeight() / Math.max(1.0, client.getWindow().getHeight());
            VertexPanelController.handleMouseMove(x, y);
        }
        ci.cancel();
    }

    @Inject(method = "method_1598", at = @At("HEAD"), cancellable = true, remap = false)
    private void seasonaldaycycle$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || !VertexPanelController.isActive()) return;
        if (AiAgentCursorCompat.isEnabled()) {
            AiAgentCursorCompat.Pointer pointer = AiAgentCursorCompat.getScaledPointer(client);
            VertexPanelController.handleMouseScroll(pointer.x(), pointer.y(), vertical);
        }
        ci.cancel();
    }
}
