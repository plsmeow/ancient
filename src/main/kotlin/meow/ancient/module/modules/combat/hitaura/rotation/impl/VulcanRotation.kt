package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.world.entity.LivingEntity

class VulcanRotation : RotationStrategy("Vulcan", keepTicks = 0) {
    override fun shouldUpdateRotation(shouldSnapNow: Boolean): Boolean {
        val player = mc.player ?: return false
        return shouldSnapNow || player.getAttackStrengthScale(0.5f) >= 0.9f
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
        if (player.getAttackStrengthScale(0.5f) < 0.9f) {
            return current
        }
        return Rotation(ideal.yaw, ideal.pitch.coerceIn(-89.9f, 89.9f))
    }
}
