package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.RayTraceUtil
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.module.modules.combat.hitaura.rotation.RotationStrategy
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.hypot

class SpookyTimeRotation : RotationStrategy("SpookyTime") {
    private var yawSpeed = 0.45f
    private var pitchSpeed = 0.35f
    private var aimFatigue = 0.0f
    private var idleTicks = 0
    private var reactionDelay = 0
    private var jitterYaw = 0.0f
    private var jitterPitch = 0.0f
    private var lastJitterTime = 0L

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
        val now = System.currentTimeMillis()
        val r = ThreadLocalRandom.current()

        var dYaw = deltaYaw
        var dPitch = deltaPitch

        val hasTrace = RayTraceUtil.isLookingAtEntity(target, current, 999.0, true)
        val isElytra = player.isFallFlying
        val angularDist = hypot(dYaw, dPitch)

        if (now - lastJitterTime > r.nextInt(30, 80)) {
            jitterYaw = (r.nextFloat() - 0.5f) * 0.12f
            jitterPitch = (r.nextFloat() - 0.5f) * 0.08f
            lastJitterTime = now
        }

        if (!hasTrace) {
            val reactionThreshold = 2 + minOf(idleTicks / 3, 5) + r.nextInt(2)
            if (reactionDelay < reactionThreshold) {
                reactionDelay++
                dYaw += (r.nextFloat() - 0.5f) * 1.2f
                dPitch += (r.nextFloat() - 0.5f) * 0.6f
            } else {
                val targetSpeed = if (angularDist > 12.0f) 0.72f else 0.35f
                yawSpeed = Mth.lerp(0.035f, yawSpeed, targetSpeed)
                pitchSpeed = Mth.lerp(0.035f, pitchSpeed, targetSpeed)
            }
            idleTicks = 0
        } else {
            reactionDelay = 0
            yawSpeed = Mth.lerp(0.55f, yawSpeed, 0.0f)
            pitchSpeed = Mth.lerp(0.55f, pitchSpeed, 0.0f)
            idleTicks++
        }

        aimFatigue = (aimFatigue + (angularDist * 0.00008f)).coerceIn(0.0f, 0.32f)
        if (hasTrace) aimFatigue *= 0.97f

        val elytraDiv = if (isElytra) 2.2f else 1.0f
        val baseYawSpeed = r.nextFloat(8.0f, 14.0f) / elytraDiv
        val basePitchSpeed = r.nextFloat(4.0f, 8.0f) / elytraDiv

        val yawS = yawSpeed * yawSpeed * (3.0f - 2.0f * yawSpeed)
        val pitchS = pitchSpeed * pitchSpeed * (3.0f - 2.0f * pitchSpeed)

        val finalYawSpeed = baseYawSpeed * yawS * (1.0f - aimFatigue)
        val finalPitchSpeed = basePitchSpeed * pitchS * (1.0f - aimFatigue)

        if (!player.onGround()) {
            dPitch *= 0.12f
            dYaw *= 0.85f
        }

        if (hasTrace && idleTicks > 3 && r.nextFloat() < 0.15f) {
            dYaw += (r.nextFloat() - 0.5f) * 2.0f
            dPitch += (r.nextFloat() - 0.5f) * 1.0f
        }

        var clampedYaw = dYaw.coerceIn(-finalYawSpeed, finalYawSpeed)
        var clampedPitch = dPitch.coerceIn(-finalPitchSpeed, finalPitchSpeed)

        if (hasTrace && abs(clampedYaw) < 0.35f) clampedYaw = 0.0f
        if (hasTrace && abs(clampedPitch) < 0.18f) clampedPitch = 0.0f

        val newYaw = current.yaw + clampedYaw + jitterYaw
        val newPitch = (current.pitch + clampedPitch + jitterPitch).coerceIn(-90.0f, 90.0f)

        return Rotation(newYaw, newPitch)
    }

    override fun reset() {
        yawSpeed = 0.45f
        pitchSpeed = 0.35f
        aimFatigue = 0.0f
        idleTicks = 0
        reactionDelay = 0
        jitterYaw = 0.0f
        jitterPitch = 0.0f
        lastJitterTime = 0L
    }
}
