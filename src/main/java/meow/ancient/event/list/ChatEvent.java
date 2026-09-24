package meow.ancient.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import meow.ancient.event.Event;

@Getter
@AllArgsConstructor
public class ChatEvent extends Event {
    private final String message;
    private final boolean command;

    public ChatEvent(String message) {
        this(message, false);
    }
}