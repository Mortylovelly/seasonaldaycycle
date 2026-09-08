package com.seasonaldaycycle.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class VertexNavigationPanel {
    private static final int WIDTH_MAIN = 400;
    private static final int HEIGHT_MAIN = 280;
    private static final int WIDTH_PAGE = 360;
    private static final int HEIGHT_PAGE = 252;
    private static final float UI_SCALE = 0.40f;
    private static final int HEADER_HEIGHT = 34;
    private static final int MINI_SIZE = 42;
    private static final int MINI_PADDING = 8;

    private static final int COLOR_PANEL = 0x8A666B73;
    private static final int COLOR_HEADER = 0x9B777C84;
    private static final int COLOR_LINE = 0xAABDC3CB;

    private final boolean worldPage;
    private int panelX;
    private int panelY;
    private int miniX;
    private int miniY;
    private boolean initialized;
    private boolean minimized;
    private boolean draggingPanel;
    private boolean draggingMini;
    private boolean miniPressCandidate;
    private int dragOffsetX;
    private int dragOffsetY;
    private int miniPressStartX;
    private int miniPressStartY;

    public VertexNavigationPanel(boolean worldPage, boolean minimized) {
        this.worldPage = worldPage;
        this.minimized = minimized;
    }

    public void setMiniPosition(int x, int y) {
        if (x >= 0 && y >= 0) {
            this.miniX = x;
            this.miniY = y;
            this.initialized = false;
        }
    }

    public boolean hasMiniPosition() {
        return miniX >= 0 && miniY >= 0;
    }

    public int getMiniX() {
        return miniX;
    }

    public int getMiniY() {
        return miniY;
    }

    public void renderOverlay(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        ensureInitialized(width, height);

        if (minimized) {
            renderMinimized(context, mouseX, mouseY);
            return;
        }

        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();

        context.getMatrices().push();
        context.getMatrices().translate(panelX * (1.0f - UI_SCALE), panelY * (1.0f - UI_SCALE), 0.0f);
        context.getMatrices().scale(UI_SCALE, UI_SCALE, 1.0f);

        drawPanelGlass(context, panelWidth, panelHeight);
        drawHeader(context, mouseX, mouseY, panelWidth);

        if (worldPage) {
            drawWorldPage(context);
        } else {
            drawMainPage(context);
        }

        context.getMatrices().pop();
    }

    public boolean handleMouseButton(double mouseX, double mouseY, int button, int action) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ensureInitialized(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());

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
            int width = getPanelWidth();
            int height = getPanelHeight();

            if (inside(x, y, panelX + width - 29, panelY + 6, 22, 22)) {
                VertexPanelController.close(client);
                return true;
            }

            if (inside(x, y, panelX + width - 57, panelY + 6, 22, 22)) {
                minimized = true;
                draggingPanel = false;
                draggingMini = false;
                miniPressCandidate = false;
                clampMini(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
                return true;
            }

            if (worldPage && inside(x, y, panelX + 10, panelY + 7, 30, 22)) {
                VertexPanelController.openMain(client);
                return true;
            }

            if (!worldPage) {
                int cardX = panelX + 30;
                int cardY = panelY + 92;
                if (inside(x, y, cardX, cardY, width - 60, 76)) {
                    VertexPanelController.openWorld(client);
                    return true;
                }
            } else {
                int cardX = panelX + 18;
                int cardY = panelY + 68;
                if (inside(x, y, cardX, cardY, width - 36, 70)) {
                    VertexPanelController.openTime(client);
                    return true;
                }
            }

            if (inside(x, y, panelX, panelY, width, height)) {
                if (inside(x, y, panelX, panelY, width - 64, HEADER_HEIGHT)) {
                    draggingPanel = true;
                    dragOffsetX = (int) mouseX - panelX;
                    dragOffsetY = (int) mouseY - panelY;
                }
                return true;
            }
            return false;
        }

        if (action == GLFW.GLFW_RELEASE) {
            if (minimized && (miniPressCandidate || draggingMini)) {
                boolean open = miniPressCandidate && !draggingMini;
                miniPressCandidate = false;
                draggingMini = false;
                if (open) {
                    minimized = false;
                }
                return true;
            }

            boolean dragged = draggingPanel;
            draggingPanel = false;
            return dragged;
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
                clampMini(MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight());
            }
            return true;
        }

        if (draggingPanel) {
            panelX = (int) mouseX - dragOffsetX;
            panelY = (int) mouseY - dragOffsetY;
            clampPanel(MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight());
            miniX = panelX;
            miniY = panelY;
            clampMini(MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight());
            return true;
        }

        return false;
    }

    private void ensureInitialized(int width, int height) {
        if (!initialized) {
            int panelWidth = getPanelWidth();
            int panelHeight = getPanelHeight();
            if (panelX == 0 && panelY == 0) {
                panelX = (width - Math.round(panelWidth * UI_SCALE)) / 2;
                panelY = Math.max(6, (height - Math.round(panelHeight * UI_SCALE)) / 2);
            }
            if (miniX < 0 || miniY < 0) {
                miniX = panelX;
                miniY = panelY;
            }
            clampPanel(width, height);
            clampMini(width, height);
            initialized = true;
        }
    }

    private int getPanelWidth() {
        return worldPage ? WIDTH_PAGE : WIDTH_MAIN;
    }

    private int getPanelHeight() {
        return worldPage ? HEIGHT_PAGE : HEIGHT_MAIN;
    }

    private void drawPanelGlass(DrawContext context, int width, int height) {
        context.fill(panelX + 3, panelY + 5, panelX + width + 3, panelY + height + 5, 0x32000000);
        context.fill(panelX, panelY, panelX + width, panelY + height, COLOR_PANEL);
        context.fill(panelX + 1, panelY + 1, panelX + width - 1, panelY + HEADER_HEIGHT, COLOR_HEADER);
        context.fill(panelX + 1, panelY + HEADER_HEIGHT, panelX + width - 1, panelY + HEADER_HEIGHT + 1, COLOR_LINE);
        context.fill(panelX + 1, panelY + height - 1, panelX + width - 1, panelY + height, 0x8A949AA3);
        context.fill(panelX + 1, panelY + 1, panelX + 2, panelY + height - 1, 0x8A949AA3);
        context.fill(panelX + width - 2, panelY + 1, panelX + width - 1, panelY + height - 1, 0x8A949AA3);
    }

    private void drawHeader(DrawContext context, int mouseX, int mouseY, int width) {
        MinecraftClient client = MinecraftClient.getInstance();
        var renderer = client.textRenderer;

        if (worldPage) {
            context.drawTextWithShadow(renderer, Text.literal("‹"), panelX + 13, panelY + 8, 0xFFE9EDF1);
            context.drawTextWithShadow(renderer, Text.literal("WORLD"), panelX + 43, panelY + 11, 0xFFFFFFFF);
            context.drawTextWithShadow(renderer, Text.literal("мир"), panelX + 43, panelY + 23, 0xFFC0C6CD);
        } else {
            context.drawTextWithShadow(renderer, Text.literal("Vertex"), panelX + 18, panelY + 9, 0xFFFFFFFF);
            context.drawTextWithShadow(renderer, Text.literal("Universal utility"), panelX + 18, panelY + 22, 0xFFC0C6CD);
        }

        int minimizeX = panelX + width - 57;
        int closeX = panelX + width - 29;
        boolean minHover = inside(mouseX, mouseY, minimizeX, panelY + 6, 22, 22);
        boolean closeHover = inside(mouseX, mouseY, closeX, panelY + 6, 22, 22);
        context.fill(minimizeX, panelY + 6, minimizeX + 22, panelY + 28, minHover ? 0xCC4A4F57 : 0x88464A51);
        context.drawCenteredTextWithShadow(renderer, "−", minimizeX + 11, panelY + 8, minHover ? 0xFFFFFFFF : 0xFFE8EBEE);
        context.fill(closeX, panelY + 6, closeX + 22, panelY + 28, closeHover ? 0xCC4A4F57 : 0x88464A51);
        context.drawCenteredTextWithShadow(renderer, "×", closeX + 11, panelY + 8, closeHover ? 0xFFFFFFFF : 0xFFE8EBEE);
    }

    private void drawMainPage(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        var renderer = client.textRenderer;
        int left = panelX + 30;
        int cardY = panelY + 92;
        int cardWidth = getPanelWidth() - 60;

        context.drawTextWithShadow(renderer, Text.literal("TOOLS"), left, panelY + 60, 0xFFD0D5DB);
        context.fill(left, cardY, left + cardWidth, cardY + 76, 0x77444A52);
        context.fill(left, cardY, left + cardWidth, cardY + 1, 0x889AA1A9);

        drawItemIcon(context, new ItemStack(Items.COMPASS), left + 17, cardY + 20, 2.0f);
        context.drawTextWithShadow(renderer, Text.literal("WORLD"), left + 65, cardY + 18, 0xFFFFFFFF);
        context.drawTextWithShadow(renderer, Text.literal("Мир, время, погода и другие инструменты"), left + 65, cardY + 35, 0xFFC0C6CD);
        context.drawTextWithShadow(renderer, Text.literal("Открыть"), left + 65, cardY + 52, 0xFFE1E5E9);
    }

    private void drawWorldPage(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        var renderer = client.textRenderer;
        int left = panelX + 18;
        int cardY = panelY + 68;
        int cardWidth = getPanelWidth() - 36;

        context.drawTextWithShadow(renderer, Text.literal("WORLD TOOLS"), left, panelY + 48, 0xFFD0D5DB);

        context.fill(left, cardY, left + cardWidth, cardY + 70, 0x77444A52);
        context.fill(left, cardY, left + cardWidth, cardY + 1, 0x889AA1A9);
        drawItemIcon(context, new ItemStack(Items.CLOCK), left + 16, cardY + 18, 1.7f);
        context.drawTextWithShadow(renderer, Text.literal("TIME AND WEATHER"), left + 58, cardY + 16, 0xFFFFFFFF);
        context.drawTextWithShadow(renderer, Text.literal("Время суток, длительность, погода и сезонные параметры"), left + 58, cardY + 34, 0xFFC0C6CD);
        context.drawTextWithShadow(renderer, Text.literal("Открыть настройки"), left + 58, cardY + 49, 0xFFE1E5E9);

        context.drawTextWithShadow(renderer, Text.literal("Другие инструменты мира появятся здесь"), left, cardY + 91, 0xFF929AA4);
    }

    private void renderMinimized(DrawContext context, int mouseX, int mouseY) {
        boolean hover = isMiniHovered(mouseX, mouseY);
        context.fill(miniX, miniY, miniX + MINI_SIZE, miniY + MINI_SIZE,
                hover ? 0x664F5964 : 0x443F4851);
        context.fill(miniX, miniY, miniX + MINI_SIZE, miniY + 1,
                hover ? 0xCCCDD3D9 : 0x667F8790);
        context.fill(miniX, miniY + MINI_SIZE - 1, miniX + MINI_SIZE, miniY + MINI_SIZE,
                0x445F6871);
        context.getMatrices().push();
        context.getMatrices().translate(miniX + 21, miniY + 21, 0.0f);
        context.drawItem(new ItemStack(Items.CLOCK), -8, -8);
        context.getMatrices().pop();
    }

    private void drawItemIcon(DrawContext context, ItemStack stack, int x, int y, float scale) {
        context.getMatrices().push();
        context.getMatrices().translate(x + 8, y + 8, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawItem(stack, -8, -8);
        context.getMatrices().pop();
    }

    private int toLogicalX(double mouseX) {
        return panelX + (int) Math.round((mouseX - panelX) / UI_SCALE);
    }

    private int toLogicalY(double mouseY) {
        return panelY + (int) Math.round((mouseY - panelY) / UI_SCALE);
    }

    private void clampPanel(int width, int height) {
        int visualWidth = Math.max(1, Math.round(getPanelWidth() * UI_SCALE));
        int visualHeight = Math.max(1, Math.round(getPanelHeight() * UI_SCALE));
        panelX = Math.max(6, Math.min(panelX, width - visualWidth - 6));
        panelY = Math.max(6, Math.min(panelY, height - visualHeight - 6));
    }

    private void clampMini(int width, int height) {
        miniX = Math.max(4, Math.min(miniX, width - MINI_SIZE - 4));
        miniY = Math.max(4, Math.min(miniY, height - MINI_SIZE - 4));
    }

    private boolean isMiniHovered(double mouseX, double mouseY) {
        return inside(mouseX, mouseY,
                miniX - MINI_PADDING,
                miniY - MINI_PADDING,
                MINI_SIZE + MINI_PADDING * 2,
                MINI_SIZE + MINI_PADDING * 2);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
