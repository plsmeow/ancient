package tech.onetap.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import tech.onetap.event.Event;

@Getter
@Setter
@AllArgsConstructor
public class EventChangeSprint extends Event {
    private boolean sprinting;
}