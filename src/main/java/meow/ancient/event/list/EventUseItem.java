package meow.ancient.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.util.Hand;
import meow.ancient.event.Event;

@Getter
@AllArgsConstructor
public class EventUseItem extends Event {
    private final Hand hand;
}
