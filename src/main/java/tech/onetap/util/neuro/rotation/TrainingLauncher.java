package tech.onetap.util.neuro.rotation;

import tech.onetap.util.IMinecraft;
import tech.onetap.util.chat.ChatUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Запускает внешний Python-тренер и стримит его вывод в чат.
 *
 * Обучение вынесено из игры целиком: JVM Minecraft не должна крутить ML.
 *
 * Скрипты тренера поставляются ВНУТРИ jar (tools/neuro упаковывается в
 * resources/onetap/neuro на этапе processResources) и при первом запуске
 * распаковываются в .options/ai/neuro — все .ai команды обучения работают
 * только с этой копией, поэтому клиент работает из любой папки, а не
 * только из корня проекта. Распаковка всегда перезаписывает файлы, чтобы
 * скрипты не расходились с версией клиента.
 */
public final class TrainingLauncher implements IMinecraft {

    private static final String RESOURCE_PREFIX = "/onetap/neuro/";
    private static final List<String> RESOURCE_FILES = List.of(
            "train.py", "dataset.py", "model.py", "requirements.txt"
    );

    private static volatile Process currentProcess = null;

    private static Path toolsDir = null;

    private TrainingLauncher() {
    }

    public static boolean isRunning() {
        Process p = currentProcess;
        return p != null && p.isAlive();
    }

    /**
     * Распаковывает скрипты тренера в .options/ai/neuro заранее, чтобы
     * .ai train / .ai improve не ждали распаковку на первом вызове.
     * Вызывается при инициализации клиента: сообщения в чат на этом этапе
     * проглатываются (игрок ещё не в мире) — это нормально.
     */
    public static void prepareTools() {
        resolveScript();
    }

    /**
     * Запускает обучение. Возвращает сразу, вывод идёт в чат асинхронно.
     */
    public static void train(String datasetName, String modelName, int epochs, String baseModel) {
        if (isRunning()) {
            ChatUtil.send("§cОбучение уже идёт. Остановите его: §f.ai cancel");
            return;
        }

        Path scriptPath = resolveScript();
        if (scriptPath == null) {
            ChatUtil.send("§cНе удалось подготовить trainer (см. лог выше)");
            return;
        }

        Path datasetPath = AIRotationManager.getDatasetsDir().resolve(datasetName + ".jsonl");
        if (!Files.exists(datasetPath)) {
            Path legacy = AIRotationManager.getDatasetsDir().resolve(datasetName + ".json");
            if (Files.exists(legacy)) {
                ChatUtil.send("§cДатасет §e" + datasetName + " §cв старом формате v1 и несовместим");
                ChatUtil.send("§7Перезапишите его через модуль Ai Record");
            } else {
                ChatUtil.send("§cДатасет §e" + datasetName + " §cне найден!");
            }
            return;
        }

        String python = findPython();
        if (python == null) {
            printManualInstructions(scriptPath, datasetPath, modelName, epochs, baseModel);
            return;
        }

        List<String> command = new ArrayList<>();
        command.add(python);
        command.add(scriptPath.toString());
        command.add("--dataset");
        command.add(datasetPath.toString());
        command.add("--out");
        command.add(modelName);
        command.add("--epochs");
        command.add(String.valueOf(epochs));
        // Из игры обучаем до конца без early stopping — пользователь сам
        // выбирает число эпох и ждёт именно столько.
        command.add("--patience");
        command.add("0");
        if (baseModel != null) {
            command.add("--base");
            command.add(baseModel);
        }

        Thread thread = new Thread(() -> runProcess(command, scriptPath, modelName), "NeuroTraining");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Определяет каталог со скриптами тренера.
     * Всегда распаковывает ресурсы из jar в .options/ai/neuro и использует их.
     */
    private static Path resolveScript() {
        if (toolsDir == null) {
            toolsDir = extractTools();
        }
        if (toolsDir == null) {
            return null;
        }
        Path script = toolsDir.resolve("train.py");
        return Files.exists(script) ? script : null;
    }

    /**
     * Распаковывает скрипты из jar в .options/ai/neuro.
     * Файлы всегда перезаписываются — копия не должна расходиться с версией клиента.
     * Fallback: живой tools/neuro из корня проекта (dev-запуск без пересобранных ресурсов).
     */
    private static Path extractTools() {
        Path target = AIRotationManager.getAiDir().resolve("neuro");

        try {
            Files.createDirectories(target);

            for (String resource : RESOURCE_FILES) {
                Path out = target.resolve(resource);
                try (InputStream in = TrainingLauncher.class.getResourceAsStream(RESOURCE_PREFIX + resource)) {
                    if (in == null) {
                        ChatUtil.send("§cРесурс не найден в jar: §f" + RESOURCE_PREFIX + resource);
                        return devFallback();
                    }
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            ChatUtil.send("§7Trainer: §f" + target.toAbsolutePath());
            return target;
        } catch (IOException e) {
            ChatUtil.send("§cНе удалось распаковать trainer: " + e.getMessage());
            e.printStackTrace();
            return devFallback();
        }
    }

    /**
     * Запасной вариант: живые скрипты tools/neuro из корня проекта,
     * если распаковка из jar по какой-то причине недоступна.
     */
    private static Path devFallback() {
        Path devScript = Paths.get("tools", "neuro", "train.py");
        if (Files.exists(devScript)) {
            ChatUtil.send("§7Trainer (dev): §f" + devScript.toAbsolutePath());
            return Paths.get("tools", "neuro").toAbsolutePath();
        }
        return null;
    }

    private static void runProcess(List<String> command, Path scriptPath, String modelName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            // Рабочая папка — игровая: скрипт пишет модели в .options/ai/models
            // относительно неё. Импорт соседних модулей обеспечивает PYTHONPATH.
            pb.directory(Paths.get("").toAbsolutePath().toFile());
            pb.redirectErrorStream(true);
            pb.environment().put("PYTHONPATH", scriptPath.getParent().toString());
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("PYTHONUNBUFFERED", "1");

            Process process = pb.start();
            currentProcess = process;

            ChatUtil.send("§7Обучаю модель §e" + modelName + "§7...");

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    reportLine(line);
                }
            }

            int exit = process.waitFor();
            if (exit == 0) {
                ChatUtil.send("§aОбучение завершено: §e" + modelName);
                ChatUtil.send("§7Загрузить: §f.ai load " + modelName);
            } else {
                ChatUtil.send("§cОбучение прервано (код " + exit + ")");
            }
        } catch (InterruptedException e) {
            ChatUtil.send("§eОбучение отменено");
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            ChatUtil.send("§cНе удалось запустить обучение: " + e.getMessage());
        } finally {
            currentProcess = null;
        }
    }

    /**
     * Фильтрует вывод скрипта: прогресс по эпохам и важные строки.
     */
    private static void reportLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return;

        if (trimmed.startsWith("[torch.onnx]")) return;

        if (trimmed.startsWith("Epoch")) {
            if (trimmed.contains("/") && !shouldReportEpoch(trimmed)) return;
            ChatUtil.send("§7" + trimmed);
            return;
        }

        if (trimmed.startsWith("Final:") || trimmed.startsWith("✓")) {
            ChatUtil.send("§a" + trimmed);
            return;
        }

        if (trimmed.contains("Ошибка") || trimmed.contains("FAILED") || trimmed.contains("Error")) {
            ChatUtil.send("§c" + trimmed);
            return;
        }

        ChatUtil.send("§7" + trimmed);
    }

    private static boolean shouldReportEpoch(String line) {
        try {
            int idx = line.indexOf("Epoch") + 5;
            int slash = line.indexOf('/', idx);
            if (slash < 0) return true;
            int epoch = Integer.parseInt(line.substring(idx, slash).trim());
            return epoch % 5 == 0;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Останавливает обучение.
     */
    public static void cancel() {
        Process process = currentProcess;
        if (process == null || !process.isAlive()) {
            ChatUtil.send("§7Обучение не запущено");
            return;
        }
        process.destroy();
        ChatUtil.send("§eОстанавливаю обучение...");
    }

    /**
     * Ищет доступный интерпретатор Python.
     */
    private static String findPython() {
        for (String candidate : new String[]{"python", "py", "python3"}) {
            try {
                Process p = new ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true)
                        .start();
                if (p.waitFor() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Python не найден — печатаем готовую команду, а не падаем.
     */
    private static void printManualInstructions(Path scriptPath, Path datasetPath,
                                                String modelName, int epochs, String baseModel) {
        ChatUtil.send("§cPython не найден в PATH");
        ChatUtil.send("§7Установите Python 3.9+ и зависимости:");
        ChatUtil.send("§f  pip install -r " + scriptPath.getParent().resolve("requirements.txt").toAbsolutePath());
        ChatUtil.send("§7Затем запустите обучение вручную:");

        StringBuilder cmd = new StringBuilder("  python ");
        cmd.append(scriptPath.toAbsolutePath())
                .append(" --dataset ").append(datasetPath.toAbsolutePath())
                .append(" --out ").append(modelName)
                .append(" --epochs ").append(epochs);
        if (baseModel != null) {
            cmd.append(" --base ").append(baseModel);
        }
        ChatUtil.send("§f" + cmd);
    }
}
