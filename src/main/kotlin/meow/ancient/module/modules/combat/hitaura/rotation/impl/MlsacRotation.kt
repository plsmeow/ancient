package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.GCDUtil
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.world.entity.LivingEntity
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MlsacRotation : RotationStrategy("MlsAC") {
    private var tracked: LivingEntity? = null
    private var initialized = false

    private var velYaw = 0.0f
    private var velPitch = 0.0f

    private var tremorYaw = 0.0f
    private var tremorPitch = 0.0f

    private var residualYaw = 0.0f
    private var residualPitch = 0.0f

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

        if (tracked != target || !initialized) {
            tracked = target
            initialized = true
            velYaw = 0.0f
            velPitch = 0.0f
            tremorYaw = 0.0f
            tremorPitch = 0.0f
            residualYaw = 0.0f
            residualPitch = 0.0f
        }

        val gcd = GCDUtil.getGCD().coerceAtLeast(0.015f)
        val onTarget = RayTraceUtil.isLookingAtEntity(target, current, 6.0, true)

        val rng = ThreadLocalRandom.current()
        val rho = 0.70f
        val rhoScale = sqrt(1.0f - rho * rho)
        tremorYaw = rho * tremorYaw + rhoScale * (rng.nextGaussian().toFloat() * gcd * 0.40f)
        tremorPitch = rho * tremorPitch + rhoScale * (rng.nextGaussian().toFloat() * gcd * 0.30f)

        val planYaw: Float
        val planPitch: Float

        if (onTarget) {
            velYaw *= 0.35f
            velPitch *= 0.35f
            planYaw = velYaw + tremorYaw
            planPitch = velPitch + tremorPitch
        } else {
            val desiredSpeed = (totalAngle * 0.75f).coerceIn(12.0f, 65.0f)
            val dirYaw = deltaYaw / maxOf(totalAngle, 0.001f)
            val dirPitch = deltaPitch / maxOf(totalAngle, 0.001f)

            val targetVelY = dirYaw * desiredSpeed
            val targetVelP = dirPitch * desiredSpeed

            velYaw += (targetVelY - velYaw) * 0.65f
            velPitch += (targetVelP - velPitch) * 0.65f

            planYaw = velYaw + tremorYaw
            planPitch = velPitch + tremorPitch
        }

        residualYaw += planYaw
        residualPitch += planPitch

        var newYaw = current.yaw
        var newPitch = current.pitch

        val stepsYaw = (residualYaw / gcd).roundToInt()
        if (stepsYaw != 0) {
            residualYaw -= stepsYaw * gcd
            newYaw += stepsYaw * gcd
        }

        val stepsPitch = (residualPitch / gcd).roundToInt()
        if (stepsPitch != 0) {
            residualPitch -= stepsPitch * gcd
            newPitch = (newPitch + stepsPitch * gcd).coerceIn(-90.0f, 90.0f)
        }

        return Rotation(newYaw, newPitch)
    }

    override fun reset() {
        tracked = null
        initialized = false
        velYaw = 0.0f
        velPitch = 0.0f
        tremorYaw = 0.0f
        tremorPitch = 0.0f
        residualYaw = 0.0f
        residualPitch = 0.0f
    }
}
