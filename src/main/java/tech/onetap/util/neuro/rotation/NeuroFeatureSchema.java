package tech.onetap.util.neuro.rotation;

/**
 * Схема Neuro Rotation v4 — контракт с тренером train_neuro.py.
 *
 * Датасет хранит RAW-состояние тика (21 колонка CSV), фичи выводятся
 * из него формулами NeuroFeatureCollector.computeFeatures — те же формулы
 * зашиты в тренер (compute_features), поэтому схема фич меняется без
 * перезаписи датасетов.
 *
 * Модель: TCN + MDN. Вход — 16 фич × 16 тиков = 256, нормализация зашита
 * ВНУТРИ графа ONNX. Выход — заголовок [VERSION, K, H, WINDOW, FEATURES],
 * затем смеси распределений pi(K) / mu(K*H*2) / log_sigma(K*H*2).
 */
public final class NeuroFeatureSchema {

    public static final int SCHEMA_VERSION = 4;
    public static final int FEATURE_COUNT = 16;
    public static final int SEQ_LEN = 16;
    public static final int HORIZON = 4;
    public static final int MDN_K = 7;
    public static final int OUTPUT_SIZE = 5 + MDN_K * (1 + 4 * HORIZON);

    /** Разброс меток при обучении — столько же позволяет модель на выходе. */
    public static final float LABEL_CLIP_DEG = 25.0f;

    // Фичи (порядок совпадает с compute_features в train_neuro.py)
    public static final int F_DYAW = 0;
    public static final int F_DPITCH = 1;
    public static final int F_ERR_YAW = 2;
    public static final int F_ERR_PITCH = 3;
    public static final int F_HALF_YAW = 4;
    public static final int F_HALF_PITCH = 5;
    public static final int F_DIST = 6;
    public static final int F_PITCH = 7;
    public static final int F_HAS = 8;
    public static final int F_VIS = 9;
    public static final int F_ON = 10;
    public static final int F_ATK = 11;
    public static final int F_GROUND = 12;
    public static final int F_SPRINT = 13;
    public static final int F_HP = 14;
    public static final int F_BH = 15;

    // Колонки RAW-строки CSV (тот же порядок, что COLUMNS в тренере)
    public static final int RAW_COLUMN_COUNT = 21;
    public static final int R_T = 0;
    public static final int R_GCD = 1;
    public static final int R_CLEAN = 2;
    public static final int R_YAW = 3;
    public static final int R_PITCH = 4;
    public static final int R_DYAW = 5;
    public static final int R_DPITCH = 6;
    public static final int R_HAS = 7;
    public static final int R_TID = 8;
    public static final int R_RX = 9;
    public static final int R_RY = 10;
    public static final int R_RZ = 11;
    public static final int R_BW = 12;
    public static final int R_BH = 13;
    public static final int R_DIST = 14;
    public static final int R_VIS = 15;
    public static final int R_ON = 16;
    public static final int R_ATK = 17;
    public static final int R_HP = 18;
    public static final int R_GROUND = 19;
    public static final int R_SPRINT = 20;

    public static final String CSV_HEADER =
            "t,gcd,clean,yaw,pitch,dyaw,dpitch,has,tid,rx,ry,rz,bw,bh,dist,vis,on,atk,hp,ground,sprint";

    private NeuroFeatureSchema() {
    }
}
