package meow.ancient.util.commands.api;

import meow.ancient.util.commands.api.argparser.IArgParserManager;

public interface ICommandSystem {
    IArgParserManager getParserManager();
}
