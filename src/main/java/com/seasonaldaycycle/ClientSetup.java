package com.seasonaldaycycle;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

@OnlyIn(Dist.CLIENT)
public class ClientSetup {

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ClientSetup());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientTimeInterpolator.onClientTick();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        ClientTimeInterpolator.onRenderTick(event.renderTickTime);
    }

    @SubscribeEvent
    public void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientTimeInterpolator.onWorldLoad();
    }

    @SubscribeEvent
    public void onPlayerLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTimeInterpolator.onWorldUnload();
    }
}
