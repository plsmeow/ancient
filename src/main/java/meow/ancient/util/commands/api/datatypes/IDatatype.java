package meow.ancient.util.commands.api.datatypes;

import meow.ancient.util.IMinecraft;
import meow.ancient.util.commands.api.exception.CommandException;

import java.util.stream.Stream;

public interface IDatatype extends IMinecraft {
    Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException;
}
