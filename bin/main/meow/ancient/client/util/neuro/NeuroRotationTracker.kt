package meow.ancient.client.util.neuro

import net.minecraft.util.Mth
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.*

class NeuroRotationTracker {
    companion object {
        private const val MAX_ATTACK_TICKS = 20.0f
    }

    private var inferenceState: NeuroModel.InferenceState? = null
    private var stateModel: NeuroModel? = null
    private val features = FloatArray(NeuroModel.FEATURES)
    private var ticksSinceAttack = MAX_ATTACK_TICKS
    private var frozenTicks = 0.0f
    private var prevTargetYaw = 0.0f
    private var prevTargetPitch = 0.0f
    var lastYawError = 0.0f
        private set
    var lastPitchError = 0.0f
        private set
    private var prevYawError = 0.0f
    private var prevPitchError = 0.0f
    private var prevYawDelta = 0.0f
    private var prevPitchDelta = 0.0f
    private var prevYawDelta2 = 0.0f
    private var prevPitchDelta2 = 0.0f
    private var fovX = 5.0f
    private var fovY = 15.0f
    private var distance = 3.0
    var isOnTarget = false
        private set
    val frozenTicksValue: Float
        get() = frozenTicks

    fun isInitialized(): Boolean = inferenceState != null

    fun init(model: NeuroModel, currentYaw: Float, currentPitch: Float, targetYaw: Float, targetPitch: Float) {
        inferenceState = model.createInferenceState()
        stateModel = model
        ticksSinceAttack = MAX_ATTACK_TICKS
        prevTargetYaw = targetYaw
        prevTargetPitch = targetPitch
        frozenTicks = 0.0f
        prevYawDelta2 = 0.0f
        prevPitchDelta2 = 0.0f
        prevYawDelta = 0.0f
        prevPitchDelta = 0.0f
        lastYawError = Mth.wrapDegrees(targetYaw - currentYaw)
        prevYawError = lastYawError
        lastPitchError = targetPitch - currentPitch
        prevPitchError = lastPitchError
        isOnTarget = abs(lastYawError) <= fovX && abs(lastPitchError) <= fovY
    }

    fun reset() {
        inferenceState = null
        stateModel = null
        frozenTicks = 0.0f
    }

    fun onAttack() {
        ticksSinceAttack = 0.0f
    }

    fun tick() {
        ticksSinceAttack = minOf(ticksSinceAttack + 1.0f, MAX_ATTACK_TICKS)
    }

    fun step(
        model: NeuroModel,
        currentYaw: Float,
        currentPitch: Float,
        targetYaw: Float,
        targetPitch: Float,
        fovX: Float,
        fovY: Float,
        distance: Double,
        aimOffset: Float,
        freezeLimit: Int,
        temperature: Float,
        candidateCount: Int,
        speedMultiplier: Float,
        outputDelta: FloatArray
    ): Boolean {
        if (stateModel !== model || inferenceState == null) {
            init(model, currentYaw, currentPitch, targetYaw, targetPitch)
        }
        val state = inferenceState!!

        this.fovX = fovX
        this.fovY = fovY
        this.distance = distance

        var gcd = meow.ancient.client.util.rotation.GCDUtil.getGCD()
        if (gcd <= 0.0f) gcd = 0.015f

        val targetYawVel = Mth.wrapDegrees(targetYaw - prevTargetYaw)
        val targetPitchVel = targetPitch - prevTargetPitch
        prevTargetYaw = targetYaw
        prevTargetPitch = targetPitch

        val currentYawError = Mth.wrapDegrees(targetYaw - currentYaw)
        val currentPitchError = targetPitch - currentPitch

        computeFeatures(model, targetYawVel, targetPitchVel)
        val modelOutput = model.forward(features, state)

        var bestDeltaYaw = 0.0f
        var bestDeltaPitch = 0.0f
        val safeAimOffset = if (isOnTarget) aimOffset.coerceIn(0.0f, 0.35f) else 0.0f
        val allowFreeze = isOnTarget && ThreadLocalRandom.current().nextFloat() < NeuroModel.sigmoid(modelOutput[0])
        val freezeReached = frozenTicks >= minOf(freezeLimit, model.freezeCut).toFloat()

        if (freezeReached || !allowFreeze) {
            var minCost = Float.MAX_VALUE
            for (i in candidateCount downTo 1) {
                val compIdx = 1 + 6 * model.sampleMixture(modelOutput, ThreadLocalRandom.current().nextFloat())
                val rho = NeuroModel.tanhCorr(modelOutput[compIdx + 5])
                val sqrt1MinusRho2 = sqrt(maxOf(0.0f, 1.0f - rho * rho))
                val eps1 = ThreadLocalRandom.current().nextGaussian().toFloat()
                val eps2 = rho * eps1 + sqrt1MinusRho2 * ThreadLocalRandom.current().nextGaussian().toFloat()

                val rawDeltaYaw = speedMultiplier * model.sampleCoordinate(modelOutput[compIdx + 1], modelOutput[compIdx + 3], true, eps1, temperature)
                val rawDeltaPitch = speedMultiplier * model.sampleCoordinate(modelOutput[compIdx + 2], modelOutput[compIdx + 4], false, eps2, temperature)

                val candDeltaYaw = quantize(rawDeltaYaw, gcd)
                val candDeltaPitch = quantize(rawDeltaPitch, gcd)

                val remainingYaw = Mth.wrapDegrees(currentYawError - candDeltaYaw)
                val remainingPitch = currentPitchError - candDeltaPitch
                val normHypot = hypot(remainingYaw / this.fovX, remainingPitch / this.fovY)

                val cost = if (isOnTarget) abs(normHypot - safeAimOffset) else normHypot

                if (cost < minCost) {
                    minCost = cost
                    bestDeltaYaw = candDeltaYaw
                    bestDeltaPitch = candDeltaPitch
                }
            }
        }

        if (bestDeltaYaw == 0.0f && bestDeltaPitch == 0.0f) {
            if (freezeReached && isOnTarget) {
                if (abs(currentYawError) >= abs(currentPitchError)) {
                    bestDeltaYaw = currentYawError.sign * gcd
                } else {
                    bestDeltaPitch = currentPitchError.sign * gcd
                }
            } else if (!isOnTarget) {
                val distHypot = hypot(currentYawError, currentPitchError)
                if (distHypot > 0.001f) {
                    val speed = minOf(distHypot, 4.5f)
                    var candYaw = quantize(speed * currentYawError / distHypot, gcd)
                    var candPitch = quantize(speed * currentPitchError / distHypot, gcd)
                    if (candYaw == 0.0f && abs(currentYawError) > gcd * 0.5f) candYaw = currentYawError.sign * gcd
                    if (candPitch == 0.0f && abs(currentPitchError) > gcd * 0.5f) candPitch = currentPitchError.sign * gcd
                    bestDeltaYaw = candYaw
                    bestDeltaPitch = candPitch
                }
            }
        }

        if (bestDeltaYaw.isNaN() || bestDeltaYaw.isInfinite()) bestDeltaYaw = 0.0f
        if (bestDeltaPitch.isNaN() || bestDeltaPitch.isInfinite()) bestDeltaPitch = 0.0f
        frozenTicks = if (bestDeltaYaw == 0.0f && bestDeltaPitch == 0.0f) frozenTicks + 1.0f else 0.0f

        val clampedPitch = (currentPitch + bestDeltaPitch).coerceIn(-90.0f, 90.0f)
        bestDeltaPitch = clampedPitch - currentPitch
        prevYawDelta2 = prevYawDelta
        prevPitchDelta2 = prevPitchDelta
        prevYawDelta = bestDeltaYaw
        prevPitchDelta = bestDeltaPitch

        prevYawError = lastYawError
        prevPitchError = lastPitchError
        lastYawError = Mth.wrapDegrees(targetYaw - (currentYaw + bestDeltaYaw))
        lastPitchError = targetPitch - clampedPitch
        isOnTarget = abs(lastYawError) <= this.fovX && abs(lastPitchError) <= this.fovY

        outputDelta[0] = bestDeltaYaw
        outputDelta[1] = bestDeltaPitch
        return true
    }

    private fun computeFeatures(model: NeuroModel, targetYawVel: Float, targetPitchVel: Float) {
        features[0] = asinhScale(lastYawError)
        features[1] = asinhScale(lastPitchError)
        features[2] = asinhScale(Mth.wrapDegrees(lastYawError - prevYawError))
        features[3] = asinhScale(lastPitchError - prevPitchError)
        features[4] = asinhScale(targetYawVel)
        features[5] = asinhScale(targetPitchVel)
        features[6] = asinhScale(prevYawDelta)
        features[7] = asinhScale(prevPitchDelta)
        features[8] = asinhScale(prevYawDelta2)
        features[9] = asinhScale(prevPitchDelta2)
        features[10] = asinhScale(lastYawError / fovX)
        features[11] = asinhScale(lastPitchError / fovY)
        features[12] = (ln(maxOf(distance, 0.05) + 0.5) / 2.0).toFloat()
        features[13] = (ln(fovX.toDouble()) / 3.0).toFloat()
        features[14] = if (isOnTarget) 1.0f else 0.0f
        features[15] = ticksSinceAttack / MAX_ATTACK_TICKS
        features[16] = frozenTicks / model.freezeCut.toFloat()
    }

    private fun asinhScale(f: Float): Float {
        if (f.isNaN() || f.isInfinite()) return 0.0f
        val abs = abs(f)
        val asinh = ln(abs + sqrt(abs.toDouble() * abs + 1.0)).toFloat()
        return asinh / 3.0f * f.sign
    }

    private fun quantize(f: Float, gcd: Float): Float = (f / gcd).roundToInt() * gcd
}
