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

    public static void render(DrawContext context, int screenWidth, int screenHeight) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!VertexHudConfig.isEnabled() || client.player == null || client.world == null) return;
        if (client.world.getRegistryKey() != World.OVERWORLD) return;

        long clientTick = client.world.getTime();
        if (cachedWorld != client.world || clientTick >= nextRefreshTick) {
            cachedWorld = client.world;
            cachedSeason = SereneSeasonsCompat.getSeasonInfo(client.world);
            nextRefreshTick = clientTick + 20L;
        }

        long timeOfDay = Math.floorMod(client.world.getTimeOfDay(), 24000L);
        long elapsedDays = Math.max(0L, Math.floorDiv(client.world.getTimeOfDay(), 24000L));
        long cycleNumber = elapsedDays + 1L;
        String seasonText = "СЕЗОН —";
        String daysLeftText = "ДО СМЕНЫ —";
        String dayDuration = "—";

        long cycleTicks = SeasonalDayCycleClient.getKnownCycleLengthTicks();
        if (cycleTicks <= 0L) cycleTicks = ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;
        if (cachedSeason.available()) {
            int ordinal = Math.max(0, Math.min(11, cachedSeason.subSeasonOrdinal()));
            int seasonIndex = ordinal / 3;
            seasonText = SEASONS[seasonIndex] + " / " + SUB_SEASONS[ordinal];
            int daysPerSeason = Math.max(1, cachedSeason.daysPerSeason());
            int dayInSeason = Math.floorMod(cachedSeason.seasonCycleDay(), daysPerSeason) + 1;
            daysLeftText = "ДО СМЕНЫ " + Math.max(0, daysPerSeason - dayInSeason) + " ДН.";
            long dayTicks = Math.round(ModConfig.getDayTicksForSubSeason(cachedSeason.subSeasonName(), cycleTicks));
            dayDuration = formatDuration(dayTicks);
        }

        String firstLine = "ДЕНЬ " + elapsedDays + "   •   ЦИКЛ #" + cycleNumber + "   •   " + seasonText;
        String secondLine = daysLeftText + "   •   ВРЕМЯ " + formatMinecraftTime(timeOfDay)
                + "   •   ДЕНЬ " + dayDuration + "   •   ЦИКЛ " + formatDuration(cycleTicks);

        TextRenderer renderer = client.textRenderer;
        float scale = VertexHudConfig.getScale();
        int paddingX = Math.max(9, Math.round(10.0f * scale));
        int paddingY = Math.max(6, Math.round(7.0f * scale));
        int lineGap = Math.max(3, Math.round(4.0f * scale));
        int firstWidth = renderer.getWidth(firstLine);
        int secondWidth = renderer.getWidth(secondLine);
        int logicalWidth = Math.max(firstWidth, secondWidth) + paddingX * 2;
        int logicalHeight = renderer.fontHeight * 2 + lineGap + paddingY * 2;

        float fitX = Math.max(0.5f, (screenWidth - 12.0f) / Math.max(1.0f, logicalWidth));
        float fitY = Math.max(0.5f, (screenHeight - 12.0f) / Math.max(1.0f, logicalHeight));
        float effectiveScale = Math.min(scale, Math.min(fitX, fitY));
        int width = Math.round(logicalWidth * effectiveScale);
        int height = Math.round(logicalHeight * effectiveScale);
        int margin = 8;
        int x = switch (VertexHudConfig.getPosition()) {
            case 0, 3 -> margin;
            case 1, 4 -> (screenWidth - width) / 2;
            default -> screenWidth - width - margin;
        };
        int y = switch (VertexHudConfig.getPosition()) {
            case 0, 1, 2 -> margin;
            default -> screenHeight - height - margin;
        };
        x = Math.max(4, Math.min(x, screenWidth - width - 4));
        y = Math.max(4, Math.min(y, screenHeight - height - 4));

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0f);
        context.getMatrices().scale(effectiveScale, effectiveScale, 1.0f);
        context.fill(0, 0, logicalWidth, logicalHeight, 0xB9161A20);
        context.fill(0, 0, logicalWidth, 1, 0xFFD6DCE3);
        context.fill(0, logicalHeight - 1, logicalWidth, logicalHeight, 0x885C6672);
        context.fill(0, 1, 1, logicalHeight - 1, 0x885C6672);
        context.fill(logicalWidth - 1, 1, logicalWidth, logicalHeight - 1, 0x885C6672);
        context.drawTextWithShadow(renderer, Text.literal(firstLine), paddingX, paddingY, 0xFFFFFFFF);
        context.drawTextWithShadow(renderer, Text.literal(secondLine),
                paddingX, paddingY + renderer.fontHeight + lineGap, 0xFFE0E6ED);
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
