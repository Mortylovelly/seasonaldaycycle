package com.seasonaldaycycle.mixin;

import net.minecraft.world.LunarWorldView;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.WeakHashMap;

@Mixin(LunarWorldView.class)
public interface LunarWorldViewMixin {
    long DAY_LENGTH = 24000L;
    long EXTERNAL_CHANGE_THRESHOLD = 200L;

    Map<LunarWorldView, State> SEASONAL_DAY_CYCLE$STATES = new WeakHashMap<>();

    @Inject(method = "getSkyAngle", at = @At("RETURN"), cancellable = true)
    default void seasonaldaycycle$interpolateSkyAngle(float tickDelta, CallbackInfoReturnable<Float> cir) {
        Object self = this;

        if (!(self instanceof World world) || !world.isClient()) {
            return;
        }

        LunarWorldView lunarWorld = (LunarWorldView) self;
        long actualTime = lunarWorld.getLunarTime();

        State state = SEASONAL_DAY_CYCLE$STATES.computeIfAbsent(lunarWorld, ignored -> new State());

        if (!state.initialized) {
            state.initialized = true;
            state.previousTime = actualTime;
            state.currentTime = actualTime;
            return;
        }

        long rawDifference = actualTime - state.currentTime;
        long difference = normalizeDifference(rawDifference);

        if (Math.abs(difference) > EXTERNAL_CHANGE_THRESHOLD) {
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
        smoothAngle = smoothAngle - (float) Math.floor(smoothAngle);

        cir.setReturnValue(smoothAngle);
    }

    private static long normalizeDifference(long difference) {
        if (difference > DAY_LENGTH / 2L) {
            return difference - DAY_LENGTH;
        }
        if (difference < -DAY_LENGTH / 2L) {
            return difference + DAY_LENGTH;
        }
        return difference;
    }

    final class State {
        private boolean initialized;
        private long previousTime;
        private long currentTime;
    }
}
