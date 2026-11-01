package meow.ancient.module.modules.combat.hitaura.rotation.impl.test2

class RotationState {
    enum class Phase {
        IDLE, APPROACH, SETTLE, CORRECTION, ATTACK, RECOVERY
    }

    var phase: Phase = Phase.IDLE
        private set
    var yaw: Float = 0.0f
        private set
    var pitch: Float = 0.0f
        private set
    var targetYaw: Float = 0.0f
        private set
    var targetPitch: Float = 0.0f
        private set
    var approachPitch: Float = 0.0f
        private set
    var yawError: Float = 0.0f
        private set
    var pitchError: Float = 0.0f
        private set
    var velocityYaw: Float = 0.0f
        private set
    var velocityPitch: Float = 0.0f
        private set
    var accelerationYaw: Float = 0.0f
        private set
    var accelerationPitch: Float = 0.0f
        private set
    var movementProgress: Float = 0.0f
        private set
    var isReadyForAttack: Boolean = false
        private set

    fun snapshot(
        phase: Phase,
        yaw: Float,
        pitch: Float,
        targetYaw: Float,
        targetPitch: Float,
        approachPitch: Float,
        yawError: Float,
        pitchError: Float,
        velocityYaw: Float,
        velocityPitch: Float,
        accelerationYaw: Float,
        accelerationPitch: Float,
        movementProgress: Float,
        readyForAttack: Boolean
    ) {
        this.phase = phase
        this.yaw = yaw
        this.pitch = pitch
        this.targetYaw = targetYaw
        this.targetPitch = targetPitch
        this.approachPitch = approachPitch
        this.yawError = yawError
        this.pitchError = pitchError
        this.velocityYaw = velocityYaw
        this.velocityPitch = velocityPitch
        this.accelerationYaw = accelerationYaw
        this.accelerationPitch = accelerationPitch
        this.movementProgress = movementProgress
        this.isReadyForAttack = readyForAttack
    }

    fun reset() {
        phase = Phase.IDLE
        yaw = 0.0f
        pitch = 0.0f
        targetYaw = 0.0f
        targetPitch = 0.0f
        approachPitch = 0.0f
        yawError = 0.0f
        pitchError = 0.0f
        velocityYaw = 0.0f
        velocityPitch = 0.0f
        accelerationYaw = 0.0f
        accelerationPitch = 0.0f
        movementProgress = 0.0f
        isReadyForAttack = false
    }
}
