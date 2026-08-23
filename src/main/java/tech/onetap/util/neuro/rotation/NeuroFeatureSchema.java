package tech.onetap.util.neuro.rotation;

/**
 * Схема фич для Neuro Rotation v3.
 * Всего 39 фич × 8 временных шагов = 312 входов.
 *
 * v3: добавлены кинематические фичи в стиле mlsac (TickData) —
 * accel/jerk поворота и GCD-error дельт относительно моды делителя.
 */
public final class NeuroFeatureSchema {

    public static final int SCHEMA_VERSION = 3;
    public static final int FEATURE_COUNT = 39;
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

    // Rotation kinematics (6 features) — аналог TickData из mlsac
    public static final int ACCEL_YAW = 33;
    public static final int ACCEL_PITCH = 34;
    public static final int JERK_YAW = 35;
    public static final int JERK_PITCH = 36;
    public static final int GCD_ERROR_YAW = 37;
    public static final int GCD_ERROR_PITCH = 38;

    private NeuroFeatureSchema() {
    }
}
