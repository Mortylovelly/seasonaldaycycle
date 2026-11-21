package com.seasonaldaycycle.client;

import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.mixin.SleepingChatScreenAccessor;
import com.seasonaldaycycle.network.DayCycleLengthSyncPayload;
import com.seasonaldaycycle.network.OpenDayCycleScreenPayload;
import com.seasonaldaycycle.network.SleepSettingsSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.gui.widget.ButtonWidget;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class SeasonalDayCycleClient implements ClientModInitializer {
    private static long knownCycleLengthTicks = ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;
    private static final Set<Screen> SLEEP_OVERLAY_SCREENS =
            Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void onInitializeClient() {
        VertexHudConfig.load();
        VertexUiConfig.load();
        VertexKeyBindings.register();

        // Use Fabric's supported per-screen render event instead of injecting
        // into a Minecraft screen method. This runs after the sleeping screen,
        // so the clock and time label are drawn above its widgets.
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof SleepingChatScreen && SLEEP_OVERLAY_SCREENS.add(screen)) {
                ScreenEvents.afterRender(screen).register(
                        (renderedScreen, drawContext, mouseX, mouseY, tickDelta) -> {
                            if (!(renderedScreen instanceof SleepingChatScreen sleepingScreen)) return;
                            if (client.player == null || client.world == null) return;

                            ButtonWidget stopButton =
                                    ((SleepingChatScreenAccessor) (Object) sleepingScreen)
                                            .seasonaldaycycle$getStopSleepingButton();
                            int width = client.getWindow().getScaledWidth();
                            int height = client.getWindow().getScaledHeight();
                            int buttonTop = stopButton == null ? height - 40 : stopButton.getY();

                            SleepClockHudOverlay.render(
                                    drawContext, width, height, tickDelta, buttonTop);
                        });

                ScreenEvents.remove(screen).register(
                        removedScreen -> SLEEP_OVERLAY_SCREENS.remove(removedScreen));
            }
        });

        // Register the compatibility bridge on the first tick, after all client
        // initializers have run. This removes dependence on mod initializer order.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AiAgentCursorCompat.tick(client);
            VertexKeyBindings.tick(client);
            VertexPanelController.tick(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(SeasonalDayCycleClient::resetForWorldExit));

        ClientPlayNetworking.registerGlobalReceiver(DayCycleLengthSyncPayload.TYPE, (payload, player, responseSender) -> {
            long ticks = ModConfig.sanitizeCycleLengthTicks(payload.ticks());
            knownCycleLengthTicks = ticks;
            ModConfig.setCycleLengthTicks(ticks);
            ModConfig.setSyncedSeasonDayRatios(
                    payload.springDayRatio(), payload.summerDayRatio(),
                    payload.autumnDayRatio(), payload.winterDayRatio()
            );
            DayCycleScreen screen = DayCycleScreen.getActive();
            if (screen != null) screen.setCycleLengthFromServer(ticks, payload.canEdit());
            else DayCycleScreen.updateCanEdit(payload.canEdit());
        });

        ClientPlayNetworking.registerGlobalReceiver(SleepSettingsSyncPayload.TYPE, (payload, player, responseSender) ->
                MinecraftClient.getInstance().execute(() ->
                        DayCycleScreen.updateSleepSettingsFromServer(
                                payload.available(), payload.multiplier(), payload.canEdit())));

        ClientPlayNetworking.registerGlobalReceiver(OpenDayCycleScreenPayload.TYPE, (payload, player, responseSender) ->
                VertexPanelController.openMain(MinecraftClient.getInstance()));

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();
            AiAgentCursorCompat.Pointer pointer = AiAgentCursorCompat.getScaledPointer(client);
            if (VertexPanelController.isActive()) {
                VertexPanelController.render(drawContext,
                        (int) Math.round(pointer.x()), (int) Math.round(pointer.y()), 0.0f);
            } else {
                VertexHudOverlay.render(drawContext, width, height, tickDelta);
            }
        });
    }

    private static void resetForWorldExit() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (VertexPanelController.isActive()) VertexPanelController.close(client);
        else {
            DayCycleScreen screen = DayCycleScreen.getActive();
            if (screen != null) screen.close();
        }
        DayCycleScreen.resetSleepSettingsState();
        knownCycleLengthTicks = ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;
        ModConfig.setCycleLengthTicks(ModConfig.DEFAULT_CYCLE_LENGTH_TICKS);
        ModConfig.clearSyncedSeasonDayRatios();
        SkyAngleInterpolationState.clearAll();
        VertexHudOverlay.reset();
    }

    public static long getKnownCycleLengthTicks() { return knownCycleLengthTicks; }

    public static void setKnownCycleLengthTicks(long ticks) {
        knownCycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(ticks);
        ModConfig.setCycleLengthTicks(knownCycleLengthTicks);
    }
}
