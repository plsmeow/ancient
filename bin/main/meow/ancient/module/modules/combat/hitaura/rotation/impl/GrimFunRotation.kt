package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.world.entity.LivingEntity

class GrimFunRotation : RotationStrategy("GrimFun") {
    private var pitchDown = false

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
        val cooldownReady = player.getAttackStrengthScale(0.5f) >= 0.98f

        val pitch = if (cooldownReady) {
            ideal.pitch
        } else {
            val p = if (pitchDown) -80.0f else 90.0f
            pitchDown = !pitchDown
            p
        }

        return Rotation(ideal.yaw, pitch)
    }

    override fun reset() {
        pitchDown = false
    }
}
