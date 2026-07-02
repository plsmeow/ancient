package tech.onetap.util.commands.defaults;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.commands.api.exception.CommandNotEnoughArgumentsException;
import tech.onetap.util.commands.api.helpers.Paginator;
import tech.onetap.util.commands.api.helpers.TabCompleteHelper;
import tech.onetap.util.config.ConfigManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static tech.onetap.util.commands.api.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CfgCommand extends Command {

    public CfgCommand() {
        super("cfg");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String action = args.hasAny() ? args.getString().toLowerCase(Locale.US) : "list";

        switch (action) {
            case "save" -> handleSave(args);
            case "load" -> handleLoad(args);
            case "list" -> handleList(args, label);
            case "clear" -> {
                List<String> configs = ConfigManager.getConfigs();
                for (String name : configs) {
                    Path file = Paths.get(".options/configs").resolve(name + ".json");
                    if (Files.exists(file)) {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            logDirect(Formatting.GRAY + "Ошибка при удалении файла.");
                            e.printStackTrace();
                        }
                    }
                }
                logDirect("Список конфигов очищен", Formatting.GRAY);
            }
            case "dir" -> {
                try {
                    File dir = new File(".options/configs/");
                    if (!dir.exists()) {
                        logDirect(Formatting.GRAY + "Ты нахуя папку удалил фрик");
                        dir.mkdirs();
                    } else {
                        logDirect(Formatting.GRAY + "Открываю папку с конфигами...");
                    }
                    Runtime.getRuntime().exec("explorer " + dir.getAbsolutePath());
                } catch (IOException e) {
                    logDirect(Formatting.GRAY + "Ошибка при открытии папки: "
                            + Formatting.WHITE + e.getMessage());
                }
            }
            case "remove" -> handleRemove(args);
            default -> logDirect("Неизвестная подкоманда. Используй load/save/remove/list/dir.", Formatting.GRAY);
        }
    }

    private void handleSave(IArgConsumer args) throws CommandException {
        args.requireExactly(1);
        String name = args.getString();
        ConfigManager.save(name);
        logDirect(Formatting.GRAY + "Конфиг с именем " + Formatting.WHITE + name + Formatting.GRAY + " успешно сохранён");
    }

    private void handleLoad(IArgConsumer args) throws CommandException {
        args.requireExactly(1);
        String name = args.getString();

        if (!ConfigManager.getConfigs().contains(name)) {
            logDirect(Formatting.GRAY + "Конфиг с таким именем не найден");
            return;
        }

        ConfigManager.load(name);
        logDirect(Formatting.GRAY + "Конфиг с именем " + Formatting.WHITE + name + Formatting.GRAY + " успешно загружен");
    }

    private void handleList(IArgConsumer args, String label) throws CommandException {
        args.requireMax(1);
        List<String> configs = ConfigManager.getConfigs();

        logDirect("Список конфигов:", Formatting.GRAY);
        Paginator.paginate(
                args,
                new Paginator<>(configs),
                name -> {
                    Text nameText = Text.literal(Formatting.GRAY + "- " + Formatting.WHITE + name + " ");
                    Text loadText = Text.literal(Formatting.GREEN + "[Загрузить]")
                            .styled(style -> style.withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    FORCE_COMMAND_PREFIX + "cfg load " + name
                            )).withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("Click to load config")
                            )));
                    Text deleteText = Text.literal(Formatting.RED + " [Удалить]")
                            .styled(style -> style.withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    FORCE_COMMAND_PREFIX + "cfg remove " + name
                            )).withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("Click to delete config")
                            )));

                    return nameText.copy().append(loadText).append(deleteText);
                },
                FORCE_COMMAND_PREFIX + label
        );
    }

    private void handleRemove(IArgConsumer args) throws CommandException {
        args.requireExactly(1);
        String name = args.getString();

        Path file = Paths.get("onetap/configs").resolve(name + ".json");
        if (Files.exists(file)) {
            try {
                Files.delete(file);
                logDirect(Formatting.GRAY + "Конфиг " + Formatting.WHITE + name + Formatting.GRAY + " успешно удалён");
            } catch (IOException e) {
                logDirect(Formatting.GRAY + "Ошибка при удалении файла.");
                e.printStackTrace();
            }
        } else {
            logDirect(Formatting.GRAY + "Конфиг не найден");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny() && args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .sortAlphabetically()
                    .prepend("load", "save", "remove", "list", "clear", "dir")
                    .filterPrefix(args.getString())
                    .stream();
        } else if (args.hasAny()) {
            String arg = args.getString();
            if (args.hasExactlyOne() && (arg.equalsIgnoreCase("load") || arg.equalsIgnoreCase("remove"))) {
                return ConfigManager.getConfigs().stream()
                        .filter(cfg -> {
                            try {
                                return cfg.startsWith(args.peekString());
                            } catch (CommandNotEnoughArgumentsException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .sorted();
            }
        }

        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Управление конфигами";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Команда для управления конфигурациями клиента.",
                "",
                "Использование:",
                "> cfg save <name> - Сохраняет текущую конфигурацию.",
                "> cfg load <name> - Загружает конфигурацию.",
                "> cfg list - Показывает все доступные конфиги.",
                "> cfg remove <name> - Удаляет конфиг по имени."
        );
    }
}