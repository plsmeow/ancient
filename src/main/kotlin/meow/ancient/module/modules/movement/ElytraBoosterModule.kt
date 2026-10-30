package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.mc
import meow.ancient.module.Module
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object ElytraBoosterModule : Module(
    name = "ElytraBooster",
    category = MOVEMENT,
    description = "Amplifies firework boost while flying on elytra"
) {
    val modeSetting = mode("Mode", "Basic", listOf("Basic", "Bravo"))
    val mode by modeSetting
    val speedSetting = number("Speed", 2.35, 1.6, 5.0, 0.05, 2, isVisible = { mode == "Basic" })

    fun getSpeedXZ(): Double {
        val player = mc.player ?: return 1.5
        return if (mode == "Bravo") {
            val pitch = normalizeAngle(player.xRot)
            val yaw = normalizeAngle(player.yRot)
            getBravoSpeedXZ(pitch, yaw).toDouble()
        } else {
            calculateBasicSpeed().coerceAtLeast(1.6)
        }
    }

    fun getSpeedY(): Double {
        val player = mc.player ?: return 1.5
        return if (mode == "Bravo") {
            val pitch = normalizeAngle(player.xRot)
            getBravoSpeedY(pitch).toDouble()
        } else {
            calculateBasicSpeed().coerceAtLeast(1.6)
        }
    }

    private fun calculateBasicSpeed(): Double {
        val player = mc.player ?: return 1.5
        val yaw = abs((player.yRot - 360f) % 360f)
        val maxSpeed = speedSetting.value.toFloat()

        val centers = floatArrayOf(45f, 135f, 225f, 315f)
        var center = centers[0]
        var minDiff = 9999f

        for (c in centers) {
            val d = abs(yaw - c)
            if (d < minDiff) {
                minDiff = d
                center = c
            }
        }

        val diff = abs(yaw - center)
        val speed = maxSpeed - (diff * 0.05f)

        val look = player.lookAngle
        var oldVelocity = Vec3.directionFromRotation(player.xRot, player.yRot).scale(speed.coerceAtLeast(1.61f).toDouble())
        val f = player.xRot * (Math.PI.toFloat() / 180.0f)
        val d = sqrt(look.x * look.x + look.z * look.z)
        val e = sqrt(oldVelocity.x * oldVelocity.x + oldVelocity.z * oldVelocity.z)
        val bl = player.deltaMovement.y <= 0.0
        val gravity = player.gravity
        val g = if (bl && player.hasEffect(MobEffects.SLOW_FALLING)) gravity.coerceAtMost(0.01) else gravity
        val h = (cos(f.toDouble()) * cos(f.toDouble()))
        oldVelocity = oldVelocity.add(0.0, g * (-1.0 + h * 0.75), 0.0)

        if (oldVelocity.y < 0.0 && d > 0.0) {
            val i = oldVelocity.y * -0.1 * h
            oldVelocity = oldVelocity.add(look.x * i / d, i, look.z * i / d)
        }

        if (f < 0.0f && d > 0.0) {
            val i = e * -sin(f.toDouble()) * 0.04
            oldVelocity = oldVelocity.add(-look.x * i / d, i * 3.2, -look.z * i / d)
        }

        if (d > 0.0) {
            oldVelocity = oldVelocity.add((look.x / d * e - oldVelocity.x) * 0.1, 0.0, (look.z / d * e - oldVelocity.z) * 0.1)
        }

        oldVelocity = oldVelocity.multiply(0.99, 0.98, 0.99)
        return oldVelocity.length().coerceAtLeast(1.5)
    }

    private fun getBravoSpeedXZ(pitch: Float, yaw: Float): Float {
        val absPitch = abs(pitch)
        val absYaw = abs(yaw)

        val speed: Float = when {
            absPitch in 38.0f..52.0f -> 2.0f
            absPitch in 32.0f..58.0f -> 1.96f
            absPitch in 28.0f..62.0f -> 1.95f
            (absYaw in 29.0f..61.0f) || (absPitch in 29.0f..61.0f) -> 1.963f
            (absYaw in 28.0f..60.0f) || (absPitch in 28.0f..60.0f) -> 1.954f
            (absYaw in 26.0f..64.0f) || (absPitch in 26.0f..64.0f) -> 1.874f
            (absYaw in 24.0f..66.0f) || (absPitch in 24.0f..66.0f) -> 1.77f
            (absYaw in 15.0f..75.0f) || (absPitch in 15.0f..75.0f) -> 1.70f
            (absYaw in 13.0f..77.0f) || (absPitch in 13.0f..77.0f) -> 1.68f
            (absYaw in 12.0f..78.0f) || (absPitch in 12.0f..78.0f) -> 1.68f
            (absYaw in 8.0f..82.0f) || (absPitch in 11.0f..79.0f) -> 1.66f
            (absYaw in 5.0f..85.0f) || (absPitch in 8.0f..82.0f) -> 1.635f
            absYaw <= 90.0f || absPitch <= 90.0f -> 1.622f
            else -> 1.621f
        }

        return if (pitch > 15.0f) speed - 0.068f else speed
    }

    private fun getBravoSpeedY(pitch: Float): Float {
        val absPitch = abs(pitch)
        return when {
            absPitch in 37.0f..38.0f -> 2.03f
            absPitch in 25.0f..30.0f -> 2.0f
            absPitch in 35.0f..45.0f -> 1.99f
            absPitch in 40.0f..50.0f -> 1.97f
            absPitch in 50.0f..60.0f -> 1.96f
            absPitch in 51.0f..61.0f -> 1.87f
            absPitch in 52.0f..65.0f -> 1.70f
            else -> 1.59f
        }
    }

    private fun normalizeAngle(angle: Float): Float {
        var n = angle % 180f
        if (n > 90f) n -= 180f
        else if (n < -90f) n += 180f
        return n
    }
}
