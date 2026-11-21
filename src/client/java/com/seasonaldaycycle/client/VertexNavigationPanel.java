package com.seasonaldaycycle.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class VertexNavigationPanel {
    private static final int WIDTH_MAIN = 360;
    private static final int HEIGHT_MAIN = 252;
    private static final int WIDTH_PAGE = 360;
    private static final int HEIGHT_PAGE = 252;
    private static final int HEADER_HEIGHT = 29;

    private static final int COLOR_PANEL = 0x8A666B73;
    private static final int COLOR_HEADER = 0x9B777C84;
    private static final int COLOR_LINE = 0xAABDC3CB;

    private final boolean worldPage;
    private int panelX;
    private int panelY;
    private boolean initialized;
    private boolean draggingPanel;
    private boolean resizingPanel;
    private double resizeStartMouseX;
    private double resizeStartMouseY;
    private float resizeStartScale;
    private int dragOffsetX;
    private int dragOffsetY;

    public VertexNavigationPanel(boolean worldPage) {
        this.worldPage = worldPage;
    }

    public void renderOverlay(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        ensureInitialized(width, height);

        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();

        context.getMatrices().push();
        float uiScale = getUiScale();
        context.getMatrices().translate(panelX * (1.0f - uiScale), panelY * (1.0f - uiScale), 0.0f);
        context.getMatrices().scale(uiScale, uiScale, 1.0f);

        drawPanelGlass(context, panelWidth, panelHeight);
        drawHeader(context, toLogicalX(mouseX), toLogicalY(mouseY), panelWidth);

        if (worldPage) {
            drawWorldPage(context);
        } else {
            drawMainPage(context);
        }
        drawResizeGrip(context, panelWidth, panelHeight);

        context.getMatrices().pop();
    }

    public boolean handleMouseButton(double mouseX, double mouseY, int button, int action) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ensureInitialized(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());

        if (action == GLFW.GLFW_PRESS) {
            int x = toLogicalX(mouseX);
            int y = toLogicalY(mouseY);
            int width = getPanelWidth();
            int height = getPanelHeight();

            if (inside(x, y, panelX + width - 14, panelY + height - 14, 14, 14)) {
                resizingPanel = true;
                resizeStartMouseX = mouseX;
                resizeStartMouseY = mouseY;
                resizeStartScale = VertexUiConfig.getScale();
                draggingPanel = false;
                return true;
            }

            if (inside(x, y, panelX + width - 29, panelY + 6, 22, 22)) {
                VertexPanelController.close(client);
                return true;
            }

            if (worldPage && inside(x, y, panelX + 8, panelY + 6, 34, 24)) {
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
            boolean wasResizing = resizingPanel;
            boolean dragged = draggingPanel || wasResizing;
            if (wasResizing) VertexUiConfig.commitScale();
            draggingPanel = false;
            resizingPanel = false;
            return dragged;
        }

        return false;
    }

    public boolean handleMouseMove(double mouseX, double mouseY) {
        if (resizingPanel) {
            double horizontalDelta = (mouseX - resizeStartMouseX) / (double) getPanelWidth();
            double verticalDelta = (mouseY - resizeStartMouseY) / (double) Math.max(1, getPanelHeight());
            double scaleDelta = Math.abs(horizontalDelta) >= Math.abs(verticalDelta)
                    ? horizontalDelta : verticalDelta;
            VertexUiConfig.previewScale(resizeStartScale + (float) scaleDelta);
            clampPanel(MinecraftClient.getInstance().getWindow().getScaledWidth(),
                    MinecraftClient.getInstance().getWindow().getScaledHeight());
            return true;
        }

        if (draggingPanel) {
            panelX = (int) mouseX - dragOffsetX;
            panelY = (int) mouseY - dragOffsetY;
            clampPanel(MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight());
            return true;
        }

        return false;
    }

    private void ensureInitialized(int width, int height) {
        if (!initialized) {
            int panelWidth = getPanelWidth();
            int panelHeight = getPanelHeight();
            if (panelX == 0 && panelY == 0) {
                panelX = (width - Math.round(panelWidth * getUiScale())) / 2;
                panelY = Math.max(6, (height - Math.round(panelHeight * getUiScale())) / 2);
            }
            clampPanel(width, height);
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
        int right = panelX + width;
        int bottom = panelY + height;
        context.fill(panelX + 3, panelY + 5, right + 3, bottom + 5, 0x32000000);
        drawRoundedPanel(context, panelX, panelY, right, bottom, 0xFF77818B);
        drawRoundedPanel(context, panelX + 1, panelY + 1, right - 1, bottom - 1, 0xFF424B56);
        drawRoundedPanel(context, panelX + 5, panelY + 5, right - 5, panelY + HEADER_HEIGHT + 7, 0xFF343D47);
        context.fill(panelX + 12, panelY + HEADER_HEIGHT + 7, right - 12,
                panelY + HEADER_HEIGHT + 8, 0x286B7886);
    }

    private void drawHeader(DrawContext context, int mouseX, int mouseY, int width) {
        MinecraftClient client = MinecraftClient.getInstance();
        var renderer = client.textRenderer;

        if (worldPage) {
            boolean backHover = inside(mouseX, mouseY, panelX + 8, panelY + 6, 34, 24);
            context.fill(panelX + 8, panelY + 6, panelX + 42, panelY + 30,
                    backHover ? 0xCC4A4F57 : 0x66464A51);
            context.drawCenteredTextWithShadow(renderer, "←", panelX + 25, panelY + 9,
                    backHover ? 0xFFFFFFFF : 0xFFE9EDF1);
            context.drawTextWithShadow(renderer, Text.literal("WORLD"), panelX + 50, panelY + 11, 0xFFFFFFFF);
        } else {
            context.drawTextWithShadow(renderer, Text.literal("Vertex"), panelX + 18, panelY + 9, 0xFFFFFFFF);
        }

        int closeX = panelX + width - 29;
        boolean closeHover = inside(mouseX, mouseY, closeX, panelY + 6, 22, 22);
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
        context.drawTextWithShadow(renderer, Text.literal(fitText("Мир, время, погода и другие инструменты", cardWidth - 82)), left + 65, cardY + 35, 0xFFC0C6CD);
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
        context.drawTextWithShadow(renderer, Text.literal(fitText("Время суток, длительность, погода и сезонные параметры", cardWidth - 76)), left + 58, cardY + 34, 0xFFC0C6CD);
        context.drawTextWithShadow(renderer, Text.literal("Открыть настройки"), left + 58, cardY + 49, 0xFFE1E5E9);

        context.drawTextWithShadow(renderer, Text.literal("Другие инструменты мира появятся здесь"), left, cardY + 91, 0xFF929AA4);
    }

    private String fitText(String value, int maxWidth) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        if (renderer.getWidth(value) <= maxWidth) return value;
        String shortened = value;
        while (!shortened.isEmpty() && renderer.getWidth(shortened + "…") > maxWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }

    private void drawItemIcon(DrawContext context, ItemStack stack, int x, int y, float scale) {
        context.getMatrices().push();
        context.getMatrices().translate(x + 8, y + 8, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawItem(stack, -8, -8);
        context.getMatrices().pop();
    }

    private void drawResizeGrip(DrawContext context, int width, int height) {
        int right = panelX + width - 5;
        int bottom = panelY + height - 5;
        int color = resizingPanel ? 0xFFEAF0F6 : 0xFF9BA8B5;
        context.fill(right - 8, bottom - 1, right, bottom, color);
        context.fill(right - 5, bottom - 4, right, bottom - 3, color);
        context.fill(right - 2, bottom - 7, right, bottom - 6, color);
    }

    private static void drawRoundedPanel(DrawContext context, int left, int top,
                                         int right, int bottom, int color) {
        if (right - left < 4 || bottom - top < 4) {
            context.fill(left, top, right, bottom, color);
            return;
        }
        context.fill(left + 4, top, right - 4, bottom, color);
        context.fill(left + 2, top + 1, right - 2, bottom - 1, color);
        context.fill(left + 1, top + 2, right - 1, bottom - 2, color);
        context.fill(left, top + 4, right, bottom - 4, color);
    }

    private int toLogicalX(double mouseX) {
        return panelX + (int) Math.round((mouseX - panelX) / getUiScale());
    }

    private int toLogicalY(double mouseY) {
        return panelY + (int) Math.round((mouseY - panelY) / getUiScale());
    }

    private float getUiScale() {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        float fitWidth = Math.max(0.25f, (width - 12.0f) / getPanelWidth());
        float fitHeight = Math.max(0.25f, (height - 12.0f) / getPanelHeight());
        return Math.max(0.25f, Math.min(VertexUiConfig.getScale(), Math.min(fitWidth, fitHeight)));
    }

    private void clampPanel(int width, int height) {
        int visualWidth = Math.max(1, Math.round(getPanelWidth() * getUiScale()));
        int visualHeight = Math.max(1, Math.round(getPanelHeight() * getUiScale()));
        panelX = Math.max(6, Math.min(panelX, width - visualWidth - 6));
        panelY = Math.max(6, Math.min(panelY, height - visualHeight - 6));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
