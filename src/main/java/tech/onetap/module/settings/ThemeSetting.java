package tech.onetap.module.settings;

import lombok.Getter;
import tech.onetap.module.settings.impl.Theme;

import java.util.Arrays;
import java.util.function.Supplier;

@Getter
public class ThemeSetting extends Setting {

    private final Theme[] themes;
    private Theme current;

    public ThemeSetting(Theme defaultValue, Theme... themes) {
        super("Гавно");
        setValue(defaultValue);
        this.themes = themes;
    }

    public void setValue(Theme theme) {
        if (current == null) {
            current = theme;
        }
        if (current != theme) {
            theme.startAnimation(current.color1, current.color2);
            current = theme;
        }
    }

    public Theme getValue() {
        return current;
    }

    @Override
    public String getValueAsString() {
        return getValue().name;
    }

    @Override
    public void setValueFromString(String value) {
        Arrays.stream(themes).filter(theme -> theme.name.equals(value)).forEach(this::setValue);
    }

    @Override
    public ThemeSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }
}