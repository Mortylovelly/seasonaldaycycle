package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.client.DayCycleScreen;
import com.seasonaldaycycle.client.SeasonalDayCycleClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class DayCycleMouseMixin {
    @Inject(method = "method_1601", at = @At("HEAD"), cancellable = true, remap = false)
    private void seasonaldaycycle$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        DayCycleScreen overlay = DayCycleScreen.getActive();
        if (overlay == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        double scale = client.getWindow().getScaleFactor();
        double mouseX = client.mouse.getX() / scale;
        double mouseY = client.mouse.getY() / scale;

        if (SeasonalDayCycleClient.isCursorMode()) {
            overlay.handleMouseButton(mouseX, mouseY, button, action);
            client.mouse.unlockCursor();
            ci.cancel();
            return;
        }

        if (overlay.handleMouseButton(mouseX, mouseY, button, action)) {
            ci.cancel();
        }
    }

    @Inject(method = "method_1600", at = @At("HEAD"), cancellable = true, remap = false)
    private void seasonaldaycycle$onCursorPos(long window, double x, double y, CallbackInfo ci) {
        DayCycleScreen overlay = DayCycleScreen.getActive();
        if (overlay == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        double scale = client.getWindow().getScaleFactor();
        double scaledX = x / scale;
        double scaledY = y / scale;

        if (SeasonalDayCycleClient.isCursorMode()) {
            overlay.handleMouseMove(scaledX, scaledY);
            // Do not cancel: vanilla must update Mouse.x/Mouse.y so clicks use
            // the current cursor position. Camera rotation is blocked separately.
            return;
        }

        if (overlay.handleMouseMove(scaledX, scaledY)) {
            ci.cancel();
        }
    }

    @Inject(method = "method_1598", at = @At("HEAD"), cancellable = true, remap = false)
    private void seasonaldaycycle$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        DayCycleScreen overlay = DayCycleScreen.getActive();
        if (overlay == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        double scale = client.getWindow().getScaleFactor();
        double mouseX = client.mouse.getX() / scale;
        double mouseY = client.mouse.getY() / scale;

        if (SeasonalDayCycleClient.isCursorMode()) {
            overlay.handleMouseScroll(mouseX, mouseY, vertical);
            client.mouse.unlockCursor();
            ci.cancel();
            return;
        }

        if (overlay.handleMouseScroll(mouseX, mouseY, vertical)) {
            ci.cancel();
        }
    }

    @Inject(method = "method_1606", at = @At("HEAD"), cancellable = true, remap = false)
    private void seasonaldaycycle$blockCameraRotation(double timeDelta, CallbackInfo ci) {
        if (DayCycleScreen.isActive() && SeasonalDayCycleClient.isCursorMode()) {
            ci.cancel();
        }
    }
}
