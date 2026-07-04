package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;
import tech.onetap.event.list.EventAttack;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "MaceKill", moduleDesc = "Усиливает булаву подменой высоты падения", moduleCategory = ModuleCategory.COMBAT)
public class MaceKill extends Module {
    private final SliderSetting fallHeight = new SliderSetting("Высота падения", 22, 1, 170, 1);

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
