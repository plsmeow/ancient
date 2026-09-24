package meow.ancient.util.commands.api.exception;

import net.minecraft.util.Formatting;
import meow.ancient.util.QuickLogger;
import meow.ancient.util.commands.api.ICommand;
import meow.ancient.util.commands.api.argument.ICommandArgument;

import java.util.List;

public interface ICommandException extends QuickLogger {

    String getMessage();

    default void handle(ICommand command, List<ICommandArgument> args) {
        logDirect(
                this.getMessage(),
                Formatting.RED
        );
    }
}
