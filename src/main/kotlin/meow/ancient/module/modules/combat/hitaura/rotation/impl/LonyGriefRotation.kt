package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import kotlin.math.abs

class LonyGriefRotation : RotationStrategy("LonyGrief") {
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
        val player = mc.player
        val isGliding = player?.isFallFlying == true
        val pon = if (isGliding) 1.2f else 1.0f

        val yawDiff = abs(Mth.wrapDegrees(ideal.yaw - current.yaw))
        val speedMultiplier = (maxOf(speed, 25.0f) / 30.0f).coerceIn(0.8f, 2.5f)
        val maxCap = (0.85f * speedMultiplier / pon).coerceIn(0.6f, 1.0f)

        if (!back) {
            speedAcceleration += (if (yawDiff > 40.0f) 0.08f / pon else 0.06f / pon)
            val isLooking = RayTraceUtil.isLookingAtEntity(target, current, 6.0, true)
            if (speedAcceleration >= maxCap || isLooking) {
                back = true
            }
        } else {
            if (speedAcceleration >= 0.35f) {
                speedAcceleration -= if (yawDiff > 60.0f) 0.05f else 0.02f
            }
            if (speedAcceleration <= 0.35f) {
                back = false
            }
        }

        val smooth = speedAcceleration.coerceIn(0.35f, 1.0f)
        val newYaw = current.yaw + deltaYaw * smooth
        val newPitch = (current.pitch + deltaPitch * (smooth * 0.75f)).coerceIn(-90.0f, 90.0f)

        return Rotation(newYaw, newPitch)
    }

    override fun reset() {
        speedAcceleration = 0.5f
        back = false
    }
}
