package com.seasonaldaycycle.mixin;

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
 * Gives Seamless Sleep a symmetric smooth start and a dedicated wake-up settling phase.
 * The last 40 ticks are reserved for Vertex to restore the normal day-cycle speed
 * while players remain in bed.
 */
@Pseudo
@Mixin(targets = "net.aqualoco.sec.sleep.SleepAnimationState", remap = false)
public abstract class SeamlessSleepEasingMixin {
    @Unique
    private static final int VERTEX_WAKE_SETTLE_TICKS = 40;

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
    private boolean vertex$releaseStarted;

    @Inject(method = "start", at = @At("TAIL"), remap = false)
    private void vertex$appendWakeSettleTime(long currentTime, long targetTime, CallbackInfo ci) {
        if (this.active) {
            this.durationTicks = Math.max(1, this.durationTicks) + VERTEX_WAKE_SETTLE_TICKS;
        }
        this.vertex$releaseStarted = false;
    }

    /**
     * @author Mortylovelly
     * @reason Keep the world at the exact sunrise while Vertex smoothly restores its
     * configured day speed; Seamless Sleep wakes players only after this settling window.
     */
    @Overwrite(remap = false)
    public void tick(ServerWorld world) {
        if (!this.active) return;

        long now = System.currentTimeMillis();
        double elapsedMs = (double) (now - this.startMillis);
        if (elapsedMs <= 0.0D) {
            world.setTimeOfDay(this.startTimeOfDay);
            return;
        }

        int totalTicks = Math.max(1, this.durationTicks);
        int skipTicks = Math.max(1, totalTicks - VERTEX_WAKE_SETTLE_TICKS);
        double skipDurationMs = skipTicks * 50.0D;
        double totalDurationMs = totalTicks * 50.0D;

        if (elapsedMs >= totalDurationMs) {
            if (!this.vertex$releaseStarted) {
                world.setTimeOfDay(this.endTimeOfDay);
                this.vertex$releaseStarted = true;
            }
            this.active = false;
            return;
        }

        if (elapsedMs >= skipDurationMs) {
            if (!this.vertex$releaseStarted) {
                world.setTimeOfDay(this.endTimeOfDay);
                this.vertex$releaseStarted = true;
            }
            // The sleep animation remains active, but Vertex now owns the clock.
            return;
        }

        double progress = Math.max(0.0D, Math.min(1.0D, elapsedMs / skipDurationMs));
        double eased = smootherStep(progress);
        long delta = this.endTimeOfDay - this.startTimeOfDay;
        long interpolated = this.startTimeOfDay + (long) (delta * eased);
        world.setTimeOfDay(interpolated);
    }

    /**
     * @author Mortylovelly
     * @reason Use a quintic ease-in/ease-out curve with zero velocity and acceleration
     * at both ends of the night-skip transition.
     */
    @Overwrite(remap = false)
    public static double integralEase(double progress) {
        return smootherStep(progress);
    }

    @Unique
    private static double smootherStep(double value) {
        if (Double.isNaN(value) || value <= 0.0D) return 0.0D;
        if (value >= 1.0D) return 1.0D;
        double x = value;
        return x * x * x * (x * (x * 6.0D - 15.0D) + 10.0D);
    }
}
