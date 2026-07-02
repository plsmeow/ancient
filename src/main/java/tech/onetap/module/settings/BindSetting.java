package tech.onetap.module.settings;

import java.util.function.Supplier;

public class BindSetting extends Setting {
    private int value;

    public BindSetting(String name, Integer defaultValue) {
        super(name);
        this.value = defaultValue;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    @Override
    public String getValueAsString() {
        return Integer.toString(value);
    }

    @Override
    public void setValueFromString(String value) {
        this.value = Integer.parseInt(value);
    }

    @Override
    public BindSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }
}