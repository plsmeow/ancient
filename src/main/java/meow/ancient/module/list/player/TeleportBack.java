package meow.ancient.module.list.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import meow.ancient.event.list.EventPacket;
import meow.ancient.event.list.EventTick;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;

@ModuleInformation(moduleName = "Teleport Back", moduleDesc = "Телепорт назад на точку смерти после респавна", moduleCategory = ModuleCategory.PLAYER)
public class TeleportBack extends Module {

    private boolean dead;

    @EventHandler
    private void onUpdate(EventTick e) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isAlive() && !dead) {
            dead = true;
            mc.player.networkHandler.sendChatCommand("sethome back");
            mc.player.requestRespawn();
        }
    }

    @EventHandler
    private void onPacket(EventPacket e) {
        if (dead && e.getPacket() instanceof PlayerRespawnS2CPacket) {
            dead = false;
            if (mc.player != null) {
                mc.player.networkHandler.sendChatCommand("home back");
            }
        }
    }
}
