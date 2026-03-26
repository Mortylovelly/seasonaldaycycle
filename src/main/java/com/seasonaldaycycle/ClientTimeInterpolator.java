package com.seasonaldaycycle;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientTimeInterpolator {

    public static ClientTimeInterpolator instance;

    private boolean initialized = false;
    private long targetTime = 0;
    private long lastTime = 0;
    private float timeVelocity = 0;
    private float lastPartialTickTime = 0;

    private static final int DAY_TICKS = 24000;

    public static void onWorldLoad() {
        instance = new ClientTimeInterpolator();
    }

    public static void onWorldUnload() {
        instance = null;
    }

    // Вызывается каждый рендер-тик (каждый кадр)
    public static void onRenderTick(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || instance == null) return;
        instance.partialTick(partialTick);
    }

    // Вызывается каждый клиентский тик — отменяем ванильный +1 на клиенте
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || instance == null) return;
        if (mc.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)) {
            mc.level.getLevelData().setDayTime(mc.level.getDayTime() - 1);
        }
    }

    private void partialTick(float partialTickTime) {
        if (!initialized) {
            long time = Minecraft.getInstance().level.getDayTime();
            targetTime = time;
            lastTime = time;
            initialized = true;
        }

        float tickTimeDelta = partialTickTime - lastPartialTickTime;
        if (tickTimeDelta < 0) tickTimeDelta += 1;
        lastPartialTickTime = partialTickTime;

        updateTargetTime();
        interpolateTime(tickTimeDelta);
    }

    private void updateTargetTime() {
        Minecraft mc = Minecraft.getInstance();
        long time = mc.level.getDayTime();

        if (time != lastTime) {
            targetTime = time;

            // Предотвращаем большие прыжки интерполяции
            long discrepancy = lastTime - time;
            if (Math.abs(discrepancy) > DAY_TICKS) {
                long newTimeOfDay = time % DAY_TICKS;
                long oldTimeOfDay = lastTime % DAY_TICKS;
                lastTime = time - newTimeOfDay + oldTimeOfDay;
            }

            mc.level.getLevelData().setDayTime(lastTime);
        }
    }

    private void interpolateTime(float tickTimeDelta) {
        Minecraft mc = Minecraft.getInstance();
        long time = mc.level.getDayTime();

        // Точно такая же формула как в Better Days
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
