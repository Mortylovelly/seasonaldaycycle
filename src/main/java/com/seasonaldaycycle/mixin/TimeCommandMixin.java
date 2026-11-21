package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.DayCycleHandler;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.TimeCommand;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps /time set from rewinding Vertex's persistent elapsed-day counter. */
@Mixin(TimeCommand.class)
public abstract class TimeCommandMixin {
    @Unique
    private static final ThreadLocal<Long> VERTEX_TIME_BEFORE_SET = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<Long> VERTEX_TIME_BEFORE_ADD = new ThreadLocal<>();

    @Inject(method = "executeSet", at = @At("HEAD"))
    private static void vertex$captureTimeBeforeSet(
            ServerCommandSource source, int time, CallbackInfoReturnable<Integer> cir) {
        VERTEX_TIME_BEFORE_SET.remove();
        if (source == null) return;
        ServerWorld world = source.getWorld();
        if (world.getRegistryKey() == World.OVERWORLD) {
            VERTEX_TIME_BEFORE_SET.set(world.getTimeOfDay());
        }
    }

    @Inject(method = "executeSet", at = @At("TAIL"))
    private static void vertex$preserveElapsedDays(
            ServerCommandSource source, int time, CallbackInfoReturnable<Integer> cir) {
        Long previousTime = VERTEX_TIME_BEFORE_SET.get();
        VERTEX_TIME_BEFORE_SET.remove();
        if (previousTime == null || source == null) return;

        DayCycleHandler.onTimeSetCommand(source.getWorld(), previousTime);
    }

    @Inject(method = "executeAdd", at = @At("HEAD"))
    private static void vertex$captureTimeBeforeAdd(
            ServerCommandSource source, int time, CallbackInfoReturnable<Integer> cir) {
        VERTEX_TIME_BEFORE_ADD.remove();
        if (source == null) return;
        ServerWorld world = source.getWorld();
        if (world.getRegistryKey() == World.OVERWORLD) {
            VERTEX_TIME_BEFORE_ADD.set(world.getTimeOfDay());
        }
    }

    @Inject(method = "executeAdd", at = @At("TAIL"))
    private static void vertex$acceptTimeAddition(
            ServerCommandSource source, int time, CallbackInfoReturnable<Integer> cir) {
        Long previousTime = VERTEX_TIME_BEFORE_ADD.get();
        VERTEX_TIME_BEFORE_ADD.remove();
        if (previousTime == null || source == null) return;

        DayCycleHandler.onTimeAddCommand(source.getWorld());
    }
}
