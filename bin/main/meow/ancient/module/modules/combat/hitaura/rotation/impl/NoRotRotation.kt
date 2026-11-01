package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.box
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationUtil
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import kotlin.math.abs
import kotlin.math.sin

class NoRotRotation : RotationStrategy("NoRot") {
    private var speedAcceleration = 0.0f

    override fun shouldUpdateRotation(shouldSnapNow: Boolean): Boolean {
        val player = mc.player ?: return false
        return player.isFallFlying
    }

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
        val player = mc.player ?: return current
        if (!player.isFallFlying) {
            return current
        }

        val time = System.nanoTime() * 1e-9
        val sinTime = abs(sin(time * 19.0)).toFloat()

        val lookingAtTarget = RayTraceUtil.isLookingAtEntity(target, current, 999.0, true)
        if (!lookingAtTarget) {
            speedAcceleration += sinTime / 666.0f
        } else {
            if (speedAcceleration >= 0.02f) {
                speedAcceleration -= 0.02f
            }
        }

        val targetVec = target.box.center.add(0.0, sinTime / 2.0, 0.0)
        val angle = RotationUtil.getRotationToVec(targetVec).normalizeTo(current)

        val dYaw = Mth.wrapDegrees(angle.yaw - current.yaw)
        val dPitch = angle.pitch - current.pitch

        val smooth = speedAcceleration.coerceIn(0.0f, 0.2f)
        val newYaw = current.yaw + dYaw * smooth
        val newPitch = (current.pitch + dPitch * (smooth / 3.0f)).coerceIn(-90.0f, 90.0f)

        return Rotation(newYaw, newPitch)
    }

    override fun reset() {
        speedAcceleration = 0.0f
    }
}
