package meow.ancient.util.timer;

import lombok.experimental.UtilityClass;
import net.minecraft.client.render.RenderTickCounter;
import meow.ancient.mixin.RenderTickCounterAccessor;
import meow.ancient.util.IMinecraft;

@UtilityClass
public class TimerManager implements IMinecraft {
    private float timerSpeed = 1.0F;

    public void setTimer(float speed) {
        timerSpeed = speed;
        apply();
    }

    public float getTimerSpeed() {
        return timerSpeed;
    }

    public void reset() {
        timerSpeed = 1.0F;
        apply();
    }

    private void apply() {
        if (mc.player != null) {
            RenderTickCounter counter = mc.getRenderTickCounter();
            if (counter instanceof RenderTickCounter.Dynamic dynamic) {
                ((RenderTickCounterAccessor) dynamic).setTickTime(50.0F / timerSpeed);
            }
        }
    }
}
