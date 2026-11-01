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
    private var yawSpeed = 0.7f
    private var pitchSpeed = 0.6f
    private var aimFatigue = 0.0f
    private var idleTicks = 0
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
            jitterYaw = (r.nextFloat() - 0.5f) * 0.15f
            jitterPitch = (r.nextFloat() - 0.5f) * 0.10f
            lastJitterTime = now
        }

        if (!hasTrace) {
            val targetSpeed = if (angularDist > 12.0f) 0.95f else 0.75f
            yawSpeed = Mth.lerp(0.2f, yawSpeed, targetSpeed)
            pitchSpeed = Mth.lerp(0.2f, pitchSpeed, targetSpeed)
            idleTicks = 0
        } else {
            yawSpeed = Mth.lerp(0.35f, yawSpeed, 0.4f)
            pitchSpeed = Mth.lerp(0.35f, pitchSpeed, 0.4f)
            idleTicks++
        }

        aimFatigue = (aimFatigue + (angularDist * 0.00004f)).coerceIn(0.0f, 0.2f)
        if (hasTrace) aimFatigue *= 0.95f

        val elytraDiv = if (isElytra) 1.5f else 1.0f
        val effectiveSpeed = maxOf(speed, 35.0f)
        val baseYawSpeed = r.nextFloat(effectiveSpeed * 0.8f, effectiveSpeed * 1.3f) / elytraDiv
        val basePitchSpeed = r.nextFloat(effectiveSpeed * 0.5f, effectiveSpeed * 0.9f) / elytraDiv
        val yawS = yawSpeed * yawSpeed * (3.0f - 2.0f * yawSpeed)
        val pitchS = pitchSpeed * pitchSpeed * (3.0f - 2.0f * pitchSpeed)

        val finalYawSpeed = (baseYawSpeed * yawS * (1.0f - aimFatigue)).coerceAtLeast(15.0f)
        val finalPitchSpeed = (basePitchSpeed * pitchS * (1.0f - aimFatigue)).coerceAtLeast(10.0f)

        if (!player.onGround() && !isElytra) {
            dPitch *= 0.75f
            dYaw *= 0.90f
        }

        if (hasTrace && idleTicks > 3 && r.nextFloat() < 0.15f) {
            dYaw += (r.nextFloat() - 0.5f) * 1.5f
            dPitch += (r.nextFloat() - 0.5f) * 0.8f
        }

        var clampedYaw = dYaw.coerceIn(-finalYawSpeed, finalYawSpeed)
        var clampedPitch = dPitch.coerceIn(-finalPitchSpeed, finalPitchSpeed)

        if (hasTrace && abs(clampedYaw) < 0.2f) clampedYaw = 0.0f
        if (hasTrace && abs(clampedPitch) < 0.12f) clampedPitch = 0.0f

        val newYaw = current.yaw + clampedYaw + jitterYaw
        val newPitch = (current.pitch + clampedPitch + jitterPitch).coerceIn(-90.0f, 90.0f)

        return Rotation(newYaw, newPitch)
    }

    override fun reset() {
        yawSpeed = 0.7f
        pitchSpeed = 0.6f
        aimFatigue = 0.0f
        idleTicks = 0
        jitterYaw = 0.0f
        jitterPitch = 0.0f
        lastJitterTime = 0L
    }
}
