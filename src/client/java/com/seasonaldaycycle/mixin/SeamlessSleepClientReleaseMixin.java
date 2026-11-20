package com.seasonaldaycycle.mixin;

import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors the server's shortened night-skip phase on the client and keeps the
 * sleep state alive during Vertex's final wake-settling window.
 */
@Pseudo
@Mixin(targets = "net.aqualoco.sec.sleep.ClientSleepAnimationState", remap = false)
public abstract class SeamlessSleepClientReleaseMixin {
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

    @Shadow(remap = false)
    private boolean replayCompatMode;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void vertex$mirrorWakeSettling(ClientWorld world, float partialTick, CallbackInfo ci) {
        if (!this.active || this.replayCompatMode) return;

        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - this.startMillis);
        long totalMillis = Math.max(1L, this.durationTicks) * 50L;
        if (elapsedMillis >= totalMillis) {
            // Let Seamless Sleep run its original completion path and reset the state.
            return;
        }

        long skipTicks = Math.max(1, this.durationTicks - VERTEX_WAKE_SETTLE_TICKS);
        long skipMillis = skipTicks * 50L;
        if (elapsedMillis >= skipMillis) {
            // Hold the exact sunrise while Vertex accelerates the normal world clock.
            world.setTimeOfDay(this.endTimeOfDay);
            ci.cancel();
            return;
        }

        double progress = elapsedMillis / (double) skipMillis;
        double eased = smootherStep(progress);
        long delta = this.endTimeOfDay - this.startTimeOfDay;
        long interpolated = this.startTimeOfDay + (long) (delta * eased);
        world.setTimeOfDay(interpolated);
        ci.cancel();
    }

    @Unique
    private static double smootherStep(double value) {
        if (Double.isNaN(value) || value <= 0.0D) return 0.0D;
        if (value >= 1.0D) return 1.0D;
        double x = value;
        return x * x * x * (x * (x * 6.0D - 15.0D) + 10.0D);
    }
}
