package meow.ancient.module.modules.combat.hitaura.rotation.impl.test2

import java.util.concurrent.ThreadLocalRandom
import kotlin.math.exp

class RotationNoise {
    var yaw: Float = 0.0f
        private set
    var pitch: Float = 0.0f
        private set
    private var targetYaw: Float = 0.0f
    private var targetPitch: Float = 0.0f
    private var nextUpdateMs: Long = 0L

    fun update(
        nowMs: Long,
        dtSec: Float,
        frequencyHz: Float,
        amplitudeYaw: Float,
        amplitudePitch: Float,
        random: ThreadLocalRandom
    ) {
        if (nowMs >= nextUpdateMs) {
            val periodMs = 1000.0f / maxOf(frequencyHz, 0.1f)
            nextUpdateMs = nowMs + (periodMs * (0.7f + random.nextDouble().toFloat() * 0.6f)).toLong()
            targetYaw = (random.nextFloat() - 0.5f) * 2.0f * amplitudeYaw
            targetPitch = (random.nextFloat() - 0.5f) * 2.0f * amplitudePitch
        }
        val response = maxOf(frequencyHz * 2.0f, 1.0f)
        val factor = 1.0f - exp(-response * dtSec)
        yaw += (targetYaw - yaw) * factor
        pitch += (targetPitch - pitch) * factor
    }

    fun scaleAmplitude(scale: Float) {
        yaw *= scale
        pitch *= scale
        targetYaw *= scale
        targetPitch *= scale
    }

    fun reset() {
        yaw = 0.0f
        pitch = 0.0f
        targetYaw = 0.0f
        targetPitch = 0.0f
        nextUpdateMs = 0L
    }
}
