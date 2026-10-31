package meow.ancient.module.modules.combat.hitaura.rotation.impl.test2

import java.util.concurrent.ThreadLocalRandom

class TargetPointSelector {
    enum class Mode {
        CENTER, ABOVE_CENTER, BELOW_CENTER, RANDOM_OFFSET
    }

    fun pickVerticalFactor(random: ThreadLocalRandom, mode: Mode): Float {
        return when (mode) {
            Mode.CENTER -> 0.5f
            Mode.ABOVE_CENTER -> 0.5f + random.nextFloat(0.08f, 0.20f)
            Mode.BELOW_CENTER -> 0.5f - random.nextFloat(0.08f, 0.20f)
            Mode.RANDOM_OFFSET -> 0.5f + (if (random.nextBoolean()) 1f else -1f) * random.nextFloat(0.05f, 0.30f)
        }
    }

    companion object {
        fun fromName(name: String?): Mode {
            return when (name) {
                "CENTER" -> Mode.CENTER
                "BELOW_CENTER" -> Mode.BELOW_CENTER
                "RANDOM_OFFSET" -> Mode.RANDOM_OFFSET
                else -> Mode.ABOVE_CENTER
            }
        }
    }
}
