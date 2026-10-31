package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.world.entity.LivingEntity
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class FuntimeRotation : RotationStrategy("Funtime") {
    private var hitCounter = 0
    private var lastHitTime = 0L
    private var wasAttacking = false

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
        val nowMs = System.currentTimeMillis()
        val canAttack = player.getAttackStrengthScale(0.5f) >= 0.9f

        if (totalAngle < 0.001f) {
            return current
        }

        val maxStepYaw = (abs(deltaYaw) / totalAngle) * 130.0f
        val maxStepPitch = (abs(deltaPitch) / totalAngle) * 130.0f

        val stepYaw = deltaYaw.coerceIn(-maxStepYaw, maxStepYaw)
        val stepPitch = deltaPitch.coerceIn(-maxStepPitch, maxStepPitch)

        var nextYaw = current.yaw + stepYaw
        var nextPitch = current.pitch + stepPitch

        val isNewHit = canAttack && !wasAttacking
        if (isNewHit) {
            hitCounter++
            lastHitTime = nowMs
        }
        wasAttacking = canAttack

        if (canAttack) {
            nextYaw = current.yaw + 0.85f * (nextYaw - current.yaw)
            nextPitch = current.pitch + 0.85f * (nextPitch - current.pitch)

            if (isNewHit && hitCounter % 86 == 0 && (nowMs - lastHitTime) < 250) {
                nextPitch = -90.0f
            }
        } else {
            val sinceLastHit = nowMs - lastHitTime
            if (sinceLastHit >= 535) {
                val r = ThreadLocalRandom.current()
                val shakeYaw = (18.0f + r.nextFloat() * 10.0f) * sin(nowMs / 60.0).toFloat()
                val shakePitch = (6.0f + r.nextFloat() * 10.0f) * cos(nowMs / 60.0).toFloat()

                nextYaw = (current.yaw + shakeYaw).coerceIn(current.yaw - 45.0f, current.yaw + 45.0f)
                nextPitch = (current.pitch + shakePitch).coerceIn(current.pitch - 45.0f, current.pitch + 45.0f)
            } else {
                nextYaw = current.yaw
                nextPitch = current.pitch
            }
        }

        return Rotation(nextYaw, nextPitch.coerceIn(-89.0f, 90.0f))
    }

    override fun reset() {
        hitCounter = 0
        lastHitTime = 0L
        wasAttacking = false
    }
}
