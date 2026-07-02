package tech.onetap.module.list.render;

import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "Ambience", moduleDesc = "Кастомное время и туман", moduleCategory = ModuleCategory.RENDER)
public class Ambience extends Module {

    public final ModeSetting timePreset = new ModeSetting("Время", "День",
            "День", "Рассвет", "Закат", "Сумерки", "Ночь", "Полночь");

    public final BooleanSetting customFog = new BooleanSetting("Туман", false);
    public final SliderSetting fogDistance = new SliderSetting("Дальность тумана", 100, 10, 500, 10).setVisible(() -> customFog.getValue());
    public final BooleanSetting themeFogColor = new BooleanSetting("Цвет тумана от темы", false).setVisible(() -> customFog.getValue());

    public long getCustomTime() {
        return switch (timePreset.getValue()) {
            case "Рассвет" -> 23000;
            case "День" -> 6000;
            case "Закат" -> 12500;
            case "Сумерки" -> 13500;
            case "Ночь" -> 16000;
            case "Полночь" -> 18000;
            default -> 6000;
        };
    }

    public boolean isFogEnabled() {
        return customFog.getValue();
    }

    public float getFogDistance() {
        return (float) fogDistance.getValue();
    }

    public boolean isThemeFogColorEnabled() {
        return themeFogColor.getValue();
    }
}
