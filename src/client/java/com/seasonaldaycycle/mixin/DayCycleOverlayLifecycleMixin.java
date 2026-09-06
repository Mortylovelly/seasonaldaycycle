package com.seasonaldaycycle.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class DayCycleOverlayLifecycleMixin {
    @Inject(method = "setOverlay", at = @At("TAIL"))
    private void seasonaldaycycle$restoreCursorOnOverlayClose(net.minecraft.client.gui.screen.Overlay overlay, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (overlay == null && client.currentScreen == null && client.world != null) {
            client.mouse.lockCursor();
        }
    }
}
