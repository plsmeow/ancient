package tech.onetap.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

@ModuleInformation(moduleName = "Auto Potion", moduleCategory = ModuleCategory.COMBAT)
public class AutoPotion extends Module {

    @EventHandler
    private void onUpdate(EventPlayerUpdate e) {

    }
}