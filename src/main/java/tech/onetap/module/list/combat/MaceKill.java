package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import tech.onetap.event.list.EventAttack;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.packet.NetworkUtils;

@ModuleInformation(moduleName = "MaceKill", moduleDesc = "Усиливает булаву через packet criticals", moduleCategory = ModuleCategory.COMBAT)
public final class MaceKill extends Module {
    private final SliderSetting height = new SliderSetting("Height", 1.0f, 1.0f, 512.0f, 1.0f);

    public static boolean cancelCrit;
    public static boolean killAuraTriggered;

    @Subscribe
    public void onAttack(EventAttack event) {
        if (killAuraTriggered) return;
        if (mc.player == null || mc.world == null) return;
        if (event.getEntity() instanceof EndCrystalEntity || cancelCrit) return;

        doCrit();
    }

    public void doCrit() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;
        if (mc.player.isInLava() || mc.player.isSubmergedInWater()) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        boolean hc = mc.player.horizontalCollision;

        double h = height.getValue();
        if (h <= 0) return;

        NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, hc));
        NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + h, z, false, hc));
        NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, hc));
    }
}
