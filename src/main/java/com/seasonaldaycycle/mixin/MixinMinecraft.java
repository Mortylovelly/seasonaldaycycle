package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.ClientTimeInterpolator;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    // Вызывается каждый кадр — здесь делаем плавную интерполяцию
    @Inject(method = "runTick", at = @At("HEAD"))
    private void sdc$onRenderTick(boolean renderLevel, CallbackInfo ci) {
        if (renderLevel) {
            Minecraft mc = (Minecraft)(Object)this;
            ClientTimeInterpolator.onRenderTick(mc);
        }
    }
}
