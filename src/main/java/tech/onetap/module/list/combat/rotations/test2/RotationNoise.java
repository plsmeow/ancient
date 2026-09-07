package tech.onetap.module.list.combat.rotations.test2;

import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Низкочастотный плавный шум микродвижения. Не независимый рандом каждый
 * тик (белый шум), а цель, обновляемая редко, к которой значение
 * подтягивается экспоненциально — непрерывная траектория.
 * Амплитуда по yaw меньше, чем по pitch.
 */
public final class RotationNoise {

    private float yaw;
    private float pitch;
    private float targetYaw;
    private float targetPitch;
    private long nextUpdateMs;

    public void update(long nowMs, float dtSec, float frequencyHz, float amplitudeYaw, float amplitudePitch,
                       ThreadLocalRandom random) {
        if (nowMs >= nextUpdateMs) {
            float periodMs = 1000.0f / Math.max(frequencyHz, 0.1f);
            nextUpdateMs = nowMs + (long) (periodMs * (0.7f + random.nextDouble() * 0.6f));
            targetYaw = (random.nextFloat() - 0.5f) * 2.0f * amplitudeYaw;
            targetPitch = (random.nextFloat() - 0.5f) * 2.0f * amplitudePitch;
        }
        float response = Math.max(frequencyHz * 2.0f, 1.0f);
        float factor = 1.0f - (float) Math.exp(-response * dtSec);
        yaw += (targetYaw - yaw) * factor;
        pitch += (targetPitch - pitch) * factor;
    }

    public void scaleAmplitude(float scale) {
        yaw *= scale;
        pitch *= scale;
        targetYaw *= scale;
        targetPitch *= scale;
    }

    public void reset() {
        yaw = 0.0f;
        pitch = 0.0f;
        targetYaw = 0.0f;
        targetPitch = 0.0f;
        nextUpdateMs = 0L;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
