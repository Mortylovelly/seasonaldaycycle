package com.seasonaldaycycle.client;

import com.seasonaldaycycle.mixin.DayCycleScreenMiniAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Owns the single visible Vertex utility panel.
 * Different pages are opened one at a time, so Vertex never shows two panels together.
 */
public final class VertexPanelController {
    private enum Page {
        NONE,
        MAIN,
        WORLD,
        TIME
    }

    private static Page currentPage = Page.NONE;
    private static VertexNavigationPanel navigationPanel;
    private static int miniX = -1;
    private static int miniY = -1;

    private VertexPanelController() {
    }

    public static boolean isActive() {
        if (currentPage == Page.TIME && DayCycleScreen.getActive() == null) {
            currentPage = Page.NONE;
        }
        return currentPage != Page.NONE;
    }

    public static void openMain(MinecraftClient client) {
        DayCycleScreen existingTime = DayCycleScreen.getActive();
        if (existingTime != null) {
            existingTime.close();
        }

        currentPage = Page.MAIN;
        navigationPanel = new VertexNavigationPanel(false, false);
        navigationPanel.setMiniPosition(miniX, miniY);
        setCursor(client);
    }

    public static void openWorld(MinecraftClient client) {
        DayCycleScreen existingTime = DayCycleScreen.getActive();
        if (existingTime != null) {
            existingTime.close();
        }

        currentPage = Page.WORLD;
        navigationPanel = new VertexNavigationPanel(true, false);
        navigationPanel.setMiniPosition(miniX, miniY);
        setCursor(client);
    }

    public static void openTime(MinecraftClient client) {
        if (navigationPanel != null) {
            syncMiniFromNavigation(navigationPanel);
        }

        navigationPanel = null;
        currentPage = Page.TIME;

        DayCycleScreen screen = new DayCycleScreen();
        DayCycleScreenMiniAccessor accessor = (DayCycleScreenMiniAccessor) screen;
        if (miniX >= 0 && miniY >= 0) {
            accessor.seasonaldaycycle$setMiniX(miniX);
            accessor.seasonaldaycycle$setMiniY(miniY);
        }

        setCursor(client);
    }

    public static void close(MinecraftClient client) {
        if (currentPage == Page.TIME) {
            DayCycleScreen screen = DayCycleScreen.getActive();
            if (screen != null) {
                syncMiniFromTime(screen);
                screen.close();
            }
        }

        navigationPanel = null;
        currentPage = Page.NONE;
        client.mouse.lockCursor();
    }

    public static void tick(MinecraftClient client) {
        if (currentPage == Page.TIME) {
            DayCycleScreen screen = DayCycleScreen.getActive();
            if (screen == null) {
                currentPage = Page.NONE;
                navigationPanel = null;
                return;
            }
            syncMiniFromTime(screen);
            return;
        }

        if (navigationPanel != null) {
            syncMiniFromNavigation(navigationPanel);
        }
    }

    public static void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isActive()) {
            return;
        }

        if (currentPage == Page.TIME) {
            DayCycleScreen screen = DayCycleScreen.getActive();
            if (screen != null) {
                screen.renderOverlay(context, mouseX, mouseY, delta);
                syncMiniFromTime(screen);
            }
            return;
        }

        if (navigationPanel != null) {
            navigationPanel.renderOverlay(context, mouseX, mouseY, delta);
            syncMiniFromNavigation(navigationPanel);
        }
    }

    public static boolean handleMouseButton(double mouseX, double mouseY, int button, int action) {
        if (!isActive()) {
            return false;
        }

        if (currentPage == Page.TIME) {
            DayCycleScreen screen = DayCycleScreen.getActive();
            if (screen == null) {
                return false;
            }
            boolean handled = screen.handleMouseButton(mouseX, mouseY, button, action);
            syncMiniFromTime(screen);
            return handled;
        }

        return navigationPanel != null && navigationPanel.handleMouseButton(mouseX, mouseY, button, action);
    }

    public static boolean handleMouseMove(double mouseX, double mouseY) {
        if (!isActive()) {
            return false;
        }

        if (currentPage == Page.TIME) {
            DayCycleScreen screen = DayCycleScreen.getActive();
            if (screen == null) {
                return false;
            }
            boolean handled = screen.handleMouseMove(mouseX, mouseY);
            syncMiniFromTime(screen);
            return handled;
        }

        return navigationPanel != null && navigationPanel.handleMouseMove(mouseX, mouseY);
    }

    public static boolean handleMouseScroll(double mouseX, double mouseY, double verticalAmount) {
        if (!isActive() || currentPage != Page.TIME) {
            return false;
        }

        DayCycleScreen screen = DayCycleScreen.getActive();
        if (screen == null) {
            return false;
        }
        return screen.handleMouseScroll(mouseX, mouseY, verticalAmount);
    }

    public static boolean isCursorBlockingCamera() {
        return isActive();
    }

    public static void setCursor(MinecraftClient client) {
        SeasonalDayCycleClient.setCursorMode(client, true);
    }

    public static void returnToMain(MinecraftClient client) {
        openMain(client);
    }

    public static int getMiniX() {
        return miniX;
    }

    public static int getMiniY() {
        return miniY;
    }

    private static void syncMiniFromNavigation(VertexNavigationPanel panel) {
        if (panel.hasMiniPosition()) {
            miniX = panel.getMiniX();
            miniY = panel.getMiniY();
        }
    }

    private static void syncMiniFromTime(DayCycleScreen screen) {
        DayCycleScreenMiniAccessor accessor = (DayCycleScreenMiniAccessor) screen;
        miniX = accessor.seasonaldaycycle$getMiniX();
        miniY = accessor.seasonaldaycycle$getMiniY();
    }
}
