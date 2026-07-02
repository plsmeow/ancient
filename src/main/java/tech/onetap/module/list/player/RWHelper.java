package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.util.packet.NetworkUtils;
import tech.onetap.util.player.other.InventoryUtil;

@ModuleInformation(moduleName = "RW Helper", moduleCategory = ModuleCategory.MISC)
public class RWHelper extends Module {

    public final BooleanSetting antipolet = new BooleanSetting("Анти-полет обход",false);

    boolean need;
    public boolean fireworkUse;

    @Subscribe
    private void onPacket(EventPacket e) {
        if (e.getPacket() instanceof GameMessageS2CPacket p) {
            if (p.content().getString().contains("Анти Полет » Вы не можете взлететь!")) {
                need = true;
            }
        }
    }

    @Subscribe
    private void onPlayerUpdate(EventPlayerUpdate e) {
        if (!antipolet.getValue()) return;

        if (need) {
            if (!mc.player.isOnGround() && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                NetworkUtils.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.startGliding();
                if (fireworkUse) {
                    InventoryUtil.swapAndUseHvH(Items.FIREWORK_ROCKET);
                    fireworkUse = false;
                }
            } else need = false;
        }
    }
} 