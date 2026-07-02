package tech.onetap.util.commands.api.exception;

public class CommandInvalidStateException extends CommandErrorMessageException {

    public CommandInvalidStateException(String reason) {
        super(reason);
    }
}
