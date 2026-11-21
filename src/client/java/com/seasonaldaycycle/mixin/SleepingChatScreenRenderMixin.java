package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.client.SleepClockHudOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SleepingChatScreen.class)
public abstract class SleepingChatScreenRenderMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void seasonaldaycycle$renderSleepClock(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        ButtonWidget stopButton =
                ((SleepingChatScreenAccessor) (Object) this).seasonaldaycycle$getStopSleepingButton();
        int buttonTop = stopButton == null ? height - 40 : stopButton.getY();

        SleepClockHudOverlay.render(context, width, height, delta, buttonTop);
    }
}
