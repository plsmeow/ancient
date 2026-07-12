package tech.onetap.util.commands.defaults;

import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.chat.ChatUtil;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.neuro.rotation.AIRotationManager;
import tech.onetap.util.neuro.rotation.AIRotationRecorder;

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
                String name = args.getString();
                AIRotationManager.saveDataset(name);
            }

            case "load" -> {
                if (!args.hasAny()) {
                    ChatUtil.send("§cИспользование: §f.ai load <modelname>");
                    return;
                }
                String modelName = args.getString();
                AIRotationManager.loadModel(modelName);
            }

            case "train" -> {
                if (!args.has(2)) {
                    ChatUtil.send("§cИспользование: §f.ai train <dataset> <model> [epochs]");
                    return;
                }
                String datasetName = args.getString();
                String modelName = args.getString();
                int epochs = 100;
                if (args.hasAny()) {
                    try {
                        epochs = Integer.parseInt(args.getString());
                        epochs = Math.max(10, Math.min(500, epochs));
                    } catch (NumberFormatException ignored) {
                    }
                }

                int finalEpochs = epochs;
                ChatUtil.send("§7Начинаю обучение (" + finalEpochs + " эпох)...");
                new Thread(() -> {
                    AIRotationManager.trainModel(datasetName, modelName, finalEpochs);
                }).start();
            }

            case "improve" -> {
                if (!args.has(2)) {
                    ChatUtil.send("§cИспользование: §f.ai improve <model> <dataset> [epochs]");
                    return;
                }
                String modelName = args.getString();
                String datasetName = args.getString();
                int epochs = 100;
                if (args.hasAny()) {
                    try {
                        epochs = Integer.parseInt(args.getString());
                        epochs = Math.max(10, Math.min(500, epochs));
                    } catch (NumberFormatException ignored) {
                    }
                }

                int finalEpochs = epochs;
                ChatUtil.send("§7Дообучаю модель §e" + modelName + " §7на §f" + finalEpochs + " §7эпох...");
                new Thread(() -> {
                    AIRotationManager.improveModel(modelName, datasetName, finalEpochs);
                }).start();
            }

            case "delete" -> {
                if (!args.hasAny()) {
                    ChatUtil.send("§cИспользование: §f.ai delete <modelname>");
                    return;
                }
                String modelName = args.getString();
                AIRotationManager.deleteModel(modelName);
            }

            case "list", "models" -> {
                AIRotationManager.listFiles();
            }

            case "dir" -> {
                AIRotationManager.openDirectory();
            }

            default -> {
                ChatUtil.send("§cНеизвестная подкоманда: §f" + subcommand);
                printHelp();
            }
        }
    }

    private void printHelp() {
        ChatUtil.send("§e§l=== AI Rotation Commands ===");
        ChatUtil.send("§f.ai save <name> §7- Сохранить датасет");
        ChatUtil.send("§f.ai train <ds> <model> [ep] §7- Обучить модель");
        ChatUtil.send("§f.ai improve <model> <ds> [ep] §7- Дообучить модель");
        ChatUtil.send("§f.ai load <model> §7- Загрузить модель");
        ChatUtil.send("§f.ai delete <model> §7- Удалить модель");
        ChatUtil.send("§f.ai models §7- Список моделей");
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
                "Использование:",
                ".ai save <name> - сохранить датасет",
                ".ai train <ds> <model> [epochs] - обучить модель",
                ".ai improve <model> <ds> [epochs] - дообучить модель",
                ".ai load <model> - загрузить модель",
                ".ai delete <model> - удалить модель",
                ".ai models - список моделей",
                ".ai dir - открыть папку"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return Stream.of("save", "load", "train", "improve", "delete", "models", "dir");
        }
        return Stream.empty();
    }
}
