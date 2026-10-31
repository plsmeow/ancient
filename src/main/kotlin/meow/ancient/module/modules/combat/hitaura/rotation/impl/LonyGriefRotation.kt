package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import kotlin.math.abs

class LonyGriefRotation : RotationStrategy("LonyGrief") {
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
        val player = mc.player
        val isGliding = player?.isFallFlying == true
        val pon = if (isGliding) 1.35f else 1.0f

        val yawDiff = abs(Mth.wrapDegrees(ideal.yaw - current.yaw))

        if (!back) {
            speedAcceleration += if (yawDiff > 40.0f) 0.005f / pon else 0.0038f / pon
            val isLooking = RayTraceUtil.isLookingAtEntity(target, current, 6.0, true)
            if (speedAcceleration >= 0.16f / pon || isLooking) {
                back = true
            }
        } else {
            if (speedAcceleration >= -0.01f) {
                speedAcceleration -= if (yawDiff > 60.0f) 0.06f else 0.01f
            }
            if (speedAcceleration <= -0.01f) {
                back = false
            }
        }

        val smooth = speedAcceleration.coerceIn(0.0f, 1.0f)
        val newYaw = current.yaw + deltaYaw * smooth
        val newPitch = (current.pitch + deltaPitch * (smooth / 2.0f)).coerceIn(-90.0f, 90.0f)

        return Rotation(newYaw, newPitch)
    }

    override fun reset() {
        speedAcceleration = 0.0f
        back = false
    }
}
