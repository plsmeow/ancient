package meow.ancient.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import meow.ancient.event.Event;

@Getter
@AllArgsConstructor
public class EventRightClickBlock extends Event {
    private final Hand hand;
    private final BlockHitResult hitResult;
}