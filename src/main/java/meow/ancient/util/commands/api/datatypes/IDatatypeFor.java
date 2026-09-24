package meow.ancient.util.commands.api.datatypes;

import meow.ancient.util.commands.api.exception.CommandException;

public interface IDatatypeFor<T> extends IDatatype  {
    T get(IDatatypeContext datatypeContext) throws CommandException;
}
