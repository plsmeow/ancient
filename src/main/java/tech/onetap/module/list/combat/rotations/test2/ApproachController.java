package tech.onetap.module.list.combat.rotations.test2;

import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Контроллер подхода: вертикальный офсет (знак ABOVE/BELOW и амплитуда),
 * который выбирается при старте цикла и не меняется до его завершения.
 * Амплитуда зависит от требуемого поворота (меньше движение — меньше
 * отклонение), размера entity, дистанции и текущего pitch.
 */
public final class ApproachController {

    private boolean offsetAbove;
    private float offsetPitchDeg;

    /**
     * Новый цикл: выбрать знак и амплитуду вертикального офсета.
     *
     * @param angularMove требуемый поворот до цели, градусы
     * @param distance    дистанция до цели, блоки
     * @param targetHeight высота хитбокса цели
     * @param currentPitch текущий pitch
     */
    public void newCycle(ThreadLocalRandom random, float offsetMinDeg, float offsetMaxDeg,
                         float angularMove, double distance, double targetHeight, float currentPitch) {
        offsetAbove = random.nextBoolean();
        float amplitude = offsetMinDeg + random.nextFloat() * Math.max(0.0f, offsetMaxDeg - offsetMinDeg);

        // Чем меньше требуемое движение, тем меньше допустимое отклонение
        float moveScale = MathHelper.clamp(angularMove / 45.0f, 0.25f, 1.0f);
        float sizeScale = MathHelper.clamp((float) (targetHeight / 1.8), 0.6f, 1.4f);
        float distanceScale = MathHelper.clamp((float) (distance / 3.0), 0.6f, 1.3f);
        // На крайних углах обзора отклонение меньше
        float pitchScale = MathHelper.clamp(1.0f - Math.abs(currentPitch) / 240.0f, 0.5f, 1.0f);

        offsetPitchDeg = amplitude * moveScale * sizeScale * distanceScale * pitchScale;
    }

    /** Pitch точки подхода: фактический pitch попадания со знаком офсета. */
    public float approachPitch(float actualPitch) {
        float signed = offsetAbove ? offsetPitchDeg : -offsetPitchDeg;
        return MathHelper.clamp(actualPitch + signed, -88.0f, 88.0f);
    }

    public boolean isOffsetAbove() {
        return offsetAbove;
    }

    public float getOffsetPitchDeg() {
        return offsetPitchDeg;
    }
}
