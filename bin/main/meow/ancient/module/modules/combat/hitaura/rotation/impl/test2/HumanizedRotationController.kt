package meow.ancient.module.modules.combat.hitaura.rotation.impl.test2

import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

class HumanizedRotationController {
    private val trajectory = RotationTrajectory()

    var currentYaw: Float = 0.0f
        private set
    var currentPitch: Float = 0.0f
        private set
    var velocityYaw: Float = 0.0f
        private set
    var velocityPitch: Float = 0.0f
        private set
    var accelerationYaw: Float = 0.0f
        private set
    var accelerationPitch: Float = 0.0f
        private set

    fun init(yaw: Float, pitch: Float) {
        currentYaw = yaw
        currentPitch = pitch
        velocityYaw = 0.0f
        velocityPitch = 0.0f
        accelerationYaw = 0.0f
        accelerationPitch = 0.0f
    }

    fun beginMovement(durationSec: Float) {
        trajectory.begin(currentYaw, currentPitch, durationSec)
    }

    fun update(
        params: Params,
        dtSec: Float,
        targetYaw: Float,
        targetPitch: Float,
        targetVelYaw: Float = 0.0f,
        targetVelPitch: Float = 0.0f
    ) {
        if (dtSec <= 0.0f || dtSec.isNaN()) return

        trajectory.advance(dtSec)
        val trajYaw = trajectory.sampleYaw(targetYaw)
        val trajPitch = trajectory.samplePitch(targetPitch)

        val errorYaw = Mth.wrapDegrees(trajYaw - currentYaw)
        val errorPitch = (Mth.wrapDegrees(trajPitch - currentPitch)).coerceIn(-180.0f, 180.0f)

        val desiredVelocityYaw = (targetVelYaw + errorYaw * params.responseYaw)
            .coerceIn(-params.maxSpeedYaw, params.maxSpeedYaw)
        val desiredVelocityPitch = (targetVelPitch + errorPitch * params.responsePitch)
            .coerceIn(-params.maxSpeedPitch, params.maxSpeedPitch)

        val prevVelocityYaw = velocityYaw
        val prevVelocityPitch = velocityPitch

        velocityYaw = lerpRate(velocityYaw, desiredVelocityYaw, params.accelerationRateYaw, dtSec)
        velocityPitch = lerpRate(velocityPitch, desiredVelocityPitch, params.accelerationRatePitch, dtSec)

        if (abs(errorYaw) < params.dampingZoneYaw) {
            val excess = velocityYaw - targetVelYaw
            velocityYaw = targetVelYaw + excess * params.dampingYaw.pow(dtSec * 20.0f)
        }
        if (abs(errorPitch) < params.dampingZonePitch) {
            val excess = velocityPitch - targetVelPitch
            velocityPitch = targetVelPitch + excess * params.dampingPitch.pow(dtSec * 20.0f)
        }

        accelerationYaw = (velocityYaw - prevVelocityYaw) / dtSec
        accelerationPitch = (velocityPitch - prevVelocityPitch) / dtSec

        currentYaw = Mth.wrapDegrees(currentYaw + velocityYaw * dtSec)
        currentPitch = (currentPitch + velocityPitch * dtSec).coerceIn(-90.0f, 90.0f)
        if (currentPitch >= 89.99f && velocityPitch > 0.0f) velocityPitch = 0.0f
        if (currentPitch <= -89.99f && velocityPitch < 0.0f) velocityPitch = 0.0f
    }

    val progress: Float
        get() = trajectory.progress

    val isTrajectoryFinished: Boolean
        get() = trajectory.isFinished

    fun scaleVelocity(scale: Float) {
        velocityYaw *= scale
        velocityPitch *= scale
    }

    fun reset() {
        velocityYaw = 0.0f
        velocityPitch = 0.0f
        accelerationYaw = 0.0f
        accelerationPitch = 0.0f
    }

    companion object {
        private fun lerpRate(current: Float, target: Float, rate: Float, dtSec: Float): Float {
            val factor = 1.0f - exp(-rate * dtSec)
            return current + (target - current) * factor
        }
    }

    data class Params(
        val responseYaw: Float,
        val responsePitch: Float,
        val maxSpeedYaw: Float,
        val maxSpeedPitch: Float,
        val accelerationRateYaw: Float,
        val accelerationRatePitch: Float,
        val dampingYaw: Float,
        val dampingPitch: Float,
        val dampingZoneYaw: Float,
        val dampingZonePitch: Float
    )
}
