package tech.onetap.util.draggable;

import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.screen.ChatScreen;
import tech.onetap.module.Module;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.render.math.MathUtil;

@Getter
@Setter
public class Draggable implements IMinecraft {
    @Expose private float x;
    @Expose private float y;

    @Expose public float initialXVal;
    @Expose public float initialYVal;

    private float startX, startY;
    private boolean dragging;
    private float width, height;

    @Expose
    private final String name;
    private final Module module;

    public Draggable(Module module, String name, float initialXVal, float initialYVal) {
        this.module = module;
        this.name = name;
        this.x = roundToHalf(initialXVal);
        this.y = roundToHalf(initialYVal);
        this.initialXVal = initialXVal;
        this.initialYVal = initialYVal;
    }

    public final void onDraw() {
        if (!(mc.currentScreen instanceof ChatScreen)) return;

        if (dragging) {
            int mouseX = normaliseX();
            int mouseY = normaliseY();

            x = roundToHalf(mouseX - startX);
            y = roundToHalf(mouseY - startY);

            int screenWidth = (int) MathUtil.calc(mc.getWindow().getScaledWidth());
            int screenHeight = (int) MathUtil.calc(mc.getWindow().getScaledHeight());

            x = Math.max(0, Math.min(x, screenWidth - width));
            y = Math.max(0, Math.min(y, screenHeight - height));
        }
    }

    public final void onClick(int button) {
        if (button == 0) {
            dragging = true;
            startX = (int) (normaliseX() - x);
            startY = (int) (normaliseY() - y);
        }
    }

    public boolean isHovering() {
        return normaliseX() > Math.min(x, x + width) && normaliseX() < Math.max(x, x + width) && normaliseY() > Math.min(y, y + height) && normaliseY() < Math.max(y, y + height);
    }

    public int normaliseX() {
        return (int) (mc.mouse.getX() / mc.getWindow().getScaleFactor());
    }

    public int normaliseY() {
        return (int) (mc.mouse.getY() / mc.getWindow().getScaleFactor());
    }

    public final void onRelease(int button) {
        if (button == 0) dragging = false;
    }

    private float roundToHalf(float value) {
        return Math.round(value * 2) / 2.0f;
    }
}