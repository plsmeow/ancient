package meow.ancient.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.entity.player.PlayerEntity;
import meow.ancient.event.Event;

@Getter
@AllArgsConstructor
public class EventPopTotem extends Event {
    private final PlayerEntity player;
}