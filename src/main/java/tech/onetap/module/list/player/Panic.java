package tech.onetap.module.list.player;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Formatting;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;

@Getter
@ModuleInformation(moduleName = "Unhook", moduleCategory = ModuleCategory.MISC)
public class Panic extends Module {

    private final BindSetting backKey = new BindSetting("Кнопка возврата",-1);

    @Setter
    private boolean hide;

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.player == null) return;
        if (backKey.getValue() == -1) {
            logDirect(Formatting.RED + "Забиндите кнопку возврата!");
            setEnabled(false);
            return;
        }
        setHide(true);
        mc.player.closeScreen();
    }
}