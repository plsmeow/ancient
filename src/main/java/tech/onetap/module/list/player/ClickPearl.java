package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.Items;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventPlayerSync;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.player.other.InventoryUtil;
import tech.onetap.util.rotation.FreeLookComponent;

@ModuleInformation(moduleName = "Click Pearl", moduleCategory = ModuleCategory.PLAYER)
public class ClickPearl extends Module {
    private final ModeSetting mode = new ModeSetting("Мод", "Обычный", "Обычный", "Легитный");
    private final BindSetting key = new BindSetting("Клавиша броска", -98);

    private boolean pearlUsed;
    private int ticksExisted;

    @Subscribe
    private void onKey(EventKeyInput e) {
        if (mc.player == null) return;
        if (e.getAction() == 0) return;

        if (e.getKey() == key.getValue()) {
            if (InventoryUtil.searchItem(Items.ENDER_PEARL, 0, 46) == -1) return;

            if (mode.is("Обычный")) {
                // Мгновенный бросок без задержек и без ротаций
                float savedYaw = mc.player.getYaw();
                float savedPitch = mc.player.getPitch();
                if (FreeLookComponent.isActive()) {
                    mc.player.setYaw(FreeLookComponent.getFreeYaw());
                    mc.player.setPitch(FreeLookComponent.getFreePitch());
                }
                InventoryUtil.swapAndUseHvH(Items.ENDER_PEARL);
                mc.player.setYaw(savedYaw);
                mc.player.setPitch(savedPitch);
            } else {
                pearlUsed = true;
            }
        }
    }

    @Subscribe
    private void onPlayerTick(final EventPlayerUpdate ignored) {
        if (mc.player == null) return;

        if (!pearlUsed && ticksExisted > 0) ticksExisted--;
        if (pearlUsed || ticksExisted > 0) mc.player.setSprinting(false);
    }

    @Subscribe
    private void onPlayerSync(final EventPlayerSync ignored) {
        if (mc.player == null || !pearlUsed) return;

        int slotHotbar = InventoryUtil.searchItem(Items.ENDER_PEARL, 0, 9);
        if (slotHotbar != -1) {
            float savedYaw = mc.player.getYaw();
            float savedPitch = mc.player.getPitch();
            if (FreeLookComponent.isActive()) {
                mc.player.setYaw(FreeLookComponent.getFreeYaw());
                mc.player.setPitch(FreeLookComponent.getFreePitch());
            }
            InventoryUtil.swapAndUseLegit(Items.ENDER_PEARL);
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
        } else {
            if (ticksExisted == 0) {
                ticksExisted++;
                return;
            }
            float savedYaw2 = mc.player.getYaw();
            float savedPitch2 = mc.player.getPitch();
            if (FreeLookComponent.isActive()) {
                mc.player.setYaw(FreeLookComponent.getFreeYaw());
                mc.player.setPitch(FreeLookComponent.getFreePitch());
            }
            InventoryUtil.swapAndUseLegit(Items.ENDER_PEARL);
            mc.player.setYaw(savedYaw2);
            mc.player.setPitch(savedPitch2);
            ticksExisted = 2;
        }

        pearlUsed = false;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        pearlUsed = false;
        ticksExisted = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }
}