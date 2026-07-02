package tech.onetap.util.commands.api.exception;

import tech.onetap.util.QuickLogger;
import tech.onetap.util.commands.api.ICommand;
import tech.onetap.util.commands.api.argument.ICommandArgument;

import java.util.List;

public class CommandNotFoundException extends CommandException implements QuickLogger {

    public final String command;

    public CommandNotFoundException(String command) {
        super(String.format("Команда не найдена: %s", command));
        this.command = command;
    }

    @Override
    public void handle(ICommand command, List<ICommandArgument> args) {
       logDirect(getMessage());
    }
}
