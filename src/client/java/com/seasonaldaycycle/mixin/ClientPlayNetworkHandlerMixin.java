package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.client.SkyAngleInterpolationState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "setTimeOfDay", at = @At("TAIL"))
    private void seasonaldaycycle$captureWorldTime(long timeOfDay, CallbackInfo ci) {
        ClientWorld world = (ClientWorld) (Object) this;
        SkyAngleInterpolationState.acceptServerTime(
                world,
                timeOfDay,
                world.getTime()
        );
    }
}
