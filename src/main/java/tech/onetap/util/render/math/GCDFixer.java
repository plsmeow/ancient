package tech.onetap.util.render.math;

import lombok.experimental.UtilityClass;
import tech.onetap.util.IMinecraft;

@UtilityClass
public class GCDFixer implements IMinecraft {
    public float getFixRotate(float rot) {
        return getDeltaMouse(rot) * getGCDValue();
    }

    public float getGCDValue() {
        return getGCD() * 0.15f;
    }

    public float getGCD() {
        float sensitivity = (float) (mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2);
        return sensitivity * sensitivity * sensitivity * 8f;
    }

    public float getDeltaMouse(float delta) {
        return Math.round(delta / getGCDValue());
    }
}