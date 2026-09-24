package meow.ancient.event.list;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.LivingEntity;
import meow.ancient.event.Event;

@Getter
@Setter
public class FireworkEvent extends Event {
    private final LivingEntity boostedEntity;
    private float speedXZ = 1.5f;
    private float speedY = 1.5f;

    public FireworkEvent(LivingEntity boostedEntity) {
        this.boostedEntity = boostedEntity;
    }
}
