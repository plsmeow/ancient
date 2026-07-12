package tech.onetap.util.commands.defaults;

import tech.onetap.Onetap;
import tech.onetap.util.chat.ChatUtil;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.neuro.rotation.AIRotationManager;
import tech.onetap.util.neuro.rotation.AIRotationRecorder;

import java.util.List;
import java.util.stream.Stream;

public class AICommand extends Command {
    
    private static AIRotationRecorder recorder = null;
    
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
            case "start" -> {
                if (AIRotationRecorder.isRecording()) {
                    ChatUtil.send("§cЗапись уже идет!");
                    return;
                }

                if (recorder == null) {
                    recorder = new AIRotationRecorder();
                    Onetap.getInstance().getEventBus().register(recorder);
                }

                AIRotationRecorder.Mode recordMode = AIRotationRecorder.Mode.KILLAURA;
                if (args.hasAny()) {
                    String modeArg = args.getString().toLowerCase();
                    if (modeArg.equals("slimes") || modeArg.equals("slime") || modeArg.equals("слизни")) {
                        recordMode = AIRotationRecorder.Mode.SLIMES;
                    }
                }

                AIRotationRecorder.startRecording(recordMode);
                if (recordMode == AIRotationRecorder.Mode.SLIMES) {
                    ChatUtil.send("§aЗапись начата (режим слизней)!");
                    ChatUtil.send("§7Ищите слизня рядом, цельтесь в него вручную");
                } else {
                    ChatUtil.send("§aЗапись начата!");
                    ChatUtil.send("§7Атакуйте цель, ваши движения будут записаны");
                }
                ChatUtil.send("§7Используйте §f.ai stop §7для остановки");
            }

            case "stop" -> {
                if (!AIRotationRecorder.isRecording()) {
                    ChatUtil.send("§cЗапись не идет!");
                    return;
                }
                
                int samples = AIRotationRecorder.stopRecording();
                ChatUtil.send("§aЗапись остановлена!");
                ChatUtil.send("§7Записано сэмплов: §f" + samples);
                ChatUtil.send("§7Используйте §f.ai save <name> §7для сохранения");
            }

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
                    ChatUtil.send("§cИспользование: §f.ai train <dataset> <modelname>");
                    return;
                }
                String datasetName = args.getString();
                String modelName = args.getString();
                
                ChatUtil.send("§7Начинаю обучение...");
                // Запускаем в отдельном потоке чтобы не блокировать игру
                new Thread(() -> {
                    AIRotationManager.trainModel(datasetName, modelName);
                }).start();
            }

            case "list" -> {
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
        ChatUtil.send("§f.ai start §7- Начать запись (KillAura)");
        ChatUtil.send("§f.ai start slimes §7- Запись датасета на слизнях");
        ChatUtil.send("§f.ai stop §7- Остановить запись");
        ChatUtil.send("§f.ai save <name> §7- Сохранить датасет");
        ChatUtil.send("§f.ai train <dataset> <model> §7- Обучить модель");
        ChatUtil.send("§f.ai load <model> §7- Загрузить модель");
        ChatUtil.send("§f.ai list §7- Список файлов");
        ChatUtil.send("§f.ai dir §7- Открыть папку");
    }

    @Override
    public String getShortDesc() {
        return "Управление AI ротациями";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "Команда для записи, обучения и использования AI моделей ротаций",
                "",
                "Использование:",
                ".ai start - начать запись (KillAura)",
                ".ai start slimes - запись датасета на слизнях",
                ".ai stop - остановить запись",
                ".ai save <name> - сохранить датасет",
                ".ai train <dataset> <model> - обучить модель",
                ".ai load <model> - загрузить модель",
                ".ai list - список файлов",
                ".ai dir - открыть папку"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return Stream.of("start", "stop", "save", "load", "train", "list", "dir");
        }
        if (args.hasAny()) {
            String subcommand = args.getString();
            if (subcommand.equalsIgnoreCase("start") && args.hasExactlyOne()) {
                String prefix = args.peekString();
                return Stream.of("slimes").filter(mode -> mode.startsWith(prefix.toLowerCase()));
            }
        }
        return Stream.empty();
    }
}
