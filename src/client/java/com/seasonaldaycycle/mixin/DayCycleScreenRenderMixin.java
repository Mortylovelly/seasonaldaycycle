package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.client.DayCycleScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DayCycleScreen.class)
public abstract class DayCycleScreenRenderMixin {
    @Shadow private int miniX;
    @Shadow private int miniY;

    @Inject(method = "renderMinimized", at = @At("HEAD"), cancellable = true)
    private void seasonaldaycycle$renderMinimized(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        int clockX = miniX + 13;
        int clockY = miniY + 13;

        context.getMatrices().push();
        context.getMatrices().translate(clockX + 8, clockY + 8, 0.0f);
        context.drawItem(new ItemStack(Items.CLOCK), -8, -8);
        context.getMatrices().pop();

        ci.cancel();
    }
}
