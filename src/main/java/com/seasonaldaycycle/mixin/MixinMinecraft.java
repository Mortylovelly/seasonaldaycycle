package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.ClientTimeInterpolator;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "m_91383_", at = @At("HEAD"), remap = false)
    private void sdc$onRenderTick(boolean renderLevel, CallbackInfo ci) {
        if (renderLevel) {
            ClientTimeInterpolator.onRenderTick((Minecraft)(Object)this);
        }
    }
}
