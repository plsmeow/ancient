package tech.onetap.ui.component;

import lombok.Getter;
import lombok.Setter;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;

@Getter
@Setter
public abstract class Component implements IComponent {
    public float x, y, width, height;

    private final Animation alphaAnim = new Animation(Easing.BACK_OUT, 550);
    private final Animation alphaAnimSetting = new Animation(Easing.CUBIC_OUT, 280);
    private final Animation alphaAnimBack = new Animation(Easing.CUBIC_OUT, 280);

    public boolean isVisible() {
        return true;
    }
}