package tech.onetap.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.LivingEntity;
import tech.onetap.event.Event;

@Getter
@Setter
@AllArgsConstructor
public class FireworkEvent extends Event {
    private final LivingEntity boostedEntity;
    private float speed;
}