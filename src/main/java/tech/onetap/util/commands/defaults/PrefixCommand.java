package tech.onetap.util.commands.defaults;

import net.minecraft.util.Formatting;
import tech.onetap.Onetap;
import tech.onetap.util.commands.CommandDispatcher;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;

import java.util.List;
import java.util.stream.Stream;

public class PrefixCommand extends Command {

    public PrefixCommand() {
        super("prefix");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        CommandDispatcher dispatcher = Onetap.getInstance().getCommandDispatcher();

        if (!args.hasAny()) {
            logDirect(Formatting.GRAY + "Текущий префикс: " + Formatting.WHITE + dispatcher.getCommandPrefix());
            return;
        }

        String input = args.getString();

        if (args.hasAny()) {
            logDirect(Formatting.RED + "Слишком много аргументов. Использование: prefix <символ> | prefix reset");
            return;
        }

        if (input.equalsIgnoreCase("reset")) {
            dispatcher.setCommandPrefix(CommandDispatcher.DEFAULT_PREFIX);
            logDirect(Formatting.GREEN + "Префикс сброшен на " + Formatting.WHITE + CommandDispatcher.DEFAULT_PREFIX);
            return;
        }

        if (input.length() != 1) {
            logDirect(Formatting.RED + "Префикс должен быть ровно один символ.");
            return;
        }

        if (Character.isWhitespace(input.charAt(0))) {
            logDirect(Formatting.RED + "Префикс не может быть пробельным символом.");
            return;
        }

        dispatcher.setCommandPrefix(input);
        logDirect(Formatting.GREEN + "Префикс изменён на " + Formatting.WHITE + input);
    }

    @Override
    public String getShortDesc() {
        return "Смена префикса команд";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "Смена префикса команд на любой одиночный символ.",
                "",
                "Использование:",
                "> prefix <символ> — сменить префикс",
                "> prefix reset — сбросить на " + CommandDispatcher.DEFAULT_PREFIX,
                "> prefix — показать текущий префикс"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            String peek = args.peekString().toLowerCase();
            return Stream.of("reset").filter(s -> s.startsWith(peek));
        }
        return Stream.empty();
    }
}
