package tech.onetap.util.neuro.rotation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Пишет и читает RAW-датасет Neuro: CSV по формату train_neuro.py
 * (21 колонка тика, первая строка — заголовок).
 */
public final class NeuroDatasetCsv {

    private NeuroDatasetCsv() {
    }

    public static int write(Path datasetPath, List<float[]> rows) throws IOException {
        Files.createDirectories(datasetPath.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(datasetPath)) {
            writer.write(NeuroFeatureSchema.CSV_HEADER);
            writer.write('\n');
            for (float[] row : rows) {
                if (row == null || row.length != NeuroFeatureSchema.RAW_COLUMN_COUNT) continue;
                writer.write(formatRow(row));
                writer.write('\n');
            }
        }
        return rows.size();
    }

    public static String formatRow(float[] row) {
        StringBuilder sb = new StringBuilder(21 * 12);
        for (int i = 0; i < row.length; i++) {
            if (i > 0) sb.append(',');
            float v = row[i];
            // tid — целое id сущности, остальное — углы/флаги в градусах
            if (i == NeuroFeatureSchema.R_TID || i == NeuroFeatureSchema.R_T) {
                sb.append((long) v);
            } else {
                sb.append(String.format(Locale.ROOT, "%.4f", v));
            }
        }
        return sb.toString();
    }

    /** Число RAW-строк (без заголовка и комментариев) — для списка датасетов. */
    public static long countRows(Path datasetPath) {
        try (var reader = Files.newBufferedReader(datasetPath)) {
            long count = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("t,")) continue;
                count++;
            }
            return count;
        } catch (IOException e) {
            return -1;
        }
    }
}
