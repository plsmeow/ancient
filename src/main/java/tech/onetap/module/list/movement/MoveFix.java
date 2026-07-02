package tech.onetap.module.list.movement;

import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;

@ModuleInformation(
        moduleName = "MoveFix",
        moduleDesc = "Коррекция движения при ротации",
        moduleCategory = ModuleCategory.MOVEMENT
)
public class MoveFix extends Module {

    // Сфокусированная — стандартная коррекция (движение относительно ротации на цель)
    // Свободная — корректирует движение, но игрок идёт в сторону взгляда
    public final ModeSetting mode = new ModeSetting("Тип", "Сфокусированная", "Сфокусированная", "Свободная");

    public boolean isFree() {
        return isEnabled() && mode.is("Свободная");
    }
}
