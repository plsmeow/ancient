package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.world.entity.LivingEntity
import java.util.concurrent.ThreadLocalRandom

class Grim1204Rotation : RotationStrategy("Grim 1.20.4") {
    private var pitchAcceleration = 1.0f

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

        val lookingAtTarget = RayTraceUtil.isLookingAtEntity(target, current, 999.0, true)
        pitchAcceleration = if (lookingAtTarget) {
            AIMED_PITCH_STEP
        } else if (pitchAcceleration < MAX_PITCH_STEP) {
            pitchAcceleration * PITCH_ACCELERATE
        } else {
            MAX_PITCH_STEP
        }

        val random = ThreadLocalRandom.current()
        val yawStep = random.nextFloat(MIN_YAW_STEP, MAX_YAW_STEP)
        val pitchStep = pitchAcceleration + random.nextFloat(-1.0f, 1.0f)

        val stepYaw = deltaYaw.coerceIn(-yawStep, yawStep)
        val stepPitch = deltaPitch.coerceIn(-pitchStep, pitchStep)

        val newYaw = current.yaw + stepYaw
        val newPitch = (current.pitch + stepPitch).coerceIn(-90.0f, 90.0f)

        return Rotation(newYaw, newPitch)
    }

    override fun reset() {
        pitchAcceleration = 1.0f
    }

    companion object {
        private const val MIN_YAW_STEP = 65.0f
        private const val MAX_YAW_STEP = 75.0f
        private const val AIMED_PITCH_STEP = 1.0f
        private const val MAX_PITCH_STEP = 8.0f
        private const val PITCH_ACCELERATE = 1.65f
    }
}
