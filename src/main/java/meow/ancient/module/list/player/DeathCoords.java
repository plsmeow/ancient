package meow.ancient.module.list.player;

import meteordevelopment.orbit.EventHandler;
import meow.ancient.event.list.EventTick;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;

@ModuleInformation(moduleName = "Death Coords", moduleDesc = "Пишет координаты смерти в чат", moduleCategory = ModuleCategory.PLAYER)
public class DeathCoords extends Module {

    private boolean send;

    @EventHandler
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