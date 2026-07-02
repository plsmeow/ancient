package tech.onetap.util.commands.api.exception;

public class CommandTooManyArgumentsException extends CommandErrorMessageException {

    public CommandTooManyArgumentsException(int maxArgs) {
        super(String.format(maxArgs == 1 ? "Слишком много аргументов, максимум %d аргумент" : maxArgs > 4 ? "Слишком много аргументов, максимум %d аргументов" : "Слишком много аргументов, максимум %d аргумента", maxArgs));
    }
}
