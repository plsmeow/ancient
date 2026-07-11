package tech.onetap.util.commands.defaults;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import tech.onetap.Onetap;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleStorage;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.argument.ICommandArgument;
import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.commands.api.helpers.Paginator;
import tech.onetap.util.commands.api.helpers.TabCompleteHelper;
import tech.onetap.util.keyboard.KeyStorage;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static tech.onetap.util.commands.api.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class BindCommand extends Command {

    final ModuleStorage moduleStorage;

    public BindCommand(Onetap onetap) {
        super("bind");
        this.moduleStorage = onetap.getModuleStorage();
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String action = args.hasAny() ? args.getString().toLowerCase(Locale.US) : "list";
        switch (action) {
            case "add" ->
                handleAddBind(label, args);
            case "remove" ->
                handleRemoveBind(args);
            case "list" ->
                handleListBinds(args, label);
            case "clear" ->
                handleClearBinds(args);
            default -> logDirect("Неизвестная подкоманда. Используй add/remove/list/clear.", Formatting.GRAY);
        }
    }

    private void handleAddBind(String label, IArgConsumer args) throws CommandException {
        args.requireMin(2);
        List<ICommandArgument> raw = args.getArgs();
        String moduleName = joinArgs(raw, 0, raw.size() - 1);
        Module module = getModule(moduleName);
        if (module == null) {
            logDirect(Formatting.GRAY + "Модуль с названием " + Formatting.WHITE + moduleName + Formatting.GRAY + " не найден");
            return;
        }
        String keyName = raw.getLast().getValue();
        Integer key = KeyStorage.keyMap.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(keyName))
                .map(java.util.Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (key == null) {
            logDirect(Formatting.GRAY + "Клавиша " + Formatting.WHITE + keyName + Formatting.GRAY + " не найдена");
            return;
        }
        module.setKey(key);
        logDirect(Formatting.GRAY + "Модуль " + Formatting.WHITE + module.getName() + Formatting.GRAY + " успешно привязан к клавише " + Formatting.WHITE + KeyStorage.getKey(key).toUpperCase());
    }

    private void handleRemoveBind(IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String moduleName = joinArgs(args.getArgs(), 0, args.getArgs().size());
        Module module = getModule(moduleName);
        if (module == null) {
            logDirect(Formatting.GRAY + "Модуль с названием " + Formatting.WHITE + moduleName + Formatting.GRAY + " не найден");
            return;
        }
        module.setKey(-1);
        logDirect(Formatting.GRAY + "Модуль " + Formatting.WHITE + module.getName() + Formatting.GRAY + " больше не имеет привязку к клавише");
    }

    private void handleListBinds(IArgConsumer args, String label) throws CommandException {
        args.requireMax(1);

        List<Module> boundModules = moduleStorage.getModules()
                .stream()
                .filter(module -> module.getKey() != -1)
                .collect(Collectors.toList());

        Paginator.paginate(
                args,
                new Paginator<>(boundModules),
                () -> logDirect("Привязанные модули:"),
                module -> {
                    int key = module.getKey();
                    String keyName = KeyStorage.getKey(key).toUpperCase();
                    return Text.literal(Formatting.GRAY + module.getName() + ": " + Formatting.WHITE + keyName);
                },
                FORCE_COMMAND_PREFIX + label
        );
    }

    private void handleClearBinds(IArgConsumer args) throws CommandException {
        args.requireMax(1);
        for (Module m : moduleStorage.getModules()) {
            m.setKey(-1);
        }
        logDirect(Formatting.GRAY + "Все модули успешно отвязаны и больше не имеют привязку к клавише");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        List<ICommandArgument> raw = args.getArgs();
        int size = raw.size();

        if (size <= 1) {
            String prefix = size == 1 ? args.peekString(0) : "";
            return new TabCompleteHelper()
                    .prepend("add", "remove", "list", "clear")
                    .filterPrefix(prefix)
                    .sortAlphabetically()
                    .stream();
        }

        String action = args.peekString(0).toLowerCase(Locale.ROOT);

        if ((action.equals("add")) && size == 2) {
            String modulePrefix = normalizeModuleName(args.peekString(1));
            return moduleStorage.getModules().stream()
                    .map(Module::getName)
                    .map(this::moduleCommandName)
                    .filter(n -> normalizeModuleName(n).startsWith(modulePrefix))
                    .sorted()
                    .distinct();
        }

        if ((action.equals("add")) && size == 3) {
            String keyPrefix = args.peekString(2);
            return KeyStorage.keyMap.keySet().stream()
                    .filter(k -> k.toLowerCase(Locale.ROOT).startsWith(keyPrefix.toLowerCase(Locale.ROOT)))
                    .sorted()
                    .distinct();
        }

        if ((action.equals("remove")) && size == 2) {
            String modulePrefix = normalizeModuleName(args.peekString(1));
            return moduleStorage.getModules().stream()
                    .filter(m -> m.getKey() != -1)
                    .map(Module::getName)
                    .map(this::moduleCommandName)
                    .filter(n -> normalizeModuleName(n).startsWith(modulePrefix))
                    .sorted()
                    .distinct();
        }

        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Управление привязкой модуля к клавише";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "Команда для установки и удаления привязки модуля к клавише",
            "",
            "Использование:",
            "> bind add <модуль> <клавиша> — привязать модуль к клавише",
            "> bind remove <модуль> — отвязать модуль от клавиши",
            "> bind list — показать все привязанные модули",
            "> bind clear — отвязать все модули от своих клавиш"
        );
    }

    private Module getModule(String name) {
        String normalizedName = normalizeModuleName(name);
        return moduleStorage.getModules().stream()
                .filter(module -> normalizeModuleName(module.getName()).equals(normalizedName))
                .findFirst()
                .orElse(null);
    }

    private String joinArgs(List<ICommandArgument> args, int from, int to) {
        return args.subList(from, to).stream()
                .map(ICommandArgument::getValue)
                .collect(Collectors.joining(" "));
    }

    private String moduleCommandName(String name) {
        return name.toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private String normalizeModuleName(String name) {
        return name.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ').trim();
    }
}
