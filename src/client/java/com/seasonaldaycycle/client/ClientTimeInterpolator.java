package com.seasonaldaycycle.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameRules;

public final class ClientTimeInterpolator {
    private static boolean initialized = false;
    private static long targetTime = 0L;
    private static long lastTime = 0L;
    private static float timeVelocity = 0.0F;
    private static float lastPartialTickTime = 0.0F;
    private static final int DAY_TICKS = 24000;

    private ClientTimeInterpolator() {}

    public static void reset() {
        initialized = false;
        timeVelocity = 0.0F;
        lastPartialTickTime = 0.0F;
    }

    public static void onRenderTick(MinecraftClient client) {
        if (client.world == null || client.isPaused()) return;
        if (!client.world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) return;

        float partialTick = client.getRenderTickCounter().getTickDelta(false);
        if (!initialized) {
            long time = client.world.getTimeOfDay();
            targetTime = time;
            lastTime = time;
            initialized = true;
            lastPartialTickTime = partialTick;
            return;
        }

        float tickTimeDelta = partialTick - lastPartialTickTime;
        if (tickTimeDelta < 0.0F) tickTimeDelta += 1.0F;
        lastPartialTickTime = partialTick;

        updateTargetTime(client);
        interpolateTime(client, tickTimeDelta);
    }

    private static void updateTargetTime(MinecraftClient client) {
        long time = client.world.getTimeOfDay();
        if (time != lastTime) {
            targetTime = time;
            long discrepancy = lastTime - time;
            if (Math.abs(discrepancy) > DAY_TICKS) {
                long newTimeOfDay = Math.floorMod(time, DAY_TICKS);
                long oldTimeOfDay = Math.floorMod(lastTime, DAY_TICKS);
                lastTime = time - newTimeOfDay + oldTimeOfDay;
            }
            client.world.getLevelProperties().setTimeOfDay(lastTime);
        }
    }

    private static void interpolateTime(MinecraftClient client, float tickTimeDelta) {
        long time = client.world.getTimeOfDay();
        final float duration = 1.0F;
        final float omega = 2.0F / duration;
        final float x = omega * tickTimeDelta;
        final float exp = 1.0F / (1.0F + x + 0.48F * x * x + 0.235F * x * x * x);
        final float change = time - targetTime;

        float temp = (timeVelocity + omega * change) * tickTimeDelta;
        time = targetTime + (long) ((change + temp) * exp);
        timeVelocity = (timeVelocity - omega * temp) * exp;

        if ((change < 0.0F) == time > targetTime) {
            time = targetTime;
            timeVelocity = 0.0F;
        }

        client.world.getLevelProperties().setTimeOfDay(time);
        lastTime = time;
    }
}
