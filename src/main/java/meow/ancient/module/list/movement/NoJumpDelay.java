package meow.ancient.module.list.movement;

import meteordevelopment.orbit.EventHandler;
import meow.ancient.event.list.EventTick;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;

@ModuleInformation(moduleName = "No Jump Delay", moduleDesc = "Убирает задержку между прыжками", moduleCategory = ModuleCategory.MOVEMENT)
public class NoJumpDelay extends Module {

    @EventHandler
    private void onUpdate(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        mc.player.jumpingCooldown = 0;
    }
}