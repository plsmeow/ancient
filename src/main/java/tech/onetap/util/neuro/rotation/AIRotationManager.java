package tech.onetap.util.neuro.rotation;

import ai.djl.ModelException;
import ai.djl.translate.TranslateException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import tech.onetap.util.chat.ChatUtil;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class AIRotationManager {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path AI_DIR = Paths.get(".options", "ai");
    private static final Path DATASETS_DIR = AI_DIR.resolve("datasets");
    private static final Path MODELS_DIR = AI_DIR.resolve("models");
    
    @Getter
    private static AIRotationModel currentModel = null;
    
    static {
        try {
            Files.createDirectories(DATASETS_DIR);
            Files.createDirectories(MODELS_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveDataset(String name) {
        List<TrainingSample> samples = AIRotationRecorder.getSamples();
        if (samples.isEmpty()) {
            ChatUtil.send("§cНет данных для сохранения! Используйте .ai start для начала записи");
            return;
        }

        try {
            Path datasetPath = DATASETS_DIR.resolve(name + ".json");
            try (FileWriter writer = new FileWriter(datasetPath.toFile())) {
                GSON.toJson(samples, writer);
            }
            ChatUtil.send("§aДатасет §e" + name + " §aсохранен (§f" + samples.size() + " §aсэмплов)");
            ChatUtil.send("§7Путь: §f" + datasetPath.toAbsolutePath());
        } catch (IOException e) {
            ChatUtil.send("§cОшибка сохранения датасета: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void trainModel(String datasetName, String modelName) {
        try {
            // Загружаем датасет
            Path datasetPath = DATASETS_DIR.resolve(datasetName + ".json");
            if (!Files.exists(datasetPath)) {
                ChatUtil.send("§cДатасет §e" + datasetName + " §cне найден!");
                return;
            }

            Type listType = new TypeToken<List<TrainingSample>>(){}.getType();
            List<TrainingSample> samples;
            
            try (FileReader reader = new FileReader(datasetPath.toFile())) {
                samples = GSON.fromJson(reader, listType);
            }

            if (samples == null || samples.isEmpty()) {
                ChatUtil.send("§cДатасет пуст!");
                return;
            }

            // Конвертируем в массивы
            float[][] features = new float[samples.size()][];
            float[][] labels = new float[samples.size()][];
            
            for (int i = 0; i < samples.size(); i++) {
                features[i] = samples.get(i).getInput();
                labels[i] = samples.get(i).getOutput();
            }

            // Создаем и обучаем модель
            AIRotationModel model = new AIRotationModel(modelName);
            model.train(features, labels);

            // Сохраняем модель
            Path modelPath = MODELS_DIR.resolve(modelName);
            model.save(modelPath);
            
            model.close();
            
            ChatUtil.send("§aМодель §e" + modelName + " §aуспешно обучена и сохранена!");
            
        } catch (IOException | ModelException | TranslateException e) {
            ChatUtil.send("§cОшибка обучения модели: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void loadModel(String modelName) {
        try {
            Path modelPath = MODELS_DIR.resolve(modelName);
            if (!Files.exists(modelPath)) {
                ChatUtil.send("§cМодель §e" + modelName + " §cне найдена!");
                System.out.println("MODEL PATH NOT FOUND: " + modelPath.toAbsolutePath());
                return;
            }

            if (currentModel != null) {
                currentModel.close();
            }

            System.out.println("Loading model from: " + modelPath.toAbsolutePath());
            currentModel = new AIRotationModel(modelName);
            currentModel.load(modelPath);
            
            ChatUtil.send("§aМодель §e" + modelName + " §aактивна!");
            System.out.println("MODEL LOADED SUCCESSFULLY: " + modelName);
            
        } catch (IOException | ModelException e) {
            ChatUtil.send("§cОшибка загрузки модели: " + e.getMessage());
            System.out.println("MODEL LOAD ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static float[] predict(float[] input) {
        if (currentModel == null) {
            System.out.println("AI MODEL IS NULL! Load a model first.");
            return new float[]{0, 0};
        }

        try {
            System.out.println("AI Input: [" + input[0] + ", " + input[1] + ", " + input[2] + ", " + input[3] + "]");
            float[] result = currentModel.predict(input);
            System.out.println("AI Output: [" + result[0] + ", " + result[1] + "]");
            return result;
        } catch (Exception e) {
            System.out.println("AI PREDICTION ERROR: " + e.getMessage());
            e.printStackTrace();
            return new float[]{0, 0};
        }
    }

    public static void listFiles() {
        ChatUtil.send("§e§l=== AI Rotation Files ===");
        
        // Датасеты
        File[] datasets = DATASETS_DIR.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        if (datasets != null && datasets.length > 0) {
            ChatUtil.send("§aДатасеты:");
            for (File dataset : datasets) {
                String name = dataset.getName().replace(".json", "");
                ChatUtil.send("  §7- §f" + name);
            }
        } else {
            ChatUtil.send("§7Датасеты: §cнет");
        }

        // Модели
        File[] models = MODELS_DIR.toFile().listFiles(File::isDirectory);
        if (models != null && models.length > 0) {
            ChatUtil.send("§aМодели:");
            for (File model : models) {
                String name = model.getName();
                String status = currentModel != null && currentModel.toString().contains(name) ? " §a(активна)" : "";
                ChatUtil.send("  §7- §f" + name + status);
            }
        } else {
            ChatUtil.send("§7Модели: §cнет");
        }
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
