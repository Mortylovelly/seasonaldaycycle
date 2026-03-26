package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.ClientTimeInterpolator;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class MixinClientLevel {

    // Вызывается каждый тик на клиенте — отменяем ванильный +1
    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void sdc$cancelVanillaTimeTick(CallbackInfo ci) {
        ClientTimeInterpolator.onClientTimeTick((ClientLevel)(Object)this);
        ci.cancel(); // отменяем ванильный тик времени полностью
    }
}
