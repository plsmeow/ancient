package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import tech.onetap.event.list.EventPacket;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.packet.NetworkUtils;

@ModuleInformation(moduleName = "RP Spoofer", moduleCategory = ModuleCategory.MISC)
public class RPSpoofer extends Module {

    @Subscribe
    private void onPacket(EventPacket e) {
        if (e.getPacket() instanceof ResourcePackSendS2CPacket) {
            NetworkUtils.sendPacket(new ResourcePackStatusC2SPacket(mc.player.getUuid(), ResourcePackStatusC2SPacket.Status.ACCEPTED));
            NetworkUtils.sendPacket(new ResourcePackStatusC2SPacket(mc.player.getUuid(), ResourcePackStatusC2SPacket.Status.DOWNLOADED));
            NetworkUtils.sendPacket(new ResourcePackStatusC2SPacket(mc.player.getUuid(), ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
            e.cancelEvent();
        }
    }
}