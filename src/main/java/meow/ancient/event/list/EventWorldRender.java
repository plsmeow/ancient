package meow.ancient.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.util.math.MatrixStack;
import meow.ancient.event.Event;

@Getter
@AllArgsConstructor
public class EventWorldRender extends Event {
    private final MatrixStack matrixStack;
    private final float tickDelta;
}