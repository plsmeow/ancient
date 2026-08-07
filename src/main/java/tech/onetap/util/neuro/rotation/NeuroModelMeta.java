package tech.onetap.util.neuro.rotation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Метаданные модели. Читаются из meta.json при загрузке.
 * Используются для валидации совместимости.
 */
@Getter
@AllArgsConstructor
public class NeuroModelMeta {
    private final int schemaVersion;
    private final int featureCount;
    private final int seqLen;
    private final String arch;
    private final int outputSize;
    private final float[] mean;
    private final float[] std;
    private final float[] labelScale;
    private final int trainSamples;
    private final float valLoss;
    private final float yawMae;
    private final float pitchMae;
    private final String source;
    private final String createdAt;
    private final String baseModel;

    /**
     * Проверяет совместимость с текущей схемой.
     * @return null если совместима, иначе сообщение об ошибке
     */
    public String checkCompatibility() {
        if (schemaVersion < NeuroFeatureSchema.SCHEMA_VERSION) {
            return String.format(
                    "Модель schemaVersion=%d, требуется %d. Переобучите модель на новом датасете.",
                    schemaVersion, NeuroFeatureSchema.SCHEMA_VERSION
            );
        }
        if (featureCount != NeuroFeatureSchema.FEATURE_COUNT) {
            return String.format(
                    "Модель featureCount=%d, ожидается %d",
                    featureCount, NeuroFeatureSchema.FEATURE_COUNT
            );
        }
        if (seqLen != NeuroFeatureSchema.SEQ_LEN) {
            return String.format(
                    "Модель seqLen=%d, ожидается %d",
                    seqLen, NeuroFeatureSchema.SEQ_LEN
            );
        }
        if (outputSize != NeuroFeatureSchema.OUTPUT_SIZE) {
            return String.format(
                    "Модель outputSize=%d, ожидается %d",
                    outputSize, NeuroFeatureSchema.OUTPUT_SIZE
            );
        }
        if (mean == null || mean.length != featureCount) {
            return "Метаданные нормализации отсутствуют или повреждены";
        }
        if (std == null || std.length != featureCount) {
            return "Метаданные нормализации отсутствуют или повреждены";
        }
        return null;
    }
}
