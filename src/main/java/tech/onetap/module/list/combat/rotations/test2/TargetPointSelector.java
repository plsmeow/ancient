package tech.onetap.module.list.combat.rotations.test2;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Выбор вертикальной точки прицеливания внутри хитбокса.
 * Фактор стабилен в течение одного цикла ротации — выбирается при
 * старте цикла, а не пересчитывается каждый тик.
 */
public final class TargetPointSelector {

    public enum Mode {
        CENTER, ABOVE_CENTER, BELOW_CENTER, RANDOM_OFFSET
    }

    /**
     * @param mode режим выбора точки
     * @return доля высоты хитбокса (0..1), стабильная до конца цикла
     */
    public float pickVerticalFactor(ThreadLocalRandom random, Mode mode) {
        return switch (mode) {
            case CENTER -> 0.5f;
            case ABOVE_CENTER -> 0.5f + random.nextFloat(0.08f, 0.20f);
            case BELOW_CENTER -> 0.5f - random.nextFloat(0.08f, 0.20f);
            case RANDOM_OFFSET -> 0.5f + (random.nextBoolean() ? 1f : -1f) * random.nextFloat(0.05f, 0.30f);
        };
    }

    public static Mode fromName(String name) {
        return switch (name == null ? "" : name) {
            case "CENTER" -> Mode.CENTER;
            case "BELOW_CENTER" -> Mode.BELOW_CENTER;
            case "RANDOM_OFFSET" -> Mode.RANDOM_OFFSET;
            default -> Mode.ABOVE_CENTER;
        };
    }
}
