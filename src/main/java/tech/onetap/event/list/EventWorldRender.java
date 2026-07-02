package tech.onetap.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.util.math.MatrixStack;
import tech.onetap.event.Event;

@Getter
@AllArgsConstructor
public class EventWorldRender extends Event {
    private final MatrixStack matrixStack;
    private final float tickDelta;
}