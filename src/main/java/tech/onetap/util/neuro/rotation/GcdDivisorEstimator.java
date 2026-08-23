package tech.onetap.util.neuro.rotation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Оценка GCD-делителя поворотов и ошибки округления дельты по нему.
 * Порт подхода mlsac: бегущая мода НОД соседних дельт (RunningMode +
 * GcdMath), затем остаток |дельты| от моды — gcd error.
 */
public class GcdDivisorEstimator {

    private static final double POPULARITY_THRESHOLD = 0.001;
    private static final double MINIMUM_DIVISOR = Math.pow(0.2f, 3.0) * 8.0 * 0.15 - 0.001;
    private static final float MAX_DELTA_FOR_GCD = 5.0f;
    private static final int MODE_WINDOW = 80;
    private static final int SIGNIFICANT_SAMPLES = 15;

    private final Deque<Double> values = new ArrayDeque<>(MODE_WINDOW);
    private final Map<Double, Integer> popularity = new HashMap<>();
    private double lastAbsDelta = 0.0;
    private double mode = 0.0;

    /**
     * Проталкивает дельту тика в оценщик.
     */
    public void update(float delta) {
        float absDelta = Math.abs(delta);
        if (absDelta <= 0.0f || absDelta >= MAX_DELTA_FOR_GCD) {
            return;
        }
        double divisor = gcd(absDelta, lastAbsDelta);
        lastAbsDelta = absDelta;
        if (divisor > MINIMUM_DIVISOR) {
            add(divisor);
        }
    }

    /**
     * Ошибка округления: расстояние |дельты| до ближайшего кратного моды.
     * Человеческие повороты лежат на сетке GCD — ошибка около нуля;
     * софтовые дельты произвольной точности дают заметный остаток.
     */
    public float gcdError(float delta) {
        if (mode == 0.0) {
            return 0.0f;
        }
        double remainder = Math.abs(delta) % mode;
        return (float) Math.min(remainder, mode - remainder);
    }

    public double getMode() {
        return mode;
    }

    public void reset() {
        values.clear();
        popularity.clear();
        lastAbsDelta = 0.0;
        mode = 0.0;
    }

    private void add(double value) {
        pop();
        for (Map.Entry<Double, Integer> entry : popularity.entrySet()) {
            if (Math.abs(entry.getKey() - value) < POPULARITY_THRESHOLD) {
                entry.setValue(entry.getValue() + 1);
                values.add(entry.getKey());
                refreshMode();
                return;
            }
        }
        popularity.put(value, 1);
        values.add(value);
        refreshMode();
    }

    private void pop() {
        if (values.size() < MODE_WINDOW) {
            return;
        }
        Double oldest = values.poll();
        if (oldest == null) {
            return;
        }
        Integer count = popularity.get(oldest);
        if (count == null) {
            return;
        }
        if (count == 1) {
            popularity.remove(oldest);
        } else {
            popularity.put(oldest, count - 1);
        }
    }

    private void refreshMode() {
        if (values.size() <= SIGNIFICANT_SAMPLES) {
            return;
        }
        int bestCount = 0;
        Double bestValue = null;
        for (Map.Entry<Double, Integer> entry : popularity.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestValue = entry.getKey();
            }
        }
        if (bestValue != null && bestCount > SIGNIFICANT_SAMPLES) {
            mode = bestValue;
        }
    }

    private static double gcd(double a, double b) {
        if (a == 0.0) {
            return 0.0;
        }
        if (a < b) {
            double temp = a;
            a = b;
            b = temp;
        }
        while (b > MINIMUM_DIVISOR) {
            double temp = a - Math.floor(a / b) * b;
            a = b;
            b = temp;
        }
        return a;
    }
}
