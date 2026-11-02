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
    private var speedAcceleration = 0.5f
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
        val speedMultiplier = (maxOf(speed, 25.0f) / 30.0f).coerceIn(0.8f, 2.5f)
        val maxCap = (0.85f * speedMultiplier).coerceIn(0.6f, 1.0f)

        if (!back) {
            speedAcceleration += if (isGliding) {
                if (diff > 40.0f) 0.06f else 0.09f
            } else {
                0.08f
            }
            val lookingAtTarget = RayTraceUtil.isLookingAtEntity(target, current, 6.0, true)
            if (speedAcceleration >= maxCap || lookingAtTarget) {
                back = true
            }
        } else {
            if (speedAcceleration >= 0.35f) {
                speedAcceleration -= if (diff > 40.0f) 0.05f else 0.02f
            }
            if (speedAcceleration <= 0.35f) {
                back = false
            }
        }
        val rng = ThreadLocalRandom.current()
        val randomStrength = 0.5f
        val randomYaw = rng.nextDouble(-randomStrength.toDouble(), randomStrength.toDouble()).toFloat()
        val randomPitch = rng.nextDouble(-randomStrength.toDouble(), randomStrength.toDouble()).toFloat()

        val targetYaw = ideal.yaw + randomYaw
        val targetPitch = ideal.pitch + randomPitch

        val smoothVal = speedAcceleration.coerceIn(0.35f, 1.0f)
        val changeYaw = Mth.wrapDegrees(targetYaw - current.yaw) * smoothVal
        val changePitch = (targetPitch - current.pitch) * (smoothVal * 0.75f)

        return Rotation(
            current.yaw + changeYaw,
            (current.pitch + changePitch).coerceIn(-90.0f, 90.0f)
        )
    }

    override fun reset() {
        speedAcceleration = 0.5f
        back = false
    }
}
