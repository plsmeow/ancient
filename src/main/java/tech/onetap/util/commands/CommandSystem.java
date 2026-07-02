package tech.onetap.util.commands;

import tech.onetap.util.commands.api.ICommandSystem;
import tech.onetap.util.commands.api.argparser.IArgParserManager;
import tech.onetap.util.commands.argparser.ArgParserManager;

public enum CommandSystem implements ICommandSystem {
    INSTANCE;

    @Override
    public IArgParserManager getParserManager() {
        return ArgParserManager.INSTANCE;
    }
}
