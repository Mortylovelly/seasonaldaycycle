package com.seasonaldaycycle.mixin;

import net.minecraft.client.MinecraftClient;
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

    @Shadow(remap = false)
    public abstract void reset();

    @Unique
    private boolean vertex$releaseTimeApplied;

    @Inject(method = "start", at = @At("TAIL"), remap = false)
    private void vertex$resetReleaseMarker(CallbackInfo ci) {
        this.vertex$releaseTimeApplied = false;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void vertex$mirrorWakeSettling(CallbackInfo ci) {
        if (!this.active || this.replayCompatMode) return;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;

        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - this.startMillis);
        long totalMillis = Math.max(1L, this.durationTicks) * 50L;
        if (elapsedMillis >= totalMillis) {
            // End the visual state without writing sunrise over the now-normal client clock.
            if (!this.vertex$releaseTimeApplied) {
                world.setTimeOfDay(this.endTimeOfDay);
            }
            this.reset();
            ci.cancel();
            return;
        }

        long skipTicks = Math.max(1, this.durationTicks - VERTEX_WAKE_SETTLE_TICKS);
        long skipMillis = skipTicks * 50L;
        if (elapsedMillis >= skipMillis) {
            // Hold the exact sunrise while Vertex accelerates the normal world clock.
            if (!this.vertex$releaseTimeApplied) {
                world.setTimeOfDay(this.endTimeOfDay);
                this.vertex$releaseTimeApplied = true;
            }
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
