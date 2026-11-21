package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.client.SkyAngleInterpolationState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirrors the server's two-phase sleep clock exactly on the client. */
@Pseudo
@Mixin(targets = "net.aqualoco.sec.sleep.ClientSleepAnimationState", remap = false)
public abstract class SeamlessSleepClientReleaseMixin {
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

    @Shadow(remap = false)
    private boolean replayCompatMode;

    @Shadow(remap = false)
    public abstract void reset();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void vertex$mirrorSleepClock(
            ClientWorld world, float partialTick, CallbackInfo ci) {
        if (!this.active || this.replayCompatMode) return;
        if (world == null || world.getRegistryKey() != World.OVERWORLD) return;
        if (!world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) return;

        int totalTicks = Math.max(1, this.durationTicks);
        int skipTicks = Math.max(1, totalTicks - VERTEX_WAKE_SETTLE_TICKS);
        long skipMillis = skipTicks * 50L;
        long totalMillis = totalTicks * 50L;
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - this.startMillis);

        if (elapsedMillis < skipMillis) {
            // Client and server use the same quintic curve for the night skip.
            double progress = elapsedMillis / (double) skipMillis;
            double eased = smootherStep(progress);
            long delta = this.endTimeOfDay - this.startTimeOfDay;
            long interpolated = this.startTimeOfDay + Math.round(delta * eased);
            world.setTimeOfDay(interpolated);
        } else {
            // Mirror Vertex's gradual return to the normal day speed instead of
            // allowing vanilla client time or delayed server packets to jump.
            double releaseElapsedTicks = Math.min(
                    VERTEX_WAKE_SETTLE_TICKS,
                    Math.max(0.0D, (elapsedMillis - skipMillis) / 50.0D));
            double initialSpeed = SkyAngleInterpolationState.getConfiguredSpeed(
                    world, this.startTimeOfDay);
            double targetSpeed = SkyAngleInterpolationState.getConfiguredSpeed(
                    world, this.endTimeOfDay);
            double startSpeed = getReleaseStartSpeed(initialSpeed, targetSpeed);
            double advance = integrateReleaseSpeed(releaseElapsedTicks, startSpeed, targetSpeed);
            world.setTimeOfDay(this.endTimeOfDay + (long) Math.floor(advance));
        }

        if (elapsedMillis >= totalMillis) {
            double initialSpeed = SkyAngleInterpolationState.getConfiguredSpeed(
                    world, this.startTimeOfDay);
            double targetSpeed = SkyAngleInterpolationState.getConfiguredSpeed(
                    world, this.endTimeOfDay);
            double startSpeed = getReleaseStartSpeed(initialSpeed, targetSpeed);
            double finalAdvance = integrateReleaseSpeed(
                    VERTEX_WAKE_SETTLE_TICKS, startSpeed, targetSpeed);
            long finalTime = this.endTimeOfDay + (long) Math.floor(finalAdvance);

            world.setTimeOfDay(finalTime);
            SkyAngleInterpolationState.finishSleepTransition(world, finalTime, world.getTime());
            this.reset();
        }

        ci.cancel();
    }

    @Unique
    private static double getReleaseStartSpeed(double initialSpeed, double targetSpeed) {
        if (!Double.isFinite(initialSpeed) || initialSpeed <= 0.0D) initialSpeed = targetSpeed;
        if (!Double.isFinite(targetSpeed) || targetSpeed <= 0.0D) targetSpeed = initialSpeed;
        if (!Double.isFinite(initialSpeed) || initialSpeed <= 0.0D) return 1.0E-9D;
        if (!Double.isFinite(targetSpeed) || targetSpeed <= 0.0D) return 1.0E-9D;
        return Math.max(1.0E-9D, Math.min(initialSpeed, targetSpeed) * 0.01D);
    }

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
