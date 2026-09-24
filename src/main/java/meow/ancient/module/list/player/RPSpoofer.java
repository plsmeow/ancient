package meow.ancient.module.list.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import meow.ancient.event.list.EventPacket;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.util.packet.NetworkUtils;

@ModuleInformation(moduleName = "RP Spoofer", moduleDesc = "Не качает ресурспак", moduleCategory = ModuleCategory.MISC)
public class RPSpoofer extends Module {

    @EventHandler
    private void onPacket(EventPacket e) {
        if (e.getPacket() instanceof ResourcePackSendS2CPacket) {
            NetworkUtils.sendPacket(new ResourcePackStatusC2SPacket(mc.player.getUuid(), ResourcePackStatusC2SPacket.Status.ACCEPTED));
            NetworkUtils.sendPacket(new ResourcePackStatusC2SPacket(mc.player.getUuid(), ResourcePackStatusC2SPacket.Status.DOWNLOADED));
            NetworkUtils.sendPacket(new ResourcePackStatusC2SPacket(mc.player.getUuid(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
            e.cancelEvent();
        }
    }
}