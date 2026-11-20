package com.seasonaldaycycle.client;

import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.SereneSeasonsCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Locale;

/** Compact, resolution-aware day and season status panel. */
public final class VertexHudOverlay {
    private static final String[] SEASONS = {"ВЕСНА", "ЛЕТО", "ОСЕНЬ", "ЗИМА"};
    private static final String[] SUB_SEASONS = {
            "РАННЯЯ", "СЕРЕДИНА", "ПОЗДНЯЯ",
            "РАННЕЕ", "СЕРЕДИНА", "ПОЗДНЕЕ",
            "РАННЯЯ", "СЕРЕДИНА", "ПОЗДНЯЯ",
            "РАННЯЯ", "СЕРЕДИНА", "ПОЗДНЯЯ"
    };
    private static long nextRefreshTick;
    private static SereneSeasonsCompat.SeasonInfo cachedSeason = SereneSeasonsCompat.SeasonInfo.unavailable();
    private static World cachedWorld;

    private VertexHudOverlay() {}

    public static void reset() {
        cachedWorld = null;
        cachedSeason = SereneSeasonsCompat.SeasonInfo.unavailable();
        nextRefreshTick = 0L;
    }

    public static void render(DrawContext context, int screenWidth, int screenHeight, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!VertexHudConfig.isEnabled() || client.player == null || client.world == null) return;
        if (client.world.getRegistryKey() != World.OVERWORLD) return;

        long clientTick = client.world.getTime();
        if (cachedWorld != client.world || clientTick >= nextRefreshTick) {
            cachedWorld = client.world;
            cachedSeason = SereneSeasonsCompat.getSeasonInfo(client.world);
            nextRefreshTick = clientTick + 20L;
        }

        // Use the same continuous, cycle-aware clock as the sun and moon.
        double visualTime = SkyAngleInterpolationState.getVisualTime(client.world, clientTick, tickDelta);
        long absoluteTime = (long) Math.floor(visualTime);
        long timeOfDay = Math.floorMod(absoluteTime, 24000L);
        long elapsedDays = Math.max(0L, Math.floorDiv(absoluteTime, 24000L));
        long daysPerYear = 365L;
        long year = Math.floorDiv(elapsedDays, daysPerYear) + 1L;
        long dayOfYear = Math.floorMod(elapsedDays, daysPerYear) + 1L;

        long cycleTicks = SeasonalDayCycleClient.getKnownCycleLengthTicks();
        if (cycleTicks <= 0L) cycleTicks = ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;

        String seasonText = "СЕЗОН —";
        if (cachedSeason.available()) {
            int ordinal = Math.max(0, Math.min(11, cachedSeason.subSeasonOrdinal()));
            int seasonIndex = ordinal / 3;
            int daysPerSeason = Math.max(1, cachedSeason.daysPerSeason());
            int dayInSeason = Math.floorMod(cachedSeason.seasonCycleDay(), daysPerSeason) + 1;
            seasonText = SEASONS[seasonIndex] + " / " + SUB_SEASONS[ordinal]
                    + " " + dayInSeason + "/" + daysPerSeason;
        }

        String subSeason = cachedSeason.available() ? cachedSeason.subSeasonName() : null;
        long dayTicks = Math.round(ModConfig.getDayTicksForSubSeason(subSeason, cycleTicks));
        long nightTicks = Math.round(ModConfig.getNightTicksForSubSeason(subSeason, cycleTicks));

        String firstLine = "ГОД " + year + " • ДЕНЬ " + dayOfYear + "/" + daysPerYear
                + " • " + seasonText;
        String secondLine = "ВРЕМЯ " + formatMinecraftTime(timeOfDay)
                + " • ДЕНЬ " + formatDuration(dayTicks)
                + " • НОЧЬ " + formatDuration(nightTicks);

        TextRenderer renderer = client.textRenderer;
        float scale = VertexHudConfig.getScale();
        int lineGap = 1;
        int firstWidth = renderer.getWidth(firstLine);
        int secondWidth = renderer.getWidth(secondLine);
        int logicalWidth = Math.max(firstWidth, secondWidth);
        int logicalHeight = renderer.fontHeight * 2 + lineGap;

        float availableWidth = Math.max(1.0f, screenWidth - 4.0f);
        float availableHeight = Math.max(1.0f, screenHeight - 4.0f);
        float fitX = availableWidth / Math.max(1.0f, logicalWidth);
        float fitY = availableHeight / Math.max(1.0f, logicalHeight);
        float effectiveScale = Math.min(scale, Math.min(fitX, fitY));
        int width = Math.round(logicalWidth * effectiveScale);
        int height = Math.round(logicalHeight * effectiveScale);
        int margin = 2;
        int x = switch (VertexHudConfig.getPosition()) {
            case 0, 3 -> margin;
            case 1, 4 -> (screenWidth - width) / 2;
            default -> screenWidth - width - margin;
        };
        int y = switch (VertexHudConfig.getPosition()) {
            case 0, 1, 2 -> margin;
            default -> screenHeight - height - margin;
        };
        x = Math.max(margin, Math.min(x, screenWidth - width - margin));
        y = Math.max(margin, Math.min(y, screenHeight - height - margin));

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0f);
        context.getMatrices().scale(effectiveScale, effectiveScale, 1.0f);
        context.drawTextWithShadow(renderer, Text.literal(firstLine), 0, 0, 0xFFFFFFFF);
        context.drawTextWithShadow(renderer, Text.literal(secondLine),
                0, renderer.fontHeight + lineGap, 0xFFE0E6ED);
        context.getMatrices().pop();
    }

    private static String formatMinecraftTime(long ticks) {
        long shifted = Math.floorMod(ticks + 6000L, 24000L);
        long hours = shifted / 1000L;
        long minutes = (shifted % 1000L) * 60L / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private static String formatDuration(long ticks) {
        long seconds = Math.max(0L, ticks / 20L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0L) return hours + "ч" + (minutes > 0L ? minutes + "м" : "");
        if (minutes > 0L) return minutes + "м" + (remainder > 0L ? remainder + "с" : "");
        return remainder + "с";
    }
}
