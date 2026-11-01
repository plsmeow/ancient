package meow.ancient.module.modules.combat.hitaura.rotation.impl.test2

import kotlin.math.abs

class VerticalCorrectionController {
    private val trajectory = RotationTrajectory()
    var isActive: Boolean = false
        private set

    fun begin(currentPitch: Float, actualPitch: Float, correctionSpeedDegSec: Float) {
        val distance = abs(actualPitch - currentPitch)
        val durationSec = (distance / maxOf(correctionSpeedDegSec, 1.0f)).coerceIn(0.08f, 0.26f)
        trajectory.begin(currentPitch, actualPitch, durationSec)
        isActive = true
    }

    fun update(dtSec: Float) {
        trajectory.advance(dtSec)
    }

    fun samplePitch(actualPitch: Float): Float {
        return trajectory.samplePitch(actualPitch)
    }

    val isFinished: Boolean
        get() = !isActive || trajectory.isFinished

    val progress: Float
        get() = trajectory.progress

    fun deactivate() {
        isActive = false
    }
}
