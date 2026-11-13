package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.box
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.combat.TpAuraModule
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import meow.ancient.module.modules.combat.hitaura.rotation.impl.test2.*
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

open class Test2Rotation(name: String = "Test2") : RotationStrategy(name) {
    protected val targetPointSelector = TargetPointSelector()
    protected val approachController = ApproachController()
    protected val controller = HumanizedRotationController()
    protected val correctionController = VerticalCorrectionController()
    protected val noise = RotationNoise()
    val state = RotationState()

    protected var init = false
    protected var targetId = Int.MIN_VALUE
    protected var verticalFactor = 0.5f
    protected var cycleMaxSpeedScale = 1.0f
    protected var recoveryUntilMs = 0L
    protected var lastFrameNanos = 0L

    protected var smoothCenter: Vec3? = null
    protected var targetVelSmooth: Vec3 = Vec3.ZERO
    protected var cycleOffsetX = 0.0f
    protected var cycleOffsetZ = 0.0f
    protected var lastHitPoint: Vec3? = null
    protected var smoothVelYaw = 0.0f
    protected var smoothVelPitch = 0.0f

    protected open val offsetMin = 3.0f
    protected open val offsetMax = 9.0f
    protected open val approachSpeed = 300.0f
    protected open val correctionSpeed = 320.0f
    protected open val minDuration = 0.15f
    protected open val maxDuration = 0.35f
    protected open val yawAccel = 15.0f
    protected open val pitchAccel = 9.0f
    protected open val yawDamping = 0.72f
    protected open val pitchDamping = 0.68f
    protected open val microAmp = 0.12f
    protected open val microFreq = 1.6f
    protected open val overshootStrength = 0.4f
    protected open val overshootChance = 0.3f
    protected open val dynamicSpeed = true
    protected open val dynamicDuration = true
    protected open val targetPointMode = TargetPointSelector.Mode.ABOVE_CENTER
    protected open val attackCooldownThreshold = 0.9f
    protected open val requireYawForAttack = false

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
        val nowNanos = System.nanoTime()
        val r = ThreadLocalRandom.current()

        var dtSec = if (lastFrameNanos == 0L) 0.05f else (nowNanos - lastFrameNanos) / 1_000_000_000.0f
        if (!init) {
            controller.init(current.yaw, current.pitch)
            lastFrameNanos = nowNanos
            init = true
            state.reset()
            beginCycle(target, false, current)
            lastFrameNanos = nowNanos
            return current
        }
        if (dtSec <= 0.0f || dtSec.isNaN()) {
            lastFrameNanos = nowNanos
            return current
        }
        dtSec = minOf(dtSec, 0.1f)
        lastFrameNanos = nowNanos

        val retargeted = targetId != target.id
        if (retargeted) {
            controller.scaleVelocity(0.7f)
            noise.scaleAmplitude(0.7f)
            beginCycle(target, true, current)
        }

        val preferredPoint = resolveHitPoint(target, dtSec)
        val hitPoint = if (HitAuraModule.wallCheck && TpAuraModule.getRenderPosition(target) == null)
            RayTraceUtil.findVisiblePoint(target, HitAuraModule.attackRange + HitAuraModule.aimRange, preferredPoint) ?: return current
            else preferredPoint
        val actual = RotationUtil.getRotationToVec(hitPoint, player.eyePosition)
        val actualYaw = actual.yaw
        val actualPitch = actual.pitch

        var targetVelYaw = 0.0f
        var targetVelPitch = 0.0f
        val lastPoint = lastHitPoint
        if (lastPoint != null && dtSec > 0.0f) {
            val lastAngle = RotationUtil.getRotationToVec(lastPoint, player.eyePosition)
            val rawVelYaw = Mth.wrapDegrees(actualYaw - lastAngle.yaw) / dtSec
            val rawVelPitch = (Mth.wrapDegrees(actualPitch - lastAngle.pitch) / dtSec).coerceIn(-300.0f, 300.0f)
            val vf = 1.0f - exp(-12.0 * dtSec).toFloat()
            smoothVelYaw += (rawVelYaw - smoothVelYaw) * vf
            smoothVelPitch += (rawVelPitch - smoothVelPitch) * vf
            targetVelYaw = smoothVelYaw.coerceIn(-240.0f, 240.0f)
            targetVelPitch = smoothVelPitch.coerceIn(-180.0f, 180.0f)
        }
        lastHitPoint = hitPoint
        val approachPitch = approachController.approachPitch(actualPitch)

        val distBlocks = player.eyePosition.distanceTo(hitPoint)
        val attackImminent = player.getAttackStrengthScale(0.5f) >= attackCooldownThreshold && distBlocks <= 6.0

        var phase = state.phase
        if (phase == RotationState.Phase.IDLE || retargeted) {
            phase = if (retargeted) RotationState.Phase.RECOVERY else RotationState.Phase.APPROACH
        }

        when (phase) {
            RotationState.Phase.RECOVERY -> {
                if (now >= recoveryUntilMs) phase = RotationState.Phase.APPROACH
            }
            RotationState.Phase.APPROACH -> {
                val pitchError = abs(Mth.wrapDegrees(controller.currentPitch - approachPitch))
                if (controller.isTrajectoryFinished && pitchError < 4.0f) {
                    val entrySpeed = abs(controller.velocityPitch)
                    if (entrySpeed > 25.0f && r.nextFloat() < overshootChance) {
                        controller.scaleVelocity(1.0f + overshootStrength * 0.6f)
                    }
                    phase = RotationState.Phase.SETTLE
                }
            }
            RotationState.Phase.SETTLE -> {
                if (attackImminent) {
                    correctionController.begin(controller.currentPitch, actualPitch, correctionSpeed)
                    phase = RotationState.Phase.CORRECTION
                }
            }
            RotationState.Phase.CORRECTION -> {
                correctionController.update(dtSec)
                val pitchError = abs(controller.currentPitch - actualPitch)
                val yawError = abs(Mth.wrapDegrees(controller.currentYaw - actualYaw))
                if (!attackImminent) {
                    correctionController.deactivate()
                    phase = RotationState.Phase.SETTLE
                } else if (correctionController.isFinished && pitchError < 1.2f && (!requireYawForAttack || yawError < 2.0f)) {
                    phase = RotationState.Phase.ATTACK
                }
            }
            RotationState.Phase.ATTACK -> {
                if (!attackImminent) {
                    phase = RotationState.Phase.SETTLE
                }
            }
            else -> {}
        }

        val pitchTarget: Float
        val params: HumanizedRotationController.Params
        val elytraScale = if (player.isFallFlying) 0.45f else 1.0f
        val approachMax = approachSpeed * cycleMaxSpeedScale * elytraScale
        val correctionMax = correctionSpeed * elytraScale

        when (phase) {
            RotationState.Phase.SETTLE -> {
                pitchTarget = approachPitch
                params = HumanizedRotationController.Params(
                    9.0f, 4.0f,
                    approachMax * 0.85f, approachMax * 0.5f,
                    yawAccel, pitchAccel * 0.8f,
                    yawDamping, pitchDamping,
                    6.0f, 6.0f
                )
            }
            RotationState.Phase.CORRECTION, RotationState.Phase.ATTACK -> {
                pitchTarget = if (correctionController.isActive) {
                    correctionController.samplePitch(actualPitch)
                } else {
                    actualPitch
                }
                params = HumanizedRotationController.Params(
                    14.0f, 12.0f,
                    maxOf(approachMax * 0.75f, 60.0f), correctionMax,
                    yawAccel, pitchAccel * 1.5f,
                    yawDamping, maxOf(pitchDamping * 0.7f, 0.2f),
                    3.0f, 2.0f
                )
            }
            RotationState.Phase.RECOVERY -> {
                pitchTarget = approachPitch
                params = HumanizedRotationController.Params(
                    8.0f, 5.0f,
                    approachMax * 0.9f, approachMax * 0.7f,
                    yawAccel * 0.9f, pitchAccel * 0.8f,
                    yawDamping, pitchDamping,
                    6.0f, 4.5f
                )
            }
            else -> {
                pitchTarget = approachPitch
                params = HumanizedRotationController.Params(
                    16.0f, 10.0f,
                    approachMax, approachMax,
                    yawAccel, pitchAccel,
                    yawDamping, pitchDamping,
                    4.0f, 3.0f
                )
            }
        }

        controller.update(params, dtSec, actualYaw, pitchTarget, targetVelYaw, targetVelPitch)

        val phaseNoiseScale = if (phase == RotationState.Phase.CORRECTION || phase == RotationState.Phase.ATTACK) 0.25f else 1.0f
        noise.update(now, dtSec, microFreq, microAmp * 0.4f * phaseNoiseScale, microAmp * phaseNoiseScale, r)

        val outYaw = controller.currentYaw + noise.yaw
        val outPitch = (controller.currentPitch + noise.pitch).coerceIn(-90.0f, 90.0f)

        state.snapshot(
            phase,
            outYaw, outPitch,
            actualYaw, actualPitch,
            approachPitch,
            Mth.wrapDegrees(controller.currentYaw - actualYaw),
            Mth.wrapDegrees(controller.currentPitch - actualPitch),
            controller.velocityYaw, controller.velocityPitch,
            controller.accelerationYaw, controller.accelerationPitch,
            controller.progress,
            phase == RotationState.Phase.ATTACK
        )

        return Rotation(outYaw, outPitch)
    }

    protected fun resolveHitPoint(target: LivingEntity, dtSec: Float): Vec3 {
        val box = target.box
        val center = box.center
        val sCenter = smoothCenter
        if (sCenter == null) {
            smoothCenter = center
            targetVelSmooth = target.deltaMovement
        } else if (dtSec > 0.0f) {
            val centerF = 1.0f - exp(-18.0 * dtSec).toFloat()
            smoothCenter = sCenter.add(center.subtract(sCenter).scale(centerF.toDouble()))
            val velF = 1.0f - exp(-10.0 * dtSec).toFloat()
            targetVelSmooth = targetVelSmooth.add(target.deltaMovement.subtract(targetVelSmooth).scale(velF.toDouble()))
        }

        val currentCenter = smoothCenter ?: center
        val eyes = mc.player?.eyePosition ?: Vec3.ZERO
        val dist = eyes.distanceTo(center)
        val lead = (dist * 0.025).coerceIn(0.03, 0.12)
        val predicted = currentCenter.add(targetVelSmooth.scale(lead))

        val halfW = (box.maxX - box.minX) * 0.5
        val halfD = (box.maxZ - box.minZ) * 0.5
        val ox = cycleOffsetX.coerceIn(-1.0f, 1.0f) * halfW * 0.6
        val oz = cycleOffsetZ.coerceIn(-1.0f, 1.0f) * halfD * 0.6

        val factor = verticalFactor.coerceIn(0.05f, 0.95f)
        val height = maxOf(box.maxY - box.minY, 0.1)
        return Vec3(predicted.x + ox, box.minY + height * factor, predicted.z + oz)
    }

    protected fun beginCycle(target: LivingEntity, softEntry: Boolean, current: Rotation) {
        val r = ThreadLocalRandom.current()
        val player = mc.player ?: return

        val retarget = softEntry && targetId != target.id
        targetId = target.id

        if (retarget || smoothCenter == null) {
            smoothCenter = target.box.center
            targetVelSmooth = target.deltaMovement
        }

        verticalFactor = targetPointSelector.pickVerticalFactor(r, targetPointMode)
        cycleOffsetX = (r.nextFloat() - 0.5f) * 2.0f
        cycleOffsetZ = (r.nextFloat() - 0.5f) * 2.0f

        val hitPoint = resolveHitPoint(target, 0.0f)
        val actual = RotationUtil.getRotationToVec(hitPoint, player.eyePosition)

        val distBlocks = player.eyePosition.distanceTo(hitPoint)
        approachController.newCycle(
            r, offsetMin, offsetMax,
            45.0f, distBlocks, target.bbHeight.toDouble(), controller.currentPitch
        )
        val approachPitch = approachController.approachPitch(actual.pitch)

        val yawError = Mth.wrapDegrees(actual.yaw - controller.currentYaw)
        val pitchError = Mth.wrapDegrees(approachPitch - controller.currentPitch)
        var angular = hypot(yawError, pitchError)
        if (angular.isNaN()) angular = 30.0f

        val minDur = minDuration
        var maxDur = maxDuration
        if (maxDur < minDur) maxDur = minDur
        var durationMs = if (dynamicDuration) {
            val t = ((angular - 15.0f) / 135.0f).coerceIn(0.0f, 1.0f)
            Mth.lerp(t, minDur, maxDur) * (0.9f + r.nextFloat() * 0.2f)
        } else {
            (minDur + maxDur) * 0.5f
        }
        if (player.isFallFlying) durationMs *= 1.6f

        cycleMaxSpeedScale = if (dynamicSpeed) {
            (angular / 50.0f).coerceIn(0.7f, 1.5f)
        } else {
            1.0f
        }

        controller.beginMovement(durationMs)
        recoveryUntilMs = System.currentTimeMillis() + minOf(250.0f, durationMs * 1000.0f * 0.35f).toLong()
        if (!softEntry) {
            state.snapshot(
                RotationState.Phase.APPROACH,
                current.yaw, current.pitch, actual.yaw, actual.pitch, approachPitch,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, false
            )
        }
    }

    override fun reset() {
        init = false
        targetId = Int.MIN_VALUE
        smoothCenter = null
        targetVelSmooth = Vec3.ZERO
        cycleOffsetX = 0.0f
        cycleOffsetZ = 0.0f
        controller.reset()
        noise.reset()
        correctionController.deactivate()
        state.reset()
        lastHitPoint = null
        smoothVelYaw = 0.0f
        smoothVelPitch = 0.0f
    }
}
