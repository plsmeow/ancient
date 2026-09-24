package meow.ancient.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import meow.ancient.event.Event;

@Getter
@AllArgsConstructor
public class LookEvent extends Event {
    private double yaw, pitch;
}