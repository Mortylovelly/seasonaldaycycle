package com.seasonaldaycycle;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class ClientEventHandler {

    // Каждый клиентский тик — отменяем ванильный +1 на клиенте
    // Точно как Better Days undoVanillaTimeTicks()
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        if (!mc.level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) return;
        mc.level.getLevelData().setDayTime(mc.level.getDayTime() - 1);
    }

    @SubscribeEvent
    public void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientTimeInterpolator.reset();
    }

    @SubscribeEvent
    public void onPlayerLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTimeInterpolator.reset();
    }
}
