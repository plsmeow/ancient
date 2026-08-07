package tech.onetap.util.neuro.rotation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.chat.ChatUtil;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Управление датасетами и активной моделью.
 *
 * Обучение вынесено во внешний Python (tools/neuro/train.py) — здесь только
 * запись датасетов и загрузка готовых ONNX-моделей.
 *
 * Активная модель живёт в AtomicReference: загрузчик собирает ActiveModel целиком
 * и только потом публикует ссылку, а старую закрывает отложенно на следующем тике.
 * Поэтому inference на игровом потоке не может попасть в закрытую сессию.
 */
public final class AIRotationManager implements IMinecraft {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path AI_DIR = Paths.get(".options", "ai");
    private static final Path DATASETS_DIR = AI_DIR.resolve("datasets");
    private static final Path MODELS_DIR = AI_DIR.resolve("models");

    private static final AtomicReference<ActiveModel> ACTIVE = new AtomicReference<>(null);

    static {
        try {
            Files.createDirectories(DATASETS_DIR);
            Files.createDirectories(MODELS_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private AIRotationManager() {
    }

    public static Path getAiDir() {
        return AI_DIR;
    }

    public static Path getDatasetsDir() {
        return DATASETS_DIR;
    }

    public static Path getModelsDir() {
        return MODELS_DIR;
    }

    /**
     * Текущая активная модель. Игровой поток должен читать это ОДИН раз за тик
     * в локальную переменную и дальше работать только с ней.
     */
    public static ActiveModel getActive() {
        return ACTIVE.get();
    }

    public static boolean hasModel() {
        return ACTIVE.get() != null;
    }

    public static String getCurrentModelName() {
        ActiveModel model = ACTIVE.get();
        return model != null ? model.getName() : null;
    }

    // ------------------------------------------------------------------
    // Датасеты
    // ------------------------------------------------------------------

    public static void saveDataset(String name) {
        List<TrainingSample> samples = AIRotationRecorder.getSamples();
        if (samples.isEmpty()) {
            ChatUtil.send("§cНет данных для сохранения! Включите модуль Ai Record для записи");
            return;
        }

        try {
            Path datasetPath = DATASETS_DIR.resolve(name + ".jsonl");
            Path metaPath = DATASETS_DIR.resolve(name + ".meta.json");

            int written = DatasetWriter.write(
                    datasetPath,
                    metaPath,
                    name,
                    AIRotationRecorder.getMode().name().toLowerCase(),
                    samples,
                    AIRotationRecorder.getBalance()
            );

            ChatUtil.send("§aДатасет §e" + name + " §aсохранен (§f" + written + " §aсэмплов)");
            ChatUtil.send("§7Путь: §f" + datasetPath.toAbsolutePath());
        } catch (IOException e) {
            ChatUtil.send("§cОшибка сохранения датасета: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Сохраняет дамп чужих ротаций (.ai dump). Сэмплы приходят из
     * RotationDumpRecorder, а не из Ai Record.
     */
    public static void saveDumpDataset(String name, List<TrainingSample> samples, DatasetBalance balance) {
        if (samples == null || samples.isEmpty()) {
            ChatUtil.send("§cНет данных для сохранения!");
            return;
        }

        try {
            Path datasetPath = DATASETS_DIR.resolve(name + ".jsonl");
            Path metaPath = DATASETS_DIR.resolve(name + ".meta.json");

            int written = DatasetWriter.write(datasetPath, metaPath, name, "dump", samples, balance);

            ChatUtil.send("§aДатасет §e" + name + " §aсохранен (§f" + written + " §aсэмплов)");
            ChatUtil.send("§7Путь: §f" + datasetPath.toAbsolutePath());
        } catch (IOException e) {
            ChatUtil.send("§cОшибка сохранения дампа: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // Загрузка модели
    // ------------------------------------------------------------------

    /**
     * Загружает модель по имени. Выполняется на вызывающем потоке —
     * вызывать только вне игрового цикла (из команды).
     */
    public static void loadModel(String modelName) {
        Path modelDir = MODELS_DIR.resolve(modelName);
        Path onnxPath = modelDir.resolve("model.onnx");
        Path metaPath = modelDir.resolve("meta.json");

        if (!Files.exists(onnxPath)) {
            ChatUtil.send("§cМодель §e" + modelName + " §cне найдена!");
            ChatUtil.send("§7Ожидался файл: §f" + onnxPath);
            return;
        }

        if (!Files.exists(metaPath)) {
            ChatUtil.send("§cУ модели §e" + modelName + " §cнет meta.json — загрузка отклонена");
            ChatUtil.send("§7Модель обучена старой версией. Переобучите через tools/neuro/train.py");
            return;
        }

        NeuroModelMeta meta;
        try (var reader = Files.newBufferedReader(metaPath)) {
            meta = GSON.fromJson(reader, NeuroModelMeta.class);
        } catch (Exception e) {
            ChatUtil.send("§cНе удалось прочитать meta.json: " + e.getMessage());
            return;
        }

        if (meta == null) {
            ChatUtil.send("§cmeta.json пуст или повреждён");
            return;
        }

        // Валидация схемы — отказ с внятным сообщением вместо тихого {0,0}
        String incompatibility = meta.checkCompatibility();
        if (incompatibility != null) {
            ChatUtil.send("§cМодель §e" + modelName + " §cнесовместима:");
            ChatUtil.send("§7" + incompatibility);
            return;
        }

        ActiveModel newModel;
        try {
            InferenceEngine engine = new InferenceEngine(onnxPath, meta.getSeqLen(), meta.getFeatureCount());
            FeatureNormalizer normalizer = new FeatureNormalizer(meta.getMean(), meta.getStd());
            newModel = new ActiveModel(modelName, meta, normalizer, engine);
        } catch (Throwable t) {
            ChatUtil.send("§cОшибка загрузки модели: " + t.getMessage());
            t.printStackTrace();
            return;
        }

        // Атомарная публикация: модель уже полностью собрана
        ActiveModel old = ACTIVE.getAndSet(newModel);

        // Старую закрываем на следующем тике — in-flight inference её ещё может читать
        if (old != null) {
            mc.execute(old::close);
        }

        ChatUtil.send("§aМодель §e" + modelName + " §aактивна!");
        ChatUtil.send(String.format("§7arch: §f%s §7| seq: §f%d §7| val loss: §f%.4f §7| yaw MAE: §f%.2f°",
                meta.getArch(), meta.getSeqLen(), meta.getValLoss(), meta.getYawMae()));
    }

    /**
     * Выгружает активную модель.
     */
    public static void unloadModel() {
        ActiveModel old = ACTIVE.getAndSet(null);
        if (old != null) {
            mc.execute(old::close);
            ChatUtil.send("§aМодель выгружена");
        } else {
            ChatUtil.send("§7Активной модели нет");
        }
    }

    // ------------------------------------------------------------------
    // Список
    // ------------------------------------------------------------------

    public static void listFiles() {
        ChatUtil.send("§e§l=== AI Models ===");

        listDatasets();
        listModels();
    }

    private static void listDatasets() {
        File[] datasets = DATASETS_DIR.toFile().listFiles(
                (dir, name) -> name.endsWith(".jsonl") || (name.endsWith(".json") && !name.endsWith(".meta.json"))
        );

        if (datasets == null || datasets.length == 0) {
            ChatUtil.send("§7Датасеты: §cнет");
            return;
        }

        ChatUtil.send("§aДатасеты:");
        List<File> sorted = new ArrayList<>(List.of(datasets));
        sorted.sort(Comparator.comparing(File::getName));

        for (File dataset : sorted) {
            String fileName = dataset.getName();

            // Старый формат .json без .jsonl — датасет v1
            if (fileName.endsWith(".json")) {
                String name = fileName.substring(0, fileName.length() - ".json".length());
                ChatUtil.send("  §7- §f" + name + " §c(v1, несовместим)");
                continue;
            }

            String name = fileName.substring(0, fileName.length() - ".jsonl".length());
            Path metaPath = DATASETS_DIR.resolve(name + ".meta.json");
            DatasetWriter.DatasetMeta meta = DatasetWriter.readMeta(metaPath);

            if (meta == null) {
                ChatUtil.send("  §7- §f" + name + " §e(нет меты)");
                continue;
            }

            if (meta.schemaVersion() < NeuroFeatureSchema.SCHEMA_VERSION) {
                ChatUtil.send("  §7- §f" + name + " §c(v" + meta.schemaVersion() + ", несовместим)");
                continue;
            }

            ChatUtil.send("  §7- §f" + name + " §7| сэмплов: §f" + meta.samples()
                    + " §7| источник: §f" + meta.source());
        }
    }

    private static void listModels() {
        File[] models = MODELS_DIR.toFile().listFiles(File::isDirectory);

        if (models == null || models.length == 0) {
            ChatUtil.send("§7Модели: §cнет");
            ChatUtil.send("§7Обучите модель: §fpython tools/neuro/train.py --dataset <ds> --out <name>");
            return;
        }

        ChatUtil.send("§aМодели:");
        String activeName = getCurrentModelName();

        List<File> sorted = new ArrayList<>(List.of(models));
        sorted.sort(Comparator.comparing(File::getName));

        for (File modelDir : sorted) {
            String name = modelDir.getName();
            Path metaPath = modelDir.toPath().resolve("meta.json");
            boolean hasOnnx = Files.exists(modelDir.toPath().resolve("model.onnx"));

            String status = name.equals(activeName) ? " §a(активна)" : "";

            if (!hasOnnx) {
                ChatUtil.send("  §7- §f" + name + " §c(нет model.onnx)");
                continue;
            }

            NeuroModelMeta meta = null;
            if (Files.exists(metaPath)) {
                try (var reader = Files.newBufferedReader(metaPath)) {
                    meta = GSON.fromJson(reader, NeuroModelMeta.class);
                } catch (Exception ignored) {
                }
            }

            if (meta == null) {
                ChatUtil.send("  §7- §f" + name + " §e(нет меты)" + status);
                continue;
            }

            ChatUtil.send("  §7- §f" + name + status);
            ChatUtil.send(String.format(
                    "      §7arch: §f%s §7| seq: §f%d §7| сэмплов: §f%d §7| loss: §f%.4f",
                    meta.getArch(), meta.getSeqLen(), meta.getTrainSamples(), meta.getValLoss()
            ));
        }
    }

    // ------------------------------------------------------------------
    // Удаление
    // ------------------------------------------------------------------

    public static void deleteModel(String modelName) {
        try {
            Path modelPath = MODELS_DIR.resolve(modelName);
            if (!Files.exists(modelPath)) {
                ChatUtil.send("§cМодель §e" + modelName + " §cне найдена!");
                return;
            }

            ActiveModel active = ACTIVE.get();
            if (active != null && modelName.equals(active.getName())) {
                ACTIVE.set(null);
                mc.execute(active::close);
            }

            deleteRecursive(modelPath.toFile());
            ChatUtil.send("§aМодель §e" + modelName + " §aудалена!");
        } catch (Exception e) {
            ChatUtil.send("§cОшибка удаления модели: " + e.getMessage());
        }
    }

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    public static void openDirectory() {
        try {
            Desktop.getDesktop().open(AI_DIR.toFile());
            ChatUtil.send("§aПапка AI открыта");
        } catch (IOException e) {
            ChatUtil.send("§cОшибка открытия папки: " + e.getMessage());
            ChatUtil.send("§7Путь: §f" + AI_DIR.toAbsolutePath());
        }
    }
}
