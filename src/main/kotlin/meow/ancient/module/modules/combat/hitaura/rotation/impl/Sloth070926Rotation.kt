package meow.ancient.module.modules.combat.hitaura.rotation.impl

import meow.ancient.module.modules.combat.hitaura.rotation.impl.test2.TargetPointSelector

class Sloth070926Rotation : Test2Rotation("Sloth 07.09.26") {
    override val offsetMin: Float = 15.0f
    override val offsetMax: Float = 25.0f
    override val approachSpeed: Float = 485.0f
    override val correctionSpeed: Float = 580.0f
    override val minDuration: Float = 0.15f
    override val maxDuration: Float = 0.35f
    override val yawAccel: Float = 13.5f
    override val pitchAccel: Float = 2.5f
    override val yawDamping: Float = 0.95f
    override val pitchDamping: Float = 0.95f
    override val microAmp: Float = 0.14f
    override val microFreq: Float = 5.0f
    override val overshootStrength: Float = 0.4f
    override val overshootChance: Float = 0.98f
    override val dynamicSpeed: Boolean = false
    override val dynamicDuration: Boolean = true
    override val targetPointMode: TargetPointSelector.Mode = TargetPointSelector.Mode.ABOVE_CENTER
}
