package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import tech.onetap.event.list.EventAttack;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.player.other.WorldUtils;

@ModuleInformation(moduleName = "Criticals", moduleCategory = ModuleCategory.COMBAT)
public class Criticals extends Module {

    public final ModeSetting mode = new ModeSetting("Режим", "Grim", "Grim", "Simple");


    @Subscribe
    private void onAttack(EventAttack e) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.fallDistance == 0 && !mc.player.isOnGround()) {
            switch (mode.getValue()) {
                case "Grim" -> {
                    if (!WorldUtils.isInWeb()) return;

                    double x = mc.player.getX();
                    double y = mc.player.getY();
                    double z = mc.player.getZ();

                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            x, y + 0.0625, z, false, false
                    ));
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            x, y + 0.0015, z, false, false
                    ));
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            x, y, z, false, false
                    ));
                }
                case "Simple" -> {
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            mc.player.getX(), mc.player.getY() + 0.0625, mc.player.getZ(), false, mc.player.horizontalCollision
                    ));
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, mc.player.horizontalCollision
                    ));
                }
            }
        }
    }
}
