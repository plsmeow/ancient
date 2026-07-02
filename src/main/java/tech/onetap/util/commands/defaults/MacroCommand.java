package tech.onetap.util.commands.defaults;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import tech.onetap.Onetap;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.argument.ICommandArgument;
import tech.onetap.util.commands.api.datatypes.KeyDataType;
import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.commands.api.helpers.Paginator;
import tech.onetap.util.commands.api.helpers.TabCompleteHelper;
import tech.onetap.util.keyboard.KeyStorage;
import tech.onetap.util.macro.MacroRepository;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static tech.onetap.util.commands.api.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class MacroCommand extends Command {

    final MacroRepository macroRepository;

    public MacroCommand(Onetap onetap) {
        super("macro");
        macroRepository = onetap.getMacroRepository();
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String action = args.hasAny() ? args.getString().toLowerCase(Locale.US) : "list";
        switch (action) {
            case "add" -> handleAddMacro(args);
            case "remove" -> handleRemoveMacro(args);
            case "list" -> handleListMacros(args, label);
            case "clear" -> handleClearMacros(args);
            default -> logDirect("Используй: add / remove / list / clear", Formatting.GRAY);
        }
    }

    private void handleAddMacro(IArgConsumer args) throws CommandException {
        args.requireMin(2);

        int key = args.getDatatypeFor(KeyDataType.INSTANCE).getValue();
        String command = args.rawRest();

        macroRepository.addMacro(command, key);

        logDirect(Formatting.GRAY +
                "Макрос добавлен: " + Formatting.WHITE +
                KeyStorage.getKey(key).toLowerCase() + Formatting.GRAY +
                " → " + Formatting.WHITE + command);
    }

    private void handleRemoveMacro(IArgConsumer args) throws CommandException {
        args.requireMin(1);

        int key = args.getDatatypeFor(KeyDataType.INSTANCE).getValue();

        if (macroRepository.removeByKey(key)) {
            logDirect(Formatting.GRAY + "Макрос на клавише "
                    + Formatting.WHITE + KeyStorage.getKey(key).toLowerCase()
                    + Formatting.GRAY + " удалён");
        } else {
            logDirect("Макрос на этой клавише не найден", Formatting.GRAY);
        }
    }

    private void handleListMacros(IArgConsumer args, String label) throws CommandException {
        Paginator.paginate(
                args,
                new Paginator<>(macroRepository.getMacroList()),
                () -> logDirect("Список макросов:"),
                macro -> Text.literal(
                        Formatting.GRAY + " " +
                                KeyStorage.getKey(macro.key()).toLowerCase() +
                                Formatting.DARK_GRAY + " → " +
                                Formatting.WHITE + macro.message()
                ),
                FORCE_COMMAND_PREFIX + label
        );
    }

    private void handleClearMacros(IArgConsumer args) {
        macroRepository.clearList();
        logDirect("Все макросы очищены", Formatting.GRAY);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        List<ICommandArgument> raw = args.getArgs();
        int size = raw.size();

        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .prepend("add", "remove", "list", "clear")
                    .filterPrefix(args.getString())
                    .sortAlphabetically()
                    .stream();
        }

        if (args.hasAny()) {
            String action = args.getString();
            if ((action.equalsIgnoreCase("add") || action.equalsIgnoreCase("remove")) && size == 2) {
                return args.tabCompleteDatatype(KeyDataType.INSTANCE);
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Макросы по клавишам";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "Использование:",
                ".macro add <key> <command>",
                ".macro remove <key>",
                ".macro list",
                ".macro clear"
        );
    }
}