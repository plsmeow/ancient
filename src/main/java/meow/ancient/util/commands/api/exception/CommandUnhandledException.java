package meow.ancient.util.commands.api.exception;

import meow.ancient.util.QuickLogger;
import meow.ancient.util.commands.api.ICommand;
import meow.ancient.util.commands.api.argument.ICommandArgument;

import java.util.List;

public class CommandUnhandledException extends RuntimeException implements ICommandException, QuickLogger {

    public CommandUnhandledException(String message) {
        super(message);
    }

    public CommandUnhandledException(Throwable cause) {
        super(cause);
    }

    @Override
    public void handle(ICommand command, List<ICommandArgument> args) {
    }
}
