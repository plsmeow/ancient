package meow.ancient.util.neuro.rotation;

import net.minecraft.util.math.MathHelper;
import meow.ancient.util.render.math.GCDFixer;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Трекер состояния и экстрактор признаков для Neuro ротации.
 * Портирован из ScriptInternal040 (ROKSTAR).
 */
public final class NeuroRotationTracker {
    private static final float MAX_ATTACK_TICKS = 20.0f;

    private float[] hiddenState;
    private float ticksSinceAttack = MAX_ATTACK_TICKS;
    private float frozenTicks;
    private float prevTargetYaw;
    private float prevTargetPitch;
    private float lastYawError;
    private float lastPitchError;
    private float prevYawError;
    private float prevPitchError;
    private float prevYawDelta;
    private float prevPitchDelta;
    private float prevYawDelta2;
    private float prevPitchDelta2;
    private float fovX = 5.0f;
    private float fovY = 15.0f;
    private double distance = 3.0;
    private boolean onTarget;

    public boolean isInitialized() {
        return this.hiddenState != null;
    }

    public void init(NeuroModel model, float currentYaw, float currentPitch, float targetYaw, float targetPitch) {
        this.hiddenState = model.createHiddenState();
        this.prevTargetYaw = targetYaw;
        this.prevTargetPitch = targetPitch;
        this.frozenTicks = 0.0f;
        this.prevYawDelta2 = 0.0f;
        this.prevPitchDelta2 = 0.0f;
        this.prevYawDelta = 0.0f;
        this.prevPitchDelta = 0.0f;
        this.lastYawError = this.prevYawError = MathHelper.wrapDegrees(targetYaw - currentYaw);
        this.lastPitchError = this.prevPitchError = targetPitch - currentPitch;
    }

    public void reset() {
        this.hiddenState = null;
        this.frozenTicks = 0.0f;
    }

    public void onAttack() {
        this.ticksSinceAttack = 0.0f;
    }

    public void tick() {
        this.ticksSinceAttack = Math.min(this.ticksSinceAttack + 1.0f, MAX_ATTACK_TICKS);
    }

    public boolean isOnTarget() {
        return this.onTarget;
    }

    public boolean step(
            NeuroModel model,
            float currentYaw,
            float currentPitch,
            float targetYaw,
            float targetPitch,
            float fovX,
            float fovY,
            double distance,
            float aimOffset,
            int freezeLimit,
            float temperature,
            int candidateCount,
            float speedMultiplier,
            float[] outputDelta
    ) {
        if (model == null) {
            return false;
        }
        if (this.hiddenState == null) {
            this.init(model, currentYaw, currentPitch, targetYaw, targetPitch);
        }

        this.fovX = fovX;
        this.fovY = fovY;
        this.distance = distance;

        float gcd = GCDFixer.getGCDValue();
        if (gcd <= 0.0f) {
            gcd = 0.015f;
        }

        float targetYawVel = MathHelper.wrapDegrees(targetYaw - this.prevTargetYaw);
        float targetPitchVel = targetPitch - this.prevTargetPitch;
        this.prevTargetYaw = targetYaw;
        this.prevTargetPitch = targetPitch;

        float currentYawError = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float currentPitchError = targetPitch - currentPitch;

        float[] features = this.computeFeatures(model, targetYawVel, targetPitchVel);
        float[] modelOutput = model.forward(features, this.hiddenState);

        float bestDeltaYaw = 0.0f;
        float bestDeltaPitch = 0.0f;

        // aimOffset применяется только для естественного микро-разброса ВНУТРИ хитбокса,
        // когда прицел уже находится на цели. Если прицел не на цели, оффсет не должен отталкивать его в воздух!
        float safeAimOffset = this.onTarget ? Math.min(Math.max(aimOffset, 0.0f), 0.35f) : 0.0f;

        // Заморозка допускается ТОЛЬКО когда мы уже навели прицел на цель.
        // Если прицел вне цели, сеть не должна "залипать" на 12 тиков (0.6 секунды).
        boolean allowFreeze = this.onTarget && (ThreadLocalRandom.current().nextFloat() < NeuroModel.sigmoid(modelOutput[0]));
        boolean freezeReached = this.frozenTicks >= (float) Math.min(freezeLimit, model.getFreezeCut());

        if (freezeReached || !allowFreeze) {
            float minCost = Float.MAX_VALUE;
            for (int i = candidateCount; i > 0; --i) {
                int compIdx = 1 + 6 * model.sampleMixture(modelOutput, ThreadLocalRandom.current().nextFloat());
                float rho = NeuroModel.tanhCorr(modelOutput[compIdx + 5]);
                float sqrt1MinusRho2 = (float) Math.sqrt(Math.max(0.0f, 1.0f - rho * rho));
                float eps1 = (float) ThreadLocalRandom.current().nextGaussian();
                float eps2 = rho * eps1 + sqrt1MinusRho2 * (float) ThreadLocalRandom.current().nextGaussian();

                float rawDeltaYaw = speedMultiplier * model.sampleCoordinate(
                        modelOutput[compIdx + 1], modelOutput[compIdx + 3], true, eps1, temperature);
                float rawDeltaPitch = speedMultiplier * model.sampleCoordinate(
                        modelOutput[compIdx + 2], modelOutput[compIdx + 4], false, eps2, temperature);

                float candDeltaYaw = quantize(rawDeltaYaw, gcd);
                float candDeltaPitch = quantize(rawDeltaPitch, gcd);

                float remainingYaw = MathHelper.wrapDegrees(currentYawError - candDeltaYaw);
                float remainingPitch = currentPitchError - candDeltaPitch;
                float normHypot = (float) Math.hypot(remainingYaw / this.fovX, remainingPitch / this.fovY);

                float cost;
                if (this.onTarget) {
                    cost = Math.abs(normHypot - safeAimOffset);
                } else {
                    cost = normHypot;
                    // Штрафуем кандидаты, уводящие прицел дальше от цели
                    if (Math.abs(remainingPitch) > Math.abs(currentPitchError) + 0.2f) {
                        cost += 5.0f;
                    }
                    if (Math.abs(remainingYaw) > Math.abs(currentYawError) + 0.2f) {
                        cost += 5.0f;
                    }
                }

                if (cost < minCost) {
                    minCost = cost;
                    bestDeltaYaw = candDeltaYaw;
                    bestDeltaPitch = candDeltaPitch;
                }
            }
        }

        // Проверка: застрял ли pitch в воздухе (вверху) или в полу (внизу)
        boolean pitchInAir = (currentPitch <= -75.0f && targetPitch > -65.0f);
        boolean pitchInGround = (currentPitch >= 75.0f && targetPitch < 65.0f);
        boolean pitchStuck = pitchInAir || pitchInGround;

        // Если прицел застрял или дельта нулевая при наличии ошибки
        if (bestDeltaYaw == 0.0f && bestDeltaPitch == 0.0f) {
            if (freezeReached || pitchStuck || !this.onTarget) {
                // Эффективный выход из заморозки / доводка: уверенный шаг к цели
                float stepLimit = Math.max(gcd * 4.0f, 3.5f);
                if (Math.abs(currentPitchError) >= Math.abs(currentYawError) || pitchStuck) {
                    bestDeltaPitch = Math.copySign(Math.min(Math.abs(currentPitchError), stepLimit), currentPitchError);
                } else {
                    bestDeltaYaw = Math.copySign(Math.min(Math.abs(currentYawError), stepLimit), currentYawError);
                }
            }
        }

        // Если pitch застрял в воздухе/полу, а кандидат не выводит его обратно к цели:
        if (pitchStuck && Math.signum(bestDeltaPitch) != Math.signum(currentPitchError)) {
            bestDeltaPitch = Math.copySign(Math.min(Math.abs(currentPitchError), 6.0f), currentPitchError);
        }

        // Защита от NaN / Inf
        if (Float.isNaN(bestDeltaYaw) || Float.isInfinite(bestDeltaYaw)) bestDeltaYaw = 0.0f;
        if (Float.isNaN(bestDeltaPitch) || Float.isInfinite(bestDeltaPitch)) bestDeltaPitch = 0.0f;

        this.frozenTicks = (bestDeltaYaw == 0.0f && bestDeltaPitch == 0.0f) ? this.frozenTicks + 1.0f : 0.0f;

        float clampedPitch = MathHelper.clamp(currentPitch + bestDeltaPitch, -90.0f, 90.0f);
        bestDeltaPitch = clampedPitch - currentPitch;

        this.prevYawDelta2 = this.prevYawDelta;
        this.prevPitchDelta2 = this.prevPitchDelta;
        this.prevYawDelta = bestDeltaYaw;
        this.prevPitchDelta = bestDeltaPitch;

        this.prevYawError = this.lastYawError;
        this.prevPitchError = this.lastPitchError;

        this.lastYawError = MathHelper.wrapDegrees(targetYaw - (currentYaw + bestDeltaYaw));
        this.lastPitchError = targetPitch - clampedPitch;

        this.onTarget = Math.abs(this.lastYawError) <= this.fovX && Math.abs(this.lastPitchError) <= this.fovY;

        outputDelta[0] = bestDeltaYaw;
        outputDelta[1] = bestDeltaPitch;
        return true;
    }

    private float[] computeFeatures(NeuroModel model, float targetYawVel, float targetPitchVel) {
        return new float[]{
                asinhScale(this.lastYawError),
                asinhScale(this.lastPitchError),
                asinhScale(MathHelper.wrapDegrees(this.lastYawError - this.prevYawError)),
                asinhScale(this.lastPitchError - this.prevPitchError),
                asinhScale(targetYawVel),
                asinhScale(targetPitchVel),
                asinhScale(this.prevYawDelta),
                asinhScale(this.prevPitchDelta),
                asinhScale(this.prevYawDelta2),
                asinhScale(this.prevPitchDelta2),
                asinhScale(this.lastYawError / this.fovX),
                asinhScale(this.lastPitchError / this.fovY),
                (float) Math.log(Math.max(this.distance, 0.05) + 0.5) / 2.0f,
                (float) Math.log(this.fovX) / 3.0f,
                this.onTarget ? 1.0f : 0.0f,
                this.ticksSinceAttack / MAX_ATTACK_TICKS,
                this.frozenTicks / (float) model.getFreezeCut()
        };
    }

    private static float asinhScale(float f) {
        if (Float.isNaN(f) || Float.isInfinite(f)) {
            return 0.0f;
        }
        float abs = Math.abs(f);
        float asinh = (float) Math.log(abs + Math.sqrt((double) abs * abs + 1.0));
        return Math.copySign(asinh / 3.0f, f);
    }

    private static float quantize(float f, float gcd) {
        return (float) Math.round(f / gcd) * gcd;
    }

    public float getFrozenTicks() {
        return this.frozenTicks;
    }

    public float getLastYawError() {
        return this.lastYawError;
    }

    public float getLastPitchError() {
        return this.lastPitchError;
    }
}
