package com.seasonaldaycycle.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/** A vanilla clock and live Minecraft time drawn over the actual sleeping screen. */
public final class SleepClockHudOverlay {
    private static final int CLOCK_SCALE = 2;
    private static final double MIN_FADE_TICKS = 18.0D;
    private static final double MAX_FADE_TICKS = 40.0D;
    private SleepClockHudOverlay() {}

    public static void render(DrawContext context, int screenWidth, int screenHeight, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (client.world.getRegistryKey() != World.OVERWORLD || !client.player.isSleeping()) return;
        SeamlessSleepClientCompat.SleepProgress sleep =
                SeamlessSleepClientCompat.getSleepAnimationProgress();
        if (sleep == null) return;

        double totalTicks = Math.max(1.0D, sleep.durationTicks());
        double elapsedTicks = Math.max(0.0D, Math.min(totalTicks, sleep.elapsedTicks()));
        double introDuration = Math.min(MAX_FADE_TICKS, Math.max(MIN_FADE_TICKS, totalTicks * 0.24D));
        double outroDuration = Math.min(MAX_FADE_TICKS, Math.max(MIN_FADE_TICKS, totalTicks * 0.24D));
        double intro = smootherStep(elapsedTicks / introDuration);
        double outroStart = Math.max(0.0D, totalTicks - outroDuration);
        double outro = smootherStep((elapsedTicks - outroStart) / outroDuration);
        double opacity = Math.max(0.0D, Math.min(1.0D, intro * (1.0D - outro)));
        if (opacity <= 0.002D) return;

        double visualTime = SkyAngleInterpolationState.getVisualTime(
                client.world, client.world.getTime(), tickDelta);
        long timeOfDay = Math.floorMod((long) Math.floor(visualTime), 24000L);
        String timeText = VertexHudOverlay.formatMinecraftTime(timeOfDay);
        int centerX = screenWidth / 2;
        int iconY = Math.max(4, screenHeight - 98);
        double verticalOffset = (1.0D - intro) * 14.0D + outro * 8.0D;
        double sizeFactor = 0.88D + 0.12D * intro - 0.05D * outro;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) opacity);
        context.getMatrices().push();
        try {
            context.getMatrices().translate(centerX, iconY + 16.0D + verticalOffset, 0.0D);
            context.getMatrices().scale((float) (CLOCK_SCALE * sizeFactor),
                    (float) (CLOCK_SCALE * sizeFactor), 1.0F);
            context.getMatrices().translate(-8.0D, -8.0D, 0.0D);
            context.drawItem(new ItemStack(Items.CLOCK), 0, 0);
        } finally {
            context.getMatrices().pop();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        int alpha = Math.max(0, Math.min(255, (int) Math.round(opacity * 255.0D)));
        int color = (alpha << 24) | 0x00FFFFFF;
        int textY = (int) Math.round(iconY + 35.0D + verticalOffset);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(timeText), centerX, textY, color);
    }

    private static double smootherStep(double value) {
        if (Double.isNaN(value) || value <= 0.0D) return 0.0D;
        if (value >= 1.0D) return 1.0D;
        double x = value;
        return x * x * x * (x * (x * 6.0D - 15.0D) + 10.0D);
    }
}
