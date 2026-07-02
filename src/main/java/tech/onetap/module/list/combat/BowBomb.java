package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "BowBomb", moduleCategory = ModuleCategory.COMBAT)
public class BowBomb extends Module {

    private final SliderSetting power = new SliderSetting("Power", 40, 5, 150, 1);
    private final BooleanSetting smart = new BooleanSetting("Smart Check", true);

    private boolean isExploiting = false;

    @Subscribe
    private void onUpdate(final EventTick event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        ItemStack mainHand = mc.player.getMainHandStack();

        if (mainHand.getItem() != Items.BOW) return;

        if (smart.getValue() && !mc.player.isUsingItem()) return;

        // ИСПРАВЛЕНИЕ: Работает с любого натяга (даже если лук зажат всего 1 тик)
        if (mc.player.getItemUseTime() > 0) {
            if (isExploiting) return;

            isExploiting = true;

            double posX = mc.player.getX();
            double posY = mc.player.getY();
            double posZ = mc.player.getZ();

            int packetsCount = (int) power.getValue();

            for (int i = 0; i < packetsCount; i++) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        posX, posY + 1E-10, posZ, false, mc.player.horizontalCollision
                ));
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        posX, posY, posZ, true, mc.player.horizontalCollision
                ));
            }

            isExploiting = false;
        }
    }

    @Override
    public void onDisable() {
        isExploiting = false;
        super.onDisable();
    }
}