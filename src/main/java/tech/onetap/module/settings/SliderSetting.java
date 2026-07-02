package tech.onetap.module.settings;

import lombok.Getter;
import lombok.Setter;
import tech.onetap.util.text.ValueUnit;

import java.util.function.Supplier;

@Getter
@Setter
public class SliderSetting extends Setting {

    private double value;
    private ValueUnit unit;
    private final double min;
    private final double max;
    private final double step;

    public SliderSetting(String name,
                         ValueUnit unit,
                         double defaultValue,
                         double min,
                         double max,
                         double step) {
        super(name);
        this.value = defaultValue;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public SliderSetting(String name,
                         double defaultValue,
                         double min,
                         double max,
                         double step) {
        super(name);
        this.value = defaultValue;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public String getFormattedValue() {
        return unit.format(value);
    }

    public int getIntValue() {
        return (int) value;
    }

    public float getFloatValue() {
        return (float) value;
    }

    public void setValue(double value) {
        this.value = Math.max(min, Math.min(max, value));
    }

    public void increment() {
        setValue(value + step);
    }

    public void decrement() {
        setValue(value - step);
    }

    @Override
    public String getValueAsString() {
        return Double.toString(value);
    }

    @Override
    public void setValueFromString(String value) {
        try {
            setValue(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public boolean canBind() {
        return true;
    }

    @Override
    public String getBindDisplayValue() {
        String raw = getBindValue() != null ? getBindValue() : getValueAsString();
        try {
            return java.math.BigDecimal.valueOf(Double.parseDouble(raw))
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }

    @Override
    public SliderSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }
}