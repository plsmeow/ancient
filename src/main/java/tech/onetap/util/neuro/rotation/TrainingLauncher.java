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
import java.util.concurrent.atomic.AtomicBoolean;

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
 *
 * Python-окружение (интерпретатор и библиотеки) подготавливается само — на
 * первом .ai train / .ai setup в фоновом потоке: системный Python превращается
 * в venv, а на Windows без Python скачивается embeddable-сборка (~11 МБ).
 */
public final class TrainingLauncher implements IMinecraft {

    private static final String RESOURCE_PREFIX = "/onetap/neuro/";
    private static final List<String> RESOURCE_FILES = List.of(
            "train_neuro.py", "requirements.txt"
    );

    private static volatile Process currentProcess = null;

    private static Path toolsDir = null;
    private static final AtomicBoolean busy = new AtomicBoolean(false);

    private TrainingLauncher() {
    }

    public static boolean isRunning() {
return busy.get();
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
            ChatUtil.send("§cОбучение/подготовка уже идёт. Остановить: §f.ai cancel");
            return;
        }

        Path scriptPath = resolveScript();
        if (scriptPath == null) {
            ChatUtil.send("§cНе удалось подготовить trainer (см. лог выше)");
            return;
        }

        Path datasetPath = AIRotationManager.getDatasetsDir().resolve(datasetName + ".csv");
        if (!Files.exists(datasetPath)) {
            Path legacy = AIRotationManager.getDatasetsDir().resolve(datasetName + ".jsonl");
            if (Files.exists(legacy)) {
                ChatUtil.send("§cДатасет §e" + datasetName + " §cв старом формате и несовместим");
                ChatUtil.send("§7Перезапишите его через модуль Ai Record (RAW-CSV)");
            } else {
                ChatUtil.send("§cДатасет §e" + datasetName + " §cне найден!");
            }
            return;
        }


        Thread thread = new Thread(
                () -> trainWorker(datasetPath, modelName, epochs, baseModel, scriptPath),
                "NeuroTraining");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Готовит Python-окружение без запуска обучения (.ai setup).
     * Прогресс скачивания виден в чате; занимает busy, как обучение.
     */
    public static void setup() {
        if (isRunning()) {
            ChatUtil.send("§cОбучение/подготовка уже идёт");
            return;
        }
        Thread thread = new Thread(() -> {
            if (!busy.compareAndSet(false, true)) {
                ChatUtil.send("§cОбучение/подготовка уже идёт");
                return;
            }
            try {
                Path toolsDirPath = resolveScript();
                if (toolsDirPath == null) {
                    ChatUtil.send("§cНе удалось подготовить trainer (см. лог выше)");
                    return;
                }
                Path python = TrainerEnvironment.ensure(
                        toolsDirPath.getParent().resolve("requirements.txt"), ChatUtil::send);
                if (python != null) {
                    ChatUtil.send("§aОкружение готово: §f" + python);
                    ChatUtil.send("§7Теперь можно обучать: §f.ai train <датасет>");
                } else {
                    ChatUtil.send("§cОкружение не готово — см. ошибки выше");
                }
            } finally {
                busy.set(false);
            }
        }, "NeuroSetup");
        thread.setDaemon(true);
        thread.start();
    }

    private static void trainWorker(Path datasetPath, String modelName, int epochs,
                                    String baseModel, Path scriptPath) {
        if (!busy.compareAndSet(false, true)) {
            ChatUtil.send("§cОбучение/подготовка уже идёт");
            return;
        }
        try {
            Path python = TrainerEnvironment.ensure(
                    scriptPath.getParent().resolve("requirements.txt"), ChatUtil::send);
            if (python == null) {
                printManualInstructions(scriptPath, datasetPath, modelName, epochs, baseModel);
                return;
            }

            List<String> command = new ArrayList<>();
            command.add(python.toString());
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

            runProcess(command, scriptPath, modelName);
        } finally {
            busy.set(false);
        }
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
        Path script = toolsDir.resolve("train_neuro.py");
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
            ;
            return devFallback();
        }
    }

    /**
     * Запасной вариант: живые скрипты tools/neuro из корня проекта,
     * если распаковка из jar по какой-то причине недоступна.
     */
    private static Path devFallback() {
        Path devScript = Paths.get("tools", "neuro", "train_neuro.py");
        if (Files.exists(devScript)) {
            ChatUtil.send("§7Trainer (dev): §f" + devScript.toAbsolutePath());
            return Paths.get("tools", "neuro").toAbsolutePath();
        }
        return null;
    }

    private static void runProcess(List<String> command, Path scriptPath, String modelName) {
        try {
            ProcessBuilder pb = TrainerEnvironment.newProcess(command);
            // Рабочая папка — игровая: скрипт пишет модели в .options/ai/models
            // относительно неё. Импорт соседних модулей обеспечивает PYTHONPATH.
            pb.directory(Paths.get("").toAbsolutePath().toFile());
            pb.environment().put("PYTHONPATH", scriptPath.getParent().toString());

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
     * Останавливает обучение или подготовку окружения.
     */
    public static void cancel() {
        boolean stopped = false;
        Process process = currentProcess;
        if (process != null && process.isAlive()) {
            process.destroy();
            stopped = true;
        }
        if (TrainerEnvironment.cancelProvisioning()) {
            stopped = true;
        }
        if (stopped) {
            ChatUtil.send("§eОстанавливаю...");
        } else {
            ChatUtil.send("§7Обучение не запущено");
        }
    }

    /**
     * Авто-подготовка не сработала — печатаем ручной сценарий, а не падаем.
     */
    private static void printManualInstructions(Path scriptPath, Path datasetPath,
                                                String modelName, int epochs, String baseModel) {
        ChatUtil.send("§cАвто-подготовка окружения не сработала — нужен Python 3.9+");
        ChatUtil.send("§7После установки Python библиотеки доедут сами при следующем запуске:");
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
