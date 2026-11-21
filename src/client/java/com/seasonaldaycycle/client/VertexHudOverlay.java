package com.seasonaldaycycle.client;

import com.seasonaldaycycle.SereneSeasonsCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Locale;

/** Compact, resolution-aware world calendar and time display. */
public final class VertexHudOverlay {
    private static final String[] SEASONS = {"ВЕСНА", "ЛЕТО", "ОСЕНЬ", "ЗИМА"};
    private static final String[] SUB_SEASONS = {
            "РАННЯЯ", "СЕРЕДИНА", "ПОЗДНЯЯ",
            "РАННЕЕ", "СЕРЕДИНА", "ПОЗДНЕЕ",
            "РАННЯЯ", "СЕРЕДИНА", "ПОЗДНЯЯ",
            "РАННЯЯ", "СЕРЕДИНА", "ПОЗДНЯЯ"
    };
    private static final int[] VANILLA_MONTH_LENGTHS = {
            31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
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

        // One continuous clock drives the sun, moon and displayed time.
        double visualTime = SkyAngleInterpolationState.getVisualTime(client.world, clientTick, tickDelta);
        long absoluteTime = (long) Math.floor(visualTime);
        long elapsedDays = Math.max(0L, Math.floorDiv(absoluteTime, 24000L));
        long timeOfDay = Math.floorMod(absoluteTime, 24000L);

        long daysInYear = 365L;
        long dayOfYear;
        int monthNumber;
        String seasonText = "СЕЗОН —";
        String phaseProgress = "ДО СМЕНЫ —";

        if (cachedSeason.available()) {
            int ordinal = Math.max(0, Math.min(11, cachedSeason.subSeasonOrdinal()));
            int seasonIndex = ordinal / 3;
            int safeDaysPerSeason = Math.max(1, cachedSeason.daysPerSeason());
            int safeDaysPerSubSeason = Math.max(1, cachedSeason.daysPerSubSeason());

            // Serene Seasons runs twelve equal calendar periods per year.
            // This automatically follows its configured season and sub-season lengths.
            daysInYear = (long) safeDaysPerSeason * 4L;
            long calendarDay = Math.max(0L, cachedSeason.seasonCycleDay());
            dayOfYear = Math.floorMod(calendarDay, daysInYear) + 1L;
            monthNumber = ordinal + 1;

            int dayInCurrentMonth = (int) (Math.floorMod(calendarDay, safeDaysPerSubSeason) + 1L);
            seasonText = SEASONS[seasonIndex] + " (" + SUB_SEASONS[ordinal] + ")";
            phaseProgress = "ДО СМЕНЫ " + dayInCurrentMonth + "/" + safeDaysPerSubSeason;
        } else {
            // Keep a useful calendar if Serene Seasons is not installed.
            dayOfYear = Math.floorMod(elapsedDays, daysInYear) + 1L;
            monthNumber = getVanillaMonthNumber((int) dayOfYear);
        }

        String firstLine = "ПРОЖИТО " + elapsedDays + " ДН. • ГОД "
                + dayOfYear + "/" + daysInYear + " • МЕСЯЦ " + monthNumber + "/12";
        String secondLine = seasonText + " • " + phaseProgress
                + " • " + formatMinecraftTime(timeOfDay);

        TextRenderer renderer = client.textRenderer;
        float scale = VertexHudConfig.getScale();
        int lineGap = 1;
        int logicalWidth = Math.max(renderer.getWidth(firstLine), renderer.getWidth(secondLine));
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

    private static int getVanillaMonthNumber(int dayOfYear) {
        int remainingDays = Math.max(1, dayOfYear);
        for (int month = 0; month < VANILLA_MONTH_LENGTHS.length; month++) {
            if (remainingDays <= VANILLA_MONTH_LENGTHS[month]) return month + 1;
            remainingDays -= VANILLA_MONTH_LENGTHS[month];
        }
        return 12;
    }

    public static String formatMinecraftTime(long ticks) {
        long shifted = Math.floorMod(ticks + 6000L, 24000L);
        long hours = shifted / 1000L;
        long minutes = (shifted % 1000L) * 60L / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }
}
