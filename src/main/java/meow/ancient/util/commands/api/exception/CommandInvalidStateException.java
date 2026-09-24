package meow.ancient.util.commands.api.exception;

public class CommandInvalidStateException extends CommandErrorMessageException {

    public CommandInvalidStateException(String reason) {
        super(reason);
    }
}
