package tech.onetap.util.neuro.rotation;

/**
 * Схема фич для Neuro Rotation v2.
 * Всего 33 фичи × 8 временных шагов = 264 входа.
 */
public final class NeuroFeatureSchema {

    public static final int SCHEMA_VERSION = 2;
    public static final int FEATURE_COUNT = 33;
    public static final int SEQ_LEN = 8;
    public static final int OUTPUT_SIZE = 2;

    // Player (9 features)
    public static final int PLAYER_VEL_X = 0;
    public static final int PLAYER_VEL_Y = 1;
    public static final int PLAYER_VEL_Z = 2;
    public static final int PLAYER_FORWARD_INPUT = 3;
    public static final int PLAYER_SIDEWAYS_INPUT = 4;
    public static final int PLAYER_ON_GROUND = 5;
    public static final int PLAYER_SPRINTING = 6;
    public static final int PLAYER_SNEAKING = 7;
    public static final int PLAYER_FALL_DISTANCE = 8;

    // Target (10 features) — в yaw-фрейме игрока
    public static final int TARGET_REL_X = 9;
    public static final int TARGET_REL_Y = 10;
    public static final int TARGET_REL_Z = 11;
    public static final int TARGET_VEL_X = 12;
    public static final int TARGET_VEL_Y = 13;
    public static final int TARGET_VEL_Z = 14;
    public static final int TARGET_DISTANCE = 15;
    public static final int TARGET_WIDTH = 16;
    public static final int TARGET_HEIGHT = 17;
    public static final int TARGET_ON_GROUND = 18;

    // Rotation (4 features)
    public static final int PREV_DELTA_YAW = 19;
    public static final int PREV_DELTA_PITCH = 20;
    public static final int TARGET_DELTA_YAW = 21;
    public static final int TARGET_DELTA_PITCH = 22;

    // Aim point (6 features) — нормализованное пространство хитбокса
    public static final int AIM_X = 23;
    public static final int AIM_Y = 24;
    public static final int AIM_Z = 25;
    public static final int AIM_VEL_X = 26;
    public static final int AIM_VEL_Y = 27;
    public static final int AIM_VEL_Z = 28;

    // Environment (4 features)
    public static final int LINE_OF_SIGHT = 29;
    public static final int TARGET_VISIBLE = 30;
    public static final int TARGET_CHANGED = 31;
    public static final int ATTACK_COOLDOWN = 32;

    private NeuroFeatureSchema() {
    }
}
