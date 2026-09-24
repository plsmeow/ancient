package meow.ancient.util.commands.defaults;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import meow.ancient.util.commands.api.Command;
import meow.ancient.util.commands.api.argument.IArgConsumer;
import meow.ancient.util.commands.api.exception.CommandException;
import meow.ancient.util.commands.api.helpers.Paginator;
import meow.ancient.util.commands.api.helpers.TabCompleteHelper;
import meow.ancient.util.config.ConfigManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static meow.ancient.util.commands.api.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CfgCommand extends Command {

    private static final Pattern SAFE_NAME = Pattern.compile("[\\w\\- .]{1,64}");

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

    private boolean isValidName(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.equals(".") || trimmed.equals("..")) return false;
        return SAFE_NAME.matcher(trimmed).matches();
    }

    private void handleSave(IArgConsumer args) throws CommandException {
        args.requireExactly(1);
        String name = args.getString();
        if (!isValidName(name)) {
            logDirect(Formatting.GRAY + "Имя конфига невалидное. Допустимы A-Z, a-z, 0-9, пробел, _, - и точка (до 64 символов)");
            return;
        }
        ConfigManager.save(name);
        logDirect(Formatting.GRAY + "Конфиг с именем " + Formatting.WHITE + name + Formatting.GRAY + " успешно сохранён");
    }

    private void handleLoad(IArgConsumer args) throws CommandException {
        args.requireExactly(1);
        String name = args.getString();
        if (!isValidName(name)) {
            logDirect(Formatting.GRAY + "Имя конфига невалидное. Допустимы A-Z, a-z, 0-9, пробел, _, - и точка (до 64 символов)");
            return;
        }

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
        if (!isValidName(name)) {
            logDirect(Formatting.GRAY + "Имя конфига невалидное");
            return;
        }

        Path file = Paths.get(".options/configs").resolve(name + ".json");
        if (Files.exists(file)) {
            try {
                Files.delete(file);
                logDirect(Formatting.GRAY + "Конфиг " + Formatting.WHITE + name + Formatting.GRAY + " успешно удалён");
            } catch (IOException e) {
                logDirect(Formatting.GRAY + "Ошибка при удалении файла.");
            }
        } else {
            logDirect(Formatting.GRAY + "Конфиг не найден");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            return Stream.empty();
        }

        String first = args.peekString();

        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .sortAlphabetically()
                    .prepend("load", "save", "remove", "list", "clear", "dir")
                    .filterPrefix(first)
                    .stream();
        }

        if (args.hasExactly(2)) {
            String action = args.getString().toLowerCase(Locale.US);
            String current = args.peekString();
            if (action.equals("load") || action.equals("remove")) {
                return ConfigManager.getConfigs().stream()
                        .filter(cfg -> cfg.startsWith(current))
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
                "> cfg remove <name> - Удаляет конфиг по имени.",
                "> cfg clear - Удаляет все локальные конфиги.",
                "> cfg dir - Открывает папку с конфигами."
        );
    }
}