package com.seasonaldaycycle.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** User-remappable shortcut for toggling the Vertex panel. */
public final class VertexKeyBindings {
    private static KeyBinding openMenu;
    private VertexKeyBindings() {}

    public static void register() {
        if (openMenu != null) return;
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.seasonaldaycycle.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.seasonaldaycycle"
        ));
    }

    public static void tick(MinecraftClient client) {
        if (openMenu == null || client.player == null || client.world == null
                || client.currentScreen != null) return;
        while (openMenu.wasPressed()) VertexPanelController.toggleMenu(client);
    }
}
