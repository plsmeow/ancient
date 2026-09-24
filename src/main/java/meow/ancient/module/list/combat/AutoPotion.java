package meow.ancient.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import meow.ancient.event.list.EventPlayerUpdate;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;

@ModuleInformation(moduleName = "Auto Potion", moduleCategory = ModuleCategory.COMBAT)
public class AutoPotion extends Module {

    @EventHandler
    private void onUpdate(EventPlayerUpdate e) {

    }
}