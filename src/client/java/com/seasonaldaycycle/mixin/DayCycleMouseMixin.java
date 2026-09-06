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
    private static final double MINI_FORWARD_OFFSET_X = 21.0;
    private static final double MINI_FORWARD_OFFSET_Y = 21.0;
    private boolean seasonaldaycycle$miniInteraction;

    @Inject(method = "method_1601", at = @At("HEAD"), cancellable = true, remap = false)
    private void seasonaldaycycle$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Never intercept vanilla GUI clicks. The Minecraft pause/menu screens
        // must keep their normal mouse handling even while our HUD cursor mode
        // is enabled in the world.
        if (client.currentScreen != null) {
            seasonaldaycycle$miniInteraction = false;
            return;
        }

        DayCycleScreen overlay = DayCycleScreen.getActive();
        if (overlay == null) {
            seasonaldaycycle$miniInteraction = false;
            return;
        }

        double scale = client.getWindow().getScaleFactor();
        double mouseX = client.mouse.getX() / scale;
        double mouseY = client.mouse.getY() / scale;

        if (SeasonalDayCycleClient.isCursorMode()) {
            boolean handled;

            if (seasonaldaycycle$miniInteraction) {
                handled = overlay.handleMouseButton(
                        mouseX - MINI_FORWARD_OFFSET_X,
                        mouseY - MINI_FORWARD_OFFSET_Y,
                        button,
                        action
                );
            } else {
                handled = overlay.handleMouseButton(mouseX, mouseY, button, action);

                if (!handled && action == 1 && button == 0) {
                    handled = overlay.handleMouseButton(
                            mouseX - MINI_FORWARD_OFFSET_X,
                            mouseY - MINI_FORWARD_OFFSET_Y,
                            button,
                            action
                    );
                    if (handled) {
                        seasonaldaycycle$miniInteraction = true;
                    }
                }
            }

            if (action == 0 && seasonaldaycycle$miniInteraction) {
                seasonaldaycycle$miniInteraction = false;
            }

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
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.currentScreen != null) {
            seasonaldaycycle$miniInteraction = false;
            return;
        }

        DayCycleScreen overlay = DayCycleScreen.getActive();
        if (overlay == null) {
            seasonaldaycycle$miniInteraction = false;
            return;
        }

        double scale = client.getWindow().getScaleFactor();
        double scaledX = x / scale;
        double scaledY = y / scale;

        if (SeasonalDayCycleClient.isCursorMode()) {
            if (seasonaldaycycle$miniInteraction) {
                overlay.handleMouseMove(
                        scaledX - MINI_FORWARD_OFFSET_X,
                        scaledY - MINI_FORWARD_OFFSET_Y
                );
            } else {
                overlay.handleMouseMove(scaledX, scaledY);
            }
            client.mouse.unlockCursor();
            return;
        }

        if (overlay.handleMouseMove(scaledX, scaledY)) {
            ci.cancel();
        }
    }

    @Inject(method = "method_1598", at = @At("HEAD"), cancellable = true, remap = false)
    private void seasonaldaycycle$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.currentScreen != null) {
            return;
        }

        DayCycleScreen overlay = DayCycleScreen.getActive();
        if (overlay == null) {
            return;
        }

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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null) {
            return;
        }

        if (DayCycleScreen.isActive() && SeasonalDayCycleClient.isCursorMode()) {
            ci.cancel();
        }
    }
}
