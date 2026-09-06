package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.client.DayCycleScreen;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class DayCycleMouseMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void seasonaldaycycle$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        DayCycleScreen overlay = DayCycleScreen.getActive();
        if (overlay == null) return;

        if (overlay.handleMouseButton(client.mouse.getX(), client.mouse.getY(), button, action)) {
            ci.cancel();
        }
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void seasonaldaycycle$onCursorPos(long window, double x, double y, CallbackInfo ci) {
        DayCycleScreen overlay = DayCycleScreen.getActive();
        if (overlay == null) return;

        if (overlay.handleMouseMove(x, y)) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void seasonaldaycycle$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        DayCycleScreen overlay = DayCycleScreen.getActive();
        if (overlay == null) return;

        if (overlay.handleMouseScroll(client.mouse.getX(), client.mouse.getY(), vertical)) {
            ci.cancel();
        }
    }
}
