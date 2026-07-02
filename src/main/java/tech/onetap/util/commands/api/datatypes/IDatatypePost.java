package tech.onetap.util.commands.api.datatypes;

import tech.onetap.util.commands.api.exception.CommandException;

public interface IDatatypePost<T, O> extends IDatatype {
    T apply(IDatatypeContext datatypeContext, O original) throws CommandException;
}
