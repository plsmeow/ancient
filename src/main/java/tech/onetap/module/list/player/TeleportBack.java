package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

@ModuleInformation(moduleName = "Teleport Back", moduleCategory = ModuleCategory.PLAYER)
public class TeleportBack extends Module {

    private boolean dead;

    @Subscribe
    private void onUpdate(EventTick e) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isAlive() && !dead) {
            dead = true;
            mc.player.networkHandler.sendChatCommand("sethome back");
            mc.player.requestRespawn();
        }
    }

    @Subscribe
    private void onPacket(EventPacket e) {
        if (dead && e.getPacket() instanceof PlayerRespawnS2CPacket) {
            dead = false;
            if (mc.player != null) {
                mc.player.networkHandler.sendChatCommand("home back");
            }
        }
    }
}
