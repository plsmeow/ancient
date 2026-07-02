package tech.onetap.util.commands.api.datatypes;

import tech.onetap.util.IMinecraft;
import tech.onetap.util.commands.api.exception.CommandException;

import java.util.stream.Stream;

public interface IDatatype extends IMinecraft {
    Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException;
}
