package tech.onetap.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tech.onetap.event.Event;

@Getter
@AllArgsConstructor
public class LookEvent extends Event {
    private double yaw, pitch;
}