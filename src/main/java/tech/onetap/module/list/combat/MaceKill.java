package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;
import tech.onetap.event.list.EventAttack;
import tech.onetap.event.list.EventPacket;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "MaceKill", moduleDesc = "Усиливает булаву подменой высоты падения", moduleCategory = ModuleCategory.COMBAT)
public class MaceKill extends Module {
    private final SliderSetting fallHeight = new SliderSetting("Высота падения", 22, 1, 170, 1);

    @Subscribe
    private void onPacket(EventPacket event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (!mc.player.getMainHandStack().isOf(Items.MACE)) return;

        if (event.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (!packet.isOnGround()) return;

            event.cancelEvent();

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

    @Subscribe
    private void onAttack(EventAttack event) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.getMainHandStack().isOf(Items.MACE)) return;

        int height = determineHeight();

        if (height > 10) {
            for (int i = 0; i < Math.ceil(Math.abs(height / 10.0)); i++) {
                warp(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false);
            }
        } else {
            for (int i = 0; i < 2; i++) {
                warp(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.isOnGround());
            }
        }

        warp(mc.player.getX(), mc.player.getY() + height, mc.player.getZ(), false);
        warp(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false);
    }

    private int determineHeight() {
        Box boundingBox = mc.player.getBoundingBox();
        for (int i = fallHeight.getIntValue(); i > 0; i--) {
            if (!mc.world.getBlockCollisions(mc.player, boundingBox.offset(0.0, i, 0.0)).iterator().hasNext()) {
                return i;
            }
        }

        return 0;
    }

    private void warp(double x, double y, double z, boolean onGround) {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                x, y, z, onGround, mc.player.horizontalCollision
        ));
    }
}
