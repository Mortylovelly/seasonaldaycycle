package com.seasonaldaycycle.mixin;

import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SleepingChatScreen.class)
public interface SleepingChatScreenAccessor {
    @Accessor("stopSleepingButton")
    ButtonWidget seasonaldaycycle$getStopSleepingButton();
}
