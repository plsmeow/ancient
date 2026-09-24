package meow.ancient.util.commands;

import meow.ancient.util.commands.api.ICommandSystem;
import meow.ancient.util.commands.api.argparser.IArgParserManager;
import meow.ancient.util.commands.argparser.ArgParserManager;

public enum CommandSystem implements ICommandSystem {
    INSTANCE;

    @Override
    public IArgParserManager getParserManager() {
        return ArgParserManager.INSTANCE;
    }
}
