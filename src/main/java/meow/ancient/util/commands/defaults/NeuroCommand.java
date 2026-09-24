package meow.ancient.util.commands.defaults;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import meow.ancient.Ancient;
import meow.ancient.module.list.combat.KillAura;
import meow.ancient.util.chat.ChatUtil;
import meow.ancient.util.commands.api.Command;
import meow.ancient.util.commands.api.argument.IArgConsumer;
import meow.ancient.util.commands.api.exception.CommandException;
import meow.ancient.util.neuro.rotation.NeuroModel;
import meow.ancient.util.neuro.rotation.NeuroRecorder;
import meow.ancient.util.neuro.rotation.NeuroTrainer;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Команда .neuro (и алиасы .nr, .neurorotation, .ai) для управления нейросетевыми моделями,
 * записью датасетов и обучением.
 */
public class NeuroCommand extends Command {
    private static volatile boolean trainingRunning;

    public NeuroCommand() {
        super("neuro", "nr", "neurorotation", "ai");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            showStatus();
            return;
        }

        String action = args.getString().toLowerCase(Locale.ROOT);
        String nameArg = args.hasAny() ? args.getString() : null;
        String epochsArg = args.hasAny() ? args.getString() : null;

        switch (action) {
            case "status" -> showStatus();
            case "list", "models" -> showList();
            case "load" -> loadModel(nameArg);
            case "dir" -> openDir();
            case "train" -> trainModel(nameArg, epochsArg);
            case "record", "rec" -> startRecord(nameArg);
            case "stop" -> stopRecord();
            case "data" -> showData();
            case "why" -> showWhy();
            default -> ChatUtil.send("§cИспользование: §f.neuro <list|load|dir|train|record|stop|data|why> [имя] [эпохи|auto]");
        }
    }

    private void showStatus() {
        NeuroModel model = NeuroModel.getActive();
        List<String> allModels = NeuroModel.listModels();
        ChatUtil.send("Модель §b" + NeuroModel.getActiveName() + "§r — "
                + (model == null ? "§cне загрузилась§r" : "§aготова§r")
                + ", всего " + allModels.size() + ".");

        int totalTicks = NeuroRecorder.getTotalTicks();
        NeuroRecorder recorder = getRecorder();
        String needed = NeuroRecorder.formatNeeded(totalTicks);

        ChatUtil.send("Записано: §b" + NeuroRecorder.formatMinutes(totalTicks) + "§r боя ("
                + NeuroRecorder.evaluateQuality(totalTicks) + ")"
                + (recorder != null && recorder.isRecording() ? " §c● пишется " + recorder.getDatasetName() + "§r" : ""));

        if (needed != null) {
            ChatUtil.send("§7До обучения не хватает ещё " + needed + " записи.§r");
        }

        ChatUtil.send("Тренер: §7проверка...§r");
        Thread thread = new Thread(() -> {
            String status = getTrainerStatus();
            MinecraftClient.getInstance().execute(() -> ChatUtil.send("Тренер: " + status));
        }, "neuro-trainer-check");
        thread.setDaemon(true);
        thread.start();
    }

    private String getTrainerStatus() {
        File py = NeuroTrainer.findPython();
        if (py == null) {
            return "§7нет питон-рантайма (установите python3)§r";
        }
        if (NeuroTrainer.isEnvironmentReady()) {
            return "§aготов§r";
        }
        boolean hasNumpy = NeuroTrainer.hasModule("numpy");
        boolean hasTorch = NeuroTrainer.hasModule("torch");
        String missing = !hasNumpy && !hasTorch ? "numpy и torch" : (!hasNumpy ? "numpy" : "torch");
        return "§cнет " + missing + " (pip install " + missing + ")§r";
    }

    private void showList() {
        List<String> list = NeuroModel.listModels();
        ChatUtil.send("Моделей: §b" + list.size() + "§r");
        for (String m : list) {
            boolean active = m.equals(NeuroModel.getActiveName());
            boolean builtin = !Files.isRegularFile(NeuroModel.getModelPath(m));
            ChatUtil.send((active ? "§b > " : "§7 · ") + m
                    + (builtin ? " §8(встроенная)" : "")
                    + (active ? " §8активна" : "")
                    + "§r");
        }
    }

    private void loadModel(String name) {
        if (name == null || name.isBlank()) {
            ChatUtil.send("§cИспользование: §f.neuro load <имя>");
            return;
        }
        if (!NeuroModel.hasModel(name)) {
            ChatUtil.send("§cМодели " + name + " нет. Список: .neuro list");
            return;
        }
        NeuroModel.setActiveModel(name);
        ChatUtil.send(NeuroModel.getActive() == null
                ? "§cМодель " + name + " выбрана, но не читается§r"
                : "Модель §b" + name + "§r загружена.");
    }

    private void openDir() {
        Path path = NeuroModel.getNeuroDir();
        try {
            Files.createDirectories(path);
        } catch (Exception ignored) {
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
                ChatUtil.send("Папка моделей открыта: " + path);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            new ProcessBuilder("xdg-open", path.toString()).start();
            ChatUtil.send("Папка моделей открыта: " + path);
            return;
        } catch (Exception ignored) {
        }
        ChatUtil.send("Папка моделей: " + path);
    }

    private void trainModel(String nameArg, String epochsArg) {
        if (trainingRunning) {
            ChatUtil.send("§cОбучение уже идёт.");
            return;
        }
        String modelName = (nameArg == null || nameArg.isBlank()) ? NeuroModel.getActiveName() : nameArg;
        String epochs = epochsArg == null ? null : (epochsArg.equalsIgnoreCase("auto") ? "0" : (epochsArg.matches("\\d+") ? epochsArg : null));
        if (epochsArg != null && epochs == null) {
            ChatUtil.send("§cЭпохи — число или auto.");
            return;
        }

        File py = NeuroTrainer.findPython();
        if (py == null) {
            ChatUtil.send("§cПитон-рантайм не найден (установите python3) — обучать нечем.");
            return;
        }
        if (!NeuroTrainer.isEnvironmentReady()) {
            ChatUtil.send("§cДля обучения нужны numpy и torch: pip install numpy torch");
            return;
        }

        int totalTicks = NeuroRecorder.getTotalTicks();
        if (totalTicks < 600) {
            ChatUtil.send("§cМало записи: " + NeuroRecorder.formatMinutes(totalTicks) + " боя, нужно хотя бы 0.5 мин.");
            ChatUtil.send("§7Запиши ещё: .neuro record <имя>§r");
            return;
        }

        trainingRunning = true;
        ChatUtil.send("Обучение модели §b" + modelName + "§r пошло ("
                + ("0".equals(epochs) ? "авто, пока падает val" : (epochs == null ? "100" : epochs) + " эпох")
                + "). Прогресс будет в чате.");

        Path dataDir = NeuroModel.getDataDir();
        Path outPath = NeuroModel.getModelPath(modelName);

        Thread thread = new Thread(() -> runTrainingProcess(py, dataDir, outPath, modelName, epochs), "neuro-train");
        thread.setDaemon(true);
        thread.start();
    }

    private void runTrainingProcess(File py, Path dataDir, Path outPath, String modelName, String epochs) {
        try {
            Files.createDirectories(outPath.getParent());
            Process proc = NeuroTrainer.startTraining(py, dataDir, outPath, epochs);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.strip();
                    if (!trimmed.isEmpty()) {
                        if (trimmed.startsWith("!! ")) {
                            printInMainThread("§c" + trimmed.substring(3) + "§r");
                        } else if (trimmed.startsWith("! ")) {
                            printInMainThread("§e" + trimmed.substring(2) + "§r");
                        } else {
                            printInMainThread("§7" + trimmed + "§r");
                        }
                    }
                }
            }
            int code = proc.waitFor();
            if (code == 0) {
                printInMainThread("§aГотово. Включаю " + modelName + ".§r");
                MinecraftClient.getInstance().execute(() -> NeuroModel.setActiveModel(modelName));
            } else {
                printInMainThread("§cТренер завершился с кодом " + code + ".§r");
            }
        } catch (Exception e) {
            printInMainThread("§cОбучение сорвалось: " + e.getMessage() + "§r");
        } finally {
            trainingRunning = false;
        }
    }

    private void printInMainThread(String text) {
        MinecraftClient.getInstance().execute(() -> ChatUtil.send(text));
    }

    private void startRecord(String name) {
        NeuroRecorder recorder = getRecorder();
        if (recorder == null) {
            ChatUtil.send("§cМодуль KillAura не найден");
            return;
        }
        if (recorder.isRecording()) {
            ChatUtil.send("§cУже пишется " + recorder.getDatasetName() + ". Останови: .neuro stop");
            return;
        }
        if (name == null || name.isBlank()) {
            ChatUtil.send("§cИспользование: .neuro record <имя>");
            return;
        }
        String cleanName = name.replaceAll("[^\\p{L}\\p{N}_.-]", "");
        if (cleanName.isEmpty()) {
            ChatUtil.send("§cПлохое имя датасета.");
            return;
        }
        int existing = NeuroRecorder.getDatasetTicks(cleanName);
        String err = recorder.startRecording(cleanName);
        if (err != null) {
            ChatUtil.send("§c" + err);
            return;
        }
        ChatUtil.send("Пишу §b" + cleanName + "§r"
                + (existing > 0 ? " (дозапись, там уже " + NeuroRecorder.formatMinutes(existing) + ")" : "")
                + ". Бей руками, аимы/ауры выключи. Стоп — .neuro stop");

        String needed = NeuroRecorder.formatNeeded(NeuroRecorder.getTotalTicks());
        if (needed != null) {
            ChatUtil.send("§7До обучения нужно ещё " + needed + " боя.§r");
        }
    }

    private void stopRecord() {
        NeuroRecorder recorder = getRecorder();
        if (recorder == null) {
            ChatUtil.send("§cМодуль KillAura не найден");
            return;
        }
        ChatUtil.send(recorder.stopRecording());
    }

    private void showData() {
        List<String> list = NeuroRecorder.listDatasets();
        if (list.isEmpty()) {
            ChatUtil.send("Датасетов нет. Записать — .neuro record <имя>");
            return;
        }
        int total = NeuroRecorder.getTotalTicks();
        ChatUtil.send("Датасетов §b" + list.size() + "§r, всего §b"
                + NeuroRecorder.formatMinutes(total) + "§r боя ("
                + NeuroRecorder.evaluateQuality(total) + ")");
        for (String s : list) {
            ChatUtil.send("§8 · §7" + s + "§r");
        }
        int corrupted = NeuroRecorder.getCorruptedLinesCount();
        if (corrupted > 0) {
            ChatUtil.send("§7Битые строки тренер пропускает автоматически.§r");
        }
        String needed = NeuroRecorder.formatNeeded(total);
        if (needed != null) {
            ChatUtil.send("§7До обучения не хватает ещё " + needed + " записи.§r");
        }
    }

    private void showWhy() {
        ChatUtil.send("§b[Neuro]§r Состояние ротации: модель " + NeuroModel.getActiveName()
                + " (" + (NeuroModel.getActive() != null ? "§aзагружена§r" : "§cне найдена§r") + ")");
    }

    private NeuroRecorder getRecorder() {
        KillAura ka = Ancient.getInstance().getModuleStorage().get(KillAura.class);
        return ka != null ? ka.getNeuroRotation().getRecorder() : null;
    }

    @Override
    public String getShortDesc() {
        return "Управление нейросетевой ротацией";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                ".neuro - статус модели и датасетов",
                ".neuro list - список моделей",
                ".neuro load <name> - загрузить модель",
                ".neuro train [name] [epochs] - обучить модель",
                ".neuro record <name> - записать датасет боя",
                ".neuro stop - остановить запись",
                ".neuro data - список датасетов",
                ".neuro dir - открыть папку с моделями"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            return Stream.of("status", "list", "load", "train", "record", "stop", "data", "dir", "why");
        }
        return Stream.empty();
    }
}
