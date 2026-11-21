package com.seasonaldaycycle.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Owns the single visible Vertex panel and its shared open/close animation. */
public final class VertexPanelController {
    private enum Page { NONE, MAIN, WORLD, TIME }
    private static final long PANEL_ANIMATION_MILLIS = 280L;
    private static final float CLOSED_SCALE = 0.94F;
    private static Page currentPage = Page.NONE;
    private static VertexNavigationPanel navigationPanel;
    private static float animationProgress;
    private static float animationFrom;
    private static float animationTarget;
    private static long animationStartMillis;
    private static boolean animationRunning;

    private VertexPanelController() {}

    public static boolean isActive() {
        updateAnimationState();
        if (currentPage == Page.TIME && DayCycleScreen.getActive() == null) {
            finishClose();
            return false;
        }
        return currentPage != Page.NONE;
    }

    public static void toggleMenu(MinecraftClient client) {
        if (!isActive()) openMain(client);
        else if (animationTarget < 0.5F) animateTo(1.0F);
        else close(client);
    }

    public static void openMain(MinecraftClient client) {
        if (currentPage == Page.TIME) {
            DayCycleScreen existingTime = DayCycleScreen.getActive();
            if (existingTime != null) existingTime.close();
        }
        currentPage = Page.MAIN;
        navigationPanel = new VertexNavigationPanel(false);
        animateTo(1.0F);
    }

    public static void openWorld(MinecraftClient client) {
        if (currentPage == Page.TIME) {
            DayCycleScreen existingTime = DayCycleScreen.getActive();
            if (existingTime != null) existingTime.close();
        }
        currentPage = Page.WORLD;
        navigationPanel = new VertexNavigationPanel(true);
        animateTo(1.0F);
    }

    public static void openTime(MinecraftClient client) {
        navigationPanel = null;
        currentPage = Page.TIME;
        new DayCycleScreen();
        animateTo(1.0F);
    }

    public static void close(MinecraftClient client) {
        if (currentPage != Page.NONE) animateTo(0.0F);
    }

    public static void closeImmediately(MinecraftClient client) {
        finishClose();
        animationProgress = 0.0F;
        animationFrom = 0.0F;
        animationTarget = 0.0F;
        animationRunning = false;
    }

    public static void tick(MinecraftClient client) {
        updateAnimationState();
        if (currentPage == Page.TIME && DayCycleScreen.getActive() == null) finishClose();
    }

    public static void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateAnimationState();
        if (currentPage == Page.NONE || animationProgress <= 0.001F) return;

        float progress = clamp01(animationProgress);
        float scale = CLOSED_SCALE + (1.0F - CLOSED_SCALE) * progress;
        float verticalOffset = (1.0F - progress) * 10.0F;
        int sw = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int sh = MinecraftClient.getInstance().getWindow().getScaledHeight();
        float cx = sw * 0.5F;
        float cy = sh * 0.5F;

        context.getMatrices().push();
        try {
            context.getMatrices().translate(cx, cy + verticalOffset, 0.0F);
            context.getMatrices().scale(scale, scale, 1.0F);
            context.getMatrices().translate(-cx, -cy, 0.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, progress);

            if (currentPage == Page.TIME) {
                DayCycleScreen screen = DayCycleScreen.getActive();
                if (screen != null) screen.renderOverlay(context, mouseX, mouseY, delta);
            } else if (navigationPanel != null) {
                navigationPanel.renderOverlay(context, mouseX, mouseY, delta);
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            context.getMatrices().pop();
        }
    }

    public static boolean handleMouseButton(double x, double y, int button, int action) {
        if (!isActive()) return false;
        if (!isInteractive()) return true;
        if (currentPage == Page.TIME) {
            DayCycleScreen screen = DayCycleScreen.getActive();
            return screen == null || screen.handleMouseButton(x, y, button, action);
        }
        return navigationPanel != null && navigationPanel.handleMouseButton(x, y, button, action);
    }

    public static boolean handleMouseMove(double x, double y) {
        if (!isActive()) return false;
        if (!isInteractive()) return true;
        if (currentPage == Page.TIME) {
            DayCycleScreen screen = DayCycleScreen.getActive();
            return screen == null || screen.handleMouseMove(x, y);
        }
        return navigationPanel != null && navigationPanel.handleMouseMove(x, y);
    }

    public static boolean handleMouseScroll(double x, double y, double amount) {
        if (!isActive()) return false;
        if (!isInteractive()) return true;
        if (currentPage != Page.TIME) return false;
        DayCycleScreen screen = DayCycleScreen.getActive();
        return screen != null && screen.handleMouseScroll(x, y, amount);
    }

    public static boolean isCursorBlockingCamera() { return isActive(); }
    public static void returnToMain(MinecraftClient client) { openMain(client); }

    private static boolean isInteractive() {
        return currentPage != Page.NONE && !animationRunning
                && animationTarget >= 0.999F && animationProgress >= 0.999F;
    }

    private static void animateTo(float target) {
        float current = currentAnimationProgress();
        animationFrom = current;
        animationTarget = clamp01(target);
        animationStartMillis = System.currentTimeMillis();
        animationRunning = Math.abs(animationTarget - animationFrom) > 0.001F;
        if (!animationRunning) animationProgress = animationTarget;
    }

    private static void updateAnimationState() {
        currentAnimationProgress();
        if (!animationRunning && animationTarget <= 0.001F
                && animationProgress <= 0.001F && currentPage != Page.NONE) finishClose();
    }

    private static float currentAnimationProgress() {
        if (!animationRunning) return animationProgress;
        long elapsed = Math.max(0L, System.currentTimeMillis() - animationStartMillis);
        float linear = clamp01(elapsed / (float) PANEL_ANIMATION_MILLIS);
        float x = linear;
        float eased = x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
        animationProgress = animationFrom + (animationTarget - animationFrom) * eased;
        if (linear >= 1.0F) {
            animationProgress = animationTarget;
            animationRunning = false;
        }
        return animationProgress;
    }

    private static void finishClose() {
        if (currentPage == Page.TIME) {
            DayCycleScreen screen = DayCycleScreen.getActive();
            if (screen != null) screen.close();
        }
        navigationPanel = null;
        currentPage = Page.NONE;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
