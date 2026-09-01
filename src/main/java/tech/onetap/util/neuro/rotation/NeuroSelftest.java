package tech.onetap.util.neuro.rotation;

import tech.onetap.util.IMinecraft;
import tech.onetap.util.chat.ChatUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Проверка совпадения формул фич Java (NeuroFeatureCollector.computeFeatures)
 * и тренера (compute_features в train_neuro.py).
 *
 * Генерирует 16 детерминированных RAW-строк, пишет selftest.csv и
 * selftest_java.txt (фичи, посчитанные Java) в .options/ai/selftest.
 * Затем тренер сравнивает свои фичи с Java: .ai selftest -> запустить
 * python train_neuro.py --selftest .options/ai/selftest
 */
public final class NeuroSelftest implements IMinecraft {

    private NeuroSelftest() {
    }

    public static void run() {
        NeuroFeatureCollector collector = new NeuroFeatureCollector();
        Random random = new Random(1337);

        List<float[]> rows = new ArrayList<>();
        for (long t = 0; t < NeuroFeatureSchema.SEQ_LEN; t++) {
            float[] row = new float[NeuroFeatureSchema.RAW_COLUMN_COUNT];
            row[NeuroFeatureSchema.R_T] = t;
            row[NeuroFeatureSchema.R_GCD] = 0.15f;
            row[NeuroFeatureSchema.R_CLEAN] = random.nextBoolean() ? 1.0f : 0.0f;
            row[NeuroFeatureSchema.R_YAW] = (random.nextFloat() * 2 - 1) * 180.0f;
            row[NeuroFeatureSchema.R_PITCH] = (random.nextFloat() * 2 - 1) * 89.0f;
            row[NeuroFeatureSchema.R_DYAW] = (random.nextFloat() * 2 - 1) * 20.0f;
            row[NeuroFeatureSchema.R_DPITCH] = (random.nextFloat() * 2 - 1) * 15.0f;

            boolean has = random.nextBoolean();
            row[NeuroFeatureSchema.R_HAS] = has ? 1.0f : 0.0f;
            row[NeuroFeatureSchema.R_TID] = has ? random.nextInt(1000) : -1.0f;
            row[NeuroFeatureSchema.R_RX] = (random.nextFloat() * 2 - 1) * 4.0f;
            row[NeuroFeatureSchema.R_RY] = (random.nextFloat() * 2 - 1) * 3.0f;
            row[NeuroFeatureSchema.R_RZ] = (random.nextFloat() * 2 - 1) * 4.0f;
            row[NeuroFeatureSchema.R_BW] = has ? (random.nextBoolean() ? 0.6f : 1.4f) : 0.0f;
            row[NeuroFeatureSchema.R_BH] = has ? (random.nextBoolean() ? 1.8f : 0.6f) : 0.0f;
            row[NeuroFeatureSchema.R_DIST] = has ? random.nextFloat() * 5.0f : 0.0f;
            row[NeuroFeatureSchema.R_VIS] = random.nextBoolean() ? 1.0f : 0.0f;
            row[NeuroFeatureSchema.R_ON] = random.nextBoolean() ? 1.0f : 0.0f;
            row[NeuroFeatureSchema.R_ATK] = random.nextBoolean() ? 1.0f : 0.0f;
            row[NeuroFeatureSchema.R_HP] = has ? random.nextFloat() * 20.0f : 0.0f;
            row[NeuroFeatureSchema.R_GROUND] = random.nextBoolean() ? 1.0f : 0.0f;
            row[NeuroFeatureSchema.R_SPRINT] = random.nextBoolean() ? 1.0f : 0.0f;

            collector.pushRaw(row);
            rows.add(row);
        }

        float[] flat = new float[NeuroFeatureSchema.SEQ_LEN * NeuroFeatureSchema.FEATURE_COUNT];
        collector.collect(flat);

        Path dir = Paths.get(".options", "ai", "selftest");
        try {
            Files.createDirectories(dir);
            Path csv = dir.resolve("selftest.csv");
            try (var writer = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
                writer.write(NeuroFeatureSchema.CSV_HEADER);
                writer.write('\n');
                for (float[] row : rows) {
                    writer.write(NeuroDatasetCsv.formatRow(row));
                    writer.write('\n');
                }
            }

            StringBuilder sb = new StringBuilder(flat.length * 10);
            for (float v : flat) {
                sb.append(v).append(' ');
            }
            Files.write(dir.resolve("selftest_java.txt"), sb.toString().getBytes(StandardCharsets.UTF_8));

            ChatUtil.send("§aSelftest файлы записаны: §f" + dir.toAbsolutePath());
            ChatUtil.send("§7Проверка (фичи Java vs тренер):");
            ChatUtil.send("§f  python .options/ai/neuro/train_neuro.py --selftest .options/ai/selftest");
        } catch (IOException e) {
            ChatUtil.send("§cНе удалось записать selftest: " + e.getMessage());
        }
    }
}
