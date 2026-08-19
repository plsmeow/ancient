package tech.onetap.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.util.Hand;
import tech.onetap.event.Event;

@Getter
@AllArgsConstructor
public class EventUseItem extends Event {
    private final Hand hand;
}
