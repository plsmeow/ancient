package tech.onetap.util.rotation;

/**
 * Режим коррекции движения при ротации.
 * Выбирается каждым модулем, использующим ротацию, индивидуально.
 * Приоритет при одновременной активации нескольких модулей: NONE > FREE > CORRECT.
 */
public enum MoveFixMode {
    /** Сфокусированная — движение корректируется к целевой ротации. */
    CORRECT(0),
    /** Свободная — игрок идёт в сторону взгляда камеры, серверный yaw используется только для packets. */
    FREE(1),
    /** Нет — вход не трогаем; движение по взгляду FreeLook задаётся миксином Entity.updateVelocity. */
    NONE(2);

    private final int priority;

    MoveFixMode(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * Возвращает режим с наивысшим приоритетом (NONE > FREE > CORRECT).
     */
    public static MoveFixMode highest(MoveFixMode a, MoveFixMode b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.priority >= b.priority ? a : b;
    }
}
