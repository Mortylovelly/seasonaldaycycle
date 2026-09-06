package com.seasonaldaycycle.client;

import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.network.SetDayCycleLengthPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class DayCycleScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 252;
    private static final int HEADER_HEIGHT = 34;
    private static final int SLIDER_WIDTH = 218;
    private static final int SLIDER_HEIGHT = 7;
    private static final int BUTTON_HEIGHT = 24;
    private static final int CLOCK_CARD_WIDTH = 104;
    private static final int CLOCK_CARD_HEIGHT = 126;

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
    private int lastSoundStep = Integer.MIN_VALUE;

    public DayCycleScreen(Screen parent) {
        super(Text.literal("Настройки суток"));
        this.parent = parent;
        this.cycleLengthTicks = ModConfig.getCycleLengthTicks();
    }

    @Override
    protected void init() {
        if (panelX == 0 && panelY == 0) {
            panelX = (this.width - PANEL_WIDTH) / 2;
            panelY = Math.max(8, (this.height - PANEL_HEIGHT) / 2);
        }
        clampPanel();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.applyBlur(delta);
        context.fill(0, 0, this.width, this.height, 0x52000000);
        drawAmbientLeaves(context);
        drawPanel(context);

        int left = panelX + 18;
        int right = panelX + PANEL_WIDTH - 18;
        int contentTop = panelY + HEADER_HEIGHT + 12;

        drawText(context, this.title, left, panelY + 11, 0xFFFFFFFF);
        drawText(context, Text.literal("скорость времени"), left, panelY + 23, 0xFF8E959F);
        drawCloseButton(context, mouseX, mouseY);

        drawText(context, Text.literal("ПОЛНЫЕ СУТКИ"), left, contentTop, 0xFF9BA2AD);
        drawCenteredText(context, Text.literal(formatDuration(cycleLengthTicks)), left + 109, contentTop + 16, 0xFFFFFFFF);
        drawCenteredText(context, Text.literal(cycleLengthTicks + " тиков"), left + 109, contentTop + 31, 0xFF777F8A);

        int sliderX = left;
        int sliderY = contentTop + 52;
        drawSlider(context, sliderX, sliderY, mouseX, mouseY);
        drawText(context, Text.literal("1 мин"), sliderX, sliderY + 13, 0xFF737B86);
        drawCenteredText(context, Text.literal("3 ч"), sliderX + SLIDER_WIDTH / 2, sliderY + 13, 0xFF737B86);
        drawRightText(context, Text.literal("6 ч"), sliderX + SLIDER_WIDTH, sliderY + 13, 0xFF737B86);

        int presetY = contentTop + 83;
        drawText(context, Text.literal("БЫСТРЫЙ ВЫБОР"), left, presetY, 0xFF9BA2AD);
        drawPresetSection(context, left, presetY + 16, mouseX, mouseY);

        drawClockCard(context, right - CLOCK_CARD_WIDTH, contentTop, CLOCK_CARD_WIDTH, CLOCK_CARD_HEIGHT);

        int infoY = panelY + PANEL_HEIGHT - 34;
        drawText(context, Text.literal("Скорость: " + formatSpeed()), left, infoY, 0xFFC7CDD5);
        drawRightText(context, Text.literal("Пропорции сезонов сохраняются"), right, infoY, 0xFF6F7782);

        long now = System.currentTimeMillis();
        if (savedUntil > now) {
            drawRightText(context, Text.literal("✓ Сохранено"), right, infoY - 14, 0xFFB8F2C0);
        } else if (!serverSynced && this.client != null && this.client.getNetworkHandler() != null) {
            drawRightText(context, Text.literal("Синхронизация..."), right, infoY - 14, 0xFFFFD580);
        }
    }

    private void drawAmbientLeaves(DrawContext context) {
        long tick = System.currentTimeMillis() / 85L;
        for (int i = 0; i < 12; i++) {
            double phase = i * 2.37;
            double speed = 0.006 + (i % 3) * 0.0012;
            double x = Math.floorMod((long) (i * 83 + tick * (8 + i % 4)), Math.max(1, this.width + 80)) - 40.0;
            double y = Math.floorMod((long) (i * 47 + tick * (5 + i % 3)), Math.max(1, this.height + 90)) - 45.0;
            x += Math.sin(tick * speed + phase) * 12.0;
            y += Math.cos(tick * speed * 0.8 + phase) * 6.0;

            context.getMatrices().push();
            context.getMatrices().translate(x, y, 0.0f);
            context.getMatrices().scale(0.55f, 0.55f, 1.0f);
            context.drawItem(new ItemStack(Items.OAK_LEAVES), 0, 0);
            context.getMatrices().pop();
        }
    }

    private void drawPanel(DrawContext context) {
        context.fill(panelX + 3, panelY + 5, panelX + PANEL_WIDTH + 3, panelY + PANEL_HEIGHT + 5, 0x3B000000);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xEC16181C);
        context.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + HEADER_HEIGHT, 0xF522252A);

        context.fill(panelX + 1, panelY + HEADER_HEIGHT, panelX + PANEL_WIDTH - 1, panelY + HEADER_HEIGHT + 1, 0xFF343940);
        context.fill(panelX + 1, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH - 1, panelY + PANEL_HEIGHT, 0xFF30343A);
        context.fill(panelX + 1, panelY + 1, panelX + 2, panelY + PANEL_HEIGHT - 1, 0xFF30343A);
        context.fill(panelX + PANEL_WIDTH - 2, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + PANEL_HEIGHT - 1, 0xFF30343A);

        context.fill(panelX + 12, panelY + HEADER_HEIGHT + 7, panelX + PANEL_WIDTH - 12, panelY + HEADER_HEIGHT + 8, 0x12FFFFFF);
    }

    private void drawCloseButton(DrawContext context, int mouseX, int mouseY) {
        int x = panelX + PANEL_WIDTH - 29;
        int y = panelY + 6;
        boolean hover = inside(mouseX, mouseY, x, y, 22, 22);
        context.fill(x, y, x + 22, y + 22, hover ? 0xFF393D44 : 0xFF292C31);
        context.drawCenteredTextWithShadow(this.textRenderer, "×", x + 11, y + 2, hover ? 0xFFFFFFFF : 0xFFE3E7EB);
    }

    private void drawSlider(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int trackY = y + 2;
        context.fill(x, trackY, x + SLIDER_WIDTH, trackY + SLIDER_HEIGHT, 0xFF30343A);
        int knobX = sliderXForValue(cycleLengthTicks);
        context.fill(x, trackY, knobX, trackY + SLIDER_HEIGHT, 0xFF7D858F);

        boolean hovered = isSliderHovered(mouseX, mouseY);
        context.fill(knobX - 5, y - 2, knobX + 5, y + 11, hovered ? 0xFFFFFFFF : 0xFFD8DCE1);
        context.fill(knobX - 2, y + 1, knobX + 3, y + 8, 0xFF626870);
    }

    private void drawPresetSection(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int gap = 4;
        int buttonWidth = (SLIDER_WIDTH - gap * 4) / 5;

        for (int i = 0; i < PRESET_TICKS.length; i++) {
            int buttonX = x + i * (buttonWidth + gap);
            boolean hover = inside(mouseX, mouseY, buttonX, y, buttonWidth, BUTTON_HEIGHT);
            boolean selected = cycleLengthTicks == PRESET_TICKS[i];

            int color = selected ? 0xFF59616B : (hover ? 0xFF383D44 : 0xFF292C32);
            context.fill(buttonX, y, buttonX + buttonWidth, y + BUTTON_HEIGHT, color);
            context.fill(buttonX, y, buttonX + buttonWidth, y + 1, selected ? 0xFFC5CBD2 : 0xFF363A40);
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    PRESET_LABELS[i],
                    buttonX + buttonWidth / 2,
                    y + 7,
                    0xFFF0F2F4
            );
        }
    }

    private void drawClockCard(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, 0x30111418);
        context.fill(x, y, x + width, y + 1, 0xFF414750);
        context.fill(x, y + height - 1, x + width, y + height, 0xFF30343A);
        context.fill(x + 1, y + 1, x + width - 1, y + 2, 0x1AFFFFFF);

        drawCenteredText(context, Text.literal("СЕЙЧАС"), x + width / 2, y + 10, 0xFFD8DDE3);

        int clockX = x + 25;
        int clockY = y + 31;
        context.fill(clockX - 10, clockY - 10, clockX + 42, clockY + 42, 0x18FFFFFF);

        context.getMatrices().push();
        context.getMatrices().translate(clockX + 8, clockY + 8, 0.0f);
        context.getMatrices().scale(1.75f, 1.75f, 1.0f);
        context.drawItem(new ItemStack(Items.CLOCK), -8, -8);
        context.getMatrices().pop();

        if (this.client != null && this.client.world != null) {
            long time = Math.floorMod(this.client.world.getTimeOfDay(), 24000L);
            drawCenteredText(context, Text.literal(formatMinecraftTime(time)), x + width / 2, y + 82, 0xFFFFFFFF);
            drawCenteredText(
                    context,
                    Text.literal(time < 12000L ? "День" : "Ночь"),
                    x + width / 2,
                    y + 98,
                    time < 12000L ? 0xFFFFD37C : 0xFFB8C8FF
            );
        }

        drawCenteredText(context, Text.literal("ванильные часы"), x + width / 2, y + height - 17, 0xFF747C87);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);

        int closeX = panelX + PANEL_WIDTH - 29;
        int closeY = panelY + 6;
        if (inside(mouseX, mouseY, closeX, closeY, 22, 22)) {
            playUiSound(1.15f);
            close();
            return true;
        }

        if (inside(mouseX, mouseY, panelX, panelY, PANEL_WIDTH - 38, HEADER_HEIGHT)) {
            draggingPanel = true;
            dragOffsetX = (int) mouseX - panelX;
            dragOffsetY = (int) mouseY - panelY;
            return true;
        }

        int sliderX = panelX + 18;
        int sliderY = panelY + HEADER_HEIGHT + 12 + 52;
        if (inside(mouseX, mouseY, sliderX - 8, sliderY - 8, SLIDER_WIDTH + 16, 24)) {
            draggingSlider = true;
            updateSliderFromMouse(mouseX);
            playUiSound(1.0f);
            return true;
        }

        int presetY = panelY + HEADER_HEIGHT + 12 + 83 + 16;
        int gap = 4;
        int buttonWidth = (SLIDER_WIDTH - gap * 4) / 5;
        for (int i = 0; i < PRESET_TICKS.length; i++) {
            int x = panelX + 18 + i * (buttonWidth + gap);
            if (inside(mouseX, mouseY, x, presetY, buttonWidth, BUTTON_HEIGHT)) {
                cycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(PRESET_TICKS[i]);
                playUiSound(1.05f);
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
                playUiSound(1.08f);
            }
            draggingPanel = false;
            draggingSlider = false;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isSliderHovered((int) mouseX, (int) mouseY) || verticalAmount == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

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

    @Override
    public void tick() {
        super.tick();
    }

    private void updateSliderFromMouse(double mouseX) {
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
        int sliderX = panelX + 18;
        int sliderY = panelY + HEADER_HEIGHT + 12 + 52;
        return inside(mouseX, mouseY, sliderX - 8, sliderY - 8, SLIDER_WIDTH + 16, 24);
    }

    private int sliderXForValue(long ticks) {
        double minLog = Math.log(MIN_TICKS);
        double maxLog = Math.log(MAX_TICKS);
        double normalized = (Math.log(ticks) - minLog) / (maxLog - minLog);
        return panelX + 18 + (int) Math.round(normalized * SLIDER_WIDTH);
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

    private void playUiSound(float pitch) {
        MinecraftClient client = this.client;
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 0.38f, pitch));
        }
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
