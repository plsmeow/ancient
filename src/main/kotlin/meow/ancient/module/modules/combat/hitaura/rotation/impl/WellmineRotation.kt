package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs

class WellmineRotation : RotationStrategy("Wellmine old") {
    private var speedAcceleration = 0.0f
    private var back = false

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
        val isGliding = player.isFallFlying
        val diff = abs(Mth.wrapDegrees(ideal.yaw - current.yaw))

        if (!back) {
            if (speedAcceleration >= 1.0f) {
                speedAcceleration = 0.0f
            } else {
                if (isGliding) {
                    speedAcceleration += if (diff > 40.0f) 0.0025f else 0.005f
                } else {
                    speedAcceleration += 0.005f
                }
            }

            val lookingAtTarget = RayTraceUtil.isLookingAtEntity(target, current, 6.0, true)
            if (speedAcceleration >= 0.18f || lookingAtTarget) {
                back = true
            }
        } else {
            if (speedAcceleration >= -0.01f) {
                speedAcceleration -= if (diff > 40.0f) 0.04f else 0.01f
            }
            if (speedAcceleration <= -0.01f) {
                back = false
            }
        }

        val rng = ThreadLocalRandom.current()
        val randomStrength = 0.75f
        val randomYaw = rng.nextDouble(-randomStrength.toDouble(), randomStrength.toDouble()).toFloat()
        val randomPitch = rng.nextDouble(-randomStrength.toDouble(), randomStrength.toDouble()).toFloat()

        val targetYaw = ideal.yaw + randomYaw
        val targetPitch = ideal.pitch + randomPitch

        val smoothVal = speedAcceleration.coerceIn(-1.0f, 1.0f)
        val changeYaw = Mth.wrapDegrees(targetYaw - current.yaw) * smoothVal
        val changePitch = (targetPitch - current.pitch) * (smoothVal / 2.0f)

        return Rotation(
            current.yaw + changeYaw,
            (current.pitch + changePitch).coerceIn(-90.0f, 90.0f)
        )
    }

    override fun reset() {
        speedAcceleration = 0.0f
        back = false
    }
}
