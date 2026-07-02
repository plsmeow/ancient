package tech.onetap.module.settings;

import lombok.Getter;
import lombok.Setter;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;

import java.util.function.Supplier;

@Getter
@Setter
public class BooleanSetting extends Setting {
    private final Animation animation = new Animation(Easing.QUINTIC_OUT, 400);
    private final Animation clickAnimation = new Animation(Easing.QUINTIC_OUT, 400);
    private boolean clicked;
    private boolean value;

    public BooleanSetting(String name, boolean defaultValue) {
        super(name);
        this.value = defaultValue;
    }

    public boolean getValue() {
        return value;
    }

    @Override
    public String getValueAsString() {
        return Boolean.toString(value);
    }

    @Override
    public void setValueFromString(String value) {
        this.value = Boolean.parseBoolean(value);
    }

    public void toggle() {
        this.value = !this.value;
    }

    @Override
    public boolean canBind() {
        return true;
    }

    /** Для чекбокса бинд просто переключает значение. */
    @Override
    public void triggerBind() {
        toggle();
    }

    @Override
    public String getBindDisplayValue() {
        return value ? "Вкл" : "Выкл";
    }

    @Override
    public BooleanSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }
}