package meow.ancient.module.modules.combat.hitaura.rotation.impl

class TestRotation : Test2Rotation("Test") {
    override val attackCooldownThreshold: Float = 0.8f
    override val requireYawForAttack: Boolean = true
}
