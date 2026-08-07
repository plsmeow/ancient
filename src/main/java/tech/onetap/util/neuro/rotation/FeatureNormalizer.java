package tech.onetap.util.neuro.rotation;

/**
 * Нормализует фичи перед inference: (x - mean) / std.
 * Статистика считается на train split при обучении и хранится в meta.json.
 */
public class FeatureNormalizer {

    private final float[] mean;
    private final float[] std;

    public FeatureNormalizer(float[] mean, float[] std) {
        if (mean.length != std.length) {
            throw new IllegalArgumentException("mean and std must have same length");
        }
        this.mean = mean;
        this.std = std;
    }

    /**
     * Нормализует плоский массив (seq_len * feature_count) in-place.
     */
    public void normalize(float[] flat) {
        if (flat.length % mean.length != 0) {
            throw new IllegalArgumentException(
                    "flat length " + flat.length + " must be multiple of " + mean.length
            );
        }

        int featureCount = mean.length;
        for (int i = 0; i < flat.length; i++) {
            int featureIdx = i % featureCount;
            flat[i] = (flat[i] - mean[featureIdx]) / std[featureIdx];
        }
    }

    public int getFeatureCount() {
        return mean.length;
    }
}
