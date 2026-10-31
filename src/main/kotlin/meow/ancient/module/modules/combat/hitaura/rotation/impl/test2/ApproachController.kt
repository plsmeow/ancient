package meow.ancient.module.modules.combat.hitaura.rotation.impl.test2

import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs

class ApproachController {
    var isOffsetAbove: Boolean = false
        private set
    var offsetPitchDeg: Float = 0.0f
        private set

    fun newCycle(
        random: ThreadLocalRandom,
        offsetMinDeg: Float,
        offsetMaxDeg: Float,
        angularMove: Float,
        distance: Double,
        targetHeight: Double,
        currentPitch: Float
    ) {
        isOffsetAbove = random.nextBoolean()
        val amplitude = offsetMinDeg + random.nextFloat() * maxOf(0.0f, offsetMaxDeg - offsetMinDeg)

        val moveScale = (angularMove / 45.0f).coerceIn(0.25f, 1.0f)
        val sizeScale = (targetHeight / 1.8).toFloat().coerceIn(0.6f, 1.4f)
        val distanceScale = (distance / 3.0).toFloat().coerceIn(0.6f, 1.3f)
        val pitchScale = (1.0f - abs(currentPitch) / 240.0f).coerceIn(0.5f, 1.0f)

        offsetPitchDeg = amplitude * moveScale * sizeScale * distanceScale * pitchScale
    }

    fun approachPitch(actualPitch: Float): Float {
        val signed = if (isOffsetAbove) offsetPitchDeg else -offsetPitchDeg
        return (actualPitch + signed).coerceIn(-88.0f, 88.0f)
    }
}
