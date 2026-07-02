package tech.onetap.util.commands.api.datatypes;

import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.commands.api.helpers.TabCompleteHelper;
import tech.onetap.util.keyboard.KeyStorage;

import java.util.Map;
import java.util.stream.Stream;

public enum KeyDataType implements IDatatypeFor<Map.Entry<String, Integer>> {
    INSTANCE;

    @Override
    public Stream<String> tabComplete(IDatatypeContext datatypeContext) throws CommandException {
        Stream<String> keys = getKeys()
                .keySet()
                .stream();

        String context = datatypeContext
                .getConsumer()
                .getString();

        return new TabCompleteHelper()
                .append(keys)
                .filterPrefix(context)
                .sortAlphabetically()
                .stream();
    }

    @Override
    public Map.Entry<String, Integer> get(IDatatypeContext datatypeContext) throws CommandException {
        String key = datatypeContext
                .getConsumer()
                .getString();

        return getKeys()
                .entrySet()
                .stream()
                .filter(s -> s.getKey().equalsIgnoreCase(key))
                .findFirst()
                .orElse(null);
    }

    private static Map<String, Integer> getKeys() {
        return KeyStorage.keyMap;
    }
}
