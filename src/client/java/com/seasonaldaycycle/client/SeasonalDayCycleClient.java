package com.seasonaldaycycle.client;

import com.seasonaldaycycle.ModConfig;
import com.seasonaldaycycle.network.DayCycleLengthSyncPayload;
import com.seasonaldaycycle.network.OpenDayCycleScreenPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class SeasonalDayCycleClient implements ClientModInitializer {
    private static KeyBinding toggleCursorKey;
    private static boolean cursorMode;
    private static long knownCycleLengthTicks = ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;

    @Override
    public void onInitializeClient() {
        toggleCursorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.seasonaldaycycle.toggle_cursor",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.seasonaldaycycle"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(SeasonalDayCycleClient::tick);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(() -> resetForWorldExit(client)));

        ClientPlayNetworking.registerGlobalReceiver(DayCycleLengthSyncPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                long ticks = ModConfig.sanitizeCycleLengthTicks(payload.ticks());
                knownCycleLengthTicks = ticks;
                ModConfig.setCycleLengthTicks(ticks);

                DayCycleScreen screen = DayCycleScreen.getActive();
                if (screen != null) {
                    screen.setCycleLengthFromServer(ticks);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenDayCycleScreenPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> VertexPanelController.openMain(client));
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (!VertexPanelController.isActive()) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            double scale = client.getWindow().getScaleFactor();
            int mouseX = (int) Math.round(client.mouse.getX() / scale);
            int mouseY = (int) Math.round(client.mouse.getY() / scale);
            VertexPanelController.render(drawContext, mouseX, mouseY, 0.0f);
        });
    }

    private static void tick(MinecraftClient client) {
        while (toggleCursorKey.wasPressed()) {
            if (!VertexPanelController.isActive()) {
                continue;
            }
            setCursorMode(client, !cursorMode);
        }

        VertexPanelController.tick(client);

        if (!VertexPanelController.isActive() && cursorMode) {
            setCursorMode(client, false);
        }
    }

    private static void resetForWorldExit(MinecraftClient client) {
        if (VertexPanelController.isActive()) {
            VertexPanelController.close(client);
        } else {
            DayCycleScreen screen = DayCycleScreen.getActive();
            if (screen != null) {
                screen.close();
            }
        }

        knownCycleLengthTicks = ModConfig.DEFAULT_CYCLE_LENGTH_TICKS;
        ModConfig.setCycleLengthTicks(ModConfig.DEFAULT_CYCLE_LENGTH_TICKS);
        cursorMode = false;
        client.mouse.lockCursor();
    }

    public static long getKnownCycleLengthTicks() {
        return knownCycleLengthTicks;
    }

    public static void setKnownCycleLengthTicks(long ticks) {
        knownCycleLengthTicks = ModConfig.sanitizeCycleLengthTicks(ticks);
        ModConfig.setCycleLengthTicks(knownCycleLengthTicks);
    }

    public static boolean isCursorMode() {
        return cursorMode;
    }

    public static void setCursorMode(MinecraftClient client, boolean enabled) {
        cursorMode = enabled;

        if (enabled) {
            client.mouse.unlockCursor();
        } else if (client.world != null && client.currentScreen == null) {
            client.mouse.lockCursor();
        }
    }
}
