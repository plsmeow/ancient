package tech.onetap.util.neuro.rotation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Пишет датасет в JSONL: одна строка на сэмпл.
 * Мета лежит отдельно и содержит schemaVersion, который читается обратно при обучении.
 */
public final class DatasetWriter {

    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Компактная форма сэмпла для JSONL. */
    private record Row(float[] f, float[] y, String q, String s, int t) {
    }

    /** Мета датасета. Читается обратно при валидации совместимости. */
    public record DatasetMeta(
            int schemaVersion,
            String name,
            String mode,
            String source,
            int samples,
            int featureCount,
            int seqLen,
            int outputSize,
            QualityHistogram qualityHistogram,
            BalanceInfo balance,
            String createdAt
    ) {
    }

    public record QualityHistogram(int clean, int transition, int targetSwitch, int occluded, int invalid) {
    }

    public record BalanceInfo(
            int stationaryTarget,
            int movingTarget,
            int strafing,
            int jumping,
            int verticalMovement,
            int playerMoving,
            int closeDistance,
            int mediumDistance,
            int longDistance,
            int largeRotationError,
            int smallRotationError
    ) {
    }

    private DatasetWriter() {
    }

    /**
     * Пишет сэмплы в JSONL и мету рядом.
     * @return число записанных сэмплов
     */
    public static int write(Path datasetPath, Path metaPath, String name, String mode,
                           List<TrainingSample> samples, DatasetBalance balance) throws IOException {

        Files.createDirectories(datasetPath.getParent());

        int clean = 0, transition = 0, targetSwitch = 0, occluded = 0, invalid = 0;
        SampleSource dominantSource = null;
        boolean mixedSource = false;
        int written = 0;

        try (BufferedWriter writer = Files.newBufferedWriter(datasetPath)) {
            for (TrainingSample sample : samples) {
                if (sample == null || sample.getInput() == null || sample.getOutput() == null) {
                    continue;
                }

                SampleQuality quality = sample.getQuality() != null ? sample.getQuality() : SampleQuality.CLEAN;
                SampleSource source = sample.getSource() != null ? sample.getSource() : SampleSource.HUMAN;

                switch (quality) {
                    case CLEAN -> clean++;
                    case TRANSITION -> transition++;
                    case TARGET_SWITCH -> targetSwitch++;
                    case OCCLUDED -> occluded++;
                    case INVALID -> invalid++;
                }

                if (dominantSource == null) {
                    dominantSource = source;
                } else if (dominantSource != source) {
                    mixedSource = true;
                }

                Row row = new Row(
                        sample.getInput(),
                        sample.getOutput(),
                        quality.name(),
                        source.name(),
                        sample.getTick()
                );
                writer.write(GSON.toJson(row));
                writer.newLine();
                written++;
            }
        }

        String sourceLabel = mixedSource
                ? "MIXED"
                : (dominantSource != null ? dominantSource.name() : SampleSource.HUMAN.name());

        DatasetMeta meta = new DatasetMeta(
                NeuroFeatureSchema.SCHEMA_VERSION,
                name,
                mode,
                sourceLabel,
                written,
                NeuroFeatureSchema.FEATURE_COUNT,
                NeuroFeatureSchema.SEQ_LEN,
                NeuroFeatureSchema.OUTPUT_SIZE,
                new QualityHistogram(clean, transition, targetSwitch, occluded, invalid),
                new BalanceInfo(
                        balance.getStationaryTarget(),
                        balance.getMovingTarget(),
                        balance.getStrafing(),
                        balance.getJumping(),
                        balance.getVerticalMovement(),
                        balance.getPlayerMoving(),
                        balance.getCloseDistance(),
                        balance.getMediumDistance(),
                        balance.getLongDistance(),
                        balance.getLargeRotationError(),
                        balance.getSmallRotationError()
                ),
                Instant.now().toString()
        );

        try (BufferedWriter writer = Files.newBufferedWriter(metaPath)) {
            PRETTY_GSON.toJson(meta, writer);
        }

        return written;
    }

    /**
     * Читает мету датасета. Возвращает null, если меты нет или она нечитаема.
     */
    public static DatasetMeta readMeta(Path metaPath) {
        if (!Files.exists(metaPath)) {
            return null;
        }
        try (var reader = Files.newBufferedReader(metaPath)) {
            return PRETTY_GSON.fromJson(reader, DatasetMeta.class);
        } catch (Exception e) {
            return null;
        }
    }
}
