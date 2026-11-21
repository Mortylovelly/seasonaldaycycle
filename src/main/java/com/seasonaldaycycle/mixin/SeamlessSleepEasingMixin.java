package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.DayCycleHandler;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Controls Seamless Sleep's server clock with a continuous curve whose final
 * velocity equals Vertex's configured normal day-cycle speed.
 */
@Pseudo
@Mixin(targets = "net.aqualoco.sec.sleep.SleepAnimationState", remap = false)
public abstract class SeamlessSleepEasingMixin {
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

    @Unique
    private int vertex$skipDurationTicks;

    @Inject(method = "start", at = @At("TAIL"), remap = false)
    private void vertex$appendNaturalRest(long currentTime, long targetTime, CallbackInfo ci) {
        this.vertex$skipDurationTicks = 0;
        if (!this.active) return;

        // Preserve Seamless Sleep's configured transition duration. The extra
        // 2 seconds are a quiet resting period after normal speed is reached.
        this.vertex$skipDurationTicks = Math.max(1, this.durationTicks);
        this.durationTicks = this.vertex$skipDurationTicks + VERTEX_REST_TICKS;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void vertex$driveContinuousSleepCurve(ServerWorld world, CallbackInfo ci) {
        if (!this.active) return;

        int skipTicks = Math.max(1, this.vertex$skipDurationTicks);
        int totalTicks = skipTicks + VERTEX_REST_TICKS;
        long skipMillis = skipTicks * 50L;
        long totalMillis = totalTicks * 50L;
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - this.startMillis);

        double normalSpeed = DayCycleHandler.getConfiguredSpeed(world, this.endTimeOfDay);
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
            // The transition has already reached sunrise at normal velocity.
            // Keep that exact velocity during the short final lying-down pause.
            double restElapsedTicks = Math.min(
                    VERTEX_REST_TICKS,
                    Math.max(0.0D, (elapsedMillis - skipMillis) / 50.0D));
            long normalAdvance = Math.max(0L, (long) Math.floor(restElapsedTicks * normalSpeed));
            world.setTimeOfDay(this.endTimeOfDay + normalAdvance);
        }

        if (elapsedMillis >= totalMillis) {
            long finalAdvance = Math.max(0L, (long) Math.floor(VERTEX_REST_TICKS * normalSpeed));
            world.setTimeOfDay(this.endTimeOfDay + finalAdvance);
            this.active = false;
        }

        // Seamless Sleep's own ServerWorld mixin still owns the wake-up event.
        // It will wake players only after this extended active period finishes.
        ci.cancel();
    }

    /**
     * @author Mortylovelly
     * @reason Keep the standard smooth ease-in curve for other Seamless Sleep callers.
     */
    @Overwrite(remap = false)
    public static double integralEase(double progress) {
        return smootherStep(progress);
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

    @Unique
    private static double smootherStep(double value) {
        if (Double.isNaN(value) || value <= 0.0D) return 0.0D;
        if (value >= 1.0D) return 1.0D;
        double x = value;
        return x * x * x * (x * (x * 6.0D - 15.0D) + 10.0D);
    }
}
