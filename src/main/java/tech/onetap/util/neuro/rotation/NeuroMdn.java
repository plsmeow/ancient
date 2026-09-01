package tech.onetap.util.neuro.rotation;

import net.minecraft.util.math.MathHelper;

/**
 * Декодер выхода TCN+MDN модели (контракт train_neuro.py):
 * [VERSION, K, H, WINDOW, FEATURES | pi(K) | mu(K*H*2) | log_sigma(K*H*2)].
 *
 * Клиенту нужен немедленный сдвиг прицела — берём смесь компонент
 * на первом шаге горизонта (t -> t+1): среднее и суммарный разброс.
 */
public final class NeuroMdn {

    /** Раскодированный предикт: дельта на тик и разброс смеси. */
    public record Prediction(float deltaYaw, float deltaPitch, float sigma) {
    }

    private NeuroMdn() {
    }

    /**
     * @return null если формат выхода не совпадает с контрактом
     */
    public static Prediction decode(float[] output) {
        if (output == null || output.length != NeuroFeatureSchema.OUTPUT_SIZE) return null;
        for (float v : output) {
            if (!Float.isFinite(v)) return null;
        }

        int headerOffset = 5;
        int k = Math.round(output[1]);
        int h = Math.round(output[2]);
        int window = Math.round(output[3]);
        int features = Math.round(output[4]);
        if (k != NeuroFeatureSchema.MDN_K || h != NeuroFeatureSchema.HORIZON
                || window != NeuroFeatureSchema.SEQ_LEN || features != NeuroFeatureSchema.FEATURE_COUNT) {
            return null;
        }

        float[] pi = new float[k];
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < k; i++) {
            pi[i] = output[headerOffset + i];
            if (pi[i] > max) max = pi[i];
        }
        float sumExp = 0.0f;
        for (int i = 0; i < k; i++) {
            pi[i] = (float) Math.exp(pi[i] - max);
            sumExp += pi[i];
        }
        for (int i = 0; i < k; i++) {
            pi[i] /= sumExp;
        }

        int muOffset = headerOffset + k;
        int lsOffset = muOffset + k * h * 2;

        // mu layout: (K, H, 2) flatten — первый шаг горизонта: индексы [k, 0, 0..1]
        float meanYaw = 0.0f;
        float meanPitch = 0.0f;
        for (int i = 0; i < k; i++) {
            meanYaw += pi[i] * output[muOffset + i * h * 2];
            meanPitch += pi[i] * output[muOffset + i * h * 2 + 1];
        }

        // дисперсия смеси: Σ pi*(σ² + μ²) − mean²
        float varYaw = 0.0f;
        float varPitch = 0.0f;
        for (int i = 0; i < k; i++) {
            float muY = output[muOffset + i * h * 2];
            float muP = output[muOffset + i * h * 2 + 1];
            float sigmaY = (float) Math.exp(output[lsOffset + i * h * 2]);
            float sigmaP = (float) Math.exp(output[lsOffset + i * h * 2 + 1]);
            varYaw += pi[i] * (sigmaY * sigmaY + muY * muY);
            varPitch += pi[i] * (sigmaP * sigmaP + muP * muP);
        }
        varYaw -= meanYaw * meanYaw;
        varPitch -= meanPitch * meanPitch;

        float sigma = (float) Math.sqrt(Math.max(varYaw, 0.0) + Math.max(varPitch, 0.0));
        return new Prediction(meanYaw, meanPitch, sigma);
    }

    /**
     * Уверенность модели из разброса смеси: узкий предикт — уверенный.
     */
    public static float confidence(float sigma) {
        return MathHelper.clamp(1.0f / (1.0f + sigma), 0.0f, 1.0f);
    }
}
