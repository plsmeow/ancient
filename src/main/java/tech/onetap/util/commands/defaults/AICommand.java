package tech.onetap.util.commands.defaults;

import tech.onetap.util.chat.ChatUtil;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.neuro.rotation.AIRotationManager;
import tech.onetap.util.neuro.rotation.NeuroBenchmark;
import tech.onetap.util.neuro.rotation.RotationDumpRecorder;
import tech.onetap.util.neuro.rotation.TrainingLauncher;

import java.util.List;
import java.util.stream.Stream;

public class AICommand extends Command {

    public AICommand() {
        super("ai");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            printHelp();
            return;
        }

        String subcommand = args.getString().toLowerCase();

        switch (subcommand) {
            case "save" -> {
                if (!args.hasAny()) {
                    ChatUtil.send("§cИспользование: §f.ai save <name>");
                    return;
                }
                AIRotationManager.saveDataset(args.getString());
            }

            case "load" -> {
                if (!args.hasAny()) {
                    ChatUtil.send("§cИспользование: §f.ai load <modelname>");
                    return;
                }
                AIRotationManager.loadModel(args.getString());
            }

            case "unload" -> AIRotationManager.unloadModel();

            case "train" -> {
                if (!args.hasAny()) {
                    ChatUtil.send("§cИспользование: §f.ai train <dataset> [model] [epochs]");
                    return;
                }
                String datasetName = args.getString();

                // Совместимость: старый синтаксис .ai train <dataset> <model> [epochs].
                // Если модель не указана — версионируем автоматически.
                String modelName = args.hasAny() ? args.getString() : nextModelVersion();
                int epochs = parseEpochs(args);

                TrainingLauncher.train(datasetName, modelName, epochs, null);
            }

            case "improve" -> {
                if (!args.has(2)) {
                    ChatUtil.send("§cИспользование: §f.ai improve <model> <dataset> [epochs]");
                    return;
                }
                // Порядок аргументов обратный относительно train — сохраняем как было
                String baseModel = args.getString();
                String datasetName = args.getString();
                int epochs = parseEpochs(args);

                String outName = nextImprovedName(baseModel);
                TrainingLauncher.train(datasetName, outName, epochs, baseModel);
            }

            case "cancel" -> TrainingLauncher.cancel();

            case "benchmark" -> NeuroBenchmark.run();

            case "delete" -> {
                if (!args.hasAny()) {
                    ChatUtil.send("§cИспользование: §f.ai delete <modelname>");
                    return;
                }
                AIRotationManager.deleteModel(args.getString());
            }

            case "list", "models" -> AIRotationManager.listFiles();

            case "dump" -> {
                if (!args.hasAny()) {
                    ChatUtil.send("§cИспользование: §f.ai dump start <ник|ник2> §7/ §f.ai dump stop");
                    return;
                }
                String action = args.getString().toLowerCase();
                switch (action) {
                    case "start" -> {
                        if (!args.hasAny()) {
                            ChatUtil.send("§cИспользование: §f.ai dump start <ник|ник2>");
                            return;
                        }
                        RotationDumpRecorder.start(args.getString());
                    }
                    case "stop" -> RotationDumpRecorder.stopAndSave();
                    default -> ChatUtil.send("§cИспользование: §f.ai dump start <ник|ник2> §7/ §f.ai dump stop");
                }
            }

            case "dir" -> AIRotationManager.openDirectory();

            default -> {
                ChatUtil.send("§cНеизвестная подкоманда: §f" + subcommand);
                printHelp();
            }
        }
    }

    private int parseEpochs(IArgConsumer args) {
        int epochs = 100;
        if (args.hasAny()) {
            try {
                epochs = Math.max(1, Integer.parseInt(args.getString()));
            } catch (Exception ignored) {
            }
        }
        return epochs;
    }

    /**
     * Подбирает свободное имя rotation-vN.
     */
    private String nextModelVersion() {
        java.io.File dir = AIRotationManager.getModelsDir().toFile();
        for (int i = 1; i < 1000; i++) {
            String candidate = "rotation-v" + i;
            if (!new java.io.File(dir, candidate).exists()) {
                return candidate;
            }
        }
        return "rotation-v1";
    }

    /**
     * Подбирает свободное имя {base}-improved-N. Старая модель не перезаписывается.
     */
    private String nextImprovedName(String baseModel) {
        java.io.File dir = AIRotationManager.getModelsDir().toFile();
        for (int i = 1; i < 1000; i++) {
            String candidate = baseModel + "-improved-" + i;
            if (!new java.io.File(dir, candidate).exists()) {
                return candidate;
            }
        }
        return baseModel + "-improved-1";
    }

    private void printHelp() {
        ChatUtil.send("§e§l=== AI Rotation Commands ===");
        ChatUtil.send("§f.ai save <name> §7- Сохранить датасет");
        ChatUtil.send("§f.ai train <ds> [model] [ep] §7- Обучить модель");
        ChatUtil.send("§f.ai improve <model> <ds> [ep] §7- Дообучить модель");
        ChatUtil.send("§f.ai cancel §7- Остановить обучение");
        ChatUtil.send("§f.ai load <model> §7- Загрузить модель");
        ChatUtil.send("§f.ai unload §7- Выгрузить модель");
        ChatUtil.send("§f.ai delete <model> §7- Удалить модель");
        ChatUtil.send("§f.ai models §7- Список моделей");
        ChatUtil.send("§f.ai dump start <ник|ник2> §7- Писать чужую ротацию");
        ChatUtil.send("§f.ai dump stop §7- Остановить дамп и сохранить");
        ChatUtil.send("§f.ai benchmark §7- Замер времени inference");
        ChatUtil.send("§f.ai dir §7- Открыть папку");
    }

    @Override
    public String getShortDesc() {
        return "Управление AI ротациями";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "Команда для управления AI моделями ротаций",
                "",
                "Обучение выполняется внешним Python-скриптом (tools/neuro/train.py),",
                "поэтому игра не лагает во время тренировки.",
                "",
                "Использование:",
                ".ai save <name> - сохранить датасет",
                ".ai train <ds> [model] [epochs] - обучить модель",
                ".ai improve <model> <ds> [epochs] - дообучить модель",
                ".ai cancel - остановить обучение",
                ".ai load <model> - загрузить модель",
                ".ai unload - выгрузить модель",
                ".ai delete <model> - удалить модель",
                ".ai models - список моделей и датасетов",
                ".ai dump start <ник|ник2> - записывать чужую ротацию в датасет",
                ".ai dump stop - остановить дамп и сохранить датасет",
                ".ai benchmark - замер времени inference",
                ".ai dir - открыть папку"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return Stream.of("save", "load", "unload", "train", "improve", "cancel",
                    "delete", "list", "models", "dump", "benchmark", "dir");
        }
        return Stream.empty();
    }
}
