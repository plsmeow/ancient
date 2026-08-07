package tech.onetap.util.neuro.rotation;

import net.minecraft.util.math.MathHelper;
import tech.onetap.util.render.math.GCDFixer;

/**
 * Контроллер выдачи поворота с переносом GCD остатка.
 * Принимает бюджет дельты на тик, выплачивает его порциями на субшаги.
 */
public class NeuroRotationController {

    private float budgetYaw = 0.0f;
    private float budgetPitch = 0.0f;
    /** Сколько от бюджета ещё не выплачено. Не даёт перелететь цель. */
    private float remainingYaw = 0.0f;
    private float remainingPitch = 0.0f;
    private float residualYaw = 0.0f;
    private float residualPitch = 0.0f;
    private boolean hasNewBudget = false;

    /**
     * Устанавливает новый бюджет дельты на тик.
     * Вызывается ровно раз на тик.
     */
    public void setBudget(float deltaYaw, float deltaPitch) {
        this.budgetYaw = deltaYaw;
        this.budgetPitch = deltaPitch;
        this.remainingYaw = deltaYaw;
        this.remainingPitch = deltaPitch;
        this.hasNewBudget = true;
    }

    /**
     * Возвращает выплату дельты для субшага.
     * @param subStepsTotal ожидаемое число субшагов в этом тике (~240 на EventGameUpdate)
     * @param applyGCD применить GCD квантование с переносом остатка
     * @return [deltaYaw, deltaPitch] для этого субшага
     */
    public float[] getStepDelta(int subStepsTotal, boolean applyGCD) {
        if (!hasNewBudget) {
            // Нет бюджета — возвращаем нули
            return new float[]{0.0f, 0.0f};
        }

        // Доля бюджета на этот субшаг.
        // subStepsTotal — только оценка, поэтому выплату всегда ограничиваем
        // остатком: иначе при большем числе субшагов поворот перелетит цель.
        float fraction = 1.0f / Math.max(1, subStepsTotal);
        float rawYaw = clampToRemaining(budgetYaw * fraction, remainingYaw);
        float rawPitch = clampToRemaining(budgetPitch * fraction, remainingPitch);

        if (!applyGCD) {
            remainingYaw -= rawYaw;
            remainingPitch -= rawPitch;
            return new float[]{rawYaw, rawPitch};
        }

        // GCD квантование с переносом остатка
        float gcd = GCDFixer.getGCDValue();

        float targetYaw = rawYaw + residualYaw;
        float targetPitch = rawPitch + residualPitch;

        float quantizedYaw = Math.round(targetYaw / gcd) * gcd;
        float quantizedPitch = Math.round(targetPitch / gcd) * gcd;

        // Квантование может превысить остаток — подрезаем и здесь
        quantizedYaw = clampToRemaining(quantizedYaw, remainingYaw);
        quantizedPitch = clampToRemaining(quantizedPitch, remainingPitch);

        residualYaw = targetYaw - quantizedYaw;
        residualPitch = targetPitch - quantizedPitch;

        remainingYaw -= quantizedYaw;
        remainingPitch -= quantizedPitch;

        return new float[]{quantizedYaw, quantizedPitch};
    }

    /**
     * Не позволяет выплате выйти за остаток бюджета (с учётом знака).
     */
    private static float clampToRemaining(float step, float remaining) {
        if (remaining >= 0.0f) {
            return MathHelper.clamp(step, 0.0f, remaining);
        }
        return MathHelper.clamp(step, remaining, 0.0f);
    }

    /**
     * Сбрасывает состояние (при потере цели).
     */
    public void reset() {
        budgetYaw = 0.0f;
        budgetPitch = 0.0f;
        remainingYaw = 0.0f;
        remainingPitch = 0.0f;
        residualYaw = 0.0f;
        residualPitch = 0.0f;
        hasNewBudget = false;
    }

    public boolean hasBudget() {
        return hasNewBudget;
    }

    // Диагностика для debug-панели
    public float getBudgetYaw() {
        return budgetYaw;
    }

    public float getBudgetPitch() {
        return budgetPitch;
    }

    public float getRemainingYaw() {
        return remainingYaw;
    }

    public float getRemainingPitch() {
        return remainingPitch;
    }
}
