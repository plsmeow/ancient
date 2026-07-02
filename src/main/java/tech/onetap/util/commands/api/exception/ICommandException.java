package tech.onetap.util.commands.api.exception;

import net.minecraft.util.Formatting;
import tech.onetap.util.QuickLogger;
import tech.onetap.util.commands.api.ICommand;
import tech.onetap.util.commands.api.argument.ICommandArgument;

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
