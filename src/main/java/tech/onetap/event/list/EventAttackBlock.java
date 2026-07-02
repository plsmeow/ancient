package tech.onetap.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import tech.onetap.event.Event;

@Getter
@AllArgsConstructor
public class EventAttackBlock extends Event {
    private final BlockPos blockPos;
    private final Direction direction;
}
