package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.player.other.InventoryUtil;
import tech.onetap.util.rotation.FreeLookComponent;

@ModuleInformation(moduleName = "FTHelper", moduleDesc = "Помощник для Funtime", moduleCategory = ModuleCategory.MISC)
public class FtHelper extends Module {

    private final BindSetting snowballKey = new BindSetting("Снежок Замароска", -1);
    private final BindSetting godsAuraKey = new BindSetting("Божья Аура", -1);
    private final BindSetting trapKey = new BindSetting("Трапка", -1);
    private final BindSetting plastKey = new BindSetting("Пласт", -1);
    private final BindSetting clearDustKey = new BindSetting("Явная пыль", -1);
    private final BindSetting fireChargeKey = new BindSetting("Огненный заряд", -1);
    private final BindSetting disorientationKey = new BindSetting("Дезориентация", -1);
    private final BindSetting windChargeKey = new BindSetting("Заряд ветра", -1);

    @Subscribe
    public void onKeyInput(EventKeyInput e) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (e.getAction() == 0) return; // Игнорируем отпускание клавиши

        Item item = getItemByKey(e.getKey());
        if (item == null) return;

        if (InventoryUtil.searchItem(item, 0, 46) == -1) return;

        float savedYaw = mc.player.getYaw();
        float savedPitch = mc.player.getPitch();
        if (FreeLookComponent.isActive()) {
            mc.player.setYaw(FreeLookComponent.getFreeYaw());
            mc.player.setPitch(FreeLookComponent.getFreePitch());
        }
        InventoryUtil.swapAndUseHvH(item);
        mc.player.setYaw(savedYaw);
        mc.player.setPitch(savedPitch);
    }

    private Item getItemByKey(int key) {
        if (key == snowballKey.getValue()) return Items.SNOWBALL;
        if (key == godsAuraKey.getValue()) return Items.PHANTOM_MEMBRANE;
        if (key == trapKey.getValue()) return Items.NETHERITE_SCRAP;
        if (key == plastKey.getValue()) return Items.DRIED_KELP;
        if (key == clearDustKey.getValue()) return Items.SUGAR;
        if (key == fireChargeKey.getValue()) return Items.FIRE_CHARGE;
        if (key == disorientationKey.getValue()) return Items.ENDER_EYE;
        if (key == windChargeKey.getValue()) return Items.WIND_CHARGE;
        return null;
    }
}