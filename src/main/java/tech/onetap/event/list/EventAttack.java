package tech.onetap.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.entity.Entity;
import tech.onetap.event.Event;

@Getter
@AllArgsConstructor
public class EventAttack extends Event {
    private final Entity entity;
}