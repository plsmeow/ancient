package meow.ancient.module.list.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import meow.ancient.event.list.EventPacket;
import meow.ancient.event.list.EventPlayerUpdate;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.module.settings.BooleanSetting;
import meow.ancient.util.packet.NetworkUtils;
import meow.ancient.util.player.other.InventoryUtil;

@ModuleInformation(moduleName = "RW Helper", moduleDesc = "Reallyworld", moduleCategory = ModuleCategory.MISC)
public class RWHelper extends Module {

    public final BooleanSetting antipolet = new BooleanSetting("Анти-полет обход",false);

    boolean need;
    public boolean fireworkUse;

    @EventHandler
    private void onPacket(EventPacket e) {
        if (e.getPacket() instanceof GameMessageS2CPacket p) {
            if (p.content().getString().contains("Анти Полет » Вы не можете взлететь!")) {
                need = true;
            }
        }
    }

    @EventHandler
    private void onPlayerUpdate(EventPlayerUpdate e) {
        if (!antipolet.getValue()) return;

        if (need) {
            if (!mc.player.isOnGround() && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                NetworkUtils.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.startGliding();
                if (fireworkUse) {
                    InventoryUtil.swapAndUseWithGuiBypass(Items.FIREWORK_ROCKET);
                    fireworkUse = false;
                }
            } else need = false;
        }
    }
} 
