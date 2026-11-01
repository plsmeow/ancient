package meow.ancient.module.modules.combat.hitaura.rotation.impl.test2

import net.minecraft.util.Mth

class RotationTrajectory {
    private var startYaw: Float = 0.0f
    private var startPitch: Float = 0.0f
    var progress: Float = 0.0f
        private set
    private var durationSec: Float = 0.2f

    fun begin(startYaw: Float, startPitch: Float, durationSec: Float) {
        this.startYaw = startYaw
        this.startPitch = startPitch
        this.progress = 0.0f
        this.durationSec = maxOf(0.05f, durationSec)
    }

    fun advance(dtSec: Float) {
        if (dtSec <= 0.0f || dtSec.isNaN()) return
        progress = minOf(1.0f, progress + dtSec / durationSec)
    }

    val isFinished: Boolean
        get() = progress >= 1.0f

    fun sampleYaw(targetYaw: Float): Float {
        return startYaw + Mth.wrapDegrees(targetYaw - startYaw) * curve(progress)
    }

    fun samplePitch(targetPitch: Float): Float {
        return startPitch + Mth.wrapDegrees(targetPitch - startPitch) * curve(progress)
    }

    companion object {
        fun curve(t: Float): Float {
            val t2 = t * t
            val t3 = t2 * t
            return t3 * (10.0f - 15.0f * t + 6.0f * t2)
        }
    }
}
