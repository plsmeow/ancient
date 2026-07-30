package tech.onetap.util.commands.defaults;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import tech.onetap.util.cloud.CloudConfigApi;
import tech.onetap.util.cloud.CloudConfigEntry;
import tech.onetap.util.cloud.CodeGenerator;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;
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
            case "cloud" -> handleCloud(args, label);
            default -> logDirect("Неизвестная подкоманда. Используй load/save/remove/list/dir/cloud.", Formatting.GRAY);
        }
    }

    private void handleCloud(IArgConsumer args, String label) throws CommandException {
        if (!args.hasAny()) {
            handleCloudList();
            return;
        }

        String sub = args.getString().toLowerCase(Locale.US);
        switch (sub) {
            case "load" -> handleCloudLoad(args, label);
            case "share" -> handleCloudShare(args);
            case "del", "delete", "remove" -> handleCloudDelete(args, label);
            case "save" -> handleCloudSave(args);
            default -> logDirect("Неизвестная подкоманда cloud. Используй load/share/save/del.", Formatting.GRAY);
        }
    }

    private void handleCloudLoad(IArgConsumer args, String label) throws CommandException {
        args.requireExactly(1);
        String code = args.getString().toUpperCase(Locale.US);

        if (!CodeGenerator.isValid(code)) {
            logDirect(Formatting.GRAY + "Неверный формат кода. Пример: ABCD-1234");
            return;
        }

        logDirect(Formatting.GRAY + "Загружаю конфиг " + Formatting.WHITE + code + Formatting.GRAY + "...");
        CloudConfigApi.fetchByCode(code,
                result -> {
                    try {
                        Path target = Paths.get(".options/configs").resolve(result.name() + ".json");
                        Files.createDirectories(target.getParent());
                        Files.write(target, result.jsonData().getBytes());
                        ConfigManager.load(result.name());
                        Text clickable = Text.literal(result.name())
                                .styled(s -> s.withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        FORCE_COMMAND_PREFIX + "cfg load " + result.name()
                                )).withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Text.literal("Click to load config")
                                )));
                        logDirect("Конфиг загружен: ", Formatting.GRAY);
                        logDirect(clickable);
                    } catch (IOException e) {
                        logDirect(Formatting.GRAY + "Ошибка при сохранении файла");
                        e.printStackTrace();
                    }
                },
                () -> logDirect(Formatting.GRAY + "Конфиг с таким кодом не найден")
        );
    }

    private void handleCloudShare(IArgConsumer args) throws CommandException {
        args.requireExactly(1);
        String name = args.getString();

        if (!ConfigManager.getConfigs().contains(name)) {
            logDirect(Formatting.GRAY + "Локальный конфиг " + Formatting.WHITE + name + Formatting.GRAY + " не найден");
            return;
        }

        Path file = Paths.get(".options/configs").resolve(name + ".json");
        if (!Files.exists(file)) {
            logDirect(Formatting.GRAY + "Файл конфига не найден");
            return;
        }

        String json;
        try {
            json = Files.readString(file);
        } catch (IOException e) {
            logDirect(Formatting.GRAY + "Не удалось прочитать файл конфига");
            return;
        }

        logDirect(Formatting.GRAY + "Загружаю на сервер...");
        CloudConfigApi.upload(name, json,
                result -> {
                    Text code = Text.literal(result.code())
                            .styled(s -> s.withClickEvent(new ClickEvent(
                                    ClickEvent.Action.COPY_TO_CLIPBOARD,
                                    result.code()
                            )).withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("Click to copy")
                            )));
                    logDirect("Готово! Код: ", Formatting.GRAY);
                    logDirect(code);
                },
                err -> logDirect(Formatting.GRAY + err)
        );
    }

    private void handleCloudSave(IArgConsumer args) throws CommandException {
        args.requireExactly(1);
        String arg = args.getString();
        boolean isCode = CodeGenerator.isValid(arg.toUpperCase(Locale.US));
        String code = isCode ? arg.toUpperCase(Locale.US) : null;
        String name = isCode ? null : arg;

        if (isCode) {
            CloudConfigApi.fetchByCode(code,
                    result -> applyLocalUpdate(result.name()),
                    () -> logDirect(Formatting.GRAY + "Конфиг с таким кодом не найден")
            );
        } else {
            applyLocalUpdate(name);
        }
    }

    private void applyLocalUpdate(String name) {
        if (!ConfigManager.getConfigs().contains(name)) {
            logDirect(Formatting.GRAY + "Сначала сохрани локальный конфиг: .cfg save " + name);
            return;
        }
        Path file = Paths.get(".options/configs").resolve(name + ".json");
        if (!Files.exists(file)) {
            logDirect(Formatting.GRAY + "Файл конфига не найден");
            return;
        }
        String json;
        try {
            json = Files.readString(file);
        } catch (IOException e) {
            logDirect(Formatting.GRAY + "Не удалось прочитать файл конфига");
            return;
        }

        logDirect(Formatting.GRAY + "Обновляю на сервере...");
        CloudConfigApi.updateByName(name, json,
                () -> {},
                result -> {
                    switch (result) {
                        case OK -> logDirect(Formatting.GRAY + "Конфиг обновлён");
                        case NOT_FOUND -> logDirect(Formatting.GRAY + "Сначала используй .cfg cloud share " + name);
                        case ERROR -> logDirect(Formatting.GRAY + "Ошибка сервера");
                    }
                }
        );
    }

    private void handleCloudDelete(IArgConsumer args, String label) throws CommandException {
        args.requireExactly(1);
        String arg = args.getString();
        String code = arg.toUpperCase(Locale.US);

        if (CodeGenerator.isValid(code)) {
            logDirect(Formatting.GRAY + "Удаляю " + Formatting.WHITE + code + Formatting.GRAY + "...");
            CloudConfigApi.deleteByCode(code,
                    () -> {},
                    result -> {
                        switch (result) {
                            case OK -> logDirect(Formatting.GRAY + "Удалено");
                            case NOT_FOUND -> logDirect(Formatting.GRAY + "Конфиг не найден или принадлежит другому HWID");
                            case ERROR -> logDirect(Formatting.GRAY + "Ошибка сервера");
                        }
                    }
            );
        } else {
            String targetName = arg;
            logDirect(Formatting.GRAY + "Ищу конфиг с именем " + Formatting.WHITE + targetName + Formatting.GRAY + "...");
            CloudConfigApi.listByHwid(list -> {
                CloudConfigEntry match = list.stream()
                        .filter(e -> e.name().equalsIgnoreCase(targetName))
                        .findFirst()
                        .orElse(null);
                if (match == null) {
                    logDirect(Formatting.GRAY + "Конфиг с таким именем не найден среди твоих облачных");
                    return;
                }
                CloudConfigApi.deleteByCode(match.code(),
                        () -> {},
                        result -> {
                            switch (result) {
                                case OK -> logDirect(Formatting.GRAY + "Удалено: " + Formatting.WHITE + match.code());
                                case NOT_FOUND -> logDirect(Formatting.GRAY + "Конфиг не найден");
                                case ERROR -> logDirect(Formatting.GRAY + "Ошибка сервера");
                            }
                        }
                );
            });
        }
    }

    private void handleCloudList() {
        logDirect(Formatting.GRAY + "Получаю список облачных конфигов...");
        CloudConfigApi.listByHwid(list -> {
            if (list.isEmpty()) {
                logDirect(Formatting.GRAY + "У тебя нет облачных конфигов");
                return;
            }
            logDirect("Твои облачные конфиги:", Formatting.GRAY);
            for (CloudConfigEntry entry : list) {
                Text line = Text.literal(Formatting.GRAY + "- " + Formatting.WHITE + entry.code()
                                + Formatting.GRAY + " — " + Formatting.WHITE + entry.name() + " ")
                        .append(Text.literal(Formatting.GREEN + "[Загрузить]")
                                .styled(s -> s.withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        FORCE_COMMAND_PREFIX + "cfg cloud load " + entry.code()
                                )).withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Text.literal("Click to load")
                                ))))
                        .append(Text.literal(Formatting.RED + " [Удалить]")
                                .styled(s -> s.withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        FORCE_COMMAND_PREFIX + "cfg cloud del " + entry.code()
                                )).withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Text.literal("Click to delete")
                                ))));
                logDirect(line);
            }
        });
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
        if (!args.hasAny()) {
            return Stream.empty();
        }

        String first = args.peekString();

        if (args.hasExactlyOne()) {
            if (first.equalsIgnoreCase("cloud")) {
                return new TabCompleteHelper()
                        .sortAlphabetically()
                        .prepend("load", "share", "save", "del")
                        .filterPrefix("")
                        .stream();
            }
            return new TabCompleteHelper()
                    .sortAlphabetically()
                    .prepend("load", "save", "remove", "list", "clear", "dir", "cloud")
                    .filterPrefix(first)
                    .stream();
        }

        if (args.hasExactly(2)) {
            String action = args.getString().toLowerCase(Locale.US);
            String current = args.peekString();
            switch (action) {
                case "load":
                case "remove": {
                    return ConfigManager.getConfigs().stream()
                            .filter(cfg -> cfg.startsWith(current))
                            .sorted();
                }
                case "cloud": {
                    return new TabCompleteHelper()
                            .sortAlphabetically()
                            .prepend("load", "share", "save", "del")
                            .filterPrefix(current)
                            .stream();
                }
            }
        }

        if (args.hasExactly(3)) {
            String action = args.getString().toLowerCase(Locale.US);
            String sub = args.getString().toLowerCase(Locale.US);
            String current = args.peekString();
            if (action.equals("cloud") && (sub.equals("share") || sub.equals("save"))) {
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
                "> cfg remove <name> - Удаляет конфиг по имени."
        );
    }
}