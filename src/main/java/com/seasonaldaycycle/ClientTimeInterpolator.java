package com.seasonaldaycycle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientTimeInterpolator {

    private static boolean initialized = false;
    private static long targetTime = 0;
    private static long lastTime = 0;
    private static float timeVelocity = 0;
    private static float lastPartialTickTime = 0;

    private static final int DAY_TICKS = 24000;

    public static void reset() {
        initialized = false;
        timeVelocity = 0;
        lastPartialTickTime = 0;
    }

    // Вызывается из MixinClientLevel каждый тик — отменяем ванильный +1
    public static void onClientTimeTick(ClientLevel level) {
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)) return;
        // Ничего не делаем — мы отменили tickTime через cancel()
        // Время двигается только через onRenderTick
    }

    // Вызывается из MixinMinecraft каждый кадр
    public static void onRenderTick(Minecraft mc) {
        if (mc.level == null || mc.isPaused()) return;

        float partialTick = mc.getPartialTick();

        if (!initialized) {
            long time = mc.level.getDayTime();
            targetTime = time;
            lastTime = time;
            initialized = true;
            return;
        }

        float tickTimeDelta = partialTick - lastPartialTickTime;
        if (tickTimeDelta < 0) tickTimeDelta += 1;
        lastPartialTickTime = partialTick;

        updateTargetTime(mc);
        interpolateTime(mc, tickTimeDelta);
    }

    private static void updateTargetTime(Minecraft mc) {
        long time = mc.level.getDayTime();

        if (time != lastTime) {
            targetTime = time;

            long discrepancy = lastTime - time;
            if (Math.abs(discrepancy) > DAY_TICKS) {
                long newTimeOfDay = time % DAY_TICKS;
                long oldTimeOfDay = lastTime % DAY_TICKS;
                lastTime = time - newTimeOfDay + oldTimeOfDay;
            }

            mc.level.getLevelData().setDayTime(lastTime);
        }
    }

    private static void interpolateTime(Minecraft mc, float tickTimeDelta) {
        long time = mc.level.getDayTime();

        final float duration = 1f;
        final float omega = 2F / duration;
        final float x = omega * tickTimeDelta;
        final float exp = 1F / (1F + x + 0.48F * x * x + 0.235F * x * x * x);
        final float change = time - targetTime;

        float temp = (timeVelocity + omega * change) * tickTimeDelta;
        time = targetTime + (long) ((change + temp) * exp);
        timeVelocity = (timeVelocity - omega * temp) * exp;

        if (change < 0.0F == time > targetTime) {
            time = targetTime;
            timeVelocity = 0.0F;
        }

        mc.level.getLevelData().setDayTime(time);
        lastTime = time;
    }
}
