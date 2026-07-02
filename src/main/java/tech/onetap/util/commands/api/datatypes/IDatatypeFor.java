package tech.onetap.util.commands.api.datatypes;

import tech.onetap.util.commands.api.exception.CommandException;

public interface IDatatypeFor<T> extends IDatatype  {
    T get(IDatatypeContext datatypeContext) throws CommandException;
}
