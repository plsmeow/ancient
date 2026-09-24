package meow.ancient.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import meow.ancient.event.Event;

@Getter
@Setter
@AllArgsConstructor
public class RotationEvent extends Event {
    private float yaw, pitch;
    private float partialTicks;
}