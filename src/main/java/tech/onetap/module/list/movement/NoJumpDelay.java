package tech.onetap.module.list.movement;

import meteordevelopment.orbit.EventHandler;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

@ModuleInformation(moduleName = "No Jump Delay", moduleCategory = ModuleCategory.MOVEMENT)
public class NoJumpDelay extends Module {

    @EventHandler
    private void onUpdate(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        mc.player.jumpingCooldown = 0;
    }
}