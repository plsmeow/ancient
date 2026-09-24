package meow.ancient.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.item.ItemStack;
import meow.ancient.event.Event;

@Getter
@AllArgsConstructor
public class EventItemUseFinish extends Event {
    private final ItemStack stack;
}
