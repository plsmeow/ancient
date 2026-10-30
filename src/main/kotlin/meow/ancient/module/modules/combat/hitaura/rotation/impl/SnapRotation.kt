package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.util.rotation.Rotation
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.world.entity.LivingEntity

class SnapRotation : RotationStrategy("Snap", keepTicks = 0) {
    override fun shouldUpdateRotation(shouldSnapNow: Boolean): Boolean {
        return shouldSnapNow
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
        return ideal
    }
}
