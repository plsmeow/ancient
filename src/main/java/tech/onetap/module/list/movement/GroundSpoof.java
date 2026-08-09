package tech.onetap.module.list.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import tech.onetap.event.list.EventPacket;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;

@ModuleInformation(moduleName = "GroundSpoof", moduleCategory = ModuleCategory.MOVEMENT)
public class GroundSpoof extends Module {

    public final ModeSetting mode = new ModeSetting("Режим", "False", "True", "False");

    @EventHandler
    private void onPacket(EventPacket e) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        if (e.getPacket() instanceof PlayerMoveC2SPacket packet) {
            boolean spoof = mode.is("True");
            if (packet.isOnGround() == spoof) return;

            e.cancelEvent();

            double x = packet.getX(mc.player.getX());
            double y = packet.getY(mc.player.getY());
            double z = packet.getZ(mc.player.getZ());
            float yaw = packet.getYaw(mc.player.getYaw());
            float pitch = packet.getPitch(mc.player.getPitch());
            boolean collision = mc.player.horizontalCollision;

            PlayerMoveC2SPacket modifiedPacket;

            if (packet.changesPosition() && packet.changesLook()) {
                modifiedPacket = new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, spoof, collision);
            } else if (packet.changesPosition()) {
                modifiedPacket = new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, spoof, collision);
            } else if (packet.changesLook()) {
                modifiedPacket = new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, spoof, collision);
            } else {
                modifiedPacket = new PlayerMoveC2SPacket.OnGroundOnly(spoof, collision);
            }

            mc.getNetworkHandler().sendPacket(modifiedPacket);
        }
    }
}
