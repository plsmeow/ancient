package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import tech.onetap.event.list.EventPacket;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

@ModuleInformation(moduleName = "No Ground", moduleCategory = ModuleCategory.MOVEMENT)
public class NoGround extends Module {

    @Subscribe
    private void onPacket(EventPacket e) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        if (e.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (!packet.isOnGround()) return;

            e.cancelEvent();

            double x = packet.getX(mc.player.getX());
            double y = packet.getY(mc.player.getY());
            double z = packet.getZ(mc.player.getZ());
            float yaw = packet.getYaw(mc.player.getYaw());
            float pitch = packet.getPitch(mc.player.getPitch());
            boolean collision = mc.player.horizontalCollision;

            PlayerMoveC2SPacket modifiedPacket;

            if (packet.changesPosition() && packet.changesLook()) {
                modifiedPacket = new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, false, collision);
            } else if (packet.changesPosition()) {
                modifiedPacket = new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, collision);
            } else if (packet.changesLook()) {
                modifiedPacket = new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, false, collision);
            } else {
                modifiedPacket = new PlayerMoveC2SPacket.OnGroundOnly(false, collision);
            }

            mc.getNetworkHandler().sendPacket(modifiedPacket);
        }
    }
}
