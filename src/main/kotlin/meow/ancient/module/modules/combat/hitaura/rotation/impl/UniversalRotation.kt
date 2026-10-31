package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.box
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.GCDUtil
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.*

class UniversalRotation : RotationStrategy("Universal") {
    private var init = false
    private var lastTarget: LivingEntity? = null
    private var lastPlanAge = Int.MIN_VALUE

    private var planYaw = 0f
    private var planPitch = 0f
    private var residualYaw = 0f
    private var residualPitch = 0f
    private var stepYaw = 0f
    private var stepPitch = 0f

    private var noiseYaw1 = 0f
    private var noiseYaw2 = 0f
    private var noisePitch1 = 0f
    private var noisePitch2 = 0f
    private var noiseA1Yaw = 0f
    private var noiseA2Yaw = 0f
    private var noiseA1Pitch = 0f
    private var noiseA2Pitch = 0f

    private var gainNoiseYaw = 0f
    private var gainNoisePitch = 0f

    private var velYaw = 0f
    private var velPitch = 0f
    private var hasPrevTarget = false
    private var prevTargetYaw = 0f
    private var prevTargetPitch = 0f

    private var hasLag = false
    private var lagYaw = 0f
    private var lagPitch = 0f
    private var lagTicks = 0

    private var stopTicks = 0
    private var stopCooldown = 0
    private var overTicks = 0
    private var overDelta = 0f
    private var reactionTicks = 0
    private var refractoryTicks = 0
    private var flickCooldown = 0
    private var holdTicks = 0

    private var lagLerp = 0.55f
    private var velLerp = 0.65f
    private var feedForward = 0.25f
    private var inertiaYaw = 0.28f
    private var inertiaPitch = 0.20f
    private var noiseSigmaYaw = 0.45f
    private var noiseSigmaPitch = 0.90f
    private var gainRho = 0.30f
    private var gainSigma = 0.10f
    private var stopChance = 0.035f
    private var stopMinError = 4.0f
    private var overMinError = 25.0f
    private var overChance = 0.10f
    private var trackRateYaw = 40.0f
    private var trackRatePitch = 15.0f
    private var flickMinError = 65.0f
    private var flickRateYaw = 110.0f
    private var flickCapYaw = 105.0f

    private var speedKYaw = 0.52f
    private var speedKPitch = 0.42f
    private var minSpeedYaw = 1.6f
    private var minSpeedPitch = 1.1f
    private var capMinYaw = 4.0f
    private var capMinPitch = 2.6f
    private var capMaxYaw = 65.0f
    private var capMaxPitch = 30.0f
    private var rampNearYaw = 8.0f
    private var rampNearPitch = 6.0f
    private var rampFarYaw = 48.0f
    private var rampFarPitch = 30.0f
    private var finishZoneYaw = 2.4f
    private var finishZonePitch = 1.8f
    private var flickSpeedK = 0.75f
    private var holdShiftChance = 0.15f
    private var holdShiftDelay = 3
    private var holdShiftYaw = 1.0f
    private var holdShiftPitch = 0.5f
    private var holdJitterYaw = 0.06f
    private var holdJitterPitch = 0.04f

    private var personalityRefreshAt = 0

    private var hasSegment = false
    private var segStartDist = 0f
    private var segProgress = 0f
    private var arcDirYaw = 0f
    private var arcDirPitch = 0f
    private var arcMag = 0f
    private var arcPeak = 0.5f
    private var arcSShape = false
    private var arcSkew = 1.0f

    override fun calculate(
        current: Rotation,
        ideal: Rotation,
        deltaYaw: Float,
        deltaPitch: Float,
        totalAngle: Float,
        target: LivingEntity,
        speed: Float,
        tickCounter: Int
    ): Rotation {
        val player = mc.player ?: return ideal
        val now = System.currentTimeMillis()
        val r = ThreadLocalRandom.current()

        if (!init) {
            init = true
            pickPersonality(r)
            personalityRefreshAt = player.tickCount + 300 + r.nextInt(600)
            lastPlanAge = Int.MIN_VALUE
        }

        if (target != lastTarget) {
            lastTarget = target
            onTargetChanged(target, r)
        }

        val age = player.tickCount
        if (age != lastPlanAge) {
            lastPlanAge = age
            plan(target, now, r, current, ideal)
        }

        val gcd = GCDUtil.getGCD().coerceAtLeast(0.015f)
        residualYaw += planYaw
        residualPitch += planPitch
        planYaw = 0f
        planPitch = 0f

        var newYaw = current.yaw
        var newPitch = current.pitch

        val stepsYaw = (residualYaw / gcd).roundToInt()
        if (stepsYaw != 0) {
            residualYaw -= stepsYaw * gcd
            newYaw += stepsYaw * gcd
        }

        val stepsPitch = (residualPitch / gcd).roundToInt()
        if (stepsPitch != 0) {
            residualPitch -= stepsPitch * gcd
            newPitch = (newPitch + stepsPitch * gcd).coerceIn(-90.0f, 90.0f)
        }

        return Rotation(newYaw, newPitch)
    }

    private fun onTargetChanged(target: LivingEntity, r: ThreadLocalRandom) {
        planYaw = 0f
        planPitch = 0f
        stepYaw = 0f
        stepPitch = 0f
        noiseYaw1 = 0f
        noiseYaw2 = 0f
        noisePitch1 = 0f
        noisePitch2 = 0f
        gainNoiseYaw = 0f
        gainNoisePitch = 0f
        velYaw = 0f
        velPitch = 0f
        hasPrevTarget = false
        hasLag = false
        lagTicks = 0
        stopTicks = 0
        stopCooldown = 0
        overTicks = 0
        overDelta = 0f
        refractoryTicks = 0
        flickCooldown = 0
        holdTicks = 0
        hasSegment = false
        segStartDist = 0f
        segProgress = 0f
        arcMag = 0f
        personalityRefreshAt = (mc.player?.tickCount ?: 0) + 300 + r.nextInt(600)
        reactionTicks = 1 + r.nextInt(3)
        pickPersonality(r)
    }

    private fun pickPersonality(r: ThreadLocalRandom) {
        val style = r.nextFloat()

        lagLerp = r.nextFloat(0.45f, 0.65f)
        velLerp = r.nextFloat(0.55f, 0.75f)
        feedForward = r.nextFloat(0.15f, 0.35f)
        inertiaYaw = r.nextFloat(Mth.lerp(style, 0.14f, 0.02f), Mth.lerp(style, 0.24f, 0.10f))
        inertiaPitch = r.nextFloat(0.06f, 0.20f)
        noiseSigmaYaw = r.nextFloat(Mth.lerp(style, 0.50f, 1.00f), Mth.lerp(style, 1.10f, 1.80f))
        noiseSigmaPitch = r.nextFloat(0.80f, 1.50f)
        gainRho = r.nextFloat(0.20f, 0.40f)
        gainSigma = r.nextFloat(0.06f, 0.14f)
        stopChance = r.nextFloat(0.025f, 0.050f)
        stopMinError = r.nextFloat(3.0f, 5.0f)
        overMinError = r.nextFloat(20.0f, 30.0f)
        overChance = r.nextFloat(0.06f, 0.14f)
        trackRateYaw = r.nextFloat(34.0f, 48.0f)
        trackRatePitch = r.nextFloat(13.0f, 17.0f)
        flickMinError = r.nextFloat(55.0f, 75.0f)
        flickRateYaw = r.nextFloat(90.0f, 130.0f)
        flickCapYaw = r.nextFloat(85.0f, 125.0f)

        speedKYaw = r.nextFloat(0.45f, 0.60f)
        speedKPitch = r.nextFloat(0.35f, 0.50f)
        minSpeedYaw = r.nextFloat(1.2f, 2.0f)
        minSpeedPitch = r.nextFloat(0.8f, 1.4f)
        capMinYaw = r.nextFloat(3.0f, 5.0f)
        capMinPitch = r.nextFloat(2.0f, 3.2f)
        capMaxYaw = r.nextFloat(55.0f, 80.0f)
        capMaxPitch = r.nextFloat(25.0f, 38.0f)
        rampNearYaw = r.nextFloat(6.0f, 10.0f)
        rampNearPitch = r.nextFloat(4.0f, 7.0f)
        rampFarYaw = r.nextFloat(40.0f, 55.0f)
        rampFarPitch = r.nextFloat(24.0f, 36.0f)
        finishZoneYaw = r.nextFloat(1.8f, 3.0f)
        finishZonePitch = r.nextFloat(1.2f, 2.2f)
        flickSpeedK = r.nextFloat(0.65f, 0.85f)

        val rhoYaw = r.nextFloat(0.75f, 0.90f)
        val periodYaw = r.nextFloat(Mth.lerp(style, 4.5f, 2.8f), Mth.lerp(style, 6.5f, 4.2f))
        noiseA1Yaw = (2.0 * rhoYaw * cos(2.0 * Math.PI / periodYaw)).toFloat()
        noiseA2Yaw = -rhoYaw * rhoYaw

        val rhoPitch = r.nextFloat(0.45f, 0.70f)
        val periodPitch = r.nextFloat(3.5f, 6.0f)
        noiseA1Pitch = (2.0 * rhoPitch * cos(2.0 * Math.PI / periodPitch)).toFloat()
        noiseA2Pitch = -rhoPitch * rhoPitch
    }

    private fun plan(
        target: LivingEntity,
        now: Long,
        r: ThreadLocalRandom,
        current: Rotation,
        ideal: Rotation
    ) {
        val player = mc.player ?: return

        if (!hasSegment && player.tickCount >= personalityRefreshAt) {
            pickPersonality(r)
            personalityRefreshAt = player.tickCount + 300 + r.nextInt(600)
        }

        val targetYaw = ideal.yaw
        val targetPitch = ideal.pitch

        if (!hasPrevTarget) {
            hasPrevTarget = true
            prevTargetYaw = targetYaw
            prevTargetPitch = targetPitch
        }
        val rawVelYaw = clampAbs(Mth.wrapDegrees(targetYaw - prevTargetYaw), trackRateYaw)
        val rawVelPitch = clampAbs(targetPitch - prevTargetPitch, trackRatePitch)
        prevTargetYaw = targetYaw
        prevTargetPitch = targetPitch
        velYaw = Mth.lerp(velLerp, velYaw, rawVelYaw)
        velPitch = Mth.lerp(velLerp, velPitch, rawVelPitch)

        if (reactionTicks > 0) {
            reactionTicks--
            hasLag = false
            planYaw = 0f
            planPitch = 0f
            stepYaw = 0f
            stepPitch = 0f
            return
        }

        val onTarget = RayTraceUtil.isLookingAtEntity(target, current, 999.0, true)
        if (onTarget) {
            holdTicks++
            stepYaw *= inertiaYaw
            stepPitch *= inertiaPitch
            planYaw = (r.nextFloat() - 0.5f) * 2f * holdJitterYaw
            planPitch = (r.nextFloat() - 0.5f) * 2f * holdJitterPitch
            if (holdTicks > holdShiftDelay && r.nextFloat() < holdShiftChance) {
                planYaw += (r.nextFloat() - 0.5f) * 2f * holdShiftYaw
                planPitch += (r.nextFloat() - 0.5f) * 2f * holdShiftPitch
            }
            hasLag = false
            return
        }
        holdTicks = 0

        val flick = flickCooldown <= 0 && abs(Mth.wrapDegrees(targetYaw - current.yaw)) > flickMinError
        if (flickCooldown > 0) flickCooldown--

        if (!hasLag || lagTicks <= 0) {
            hasLag = true
            lagYaw = current.yaw
            lagPitch = current.pitch
            lagTicks = 1 + r.nextInt(3)
        }
        lagTicks--
        val lagRateYaw = if (flick) flickRateYaw else trackRateYaw
        val lagRatePitch = if (flick) trackRatePitch * 2.5f else trackRatePitch
        lagYaw += clampAbs(Mth.wrapDegrees(targetYaw - lagYaw) * lagLerp, lagRateYaw)
        lagPitch += clampAbs((targetPitch - lagPitch) * lagLerp, lagRatePitch)

        val noiseYaw = noiseA1Yaw * noiseYaw1 + noiseA2Yaw * noiseYaw2 + r.nextGaussian().toFloat() * noiseSigmaYaw
        noiseYaw2 = noiseYaw1
        noiseYaw1 = noiseYaw

        val noisePitch = noiseA1Pitch * noisePitch1 + noiseA2Pitch * noisePitch2 + r.nextGaussian().toFloat() * noiseSigmaPitch
        noisePitch2 = noisePitch1
        noisePitch1 = noisePitch

        val curYaw = current.yaw
        val curPitch = current.pitch
        val directYaw = Mth.wrapDegrees(lagYaw + noiseYaw - curYaw)
        val directPitch = (lagPitch + noisePitch) - curPitch
        val directDist = hypot(directYaw, directPitch)

        updateArcSegment(r, directYaw, directPitch, directDist)
        val arcScale = if (hasSegment) arcHump(segProgress) * arcMag else 0f
        val errorYaw = directYaw + arcDirYaw * arcScale
        val errorPitch = directPitch + arcDirPitch * arcScale
        val errorDist = hypot(errorYaw, errorPitch)

        if (stopCooldown > 0) stopCooldown--
        if (stopTicks <= 0 && stopCooldown <= 0 && errorDist > stopMinError && r.nextFloat() < stopChance) {
            stopTicks = 1 + r.nextInt(2)
            stopCooldown = 5 + r.nextInt(7)
        }
        if (stopTicks > 0) {
            stopTicks--
            stepYaw *= inertiaYaw
            stepPitch *= inertiaPitch
            planYaw = clampAbs(stepYaw, capMinYaw)
            planPitch = clampAbs(stepPitch, capMinPitch)
            return
        }

        if (overTicks <= 0 && abs(errorYaw) > overMinError && r.nextFloat() < overChance) {
            overTicks = 2 + r.nextInt(2)
            overDelta = withSign(r.nextFloat(2f, 4f), errorYaw)
        }
        var overshoot = 0f
        if (overTicks > 0) {
            overTicks--
            overshoot = overDelta * (overTicks / 3f)
        }

        gainNoiseYaw = gainRho * gainNoiseYaw + r.nextGaussian().toFloat() * gainSigma
        gainNoisePitch = gainRho * gainNoisePitch + r.nextGaussian().toFloat() * gainSigma

        val finishYaw = abs(errorYaw) < finishZoneYaw
        val finishPitch = abs(errorPitch) < finishZonePitch

        val kYaw = if (flick) flickSpeedK else speedKYaw
        val kPitch = if (flick) minOf(1f, speedKPitch * 1.6f) else speedKPitch
        var wantYaw = dynamicStep(errorYaw, kYaw, minSpeedYaw, capMinYaw, capMaxYaw, rampNearYaw, rampFarYaw, finishZoneYaw)
        var wantPitch = dynamicStep(errorPitch, kPitch, minSpeedPitch, capMinPitch, capMaxPitch, rampNearPitch, rampFarPitch, finishZonePitch)
        if (!finishYaw) {
            wantYaw = wantYaw * exp(gainNoiseYaw) + velYaw * feedForward + overshoot * kYaw
        }
        if (!finishPitch) {
            wantPitch = wantPitch * exp(gainNoisePitch) + velPitch * feedForward
        }

        stepYaw = if (finishYaw) wantYaw else Mth.lerp(inertiaYaw, stepYaw, wantYaw)
        stepPitch = if (finishPitch) wantPitch else Mth.lerp(inertiaPitch, stepPitch, wantPitch)

        var capYaw = dynamicCap(abs(errorYaw), capMinYaw, if (flick) maxOf(flickCapYaw, capMaxYaw) else capMaxYaw, rampNearYaw, rampFarYaw)
        var capPitch = dynamicCap(abs(errorPitch), capMinPitch, if (flick) capMaxPitch * 2.2f else capMaxPitch, rampNearPitch, rampFarPitch)
        if (!flick && refractoryTicks > 0) {
            refractoryTicks--
            capYaw *= 0.45f
            capPitch *= 0.55f
        }

        val emitYaw = clampAbs(stepYaw, capYaw)
        val emitPitch = clampAbs(stepPitch, capPitch)
        if (flick && abs(emitYaw) > capMaxYaw * 0.8f) {
            flickCooldown = 25 + r.nextInt(36)
        } else if (!flick && abs(emitYaw) > capMaxYaw * 0.7f) {
            refractoryTicks = 1 + r.nextInt(2)
        }

        planYaw = emitYaw
        planPitch = emitPitch
    }

    private fun updateArcSegment(r: ThreadLocalRandom, errYaw: Float, errPitch: Float, errDist: Float) {
        val strengthScale = 1.0f

        if (hasSegment) {
            if (errDist > segStartDist * 1.3f + 3f) {
                hasSegment = false
            } else if (errDist < maxOf(finishZoneYaw, finishZonePitch)) {
                hasSegment = false
                return
            } else {
                val t = 1f - errDist / segStartDist
                segProgress = maxOf(segProgress, t.coerceIn(0f, 1f))
                return
            }
        }

        if (errDist > 6f) {
            startArcSegment(r, errYaw, errPitch, errDist, strengthScale)
        }
    }

    private fun startArcSegment(r: ThreadLocalRandom, errYaw: Float, errPitch: Float, errDist: Float, strengthScale: Float) {
        hasSegment = true
        segStartDist = maxOf(errDist, 1.0e-3f)
        segProgress = 0f

        val roll = r.nextFloat()
        val frac = if (roll < 0.15f) r.nextFloat(0.02f, 0.08f) else r.nextFloat(0.10f, 0.42f)
        arcMag = minOf(frac * segStartDist, r.nextFloat(3.5f, 9.0f)) * strengthScale

        val len = hypot(errYaw, errPitch)
        val pathYaw = if (len < 1.0e-4f) 1f else errYaw / len
        val pathPitch = if (len < 1.0e-4f) 0f else errPitch / len
        val tilt = (r.nextFloat() - 0.5f) * 0.6f
        val side = if (r.nextBoolean()) 1f else -1f
        arcDirYaw = side * (-pathPitch + tilt * pathYaw)
        arcDirPitch = side * (pathYaw + tilt * pathPitch)

        arcPeak = r.nextFloat(0.25f, 0.75f)
        arcSShape = r.nextFloat() < 0.25f
        arcSkew = r.nextFloat(0.6f, 1.7f)
    }

    private fun arcHump(t: Float): Float {
        if (arcSShape) {
            return sin(2.0 * Math.PI * t.toDouble().pow(arcSkew.toDouble())).toFloat()
        }
        if (t < arcPeak) {
            return sin(0.5 * Math.PI * (t / arcPeak)).toFloat()
        }
        return sin(0.5 * Math.PI * (1f - (t - arcPeak) / (1f - arcPeak))).toFloat()
    }

    private fun dynamicCap(absError: Float, capMin: Float, capMax: Float, rampNear: Float, rampFar: Float): Float {
        val t = ((absError - rampNear) / maxOf(1f, rampFar - rampNear)).coerceIn(0f, 1f)
        return Mth.lerp(smoothStep(t), capMin, capMax)
    }

    private fun dynamicStep(
        error: Float, speedK: Float, minSpeed: Float, capMin: Float,
        capMax: Float, rampNear: Float, rampFar: Float, finishZone: Float
    ): Float {
        val absError = abs(error)
        if (absError < finishZone) return error
        val cap = dynamicCap(absError, capMin, capMax, rampNear, rampFar)
        return withSign((absError * speedK).coerceIn(minSpeed, cap), error)
    }

    override fun reset() {
        init = false
        lastTarget = null
        lastPlanAge = Int.MIN_VALUE
        planYaw = 0f
        planPitch = 0f
        residualYaw = 0f
        residualPitch = 0f
        stepYaw = 0f
        stepPitch = 0f
        noiseYaw1 = 0f
        noiseYaw2 = 0f
        noisePitch1 = 0f
        noisePitch2 = 0f
        gainNoiseYaw = 0f
        gainNoisePitch = 0f
        velYaw = 0f
        velPitch = 0f
        hasPrevTarget = false
        hasLag = false
        lagTicks = 0
        stopTicks = 0
        stopCooldown = 0
        overTicks = 0
        overDelta = 0f
        reactionTicks = 0
        refractoryTicks = 0
        flickCooldown = 0
        holdTicks = 0
        hasSegment = false
        segStartDist = 0f
        segProgress = 0f
        arcDirYaw = 0f
        arcDirPitch = 0f
        arcMag = 0f
        personalityRefreshAt = 0
    }

    companion object {
        private fun clampAbs(value: Float, limit: Float): Float {
            return if (abs(value) > limit) withSign(limit, value) else value
        }

        private fun smoothStep(x: Float): Float {
            val c = x.coerceIn(0f, 1f)
            return c * c * (3f - 2f * c)
        }

        private fun withSign(magnitude: Float, sign: Float): Float {
            return if (sign < 0f) -abs(magnitude) else abs(magnitude)
        }
    }
}
