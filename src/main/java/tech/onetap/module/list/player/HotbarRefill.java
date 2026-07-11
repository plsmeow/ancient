package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.player.other.InventoryUtil;

@ModuleInformation(moduleName = "Hotbar Refill", moduleDesc = "Пополняет стаки в хотбаре из инвентаря", moduleCategory = ModuleCategory.PLAYER)
public class HotbarRefill extends Module {
    private final SliderSetting minCount = new SliderSetting("Мин. кол-во", 8, 1, 63, 1);
    private final SliderSetting delay = new SliderSetting("Задержка", 1, 0, 20, 1);
    private final BooleanSetting unstackable = new BooleanSetting("Нестаки", true);

    private final ItemStack[] previousStacks = new ItemStack[9];
    private int delayTicks;

    @Override
    public void onEnable() {
        super.onEnable();
        fillPreviousStacks();
        delayTicks = delay.getIntValue();
    }

    @Subscribe
    private void onUpdate(EventPlayerUpdate e) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) {
            fillPreviousStacks();
            return;
        }

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (checkSlot(slot)) {
                delayTicks = delay.getIntValue();
                return;
            }
        }

        delayTicks = delay.getIntValue();
    }

    private boolean checkSlot(int slot) {
        ItemStack stack = mc.player.getInventory().getStack(slot);
        ItemStack previousStack = previousStacks[slot];
        previousStacks[slot] = stack.copy();

        if (stack.isEmpty() && previousStack.isEmpty()) return false;

        int fromSlot = -1;
        boolean quickMove = false;
        if (stack.isStackable() && !stack.isEmpty() && stack.getCount() <= minCount.getIntValue()) {
            fromSlot = findMatchingStack(stack, slot, true);
            quickMove = true;
        } else if (previousStack.isStackable() && stack.isEmpty() && !previousStack.isEmpty()) {
            fromSlot = findMatchingStack(previousStack, slot, false);
            quickMove = true;
        } else if (unstackable.getValue() && !previousStack.isStackable() && stack.isEmpty() && !previousStack.isEmpty()) {
            fromSlot = findMatchingStack(previousStack, slot, false);
        }

        if (fromSlot == -1) return false;

        int finalFromSlot = fromSlot;
        if (quickMove) {
            mc.interactionManager.clickSlot(0, finalFromSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
        } else {
            InventoryUtil.clickWithGuiBypass(() -> mc.interactionManager.clickSlot(0, finalFromSlot, slot, SlotActionType.SWAP, mc.player));
        }

        fillPreviousStacks();
        return true;
    }

    private int findMatchingStack(ItemStack targetStack, int excludedSlot, boolean mustCombine) {
        int bestSlot = -1;
        int bestCount = 0;

        for (int slot = 35; slot >= 9; slot--) {
            if (slot == excludedSlot) continue;

            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            if (stack.getItem() != targetStack.getItem()) continue;
            if (mustCombine && !ItemStack.areItemsAndComponentsEqual(targetStack, stack)) continue;

            if (stack.getCount() > bestCount) {
                bestSlot = slot;
                bestCount = stack.getCount();
            }
        }

        return bestSlot;
    }

    private void fillPreviousStacks() {
        if (mc.player == null) return;

        for (int slot = 0; slot < 9; slot++) {
            previousStacks[slot] = mc.player.getInventory().getStack(slot).copy();
        }
    }
}
