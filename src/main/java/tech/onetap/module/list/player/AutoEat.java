package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "Auto Eat", moduleDesc = "Автоматически ест когда голод ниже порога", moduleCategory = ModuleCategory.PLAYER)
public class AutoEat extends Module {

    private final SliderSetting hungerThreshold = new SliderSetting("Порог голода", 14, 1, 20, 1);

    private boolean isEating = false;
    private int previousSlot = -1;

    @Subscribe
    private void onUpdate(EventPlayerUpdate e) {
        if (mc.player == null || mc.world == null) return;

        int currentHunger = mc.player.getHungerManager().getFoodLevel();

        if (currentHunger <= hungerThreshold.getValue()) {
            int foodSlot = findFoodSlot();
            if (foodSlot != -1) {
                if (!isEating) {
                    previousSlot = mc.player.getInventory().selectedSlot;
                    mc.player.getInventory().selectedSlot = foodSlot;
                    isEating = true;
                }
                mc.options.useKey.setPressed(true);
            }
        } else if (isEating) {
            mc.options.useKey.setPressed(false);
            if (previousSlot != -1) {
                mc.player.getInventory().selectedSlot = previousSlot;
                previousSlot = -1;
            }
            isEating = false;
        }
    }

    private int findFoodSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                FoodComponent food = stack.get(DataComponentTypes.FOOD);
                if (food != null) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (isEating) {
            mc.options.useKey.setPressed(false);
            if (previousSlot != -1 && mc.player != null) {
                mc.player.getInventory().selectedSlot = previousSlot;
            }
        }
        isEating = false;
        previousSlot = -1;
    }
}
