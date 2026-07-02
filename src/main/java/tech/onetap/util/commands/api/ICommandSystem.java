package tech.onetap.util.commands.api;

import tech.onetap.util.commands.api.argparser.IArgParserManager;

public interface ICommandSystem {
    IArgParserManager getParserManager();
}
