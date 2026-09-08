package com.seasonaldaycycle.mixin;

import com.seasonaldaycycle.client.DayCycleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DayCycleScreen.class)
public interface DayCycleScreenMiniAccessor {
    @Accessor("miniX")
    int seasonaldaycycle$getMiniX();

    @Accessor("miniX")
    void seasonaldaycycle$setMiniX(int value);

    @Accessor("miniY")
    int seasonaldaycycle$getMiniY();

    @Accessor("miniY")
    void seasonaldaycycle$setMiniY(int value);
}
