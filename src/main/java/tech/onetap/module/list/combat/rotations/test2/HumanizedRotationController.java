package tech.onetap.module.list.combat.rotations.test2;

import net.minecraft.util.math.MathHelper;

/**
 * Ядро humanized-динамики: преобразует требуемую ротацию в плавную
 * траекторию, не меняя расчёт цели. Движение — не постоянная скорость,
 * а связка position/velocity/acceleration:
 *
 * <ul>
 *   <li>minimum-jerk траектория задаёт форму и тайминг движения;</li>
 *   <li>замкнутый контур скорости (ошибка × отклик, ограничение скорости,
 *       экспоненциальное сглаживание) держит непрерывность при движении
 *       цели, смене цели и переходах фаз;</li>
 *   <li>демпфирование возле цели гасит скорость плавно, допуская
 *       зависящий от скорости overshoot.</li>
 * </ul>
 *
 * Внутреннее состояние точное (float, без GCD-квантования) — квантование
 * применяет только координатор к выходному углу, поэтому на малых
 * скоростях нет залипания и рывков.
 */
public final class HumanizedRotationController {

    private final RotationTrajectory trajectory = new RotationTrajectory();

    private float currentYaw;
    private float currentPitch;
    private float velocityYaw;
    private float velocityPitch;
    private float accelerationYaw;
    private float accelerationPitch;

    public void init(float yaw, float pitch) {
        currentYaw = yaw;
        currentPitch = pitch;
        velocityYaw = 0.0f;
        velocityPitch = 0.0f;
        accelerationYaw = 0.0f;
        accelerationPitch = 0.0f;
    }

    /** Начать движение из текущей позиции (скорость сохраняется — телепорта нет). */
    public void beginMovement(float durationSec) {
        trajectory.begin(currentYaw, currentPitch, durationSec);
    }

    /**
     * Один шаг динамики.
     *
     * @param targetYaw   цель по yaw (фактическая точка попадания)
     * @param targetPitch цель по pitch (точка подхода / доводки — зависит от фазы)
     */
    public void update(Params params, float dtSec, float targetYaw, float targetPitch) {
        update(params, dtSec, targetYaw, targetPitch, 0.0f, 0.0f);
    }

    /**
     * Один шаг динамики с опережением по скорости цели.
     *
     * @param targetVelYaw   скорость цели по yaw, град/с
     * @param targetVelPitch скорость цели по pitch, град/с
     */
    public void update(Params params, float dtSec, float targetYaw, float targetPitch,
                       float targetVelYaw, float targetVelPitch) {
        if (dtSec <= 0.0f || Float.isNaN(dtSec)) return;

        trajectory.advance(dtSec);
        float trajYaw = trajectory.sampleYaw(targetYaw);
        float trajPitch = trajectory.samplePitch(targetPitch);

        float errorYaw = MathHelper.wrapDegrees(trajYaw - currentYaw);
        float errorPitch = MathHelper.clamp(MathHelper.wrapDegrees(trajPitch - currentPitch), -180.0f, 180.0f);

        // Feedforward: скорость цели складывается с ошибкой — без этого
        // чисто пропорциональный контур отстаёт от движущейся цели на
        // ошибку рампы (скорость / отклик), большую допуска raycast
        float desiredVelocityYaw = MathHelper.clamp(
                targetVelYaw + errorYaw * params.responseYaw, -params.maxSpeedYaw, params.maxSpeedYaw);
        float desiredVelocityPitch = MathHelper.clamp(
                targetVelPitch + errorPitch * params.responsePitch, -params.maxSpeedPitch, params.maxSpeedPitch);

        float prevVelocityYaw = velocityYaw;
        float prevVelocityPitch = velocityPitch;

        velocityYaw = lerpRate(velocityYaw, desiredVelocityYaw, params.accelerationRateYaw, dtSec);
        velocityPitch = lerpRate(velocityPitch, desiredVelocityPitch, params.accelerationRatePitch, dtSec);

        // Демпфирование возле цели: гасится только избыточная часть скорости
        // ( сверх скорости цели), остаток даёт зависящий от скорости overshoot
        if (Math.abs(errorYaw) < params.dampingZoneYaw) {
            float excess = velocityYaw - targetVelYaw;
            velocityYaw = targetVelYaw + excess * (float) Math.pow(params.dampingYaw, dtSec * 20.0f);
        }
        if (Math.abs(errorPitch) < params.dampingZonePitch) {
            float excess = velocityPitch - targetVelPitch;
            velocityPitch = targetVelPitch + excess * (float) Math.pow(params.dampingPitch, dtSec * 20.0f);
        }

        accelerationYaw = (velocityYaw - prevVelocityYaw) / dtSec;
        accelerationPitch = (velocityPitch - prevVelocityPitch) / dtSec;

        currentYaw = MathHelper.wrapDegrees(currentYaw + velocityYaw * dtSec);
        currentPitch = MathHelper.clamp(currentPitch + velocityPitch * dtSec, -90.0f, 90.0f);
        if (currentPitch >= 89.99f && velocityPitch > 0.0f) velocityPitch = 0.0f;
        if (currentPitch <= -89.99f && velocityPitch < 0.0f) velocityPitch = 0.0f;
    }

    /** Экспоненциальное сглаживание скорости, независимое от частоты кадров. */
    private static float lerpRate(float current, float target, float rate, float dtSec) {
        float factor = 1.0f - (float) Math.exp(-rate * dtSec);
        return current + (target - current) * factor;
    }

    public float getCurrentYaw() {
        return currentYaw;
    }

    public float getCurrentPitch() {
        return currentPitch;
    }

    public float getVelocityYaw() {
        return velocityYaw;
    }

    public float getVelocityPitch() {
        return velocityPitch;
    }

    public float getAccelerationYaw() {
        return accelerationYaw;
    }

    public float getAccelerationPitch() {
        return accelerationPitch;
    }

    public float getProgress() {
        return trajectory.getProgress();
    }

    public boolean isTrajectoryFinished() {
        return trajectory.isFinished();
    }

    public void scaleVelocity(float scale) {
        velocityYaw *= scale;
        velocityPitch *= scale;
    }

    public void reset() {
        velocityYaw = 0.0f;
        velocityPitch = 0.0f;
        accelerationYaw = 0.0f;
        accelerationPitch = 0.0f;
    }

    /** Параметры динамики одного шага — задаются фазой. */
    public record Params(float responseYaw, float responsePitch,
                         float maxSpeedYaw, float maxSpeedPitch,
                         float accelerationRateYaw, float accelerationRatePitch,
                         float dampingYaw, float dampingPitch,
                         float dampingZoneYaw, float dampingZonePitch) {
    }
}
