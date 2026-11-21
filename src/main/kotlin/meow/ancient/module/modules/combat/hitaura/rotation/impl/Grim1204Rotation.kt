package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.util.rotation.Rotation
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.world.entity.LivingEntity

class Grim1204Rotation : RotationStrategy("Grim 1.20.4") {
    private var attackTarget: LivingEntity? = null
    private var attackRotation: Rotation? = null

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
        // ThunderHack Grim snaps only around the attack, rather than tracking every movement tick.
        val result = ideal.normalizeTo(current)
        attackTarget = target
        attackRotation = result
        return result
    }

    fun getAttackRotation(target: LivingEntity): Rotation? =
        attackRotation?.takeIf { attackTarget === target }?.copy()

    override fun reset() {
        attackTarget = null
        attackRotation = null
    }
}
