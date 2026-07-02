package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

@ModuleInformation(moduleName = "Death Coords", moduleCategory = ModuleCategory.PLAYER)
public class DeathCoords extends Module {

    private boolean send;

    @Subscribe
    private void onUpdate(EventTick e) {
        if (mc.player == null) return;
        if (mc.player.isDead()) {
            if (!send) {
                logDirect(String.format("Вы умерли на XYZ: %.0f %.0f %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ()).replace(",","."));
                send = true;
            }
        } else send = false;
    }
}