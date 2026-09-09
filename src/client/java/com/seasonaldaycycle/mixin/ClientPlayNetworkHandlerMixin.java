package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.client.SkyAngleInterpolationState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "method_11079(Lnet/minecraft/class_2761;)V", at = @At("TAIL"))
    private void seasonaldaycycle$captureWorldTime(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        SkyAngleInterpolationState.acceptServerTime(
                client.world,
                packet.getTimeOfDay(),
                client.world.getTime()
        );
    }
}
