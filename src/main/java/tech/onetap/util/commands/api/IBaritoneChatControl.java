package tech.onetap.util.commands.api;

import java.util.UUID;

public interface IBaritoneChatControl {
    String FORCE_COMMAND_PREFIX = String.format("<<%s>>", UUID.randomUUID());
}
