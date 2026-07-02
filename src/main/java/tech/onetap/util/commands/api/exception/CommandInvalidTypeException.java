package tech.onetap.util.commands.api.exception;

import tech.onetap.util.commands.api.argument.ICommandArgument;

public class CommandInvalidTypeException extends CommandInvalidArgumentException {

    public CommandInvalidTypeException(ICommandArgument arg, String expected) {
        super(arg, String.format("%s", expected));
    }

    public CommandInvalidTypeException(ICommandArgument arg, String expected, Throwable cause) {
        super(arg, String.format("%s", expected), cause);
    }

    public CommandInvalidTypeException(ICommandArgument arg, String expected, String got) {
        super(arg, String.format("%s, %s", expected, got));
    }

    public CommandInvalidTypeException(ICommandArgument arg, String expected, String got, Throwable cause) {
        super(arg, String.format("%s, %s", expected, got), cause);
    }
}
