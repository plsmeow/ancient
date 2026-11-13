package meow.ancient.module.modules.movement

import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Firework component limits from Grim 2.0's UncertaintyHandler.tickFireworksBox. */
internal object GrimElytraBoost {
    private const val COMPONENT_LIMIT = 1.7
    private const val SAFETY = 0.97
    private const val VANILLA_FACTOR = 1.7

    data class Angles(val yaw: Float, val pitch: Float)

    fun minimalCorrection(yaw: Float, pitch: Float, previous: Vec3): Angles {
        val requested = Angles(yaw, pitch.coerceIn(-90f, 90f))
        val base = maximumFactor(Vec3.directionFromRotation(requested.pitch, requested.yaw), previous)
        var best = requested
        var bestFactor = base
        var bestDistance = 0f
        // Search only within two degrees of the camera. Keep the original angle
        // unless the complete three-axis speed limit improves by at least 1%.
        for (yawStep in -4..4) for (pitchStep in -4..4) {
            val candidate = Angles(yaw + yawStep * 0.5f, (requested.pitch + pitchStep * 0.5f).coerceIn(-90f, 90f))
            val factor = maximumFactor(Vec3.directionFromRotation(candidate.pitch, candidate.yaw), previous)
            val distance = (candidate.yaw - yaw) * (candidate.yaw - yaw) +
                (candidate.pitch - requested.pitch) * (candidate.pitch - requested.pitch)
            if (factor > bestFactor + 1.0E-6 || (abs(factor - bestFactor) <= 1.0E-6 && distance < bestDistance)) {
                best = candidate
                bestFactor = factor
                bestDistance = distance
            }
        }
        return if (bestFactor > max(VANILLA_FACTOR, base * 1.01)) best else requested
    }

    private fun limit(current: Double, previous: Double): Double =
        min(COMPONENT_LIMIT, COMPONENT_LIMIT * (max(0.0, current) + max(0.0, previous))) * SAFETY

    fun maximumFactor(look: Vec3, previous: Vec3): Double {
        var factor = Double.POSITIVE_INFINITY
        fun consider(current: Double, last: Double) {
            if (abs(current) < 1.0E-8) return
            val sign = if (current < 0.0) -1.0 else 1.0
            factor = min(factor, limit(current * sign, last * sign) / abs(current))
        }
        consider(look.x, previous.x)
        consider(look.y, previous.y)
        consider(look.z, previous.z)
        return factor.takeIf { it.isFinite() } ?: 0.0
    }

    fun boost(velocity: Vec3, look: Vec3, previous: Vec3, strength: Double): Vec3 {
        val vanilla = velocity.scale(0.5).add(look.scale(0.85))
        val factor = maximumFactor(look, previous)
        if (!factor.isFinite() || factor <= VANILLA_FACTOR || strength <= 0.0) return vanilla
        val target = velocity.scale(0.5).add(look.scale(factor * 0.5))
        return vanilla.lerp(boundMovement(target, look, previous), strength.coerceIn(0.0, 1.0))
    }

    fun boundMovement(velocity: Vec3, look: Vec3, previous: Vec3): Vec3 {
        fun bounded(value: Double, current: Double, last: Double): Double =
            value.coerceIn(-limit(-current, -last), limit(current, last))
        return Vec3(bounded(velocity.x, look.x, previous.x),
            bounded(velocity.y, look.y, previous.y), bounded(velocity.z, look.z, previous.z))
    }

    fun movement(velocity: Vec3, look: Vec3, previous: Vec3, strength: Double): Vec3 {
        val factor = maximumFactor(look, previous)
        if (strength <= 0.0 || factor <= VANILLA_FACTOR) return velocity
        // Optimize the actual displacement after gravity, lift and drag. Capping only the
        // preceding rocket velocity loses vertical speed, especially around +/-35 degrees.
        val target = look.scale(factor)
        return boundMovement(velocity.lerp(target, strength.coerceIn(0.0, 1.0) * 0.65), look, previous)
    }

    // Lifetime is randomized only on the server. Use the minimum from the rocket's
    // item data, subtract latency, and taper early instead of trusting client lifetime.
    fun strength(age: Int, minimumLifetime: Int, latencyTicks: Int): Double {
        val end = minimumLifetime - latencyTicks.coerceAtLeast(0) - 2
        if (age < 2 || age >= end) return 0.0
        return min((age - 1) / 2.0, (end - age) / 2.0).coerceIn(0.0, 1.0)
    }
}
