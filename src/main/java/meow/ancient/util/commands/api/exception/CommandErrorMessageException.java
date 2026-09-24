package meow.ancient.util.commands.api.exception;

public abstract class CommandErrorMessageException extends CommandException {

    protected CommandErrorMessageException(String reason) {
        super(reason);
    }

    protected CommandErrorMessageException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
