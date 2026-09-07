package tech.onetap.module.list.combat.rotations.test2;

/**
 * Снимок состояния ротации Test2 для отладки и синхронизации удара.
 * Контроллер не бьёт сам — KillAura решает, когда атаковать;
 * {@link #isReadyForAttack()} лишь сообщает, что доводка завершена.
 */
public final class RotationState {

    public enum Phase {
        IDLE, APPROACH, SETTLE, CORRECTION, ATTACK, RECOVERY
    }

    private Phase phase = Phase.IDLE;
    private float yaw;
    private float pitch;
    private float targetYaw;
    private float targetPitch;
    private float approachPitch;
    private float yawError;
    private float pitchError;
    private float velocityYaw;
    private float velocityPitch;
    private float accelerationYaw;
    private float accelerationPitch;
    private float movementProgress;
    private boolean readyForAttack;

    public Phase getPhase() {
        return phase;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getTargetYaw() {
        return targetYaw;
    }

    public float getTargetPitch() {
        return targetPitch;
    }

    public float getApproachPitch() {
        return approachPitch;
    }

    public float getYawError() {
        return yawError;
    }

    public float getPitchError() {
        return pitchError;
    }

    public float getVelocityYaw() {
        return velocityYaw;
    }

    public float getVelocityPitch() {
        return velocityPitch;
    }

    public float getAccelerationYaw() {
        return accelerationYaw;
    }

    public float getAccelerationPitch() {
        return accelerationPitch;
    }

    public float getMovementProgress() {
        return movementProgress;
    }

    public boolean isReadyForAttack() {
        return readyForAttack;
    }

    public void snapshot(Phase phase, float yaw, float pitch, float targetYaw, float targetPitch,
                  float approachPitch, float yawError, float pitchError,
                  float velocityYaw, float velocityPitch,
                  float accelerationYaw, float accelerationPitch,
                  float movementProgress, boolean readyForAttack) {
        this.phase = phase;
        this.yaw = yaw;
        this.pitch = pitch;
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.approachPitch = approachPitch;
        this.yawError = yawError;
        this.pitchError = pitchError;
        this.velocityYaw = velocityYaw;
        this.velocityPitch = velocityPitch;
        this.accelerationYaw = accelerationYaw;
        this.accelerationPitch = accelerationPitch;
        this.movementProgress = movementProgress;
        this.readyForAttack = readyForAttack;
    }

    public void reset() {
        phase = Phase.IDLE;
        readyForAttack = false;
        movementProgress = 0f;
        velocityYaw = velocityPitch = 0f;
        accelerationYaw = accelerationPitch = 0f;
    }
}
