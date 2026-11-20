package com.seasonaldaycycle.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Makes Seamless Sleep's transition use quintic smootherstep easing.
 * The pseudo mixin is silently skipped when Seamless Sleep is not installed.
 *
 * @author Mortylovelly
 */
@Pseudo
@Mixin(targets = "net.aqualoco.sec.sleep.SleepAnimationState", remap = false)
public abstract class SeamlessSleepEasingMixin {
    /**
     * A C2-continuous ease-in/ease-out curve: both acceleration and deceleration
     * settle gently at the beginning and end of the sleep transition.
     *
     * @author Mortylovelly
     * @reason Reduce visible acceleration changes during the optional sleep animation.
     */
    @Overwrite(remap = false)
    public static double integralEase(double progress) {
        if (Double.isNaN(progress) || progress <= 0.0D) return 0.0D;
        if (progress >= 1.0D) return 1.0D;

        double x = progress;
        return x * x * x * (x * (x * 6.0D - 15.0D) + 10.0D);
    }
}
