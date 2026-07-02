package tech.onetap.module.settings;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ModeListSetting extends Setting {

    private final List<BooleanSetting> settings;

    public ModeListSetting(String name, BooleanSetting... settings) {
        super(name);
        this.settings = Arrays.asList(settings);
    }

    public List<BooleanSetting> getSettings() {
        return settings;
    }

    public List<String> getEnabledModules() {
        return settings.stream()
                .filter(BooleanSetting::getValue)
                .map(Setting::getName)
                .collect(Collectors.toList());
    }

    public boolean isEnabled(String moduleName) {
        return settings.stream()
                .anyMatch(setting -> setting.getName().equalsIgnoreCase(moduleName) && setting.getValue());
    }

    @Override
    public String getValueAsString() {
        List<String> enabled = getEnabledModules();
        return enabled.isEmpty() ? "None" : String.join(", ", enabled);
    }

    @Override
    public void setValueFromString(String value) {
        List<String> names = Arrays.asList(value.split(",\\s*"));
        for (BooleanSetting setting : settings) {
            setting.setValue(names.contains(setting.getName()));
        }
    }

    @Override
    public ModeListSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }
}