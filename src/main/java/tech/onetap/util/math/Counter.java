package tech.onetap.util.math;

import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.minecraft.util.math.MathHelper;
import tech.onetap.util.IMinecraft;

@UtilityClass
public class Counter implements IMinecraft {

    @Getter
    private int currentFPS;

    public void updateFPS() {
        int prevFPS = mc.getCurrentFps();
        currentFPS = MathHelper.lerp(0.5f, prevFPS, currentFPS);
    }
}