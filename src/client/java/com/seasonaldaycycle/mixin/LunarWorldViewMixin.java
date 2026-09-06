package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.client.SkyAngleInterpolationState;
import net.minecraft.world.LunarWorldView;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LunarWorldView.class)
public abstract class LunarWorldViewMixin {
    @Inject(method = "getSkyAngle", at = @At("RETURN"), cancellable = true)
    private void seasonaldaycycle$interpolateSkyAngle(float tickDelta, CallbackInfoReturnable<Float> cir) {
        LunarWorldView lunarWorld = (LunarWorldView) (Object) this;

        if (!(lunarWorld instanceof World world) || !world.isClient()) {
            return;
        }

        long actualTime = lunarWorld.getLunarTime();
        SkyAngleInterpolationState.State state = SkyAngleInterpolationState.get(lunarWorld);

        if (!state.initialized) {
            state.initialized = true;
            state.previousTime = actualTime;
            state.currentTime = actualTime;
            return;
        }

        long rawDifference = actualTime - state.currentTime;
        long difference = SkyAngleInterpolationState.normalizeDifference(rawDifference);

        if (SkyAngleInterpolationState.isExternalChange(difference)) {
            state.previousTime = actualTime;
            state.currentTime = actualTime;
            return;
        }

        if (difference != 0L) {
            state.previousTime = state.currentTime;
            state.currentTime = actualTime;
        }

        DimensionType dimension = world.getDimension();
        float previousAngle = dimension.getSkyAngle(state.previousTime);
        float currentAngle = dimension.getSkyAngle(state.currentTime);

        float angleDifference = currentAngle - previousAngle;
        if (angleDifference > 0.5F) {
            angleDifference -= 1.0F;
        } else if (angleDifference < -0.5F) {
            angleDifference += 1.0F;
        }

        float smoothAngle = previousAngle + angleDifference * tickDelta;
        smoothAngle -= (float) Math.floor(smoothAngle);

        cir.setReturnValue(smoothAngle);
    }
}
