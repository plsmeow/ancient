package meow.ancient.util.commands.api.datatypes;

import meow.ancient.Ancient;
import meow.ancient.util.commands.api.exception.CommandException;
import meow.ancient.util.commands.api.helpers.TabCompleteHelper;
import meow.ancient.util.keyboard.KeyStorage;
import meow.ancient.util.macro.Macro;

import java.util.List;
import java.util.stream.Stream;

public enum MacroDataType implements IDatatypeFor<Macro> {
    INSTANCE;

    @Override
    public Stream<String> tabComplete(IDatatypeContext datatypeContext) throws CommandException {
        Stream<String> macros = getMacro()
                .stream()
                .map(macro -> KeyStorage.getKey(macro.key()));

        String context = datatypeContext
                .getConsumer()
                .getString();

        return new TabCompleteHelper()
                .append(macros)
                .filterPrefix(context)
                .sortAlphabetically()
                .stream();
    }

    @Override
    public Macro get(IDatatypeContext datatypeContext) throws CommandException {
        String username = datatypeContext
                .getConsumer()
                .getString();

        return getMacro().stream()
                .filter(s -> KeyStorage.getKey(s.key()).equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);
    }

    private List<? extends Macro> getMacro() {
        return Ancient.getInstance().getMacroRepository().getMacroList();
    }
}