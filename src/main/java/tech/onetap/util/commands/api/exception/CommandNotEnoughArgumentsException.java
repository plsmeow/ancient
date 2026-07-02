package tech.onetap.util.commands.api.exception;

public class CommandNotEnoughArgumentsException extends CommandErrorMessageException {

    public CommandNotEnoughArgumentsException(int minArgs) {
        super(String.format("Недостаточно аргументов, нужен %d аргумент", minArgs));
    }
}
