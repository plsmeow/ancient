package tech.onetap.module.list.combat.rotations.test2;

import net.minecraft.util.math.MathHelper;

/**
 * Вертикальная доводка перед ударом: отдельная короткая (не мгновенная)
 * траектория от текущего pitch к фактической точке попадания.
 */
public final class VerticalCorrectionController {

    private final RotationTrajectory trajectory = new RotationTrajectory();
    private boolean active;

    /**
     * Начать доводку. Длительность зависит от ошибки и скорости доводки:
     * короче основной фазы, но не один тик.
     */
    public void begin(float currentPitch, float actualPitch, float correctionSpeedDegSec) {
        float distance = Math.abs(actualPitch - currentPitch);
        float durationSec = MathHelper.clamp(
                distance / Math.max(correctionSpeedDegSec, 1.0f), 0.08f, 0.26f);
        trajectory.begin(currentPitch, actualPitch, durationSec);
        active = true;
    }

    public void update(float dtSec) {
        trajectory.advance(dtSec);
    }

    /** Целевой pitch доводки в текущий момент (цель может двигаться). */
    public float samplePitch(float actualPitch) {
        return trajectory.samplePitch(actualPitch);
    }

    public boolean isFinished() {
        return !active || trajectory.isFinished();
    }

    public float getProgress() {
        return trajectory.getProgress();
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        active = false;
    }
}
