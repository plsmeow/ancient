package tech.onetap.util.neuro.rotation;

import lombok.Getter;

/**
 * Счётчики распределения датасета (§27).
 * Нужны чтобы не получить датасет, где 90% сэмплов — неподвижная цель перед игроком.
 */
@Getter
public class DatasetBalance {

    private int stationaryTarget;
    private int movingTarget;
    private int strafing;
    private int jumping;
    private int verticalMovement;
    private int playerMoving;
    private int closeDistance;
    private int mediumDistance;
    private int longDistance;
    private int largeRotationError;
    private int smallRotationError;

    public void record(float[] features) {
        float targetVelX = features[NeuroFeatureSchema.TARGET_VEL_X];
        float targetVelY = features[NeuroFeatureSchema.TARGET_VEL_Y];
        float targetVelZ = features[NeuroFeatureSchema.TARGET_VEL_Z];
        float targetSpeed = (float) Math.sqrt(targetVelX * targetVelX + targetVelZ * targetVelZ);

        if (targetSpeed < 0.02f) {
            stationaryTarget++;
        } else {
            movingTarget++;
        }

        if (Math.abs(targetVelY) > 0.05f) {
            verticalMovement++;
        }

        float playerVelX = features[NeuroFeatureSchema.PLAYER_VEL_X];
        float playerVelZ = features[NeuroFeatureSchema.PLAYER_VEL_Z];
        float playerSpeed = (float) Math.sqrt(playerVelX * playerVelX + playerVelZ * playerVelZ);
        if (playerSpeed > 0.02f) {
            playerMoving++;
        }

        if (Math.abs(features[NeuroFeatureSchema.PLAYER_SIDEWAYS_INPUT]) > 0.1f) {
            strafing++;
        }

        if (features[NeuroFeatureSchema.PLAYER_ON_GROUND] < 0.5f) {
            jumping++;
        }

        float distance = features[NeuroFeatureSchema.TARGET_DISTANCE];
        if (distance < 2.0f) {
            closeDistance++;
        } else if (distance < 4.0f) {
            mediumDistance++;
        } else {
            longDistance++;
        }

        float errorYaw = Math.abs(features[NeuroFeatureSchema.TARGET_DELTA_YAW]);
        float errorPitch = Math.abs(features[NeuroFeatureSchema.TARGET_DELTA_PITCH]);
        if (errorYaw > 15.0f || errorPitch > 10.0f) {
            largeRotationError++;
        } else {
            smallRotationError++;
        }
    }

    public void reset() {
        stationaryTarget = 0;
        movingTarget = 0;
        strafing = 0;
        jumping = 0;
        verticalMovement = 0;
        playerMoving = 0;
        closeDistance = 0;
        mediumDistance = 0;
        longDistance = 0;
        largeRotationError = 0;
        smallRotationError = 0;
    }
}
