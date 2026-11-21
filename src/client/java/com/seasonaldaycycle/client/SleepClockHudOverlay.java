package com.seasonaldaycycle.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Locale;

/** A vanilla clock and live Minecraft time drawn over the actual sleeping screen. */
public final class SleepClockHudOverlay {
    private static final int CLOCK_SCALE = 2;
    private static final int INTRO_TICKS = 12;
    private static final int TEXT_DELAY_TICKS = 3;
    private static final int MAX_OUTRO_TICKS = 24;

    private SleepClockHudOverlay() {
    }

    /**
     * stopButtonTopY is read from SleepingChatScreen so the label always stays
     * below the icon and safely above the "Leave Bed" button.
     */
    public static void render(DrawContext context, int screenWidth, int screenHeight,
                              float tickDelta, int stopButtonTopY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (client.world.getRegistryKey() != World.OVERWORLD || !client.player.isSleeping()) return;

        SeamlessSleepClientCompat.SleepProgress sleep =
                SeamlessSleepClientCompat.getSleepAnimationProgress();
        if (sleep == null) return;

        double totalTicks = Math.max(1.0, sleep.durationTicks());
        double elapsedTicks = Math.max(0.0, Math.min(totalTicks, sleep.elapsedTicks()));

        double introDuration = Math.min(INTRO_TICKS, Math.max(1.0, totalTicks * 0.25));
        double intro = smootherStep(elapsedTicks / introDuration);
        double outroDuration = Math.min(MAX_OUTRO_TICKS, Math.max(8.0, totalTicks * 0.30));
        double outro = smootherStep((elapsedTicks - (totalTicks - outroDuration)) / outroDuration);
        double opacity = Math.max(0.0, Math.min(1.0, intro * (1.0 - outro)));
        if (opacity <= 0.005) return;

        double textIntro = smootherStep((elapsedTicks - TEXT_DELAY_TICKS)
                / Math.max(1.0, introDuration - TEXT_DELAY_TICKS));
        double textOpacity = opacity * textIntro;

        double visualTime = SkyAngleInterpolationState.getVisualTime(
                client.world, client.world.getTime(), tickDelta);
        long timeOfDay = Math.floorMod((long) Math.floor(visualTime), 24000L);
        String timeText = formatMinecraftTime(timeOfDay);

        int centerX = screenWidth / 2;
        int iconY = Math.max(4, Math.min(screenHeight - 48, stopButtonTopY - 58));
        double verticalOffset = (1.0 - intro) * 8.0 + outro * 4.0;
        double iconSizeFactor = 0.92 + 0.08 * intro - 0.04 * outro;
        float alpha = (float) opacity;

        // The real vanilla clock item keeps its normal animated clock face.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        context.getMatrices().push();
        context.getMatrices().translate(centerX, iconY + 16.0 + verticalOffset, 0.0);
        context.getMatrices().scale(
                (float) (CLOCK_SCALE * iconSizeFactor),
                (float) (CLOCK_SCALE * iconSizeFactor),
                1.0F);
        context.getMatrices().translate(-8.0, -8.0, 0.0);
        context.drawItem(new ItemStack(Items.CLOCK), 0, 0);
        context.getMatrices().pop();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (textOpacity > 0.005) {
            int textAlpha = Math.max(0, Math.min(255, (int) Math.round(textOpacity * 255.0)));
            int color = (textAlpha << 24) | 0x00FFFFFF;
            int textY = (int) Math.round(iconY + 35.0 + verticalOffset);
            context.drawCenteredTextWithShadow(
                    client.textRenderer, Text.literal(timeText), centerX, textY, color);
        }
    }

    private static String formatMinecraftTime(long ticks) {
        long shifted = Math.floorMod(ticks + 6000L, 24000L);
        long hours = shifted / 1000L;
        long minutes = (shifted % 1000L) * 60L / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private static double smootherStep(double value) {
        if (Double.isNaN(value) || value <= 0.0) return 0.0;
        if (value >= 1.0) return 1.0;
        double x = value;
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }
}
