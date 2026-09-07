package tech.onetap.module.list.combat.rotations.test2;

import net.minecraft.util.math.MathHelper;

/**
 * Траектория одного движения: интерполяция старт → цель по кривой
 * p(t) = 10t³ - 15t⁴ + 6t⁵ (minimum-jerk): нулевые скорость и ускорение
 * на концах траектории. Длительность и цель движения могут выбираться
 * заново каждый цикл — кривая никогда не одинакова.
 */
public final class RotationTrajectory {

    private float startYaw;
    private float startPitch;
    private float progress;
    private float durationSec = 0.2f;

    public void begin(float startYaw, float startPitch, float durationSec) {
        this.startYaw = startYaw;
        this.startPitch = startPitch;
        this.progress = 0.0f;
        this.durationSec = Math.max(0.05f, durationSec);
    }

    public void advance(float dtSec) {
        if (dtSec <= 0.0f || Float.isNaN(dtSec)) return;
        progress = Math.min(1.0f, progress + dtSec / durationSec);
    }

    public boolean isFinished() {
        return progress >= 1.0f;
    }

    public float getProgress() {
        return progress;
    }

    /** Позиция по yaw в момент progress; цель может двигаться — берём её текущее значение. */
    public float sampleYaw(float targetYaw) {
        return startYaw + MathHelper.wrapDegrees(targetYaw - startYaw) * curve(progress);
    }

    public float samplePitch(float targetPitch) {
        return startPitch + MathHelper.wrapDegrees(targetPitch - startPitch) * curve(progress);
    }

    /** Кривая minimum-jerk: p(t) = 10t³ - 15t⁴ + 6t⁵. */
    public static float curve(float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return t3 * (10.0f - 15.0f * t + 6.0f * t2);
    }
}
