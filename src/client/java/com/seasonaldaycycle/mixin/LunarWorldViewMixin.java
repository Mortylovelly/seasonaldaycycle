package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.client.SkyAngleInterpolationState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LunarWorldView;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LunarWorldView.class)
public interface LunarWorldViewMixin {
    /**
     * Uses a fractional visual time for the sky instead of Minecraft's whole-tick
     * day time. This keeps the logical/server time untouched while making the
     * sun and moon move continuously between server time updates.
     */
    @Overwrite
    default float getSkyAngle(float tickDelta) {
        LunarWorldView lunarWorld = (LunarWorldView) this;
        DimensionType dimension = lunarWorld.getDimension();

        if (dimension.hasFixedTime()) {
            return dimension.getSkyAngle(lunarWorld.getLunarTime());
        }

        if (!(lunarWorld instanceof World world) || !world.isClient()) {
            return dimension.getSkyAngle(lunarWorld.getLunarTime());
        }

        long clientTick = MinecraftClient.getInstance().world == null
                ? 0L
                : MinecraftClient.getInstance().world.getTime();

        double visualTime = SkyAngleInterpolationState.getVisualTime(lunarWorld, clientTick, tickDelta);
        double cyclePosition = MathHelper.fractionalPart(visualTime / 24000.0 - 0.25);
        double smoothing = 0.5 - Math.cos(cyclePosition * Math.PI) / 2.0;
        return (float) ((cyclePosition * 2.0 + smoothing) / 3.0);
    }
}
