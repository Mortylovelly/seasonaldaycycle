package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.client.SkyAngleInterpolationState;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LunarWorldView;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LunarWorldView.class)
public interface LunarWorldViewMixin {
    /**
     * Smooths the visual sky position when the server changes the world time
     * by more than one vanilla tick per server tick.
     *
     * @reason The mod's accelerated day/night cycle advances the logical time
     * in whole ticks, so vanilla interpolation is no longer sufficient.
     */
    @Overwrite
    default float getSkyAngle(float tickDelta) {
        LunarWorldView lunarWorld = (LunarWorldView) this;

        if (!(lunarWorld instanceof World world) || !world.isClient()) {
            long time = lunarWorld.getLunarTime();
            DimensionType dimension = lunarWorld.getDimension();
            float current = dimension.getSkyAngle(time);
            float next = dimension.getSkyAngle(time + 1L);
            return MathHelper.lerp(tickDelta, current, next);
        }

        long actualTime = lunarWorld.getLunarTime();
        SkyAngleInterpolationState.State state = SkyAngleInterpolationState.get(lunarWorld);

        if (!state.initialized) {
            state.initialized = true;
            state.previousTime = actualTime;
            state.currentTime = actualTime;
        } else {
            long rawDifference = actualTime - state.currentTime;
            long difference = SkyAngleInterpolationState.normalizeDifference(rawDifference);

            if (SkyAngleInterpolationState.isExternalChange(difference)) {
                state.previousTime = actualTime;
                state.currentTime = actualTime;
            } else if (difference != 0L) {
                state.previousTime = state.currentTime;
                state.currentTime = actualTime;
            }
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
        return smoothAngle - (float) Math.floor(smoothAngle);
    }
}
