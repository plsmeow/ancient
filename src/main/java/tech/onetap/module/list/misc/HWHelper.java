package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.util.player.other.InventoryUtil;
import tech.onetap.util.rotation.FreeLookComponent;

@ModuleInformation(moduleName = "HWHelper", moduleDesc = "Помощник для HolyWorld", moduleCategory = ModuleCategory.MISC)
public class HWHelper extends Module {

    private final BindSetting vzrKey = new BindSetting("Взрывная штучка", -1);
    private final BindSetting StanKey = new BindSetting("Стан", -1);
    private final BindSetting trapKey = new BindSetting("Взрывная Трапка", -1);
    private final BindSetting trappKey = new BindSetting("Трапка", -1);
    private final BindSetting snowKey = new BindSetting("Ком снега", -1);

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
        if (key == vzrKey.getValue()) return Items.FIRE_CHARGE;
        if (key == StanKey.getValue()) return Items.NETHER_STAR;
        if (key == trapKey.getValue()) return Items.PRISMARINE_SHARD;
        if (key == trappKey.getValue()) return Items.POPPED_CHORUS_FRUIT;
        if (key == snowKey.getValue()) return Items.SNOWBALL;
        return null;
    }
}