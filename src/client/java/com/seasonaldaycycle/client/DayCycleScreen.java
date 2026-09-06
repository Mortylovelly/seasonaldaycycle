package com.seasonaldaycycle.client;

import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.network.SetDayCycleLengthPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class DayCycleScreen extends Screen {
    private static final int PANEL_WIDTH = 460;
    private static final int PANEL_HEIGHT = 320;
    private static final int HEADER_HEIGHT = 42;
    private static final int SLIDER_WIDTH = 286;
    private static final int SLIDER_HEIGHT = 8;
    private static final int BUTTON_HEIGHT = 28;

    private static final long MIN_TICKS = ModConfig.MIN_CYCLE_LENGTH_TICKS;
    private static final long MAX_TICKS = ModConfig.MAX_CYCLE_LENGTH_TICKS;
    private static final long STEP = ModConfig.CYCLE_LENGTH_STEP_TICKS;

    private static final long[] PRESET_TICKS = {
            20L * 60L * 20L,
            30L * 60L * 20L,
            45L * 60L * 20L,
            60L * 60L * 20L,
            120L * 60L * 20L
    };

    private static final String[] PRESET_LABELS = {
            "20 мин",
            "30 мин",
            "45 мин",
            "60 мин",
            "2 часа"
    };

    private final Screen parent;
    private long cycleLengthTicks;
    private int panelX;
    private int panelY;
    private boolean draggingPanel;
    private boolean draggingSlider;
    private int dragOffsetX;
    private int dragOffsetY;
    private long savedUntil;
    private boolean serverSynced;

    public DayCycleScreen(Screen parent) {
        super(Text.literal("Настройки суток"));
        this.parent = parent;
        this.cycleLengthTicks = ModConfig.getCycleLengthTicks();
    }

    @Override
    protected void init() {
        if (panelX == 0 && panelY == 0) {
            panelX = (this.width - PANEL_WIDTH) / 2;
            panelY = Math.max(12, (this.height - PANEL_HEIGHT) / 2);
        }
        clampPanel();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.applyBlur(delta);
        context.fill(0, 0, this.width, this.height, 0x66000000);

        drawPanel(context);
        drawText(context, this.title, panelX + 18, panelY + 14, 0xFFFFFFFF);
        drawCloseButton(context, mouseX, mouseY);

        int left = panelX + 22;
        int right = panelX + PANEL_WIDTH - 22;
        int sectionTop = panelY + HEADER_HEIGHT + 16;

        drawText(context, Text.literal("Длина полных суток"), left, sectionTop, 0xFFE5E7EB);
        drawCenteredText(context, Text.literal(formatDuration(cycleLengthTicks)), left + 143, sectionTop + 22, 0xFFFFFFFF);
        drawCenteredText(context, Text.literal("Реальных тиков: " + cycleLengthTicks), left + 143, sectionTop + 39, 0xFF9CA3AF);

        int sliderX = left;
        int sliderY = sectionTop + 62;
        drawSlider(context, sliderX, sliderY, mouseX, mouseY);

        drawText(context, Text.literal("1 мин"), sliderX - 1, sliderY + 14, 0xFF8F96A3);
        drawCenteredText(context, Text.literal("3 ч"), sliderX + SLIDER_WIDTH / 2, sliderY + 14, 0xFF8F96A3);
        drawRightText(context, Text.literal("6 ч"), sliderX + SLIDER_WIDTH, sliderY + 14, 0xFF8F96A3);

        drawPresetSection(context, left, sectionTop + 100, mouseX, mouseY);
        drawClockCard(context, right - 138, sectionTop + 100, 138, 126);

        int infoY = sectionTop + 238;
        drawText(context, Text.literal("Скорость относительно ванили: " + formatSpeed()), left, infoY, 0xFFC5CBD5);
        drawText(context, Text.literal("Смена сезона сохраняет пропорции дня и ночи."), left, infoY + 18, 0xFF818895);

        long now = System.currentTimeMillis();
        if (savedUntil > now) {
            drawRightText(context, Text.literal("✓ Сохранено"), right, infoY + 18, 0xFFB8F2C0);
        } else if (!serverSynced && this.client != null && this.client.getNetworkHandler() != null) {
            drawRightText(context, Text.literal("Синхронизация..."), right, infoY + 18, 0xFFFFD580);
        }
    }

    private void drawPanel(DrawContext context) {
        context.fill(panelX + 4, panelY + 6, panelX + PANEL_WIDTH + 4, panelY + PANEL_HEIGHT + 6, 0x33000000);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xEE191B1F);
        context.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + HEADER_HEIGHT, 0xF525272C);
        context.fill(panelX + 1, panelY + HEADER_HEIGHT, panelX + PANEL_WIDTH - 1, panelY + HEADER_HEIGHT + 1, 0xFF30343B);
        context.fill(panelX + 1, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH - 1, panelY + PANEL_HEIGHT, 0xFF30343B);
        context.fill(panelX + 1, panelY + 1, panelX + 2, panelY + PANEL_HEIGHT - 1, 0xFF30343B);
        context.fill(panelX + PANEL_WIDTH - 2, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + PANEL_HEIGHT - 1, 0xFF30343B);
    }

    private void drawCloseButton(DrawContext context, int mouseX, int mouseY) {
        int x = panelX + PANEL_WIDTH - 34;
        int y = panelY + 9;
        boolean hover = inside(mouseX, mouseY, x, y, 24, 24);
        context.fill(x, y, x + 24, y + 24, hover ? 0xFF34383F : 0xFF292C31);
        context.drawCenteredTextWithShadow(this.textRenderer, "×", x + 12, y + 3, 0xFFE8EBEF);
    }

    private void drawSlider(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int trackY = y + 1;
        context.fill(x, trackY, x + SLIDER_WIDTH, trackY + SLIDER_HEIGHT, 0xFF343840);
        int knobX = sliderXForValue(cycleLengthTicks);
        context.fill(x, trackY, knobX, trackY + SLIDER_HEIGHT, 0xFF8E959F);
        context.fill(knobX - 5, y - 3, knobX + 5, y + 11, isSliderHovered(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFD3D7DD);
        context.fill(knobX - 2, y, knobX + 3, y + 8, 0xFF5D626B);
    }

    private void drawPresetSection(DrawContext context, int x, int y, int mouseX, int mouseY) {
        drawText(context, Text.literal("Быстрый выбор"), x, y, 0xFFE5E7EB);
        int buttonY = y + 22;
        int gap = 5;
        int buttonWidth = (286 - gap * 4) / 5;

        for (int i = 0; i < PRESET_TICKS.length; i++) {
            int buttonX = x + i * (buttonWidth + gap);
            boolean hover = inside(mouseX, mouseY, buttonX, buttonY, buttonWidth, BUTTON_HEIGHT);
            boolean selected = cycleLengthTicks == PRESET_TICKS[i];
            int color = selected ? 0xFF5C6470 : (hover ? 0xFF3A3F47 : 0xFF2B2E34);
            context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + BUTTON_HEIGHT, color);
            context.drawCenteredTextWithShadow(this.textRenderer, PRESET_LABELS[i], buttonX + buttonWidth / 2, buttonY + 9, 0xFFF1F3F5);
        }
    }

    private void drawClockCard(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, 0x33111418);
        context.fill(x, y, x + width, y + 1, 0xFF30343B);
        context.fill(x, y + height - 1, x + width, y + height, 0xFF30343B);

        drawText(context, Text.literal("Текущее время"), x + 12, y + 10, 0xFFDDE1E7);

        int clockX = x + 18;
        int clockY = y + 30;
        context.fill(clockX - 6, clockY - 6, clockX + 28, clockY + 28, 0x22FFFFFF);
        context.drawItem(new ItemStack(Items.CLOCK), clockX, clockY);

        if (this.client != null && this.client.world != null) {
            long time = Math.floorMod(this.client.world.getTimeOfDay(), 24000L);
            drawText(context, Text.literal(formatMinecraftTime(time)), x + 57, y + 39, 0xFFFFFFFF);
            drawText(context, Text.literal(time < 12000L ? "День" : "Ночь"), x + 57, y + 56,
                    time < 12000L ? 0xFFFFD37C : 0xFFB8C8FF);
        }

        drawText(context, Text.literal("как в Minecraft"), x + 57, y + 74, 0xFF818895);
        drawText(context, Text.literal("обновляется в реальном времени"), x + 12, y + 99, 0xFF737A86);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);

        int closeX = panelX + PANEL_WIDTH - 34;
        int closeY = panelY + 9;
        if (inside(mouseX, mouseY, closeX, closeY, 24, 24)) {
            close();
            return true;
        }

        if (inside(mouseX, mouseY, panelX, panelY, PANEL_WIDTH - 46, HEADER_HEIGHT)) {
            draggingPanel = true;
            dragOffsetX = (int) mouseX - panelX;
            dragOffsetY = (int) mouseY - panelY;
            return true;
        }

        int sliderX = panelX + 22;
        int sliderY = panelY + HEADER_HEIGHT + 16 + 62;
        if (inside(mouseX, mouseY, sliderX - 8, sliderY - 8, SLIDER_WIDTH + 16, 24)) {
            draggingSlider = true;
            updateSliderFromMouse(mouseX);
            return true;
        }

        int presetY = panelY + HEADER_HEIGHT + 16 + 100 + 22;
        int gap = 5;
        int buttonWidth = (286 - gap * 4) / 5;
        for (int i = 0; i < PRESET_TICKS.length; i++) {
            int x = panelX + 22 + i * (buttonWidth + gap);
            if (inside(mouseX, mouseY, x, presetY, buttonWidth, BUTTON_HEIGHT)) {
                cycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(PRESET_TICKS[i]);
                commitCurrentValue();
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        if (draggingPanel) {
            panelX = (int) mouseX - dragOffsetX;
            panelY = (int) mouseY - dragOffsetY;
            clampPanel();
            return true;
        }

        if (draggingSlider) {
            updateSliderFromMouse(mouseX);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (draggingSlider) {
                commitCurrentValue();
            }
            draggingPanel = false;
            draggingSlider = false;
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
    }

    private void updateSliderFromMouse(double mouseX) {
        int sliderX = panelX + 22;
        double normalized = Math.max(0.0, Math.min(1.0, (mouseX - sliderX) / (double) SLIDER_WIDTH));
        double minLog = Math.log(MIN_TICKS);
        double maxLog = Math.log(MAX_TICKS);
        long value = Math.round(Math.exp(minLog + normalized * (maxLog - minLog)) / STEP) * STEP;
        cycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(value);
    }

    private void commitCurrentValue() {
        cycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(cycleLengthTicks);
        ModConfig.setCycleLengthTicks(cycleLengthTicks);
        if (this.client != null && this.client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(new SetDayCycleLengthPayload((int) cycleLengthTicks));
        }
        savedUntil = System.currentTimeMillis() + 900L;
    }

    public void setCycleLengthFromServer(long ticks) {
        this.cycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(ticks);
        this.serverSynced = true;
        this.savedUntil = System.currentTimeMillis() + 600L;
    }

    private boolean isSliderHovered(int mouseX, int mouseY) {
        int sliderX = panelX + 22;
        int sliderY = panelY + HEADER_HEIGHT + 16 + 62;
        return inside(mouseX, mouseY, sliderX - 8, sliderY - 8, SLIDER_WIDTH + 16, 24);
    }

    private int sliderXForValue(long ticks) {
        double minLog = Math.log(MIN_TICKS);
        double maxLog = Math.log(MAX_TICKS);
        double normalized = (Math.log(ticks) - minLog) / (maxLog - minLog);
        return panelX + 22 + (int) Math.round(normalized * SLIDER_WIDTH);
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

    private static String formatMinecraftTime(long ticks) {
        long shifted = Math.floorMod(ticks + 6000L, 24000L);
        long hours = shifted / 1000L;
        long minutes = (shifted % 1000L) * 60L / 1000L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private void drawText(DrawContext context, Text text, int x, int y, int color) {
        context.drawTextWithShadow(this.textRenderer, text, x, y, color);
    }

    private void drawCenteredText(DrawContext context, Text text, int centerX, int y, int color) {
        context.drawCenteredTextWithShadow(this.textRenderer, text, centerX, y, color);
    }

    private void drawRightText(DrawContext context, Text text, int rightX, int y, int color) {
        context.drawTextWithShadow(this.textRenderer, text, rightX - this.textRenderer.getWidth(text), y, color);
    }

    private void clampPanel() {
        panelX = Math.max(6, Math.min(panelX, this.width - PANEL_WIDTH - 6));
        panelY = Math.max(6, Math.min(panelY, this.height - PANEL_HEIGHT - 6));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
