package com.seasonaldaycycle.client;

import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.SereneSeasonsCompat;
import com.seasonaldaycycle.network.SetDayCycleLengthPayload;
import com.seasonaldaycycle.network.SetSeasonPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class DayCycleScreen {
    private static final int PANEL_WIDTH = 360;
    private static final int DAY_PANEL_HEIGHT = 280;
    private static final int SEASON_PANEL_HEIGHT = 360;
    private static final int HEADER_HEIGHT = 34;
    private static final int SLIDER_WIDTH = 218;
    private static final int SLIDER_HEIGHT = 7;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CLOCK_CARD_WIDTH = 104;
    private static final int CLOCK_CARD_HEIGHT = 126;
    private static final float UI_SCALE = 0.40f;

    private static final int MINI_PANEL_SIZE = 42;
    private static final int MINI_HITBOX_PADDING = 8;

    private static final long MIN_TICKS = ModConfig.MIN_CYCLE_LENGTH_TICKS;
    private static final long MAX_TICKS = ModConfig.MAX_CYCLE_LENGTH_TICKS;
    private static final long STEP = ModConfig.CYCLE_LENGTH_STEP_TICKS;

    private static final long[] PRESET_TICKS = {
            20L,
            20L * 60L * 20L,
            30L * 60L * 20L,
            60L * 60L * 20L,
            2L * 60L * 60L * 20L,
            3L * 60L * 60L * 20L,
            6L * 60L * 60L * 20L,
            24L * 60L * 60L * 20L
    };

    private static final String[] PRESET_LABELS = {
            "1 с", "20 м", "30 м", "1 ч", "2 ч", "3 ч", "6 ч", "24 ч"
    };

    private static final String[] SUB_SEASON_NAMES = {
            "Ранняя весна", "Середина весны", "Поздняя весна",
            "Раннее лето", "Середина лета", "Позднее лето",
            "Ранняя осень", "Середина осени", "Поздняя осень",
            "Ранняя зима", "Середина зимы", "Поздняя зима"
    };

    private static boolean serverCanEdit;

    private static DayCycleScreen ACTIVE;

    private long cycleLengthTicks;
    private int panelX;
    private int panelY;
    private int miniX;
    private int miniY;
    private boolean minimized;
    private boolean showSeasonsPage;
    private String seasonActionMessage = "";
    private long seasonActionUntil;
    private SereneSeasonsCompat.SeasonInfo cachedSeasonInfo = SereneSeasonsCompat.SeasonInfo.unavailable();
    private long nextSeasonRefreshMillis;
    private boolean draggingPanel;
    private boolean draggingSlider;
    private boolean draggingMini;
    private boolean miniPressCandidate;
    private int dragOffsetX;
    private int dragOffsetY;
    private int miniPressStartX;
    private int miniPressStartY;
    private long savedUntil;
    private boolean serverSynced;
    private int lastSoundStep = Integer.MIN_VALUE;

    public DayCycleScreen() {
        this.cycleLengthTicks = ModConfig.getCycleLengthTicks();
        ACTIVE = this;
    }

    public static boolean isActive() {
        return ACTIVE != null;
    }

    public static DayCycleScreen getActive() {
        return ACTIVE;
    }

    public void init(int width, int height) {
        if (panelX == 0 && panelY == 0) {
            panelX = (width - visualPanelWidth()) / 2;
            panelY = Math.max(8, (height - visualPanelHeight()) / 2);
        }
        if (miniX == 0 && miniY == 0) {
            miniX = panelX;
            miniY = panelY;
        }
        clampPanel(width, height);
        clampMini(width, height);
    }

    public void renderOverlay(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        if (panelX == 0 && panelY == 0) {
            init(width, height);
        }

        if (minimized) {
            renderMinimized(context, mouseX, mouseY);
            return;
        }

        context.getMatrices().push();
        context.getMatrices().translate(panelX * (1.0f - UI_SCALE), panelY * (1.0f - UI_SCALE), 0.0f);
        context.getMatrices().scale(UI_SCALE, UI_SCALE, 1.0f);

        int localMouseX = toLogicalX(mouseX);
        int localMouseY = toLogicalY(mouseY);
        drawPanelGlass(context);
        drawAmbientLeaves(context);
        drawContent(context, localMouseX, localMouseY);

        context.getMatrices().pop();
    }

    private void renderMinimized(DrawContext context, int mouseX, int mouseY) {
        boolean hover = isMiniHovered(mouseX, mouseY);
        int clockX = miniX + (MINI_PANEL_SIZE - 16) / 2;
        int clockY = miniY + (MINI_PANEL_SIZE - 16) / 2;

        context.fill(miniX, miniY, miniX + MINI_PANEL_SIZE, miniY + MINI_PANEL_SIZE,
                hover ? 0x664F5964 : 0x443F4851);
        context.fill(miniX, miniY, miniX + MINI_PANEL_SIZE, miniY + 1,
                hover ? 0xCCCDD3D9 : 0x667F8790);
        context.fill(miniX, miniY + MINI_PANEL_SIZE - 1, miniX + MINI_PANEL_SIZE, miniY + MINI_PANEL_SIZE,
                0x445F6871);

        context.getMatrices().push();
        context.getMatrices().translate(clockX + 8, clockY + 8, 0.0f);
        context.drawItem(new ItemStack(Items.CLOCK), -8, -8);
        context.getMatrices().pop();
    }

    private void drawContent(DrawContext context, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        var textRenderer = client.textRenderer;
        int left = panelX + 18;
        int right = panelX + PANEL_WIDTH - 18;
        int contentTop = panelY + HEADER_HEIGHT + 30;

        context.drawTextWithShadow(textRenderer, Text.literal("Настройки суток"), left, panelY + 11, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("скорость времени"), left, panelY + 23, 0xFFC0C6CD);
        drawMinimizeButton(context, mouseX, mouseY);
        drawCloseButton(context, mouseX, mouseY);
        drawTabs(context, mouseX, mouseY);

        if (showSeasonsPage) {
            drawSeasonContent(context, left, right, contentTop, mouseX, mouseY);
            return;
        }

        context.drawTextWithShadow(textRenderer, Text.literal("ПОЛНЫЕ СУТКИ"), left, contentTop, 0xFFD0D5DB);
        drawCenteredText(context, Text.literal(formatDuration(cycleLengthTicks)), left + 109, contentTop + 16, 0xFFFFFFFF);
        drawCenteredText(context, Text.literal(cycleLengthTicks + " тиков"), left + 109, contentTop + 31, 0xFF929AA4);

        int sliderX = left;
        int sliderY = contentTop + 52;
        drawSlider(context, sliderX, sliderY, mouseX, mouseY);
        context.drawTextWithShadow(textRenderer, Text.literal(formatDurationForLabel(MIN_TICKS)), sliderX, sliderY + 13, 0xFF929AA4);
        drawCenteredText(context, Text.literal(formatDurationForLabel(sliderMidpointTicks())), sliderX + SLIDER_WIDTH / 2, sliderY + 13, 0xFF929AA4);
        drawRightText(context, Text.literal(formatDurationForLabel(MAX_TICKS)), sliderX + SLIDER_WIDTH, sliderY + 13, 0xFF929AA4);

        int presetY = contentTop + 83;
        context.drawTextWithShadow(textRenderer, Text.literal("БЫСТРЫЙ ВЫБОР"), left, presetY, 0xFFD0D5DB);
        drawPresetSection(context, left, presetY + 16, mouseX, mouseY);

        drawClockCard(context, right - CLOCK_CARD_WIDTH, contentTop);

        int infoY = panelY + getPanelHeight() - 34;
        context.drawTextWithShadow(textRenderer, Text.literal("Скорость: " + formatSpeed()), left, infoY, 0xFFE2E6EA);
        drawRightText(context, Text.literal("День и ночь масштабируются по сезону"), right, infoY, 0xFF929AA4);

        long now = System.currentTimeMillis();
        if (savedUntil > now) {
            drawRightText(context, Text.literal("✓ Сохранено"), right, infoY - 14, 0xFFC9F6D0);
        } else if (!serverSynced && client.getNetworkHandler() != null) {
            drawRightText(context, Text.literal("Синхронизация..."), right, infoY - 14, 0xFFFFDB91);
        }
    }

    private void drawTabs(DrawContext context, int mouseX, int mouseY) {
        int x = panelX + 18;
        int y = panelY + HEADER_HEIGHT + 5;
        drawTab(context, x, y, 112, "СУТОЧНЫЙ ЦИКЛ", !showSeasonsPage, inside(mouseX, mouseY, x, y, 112, 20));
        drawTab(context, x + 118, y, 112, "СЕЗОНЫ", showSeasonsPage, inside(mouseX, mouseY, x + 118, y, 112, 20));
    }

    private void drawTab(DrawContext context, int x, int y, int width, String label, boolean selected, boolean hovered) {
        int fill = selected ? 0xAA737B84 : (hovered ? 0x995B626A : 0x77444A52);
        context.fill(x, y, x + width, y + 20, fill);
        context.fill(x, y, x + width, y + 1, selected ? 0xFFE0E4E8 : 0x889AA1A9);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, label, x + width / 2, y + 6, 0xFFF4F5F6);
    }

    private void drawSeasonContent(DrawContext context, int left, int right, int contentTop, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        var renderer = client.textRenderer;
        long now = System.currentTimeMillis();
        if (now >= nextSeasonRefreshMillis) {
            cachedSeasonInfo = SereneSeasonsCompat.getSeasonInfo(client.world);
            nextSeasonRefreshMillis = now + 500L;
        }
        SereneSeasonsCompat.SeasonInfo info = cachedSeasonInfo;

        context.drawTextWithShadow(renderer, Text.literal("КАЛЕНДАРЬ И СЕЗОННЫЙ СВЕТОВОЙ ДЕНЬ"), left, contentTop, 0xFFD0D5DB);

        if (!info.available()) {
            context.drawTextWithShadow(renderer, Text.literal("Serene Seasons не найден или не ответил."), left, contentTop + 24, 0xFFFFC27A);
            context.drawTextWithShadow(renderer, Text.literal("Сезонные кнопки доступны только с этим модом."), left, contentTop + 39, 0xFFB8C0C8);
            context.drawTextWithShadow(renderer, Text.literal("Проверь, что Serene Seasons 9.1.0+ установлен на сервере."), left, contentTop + 54, 0xFF929AA4);
            return;
        }

        int seasonIndex = info.subSeasonOrdinal() / 3;
        String currentSeason = getSeasonName(seasonIndex);
        String currentSubSeason = getSubSeasonName(info.subSeasonOrdinal());
        long elapsedDays = Math.max(0L, Math.floorDiv(client.world.getTimeOfDay(), 24000L));
        int dayInSeason = Math.floorMod(info.seasonCycleDay(), Math.max(1, info.daysPerSeason())) + 1;
        long seasonRealTicks = Math.min(Long.MAX_VALUE, (long) info.daysPerSeason() * cycleLengthTicks);

        context.drawTextWithShadow(renderer, Text.literal("Сейчас: " + currentSeason + " · " + currentSubSeason), left, contentTop + 15, 0xFFFFFFFF);
        context.drawTextWithShadow(renderer, Text.literal("Прошло игровых дней: " + elapsedDays), left, contentTop + 28, 0xFFE2E6EA);
        context.drawTextWithShadow(renderer, Text.literal("День сезона: " + dayInSeason + "/" + info.daysPerSeason()
                + " · длительность: " + info.daysPerSeason() + " дней (~" + formatCompactDuration(seasonRealTicks) + ")"),
                left, contentTop + 41, 0xFFBFC6CE);

        int buttonsY = contentTop + 57;
        int gap = 4;
        int buttonWidth = (PANEL_WIDTH - 36 - gap * 3) / 4;
        for (int i = 0; i < 4; i++) {
            int buttonX = left + i * (buttonWidth + gap);
            boolean selected = i == seasonIndex;
            boolean hovered = inside(mouseX, mouseY, buttonX, buttonsY, buttonWidth, 24);
            int fill = selected ? 0xAA737B84 : (hovered ? 0x995B626A : 0x77444A52);
            context.fill(buttonX, buttonsY, buttonX + buttonWidth, buttonsY + 24, fill);
            context.fill(buttonX, buttonsY, buttonX + buttonWidth, buttonsY + 1, selected ? 0xFFE0E4E8 : 0x889AA1A9);
            context.drawCenteredTextWithShadow(renderer, getSeasonName(i), buttonX + buttonWidth / 2, buttonsY + 8, 0xFFF4F5F6);
        }

        context.drawTextWithShadow(renderer, Text.literal("Сезон меняется через Serene Seasons; снег и лёд оттаивают постепенно."),
                left, contentTop + 87, 0xFFBFC6CE);
        String bigGlobeStatus = SereneSeasonsCompat.isBigGlobeLoaded()
                ? "Big Globe: используются общие сезонные biome hooks"
                : "Big Globe не обнаружен";
        context.drawTextWithShadow(renderer, Text.literal(bigGlobeStatus), left, contentTop + 100, 0xFF9FD0A7);

        int tableY = contentTop + 117;
        context.drawTextWithShadow(renderer, Text.literal("12 ПЕРИОДОВ ГОДА · ДОЛЯ ДНЯ И НОЧИ"), left, tableY, 0xFFD0D5DB);
        for (int i = 0; i < 12; i++) {
            int column = i / 6;
            int row = i % 6;
            int columnX = left + column * 164;
            int rowY = tableY + 14 + row * 21;
            String enumName = getSubSeasonEnumName(i);
            double dayRatio = ModConfig.getDayRatioForSubSeason(enumName);
            double nightRatio = 1.0 - dayRatio;
            double dayTicks = ModConfig.getDayTicksForSubSeason(enumName, cycleLengthTicks);
            double nightTicks = ModConfig.getNightTicksForSubSeason(enumName, cycleLengthTicks);

            context.drawTextWithShadow(renderer, Text.literal(SUB_SEASON_NAMES[i]), columnX, rowY, 0xFFFFFFFF);
            String durations = "Д " + Math.round(dayRatio * 100.0) + "% " + formatCompactDuration((long) dayTicks)
                    + " · Н " + Math.round(nightRatio * 100.0) + "% " + formatCompactDuration((long) nightTicks);
            context.drawTextWithShadow(renderer, Text.literal(durations), columnX, rowY + 9, 0xFFB3BBC4);
        }

        if (seasonActionUntil > now) {
            drawRightText(context, Text.literal(seasonActionMessage), right, panelY + getPanelHeight() - 34, 0xFFFFDB91);
        } else if (!serverCanEdit) {
            drawRightText(context, Text.literal("Для смены сезона нужны права OP 2"), right, panelY + getPanelHeight() - 34, 0xFFFFC27A);
        } else {
            drawRightText(context, Text.literal("Смена сезона доступна"), right, panelY + getPanelHeight() - 34, 0xFF9FD0A7);
        }
    }

    private static String getSeasonName(int seasonIndex) {
        return switch (seasonIndex) {
            case 0 -> "Весна";
            case 1 -> "Лето";
            case 2 -> "Осень";
            case 3 -> "Зима";
            default -> "Неизвестно";
        };
    }

    private static String getSubSeasonName(int ordinal) {
        return ordinal >= 0 && ordinal < SUB_SEASON_NAMES.length ? SUB_SEASON_NAMES[ordinal] : "Неизвестно";
    }

    private static String getSubSeasonEnumName(int ordinal) {
        String[] names = {
                "EARLY_SPRING", "MID_SPRING", "LATE_SPRING",
                "EARLY_SUMMER", "MID_SUMMER", "LATE_SUMMER",
                "EARLY_AUTUMN", "MID_AUTUMN", "LATE_AUTUMN",
                "EARLY_WINTER", "MID_WINTER", "LATE_WINTER"
        };
        return ordinal >= 0 && ordinal < names.length ? names[ordinal] : "MID_SPRING";
    }

    private static String formatCompactDuration(long ticks) {
        long totalSeconds = Math.max(0L, ticks / 20L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) return hours + "ч" + (minutes > 0L ? " " + minutes + "м" : "");
        if (minutes > 0L) return minutes + "м";
        return seconds + "с";
    }

    private void drawAmbientLeaves(DrawContext context) {
        int left = panelX + 2;
        int top = panelY + 2;
        int right = panelX + PANEL_WIDTH - 2;
        int bottom = panelY + getPanelHeight() - 2;
        long tick = System.currentTimeMillis() / 85L;

        for (int i = 0; i < 12; i++) {
            double phase = i * 2.37;
            double speed = 0.006 + (i % 3) * 0.0012;
            double x = left + Math.floorMod((long) (i * 71 + tick * (5 + i % 4)), Math.max(1, right - left));
            double y = top + Math.floorMod((long) (i * 43 + tick * (3 + i % 3)), Math.max(1, bottom - top));
            x += Math.sin(tick * speed + phase) * 7.0;
            y += Math.cos(tick * speed * 0.8 + phase) * 5.0;

            context.getMatrices().push();
            context.getMatrices().translate(x, y, 0.0f);
            context.getMatrices().scale(0.55f, 0.55f, 1.0f);
            context.drawItem(new ItemStack(Items.OAK_LEAVES), 0, 0);
            context.getMatrices().pop();
        }
    }

    private void drawPanelGlass(DrawContext context) {
        context.fill(panelX + 3, panelY + 5, panelX + PANEL_WIDTH + 3, panelY + getPanelHeight() + 5, 0x32000000);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + getPanelHeight(), 0x8A666B73);
        context.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + HEADER_HEIGHT, 0x9B777C84);
        context.fill(panelX + 1, panelY + HEADER_HEIGHT, panelX + PANEL_WIDTH - 1, panelY + HEADER_HEIGHT + 1, 0xAABDC3CB);
        context.fill(panelX + 1, panelY + getPanelHeight() - 1, panelX + PANEL_WIDTH - 1, panelY + getPanelHeight(), 0x8A949AA3);
        context.fill(panelX + 1, panelY + 1, panelX + 2, panelY + getPanelHeight() - 1, 0x8A949AA3);
        context.fill(panelX + PANEL_WIDTH - 2, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + getPanelHeight() - 1, 0x8A949AA3);
        context.fill(panelX + 12, panelY + HEADER_HEIGHT + 7, panelX + PANEL_WIDTH - 12, panelY + HEADER_HEIGHT + 8, 0x28FFFFFF);
    }

    private void drawCloseButton(DrawContext context, int mouseX, int mouseY) {
        int x = panelX + PANEL_WIDTH - 29;
        int y = panelY + 6;
        boolean hover = inside(mouseX, mouseY, x, y, 22, 22);
        context.fill(x, y, x + 22, y + 22, hover ? 0xCC4A4F57 : 0x88464A51);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, "×", x + 11, y + 2, hover ? 0xFFFFFFFF : 0xFFE8EBEE);
    }

    private void drawMinimizeButton(DrawContext context, int mouseX, int mouseY) {
        int x = panelX + PANEL_WIDTH - 57;
        int y = panelY + 6;
        boolean hover = inside(mouseX, mouseY, x, y, 22, 22);
        context.fill(x, y, x + 22, y + 22, hover ? 0xCC4A4F57 : 0x88464A51);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, "−", x + 11, y + 2, hover ? 0xFFFFFFFF : 0xFFE8EBEE);
    }

    private void drawSlider(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int trackY = y + 2;
        context.fill(x, trackY, x + SLIDER_WIDTH, trackY + SLIDER_HEIGHT, 0x99515760);
        int knobX = sliderXForValue(cycleLengthTicks);
        context.fill(x, trackY, knobX, trackY + SLIDER_HEIGHT, 0xCCBCC3CA);
        boolean hovered = isSliderHovered(mouseX, mouseY);
        context.fill(knobX - 5, y - 2, knobX + 5, y + 11, hovered ? 0xFFFFFFFF : 0xFFE4E7EA);
        context.fill(knobX - 2, y + 1, knobX + 3, y + 8, 0xFF626870);
    }

    private void drawPresetSection(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int gap = 4;
        int columns = 4;
        int buttonWidth = (SLIDER_WIDTH - gap * (columns - 1)) / columns;
        for (int i = 0; i < PRESET_TICKS.length; i++) {
            int column = i % columns;
            int row = i / columns;
            int buttonX = x + column * (buttonWidth + gap);
            int buttonY = y + row * (BUTTON_HEIGHT + 3);
            boolean hover = inside(mouseX, mouseY, buttonX, buttonY, buttonWidth, BUTTON_HEIGHT);
            boolean selected = cycleLengthTicks == PRESET_TICKS[i];
            int color = selected ? 0xAA737B84 : (hover ? 0x995B626A : 0x77444A52);
            context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + BUTTON_HEIGHT, color);
            context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + 1, selected ? 0xFFE0E4E8 : 0x889AA1A9);
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, PRESET_LABELS[i], buttonX + buttonWidth / 2, buttonY + 6, 0xFFF4F5F6);
        }
    }

    private void drawClockCard(DrawContext context, int x, int y) {
        context.fill(x, y, x + CLOCK_CARD_WIDTH, y + CLOCK_CARD_HEIGHT, 0x62495058);
        context.fill(x, y, x + CLOCK_CARD_WIDTH, y + 1, 0xFF9AA1AA);
        context.fill(x, y + CLOCK_CARD_HEIGHT - 1, x + CLOCK_CARD_WIDTH, y + CLOCK_CARD_HEIGHT, 0xFF69717A);
        context.fill(x + 1, y + 1, x + CLOCK_CARD_WIDTH - 1, y + 2, 0x2AFFFFFF);
        drawCenteredText(context, Text.literal("СЕЙЧАС"), x + CLOCK_CARD_WIDTH / 2, y + 10, 0xFFEFF1F3);

        int clockSize = 28;
        int clockX = x + (CLOCK_CARD_WIDTH - clockSize) / 2;
        int clockY = y + 31;
        context.fill(clockX - 8, clockY - 8, clockX + clockSize + 8, clockY + clockSize + 8, 0x2AFFFFFF);

        context.getMatrices().push();
        context.getMatrices().translate(x + CLOCK_CARD_WIDTH / 2.0f, clockY + clockSize / 2.0f, 0.0f);
        context.getMatrices().scale(1.75f, 1.75f, 1.0f);
        context.drawItem(new ItemStack(Items.CLOCK), -8, -8);
        context.getMatrices().pop();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            long time = Math.floorMod(client.world.getTimeOfDay(), 24000L);
            drawCenteredText(context, Text.literal(formatMinecraftTime(time)), x + CLOCK_CARD_WIDTH / 2, y + 82, 0xFFFFFFFF);
            drawCenteredText(context, Text.literal(time < 12000L ? "День" : "Ночь"), x + CLOCK_CARD_WIDTH / 2, y + 98, time < 12000L ? 0xFFFFD37C : 0xFFB8C8FF);
        }

        drawCenteredText(context, Text.literal("ванильные часы"), x + CLOCK_CARD_WIDTH / 2, y + CLOCK_CARD_HEIGHT - 17, 0xFFB1B7BE);
    }

    public boolean handleMouseButton(double mouseX, double mouseY, int button, int action) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        if (action == GLFW.GLFW_PRESS) {
            if (minimized) {
                if (isMiniHovered(mouseX, mouseY)) {
                    miniPressCandidate = true;
                    draggingMini = false;
                    miniPressStartX = (int) mouseX;
                    miniPressStartY = (int) mouseY;
                    dragOffsetX = miniPressStartX - miniX;
                    dragOffsetY = miniPressStartY - miniY;
                    return true;
                }
                return false;
            }

            int x = toLogicalX(mouseX);
            int y = toLogicalY(mouseY);

            int closeX = panelX + PANEL_WIDTH - 29;
            int closeY = panelY + 6;
            if (inside(x, y, closeX, closeY, 22, 22)) {
                playUiSound(1.15f);
                close();
                return true;
            }

            int minimizeX = panelX + PANEL_WIDTH - 57;
            int minimizeY = panelY + 6;
            if (inside(x, y, minimizeX, minimizeY, 22, 22)) {
                miniX = panelX;
                miniY = panelY;
                clampMini(MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight());
                minimized = true;
                draggingPanel = false;
                draggingSlider = false;
                playUiSound(0.92f);
                return true;
            }

            int tabY = panelY + HEADER_HEIGHT + 5;
            if (inside(x, y, panelX + 18, tabY, 112, 20)) {
                showSeasonsPage = false;
                clampPanel(MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight());
                playUiSound(1.0f);
                return true;
            }
            if (inside(x, y, panelX + 136, tabY, 112, 20)) {
                showSeasonsPage = true;
                clampPanel(MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight());
                playUiSound(1.0f);
                return true;
            }

            if (showSeasonsPage) {
                SereneSeasonsCompat.SeasonInfo seasonInfo = cachedSeasonInfo;
                int buttonsY = panelY + HEADER_HEIGHT + 30 + 57;
                int gap = 4;
                int buttonWidth = (PANEL_WIDTH - 36 - gap * 3) / 4;
                for (int i = 0; i < 4; i++) {
                    int buttonX = panelX + 18 + i * (buttonWidth + gap);
                    if (inside(x, y, buttonX, buttonsY, buttonWidth, 24)) {
                        if (!seasonInfo.available()) {
                            seasonActionMessage = "Serene Seasons не установлен";
                            seasonActionUntil = System.currentTimeMillis() + 1800L;
                        } else if (!serverCanEdit) {
                            seasonActionMessage = "Нужны права оператора (OP 2)";
                            seasonActionUntil = System.currentTimeMillis() + 1800L;
                        } else if (MinecraftClient.getInstance().getNetworkHandler() != null) {
                            ClientPlayNetworking.send(new SetSeasonPayload(i));
                            seasonActionMessage = "Запрос смены сезона отправлен";
                            seasonActionUntil = System.currentTimeMillis() + 1800L;
                        }
                        playUiSound(1.05f);
                        return true;
                    }
                }
                if (inside(x, y, panelX, panelY, PANEL_WIDTH, getPanelHeight())) return true;
                return false;
            }

            if (inside(x, y, panelX, panelY, PANEL_WIDTH - 64, HEADER_HEIGHT)) {
                draggingPanel = true;
                dragOffsetX = (int) mouseX - panelX;
                dragOffsetY = (int) mouseY - panelY;
                return true;
            }

            int sliderX = panelX + 18;
            int sliderY = panelY + HEADER_HEIGHT + 30 + 52;
            if (inside(x, y, sliderX - 8, sliderY - 8, SLIDER_WIDTH + 16, 24)) {
                draggingSlider = true;
                updateSliderFromMouse(x);
                playUiSound(1.0f);
                return true;
            }

            int presetY = panelY + HEADER_HEIGHT + 30 + 83 + 16;
            int gap = 4;
            int columns = 4;
            int buttonWidth = (SLIDER_WIDTH - gap * (columns - 1)) / columns;
            for (int i = 0; i < PRESET_TICKS.length; i++) {
                int column = i % columns;
                int row = i / columns;
                int buttonX = panelX + 18 + column * (buttonWidth + gap);
                int buttonY = presetY + row * (BUTTON_HEIGHT + 3);
                if (inside(x, y, buttonX, buttonY, buttonWidth, BUTTON_HEIGHT)) {
                    cycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(PRESET_TICKS[i]);
                    playUiSound(1.05f);
                    commitCurrentValue();
                    return true;
                }
            }

            if (inside(x, y, panelX, panelY, PANEL_WIDTH, getPanelHeight())) return true;
            return false;
        }

        if (action == GLFW.GLFW_RELEASE) {
            if (minimized && (miniPressCandidate || draggingMini)) {
                boolean shouldOpen = miniPressCandidate && !draggingMini;
                miniPressCandidate = false;
                draggingMini = false;
                if (shouldOpen) {
                    minimized = false;
                    panelX = miniX;
                    panelY = miniY;
                    clampPanel(MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight());
                    playUiSound(1.12f);
                }
                return true;
            }

            boolean wasDraggingPanel = draggingPanel;
            boolean wasDraggingSlider = draggingSlider;
            if (draggingSlider) {
                commitCurrentValue();
                playUiSound(1.08f);
            }
            draggingPanel = false;
            draggingSlider = false;
            return wasDraggingPanel || wasDraggingSlider;
        }

        return false;
    }

    public boolean handleMouseMove(double mouseX, double mouseY) {
        if (minimized && miniPressCandidate) {
            int dx = (int) mouseX - miniPressStartX;
            int dy = (int) mouseY - miniPressStartY;
            if (!draggingMini && dx * dx + dy * dy >= 9) {
                draggingMini = true;
            }
            if (draggingMini) {
                miniX = (int) mouseX - dragOffsetX;
                miniY = (int) mouseY - dragOffsetY;
                int width = MinecraftClient.getInstance().getWindow().getScaledWidth();
                int height = MinecraftClient.getInstance().getWindow().getScaledHeight();
                clampMini(width, height);
                return true;
            }
            return true;
        }

        if (draggingPanel) {
            panelX = (int) mouseX - dragOffsetX;
            panelY = (int) mouseY - dragOffsetY;
            int width = MinecraftClient.getInstance().getWindow().getScaledWidth();
            int height = MinecraftClient.getInstance().getWindow().getScaledHeight();
            clampPanel(width, height);
            miniX = panelX;
            miniY = panelY;
            clampMini(width, height);
            return true;
        }

        if (draggingSlider) {
            updateSliderFromMouse(toLogicalX(mouseX));
            return true;
        }

        return false;
    }

    public boolean handleMouseScroll(double mouseX, double mouseY, double verticalAmount) {
        if (minimized) return false;
        int x = toLogicalX(mouseX);
        int y = toLogicalY(mouseY);
        if (!isSliderHovered(x, y) || verticalAmount == 0.0) return false;

        long oldValue = cycleLengthTicks;
        int direction = verticalAmount > 0.0 ? 1 : -1;
        cycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(cycleLengthTicks + direction * STEP * 10L);
        if (oldValue != cycleLengthTicks) {
            int soundStep = (int) (cycleLengthTicks / (STEP * 10L));
            if (soundStep != lastSoundStep) {
                playUiSound(direction > 0 ? 1.28f : 1.18f);
                lastSoundStep = soundStep;
            }
            commitCurrentValue();
        }
        return true;
    }

    private void updateSliderFromMouse(int mouseX) {
        int sliderX = panelX + 18;
        double normalized = Math.max(0.0, Math.min(1.0, (mouseX - sliderX) / (double) SLIDER_WIDTH));
        double minLog = Math.log(MIN_TICKS);
        double maxLog = Math.log(MAX_TICKS);
        long value = Math.round(Math.exp(minLog + normalized * (maxLog - minLog)) / STEP) * STEP;
        long previous = cycleLengthTicks;
        cycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(value);
        if (previous != cycleLengthTicks) {
            int soundStep = (int) (cycleLengthTicks / (STEP * 10L));
            if (soundStep != lastSoundStep) {
                playUiSound(cycleLengthTicks > previous ? 1.22f : 1.16f);
                lastSoundStep = soundStep;
            }
        }
    }

    private void commitCurrentValue() {
        cycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(cycleLengthTicks);
        ModConfig.setCycleLengthTicks(cycleLengthTicks);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(new SetDayCycleLengthPayload((int) cycleLengthTicks));
        }
        savedUntil = System.currentTimeMillis() + 900L;
    }

    public void setCycleLengthFromServer(long ticks, boolean canEdit) {
        this.cycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(ticks);
        serverCanEdit = canEdit;
        this.serverSynced = true;
        this.savedUntil = System.currentTimeMillis() + 600L;
    }

    public static void updateCanEdit(boolean canEdit) {
        serverCanEdit = canEdit;
    }

    private boolean isSliderHovered(int mouseX, int mouseY) {
        int sliderX = panelX + 18;
        int sliderY = panelY + HEADER_HEIGHT + 30 + 52;
        return inside(mouseX, mouseY, sliderX - 8, sliderY - 8, SLIDER_WIDTH + 16, 24);
    }

    private int sliderXForValue(long ticks) {
        double minLog = Math.log(MIN_TICKS);
        double maxLog = Math.log(MAX_TICKS);
        double normalized = (Math.log(ticks) - minLog) / (maxLog - minLog);
        return panelX + 18 + (int) Math.round(normalized * SLIDER_WIDTH);
    }

    private long sliderMidpointTicks() {
        return Math.round(Math.sqrt((double) MIN_TICKS * (double) MAX_TICKS) / STEP) * STEP;
    }

    private String formatSpeed() {
        double seconds = cycleLengthTicks / 20.0;
        double ratio = 1200.0 / seconds;
        return String.format(java.util.Locale.ROOT, "%.2f×", ratio);
    }

    private static String formatDuration(long ticks) {
        long totalSeconds = ticks / 20L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) return hours + " ч " + minutes + " мин " + seconds + " с";
        if (minutes > 0L) return minutes + " мин " + seconds + " с";
        return seconds + " с";
    }

    private static String formatDurationForLabel(long ticks) {
        long totalSeconds = Math.max(1L, ticks / 20L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) return hours + " ч" + (minutes > 0L ? " " + minutes + " мин" : "");
        if (minutes > 0L) return minutes + " мин";
        return seconds + " с";
    }

    private static String formatMinecraftTime(long ticks) {
        long shifted = Math.floorMod(ticks + 6000L, 24000L);
        long hours = shifted / 1000L;
        long minutes = (shifted % 1000L) * 60L / 1000L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private void playUiSound(float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 0.38f, pitch));
    }

    private int toLogicalX(double mouseX) {
        return panelX + (int) Math.round((mouseX - panelX) / UI_SCALE);
    }

    private int toLogicalY(double mouseY) {
        return panelY + (int) Math.round((mouseY - panelY) / UI_SCALE);
    }

    private int visualPanelWidth() {
        return Math.max(1, Math.round(PANEL_WIDTH * UI_SCALE));
    }

    private int visualPanelHeight() {
        return Math.max(1, Math.round(getPanelHeight() * UI_SCALE));
    }

    private int getPanelHeight() {
        return showSeasonsPage ? SEASON_PANEL_HEIGHT : DAY_PANEL_HEIGHT;
    }

    private void clampPanel(int width, int height) {
        panelX = Math.max(6, Math.min(panelX, width - visualPanelWidth() - 6));
        panelY = Math.max(6, Math.min(panelY, height - visualPanelHeight() - 6));
    }

    private void clampMini(int width, int height) {
        miniX = Math.max(4, Math.min(miniX, width - MINI_PANEL_SIZE - 4));
        miniY = Math.max(4, Math.min(miniY, height - MINI_PANEL_SIZE - 4));
    }

    private boolean isMiniHovered(double mouseX, double mouseY) {
        return inside(mouseX, mouseY,
                miniX - MINI_HITBOX_PADDING,
                miniY - MINI_HITBOX_PADDING,
                MINI_PANEL_SIZE + MINI_HITBOX_PADDING * 2,
                MINI_PANEL_SIZE + MINI_HITBOX_PADDING * 2);
    }

    private static void drawCenteredText(DrawContext context, Text text, int centerX, int y, int color) {
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, text, centerX, y, color);
    }

    private static void drawRightText(DrawContext context, Text text, int rightX, int y, int color) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        context.drawTextWithShadow(renderer, text, rightX - renderer.getWidth(text), y, color);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ACTIVE == this) {
            ACTIVE = null;
        }
        draggingPanel = false;
        draggingSlider = false;
        draggingMini = false;
        miniPressCandidate = false;
        if (client.world != null) {
            client.mouse.lockCursor();
        }
    }
}
