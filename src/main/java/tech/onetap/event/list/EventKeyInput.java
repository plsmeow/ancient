package tech.onetap.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tech.onetap.event.Event;

@Getter
@AllArgsConstructor
public class EventKeyInput extends Event {
    private final int key, action;
}