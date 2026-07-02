package tech.onetap.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import tech.onetap.event.Event;

@Getter
@Setter
@AllArgsConstructor
public class RotationEvent extends Event {
    private float yaw, pitch;
    private float partialTicks;
}