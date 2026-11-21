package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.client.SkyAngleInterpolationState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirrors the server's continuous sleep curve on the client. */
@Pseudo
@Mixin(targets = "net.aqualoco.sec.sleep.ClientSleepAnimationState", remap = false)
public abstract class SeamlessSleepClientReleaseMixin {
    @Unique
    private static final int VERTEX_REST_TICKS = 40;

    @Shadow(remap = false)
    private boolean active;

    @Shadow(remap = false)
    private long startTimeOfDay;

    @Shadow(remap = false)
    private long endTimeOfDay;

    @Shadow(remap = false)
    private int durationTicks;

    @Shadow(remap = false)
    private long startMillis;

    @Shadow(remap = false)
    private boolean replayCompatMode;

    @Shadow(remap = false)
    public abstract void reset();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void vertex$driveContinuousSleepCurve(
            ClientWorld world, float partialTick, CallbackInfo ci) {
        if (!this.active || this.replayCompatMode) return;
        if (world == null || world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;

        int totalTicks = Math.max(1, this.durationTicks);
        int skipTicks = Math.max(1, totalTicks - VERTEX_REST_TICKS);
        long skipMillis = skipTicks * 50L;
        long totalMillis = totalTicks * 50L;
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - this.startMillis);

        double normalSpeed = SkyAngleInterpolationState.getConfiguredSpeed(world, this.endTimeOfDay);
        long skippedTime = Math.max(1L, this.endTimeOfDay - this.startTimeOfDay);
        double endSlope = Math.max(0.0D, Math.min(2.0D,
                normalSpeed * skipTicks / skippedTime));

        if (elapsedMillis < skipMillis) {
            double progress = elapsedMillis / (double) skipMillis;
            double eased = smootherStepWithEndVelocity(progress, endSlope);
            long interpolated = this.startTimeOfDay
                    + Math.round((this.endTimeOfDay - this.startTimeOfDay) * eased);
            world.setTimeOfDay(interpolated);
        } else {
            double restElapsedTicks = Math.min(
                    VERTEX_REST_TICKS,
                    Math.max(0.0D, (elapsedMillis - skipMillis) / 50.0D));
            long normalAdvance = Math.max(0L, (long) Math.floor(restElapsedTicks * normalSpeed));
            world.setTimeOfDay(this.endTimeOfDay + normalAdvance);
        }

        if (elapsedMillis >= totalMillis) {
            long finalAdvance = Math.max(0L, (long) Math.floor(VERTEX_REST_TICKS * normalSpeed));
            long finalTime = this.endTimeOfDay + finalAdvance;
            world.setTimeOfDay(finalTime);
            SkyAngleInterpolationState.finishSleepTransition(world, finalTime, world.getTime());
            this.reset();
        }

        // The state has already written the synchronized server-equivalent time.
        ci.cancel();
    }

    @Unique
    private static double smootherStepWithEndVelocity(double value, double endVelocity) {
        if (Double.isNaN(value) || value <= 0.0D) return 0.0D;
        if (value >= 1.0D) return 1.0D;

        double x = value;
        double m = Math.max(0.0D, Math.min(2.0D, endVelocity));
        double x2 = x * x;
        double x3 = x2 * x;
        double x4 = x3 * x;
        double x5 = x4 * x;
        return x3 * (10.0D - 4.0D * m)
                + x4 * (7.0D * m - 15.0D)
                + x5 * (6.0D - 3.0D * m);
    }
}
