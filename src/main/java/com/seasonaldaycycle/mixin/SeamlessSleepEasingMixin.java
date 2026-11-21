package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.DayCycleHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Seamless Sleep's night-skip easing separate from Vertex's gradual
 * restoration of the configured day-cycle speed before players wake up.
 */
@Pseudo
@Mixin(targets = "net.aqualoco.sec.sleep.SleepAnimationState", remap = false)
public abstract class SeamlessSleepEasingMixin {
    @Unique
    private static final int VERTEX_WAKE_SETTLE_TICKS = 40;

    @Unique
    private static final int VERTEX_SPEED_RAMP_TICKS = 28;

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
    private void vertex$appendWakeSettleTime(long currentTime, long targetTime, CallbackInfo ci) {
        this.vertex$skipDurationTicks = 0;
        if (!this.active) return;

        // Leave the configured Seamless Sleep duration untouched and append
        // a separate 2-second phase for Vertex to restore normal world speed.
        this.vertex$skipDurationTicks = Math.max(1, this.durationTicks);
        this.durationTicks = this.vertex$skipDurationTicks + VERTEX_WAKE_SETTLE_TICKS;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void vertex$driveSleepClock(ServerWorld world, CallbackInfo ci) {
        if (!this.active) return;
        if (world == null || world.getRegistryKey() != World.OVERWORLD) return;
        if (!world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) return;

        int skipTicks = Math.max(1, this.vertex$skipDurationTicks);
        int totalTicks = skipTicks + VERTEX_WAKE_SETTLE_TICKS;
        long skipMillis = skipTicks * 50L;
        long totalMillis = totalTicks * 50L;
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - this.startMillis);

        if (elapsedMillis < skipMillis) {
            // Keep Seamless Sleep's original full night-skip phase smooth.
            double progress = elapsedMillis / (double) skipMillis;
            double eased = smootherStep(progress);
            long delta = this.endTimeOfDay - this.startTimeOfDay;
            long interpolated = this.startTimeOfDay + Math.round(delta * eased);
            world.setTimeOfDay(interpolated);
        } else {
            // The last 40 ticks are a distinct phase: first restore the configured
            // day speed smoothly, then hold that exact speed before waking.
            double releaseElapsedTicks = Math.min(
                    VERTEX_WAKE_SETTLE_TICKS,
                    Math.max(0.0D, (elapsedMillis - skipMillis) / 50.0D));
            double initialSpeed = DayCycleHandler.getConfiguredSpeed(world, this.startTimeOfDay);
            double targetSpeed = DayCycleHandler.getConfiguredSpeed(world, this.endTimeOfDay);
            double startSpeed = getReleaseStartSpeed(initialSpeed, targetSpeed);
            double advance = integrateReleaseSpeed(releaseElapsedTicks, startSpeed, targetSpeed);
            world.setTimeOfDay(this.endTimeOfDay + (long) Math.floor(advance));
        }

        if (elapsedMillis >= totalMillis) {
            double initialSpeed = DayCycleHandler.getConfiguredSpeed(world, this.startTimeOfDay);
            double targetSpeed = DayCycleHandler.getConfiguredSpeed(world, this.endTimeOfDay);
            double startSpeed = getReleaseStartSpeed(initialSpeed, targetSpeed);
            double finalAdvance = integrateReleaseSpeed(
                    VERTEX_WAKE_SETTLE_TICKS, startSpeed, targetSpeed);
            world.setTimeOfDay(this.endTimeOfDay + (long) Math.floor(finalAdvance));
            this.active = false;
        }

        // Seamless Sleep's ServerWorld mixin still performs the normal wake-up
        // and weather-reset path when this state becomes inactive.
        ci.cancel();
    }

    /**
     * @author Mortylovelly
     * @reason Use a smooth quintic curve for the night-skip portion.
     */
    @Overwrite(remap = false)
    public static double integralEase(double progress) {
        return smootherStep(progress);
    }

    @Unique
    private static double getReleaseStartSpeed(double initialSpeed, double targetSpeed) {
        if (!Double.isFinite(initialSpeed) || initialSpeed <= 0.0D) initialSpeed = targetSpeed;
        if (!Double.isFinite(targetSpeed) || targetSpeed <= 0.0D) targetSpeed = initialSpeed;
        if (!Double.isFinite(initialSpeed) || initialSpeed <= 0.0D) return 1.0E-9D;
        if (!Double.isFinite(targetSpeed) || targetSpeed <= 0.0D) return 1.0E-9D;
        return Math.max(1.0E-9D, Math.min(initialSpeed, targetSpeed) * 0.01D);
    }

    /**
     * Integrates a smootherstep velocity ramp for 28 ticks, followed by
     * 12 ticks at the exact normal speed. This gives a continuous clock position
     * and reaches normal speed with zero acceleration at the end of the ramp.
     */
    @Unique
    private static double integrateReleaseSpeed(double elapsedTicks, double startSpeed, double targetSpeed) {
        double elapsed = Math.max(0.0D, Math.min(VERTEX_WAKE_SETTLE_TICKS, elapsedTicks));
        double rampTicks = VERTEX_SPEED_RAMP_TICKS;
        double rampElapsed = Math.min(elapsed, rampTicks);
        double x = rampElapsed / rampTicks;

        // Integral of smootherstep(x) = 2.5*x^4 - 3*x^5 + x^6.
        double x2 = x * x;
        double x4 = x2 * x2;
        double easeIntegral = x4 * (2.5D + x * (-3.0D + x));
        double advance = startSpeed * rampElapsed
                + (targetSpeed - startSpeed) * rampTicks * easeIntegral;

        if (elapsed > rampTicks) {
            advance += targetSpeed * (elapsed - rampTicks);
        }
        return Math.max(0.0D, advance);
    }

    @Unique
    private static double smootherStep(double value) {
        if (Double.isNaN(value) || value <= 0.0D) return 0.0D;
        if (value >= 1.0D) return 1.0D;
        double x = value;
        return x * x * x * (x * (x * 6.0D - 15.0D) + 10.0D);
    }
}
