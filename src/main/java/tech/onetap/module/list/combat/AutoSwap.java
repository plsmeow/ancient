package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.player.other.InventoryUtil;

@ModuleInformation(moduleName = "Auto Swap", moduleCategory = ModuleCategory.COMBAT)
public class AutoSwap extends Module {
    private final BindSetting swapKey = new BindSetting("Клавиша свапа", -1);
    private final ModeSetting firstItem = new ModeSetting("Свапать с", "Шар", "Гепл", "Щит", "Талисман", "Шар");
    private final ModeSetting secondItem = new ModeSetting("Свапать с", "Шар", "Гепл", "Щит", "Талисман", "Шар");

    private boolean swapped;

    @Subscribe
    private void onPlayerUpdate(EventPlayerUpdate e) {
        if (mc.player == null) return;

        if (swapped) {
            swapped = false;
            boolean sameItem = firstItem.getValue().equals(secondItem.getValue());

            int slotFirstItem = findItemByName(firstItem.getValue(), sameItem);
            int slotSecondItem = findItemByName(secondItem.getValue(), sameItem);

            if (slotFirstItem == 40 && slotSecondItem == 40) {
                slotSecondItem = InventoryUtil.searchItemStack(item ->
                        item.getItem() == Items.PLAYER_HEAD &&
                                item != mc.player.getOffHandStack()
                );
            }

            if (slotFirstItem == -1 && slotSecondItem == -1) return;
            if (slotFirstItem == 40 || slotFirstItem == -1 && slotSecondItem != 40) {
                if (slotSecondItem >= 0 && slotSecondItem <= 8) {
                    int finalSlotSecondItem = slotSecondItem;
                    runSwap(() -> mc.interactionManager.clickSlot(0, 45, finalSlotSecondItem, SlotActionType.SWAP, mc.player));
                } else if (slotSecondItem != -1) {
                    int finalSlotSecondItem = slotSecondItem;
                    runSwap(() -> mc.interactionManager.clickSlot(0, finalSlotSecondItem, 40, SlotActionType.SWAP, mc.player));
                }
            } else {
                if (slotFirstItem == -1) return;
                if (slotFirstItem >= 0 && slotFirstItem <= 8) {
                    runSwap(() -> mc.interactionManager.clickSlot(0, 45, slotFirstItem, SlotActionType.SWAP, mc.player));
                } else {
                    runSwap(() -> mc.interactionManager.clickSlot(0, slotFirstItem, 40, SlotActionType.SWAP, mc.player));
                }
            }
        }
    }

    @Subscribe
    private void onKey(EventKeyInput e) {
        if (e.getAction() == 0) return;
        if (e.getKey() == swapKey.getValue()) swapped = true;
    }

    private void runSwap(Runnable action) {
        InventoryUtil.clickWithGuiBypass(action);
    }

    private int findItemByName(String name, boolean ignoreOffhand) {
        switch (name) {
            case "Гепл" -> {
                if (!ignoreOffhand && mc.player.getOffHandStack().getItem() == Items.GOLDEN_APPLE)
                    return 40;
                return InventoryUtil.searchItem(Items.GOLDEN_APPLE);
            }

            case "Щит" -> {
                if (!ignoreOffhand && mc.player.getOffHandStack().getItem() == Items.SHIELD)
                    return 40;
                return InventoryUtil.searchItem(Items.SHIELD);
            }

            case "Талисман" -> {
                if (!ignoreOffhand &&
                        mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING &&
                        mc.player.getOffHandStack().get(DataComponentTypes.ATTRIBUTE_MODIFIERS) != null &&
                        !mc.player.getOffHandStack().get(DataComponentTypes.ATTRIBUTE_MODIFIERS).modifiers().isEmpty())
                    return 40;

                return InventoryUtil.searchItemStack(item ->
                        item.getItem() == Items.TOTEM_OF_UNDYING &&
                                item.get(DataComponentTypes.ATTRIBUTE_MODIFIERS) != null &&
                                !item.get(DataComponentTypes.ATTRIBUTE_MODIFIERS).modifiers().isEmpty()
                );
            }

            case "Шар" -> {
                if (!ignoreOffhand &&
                        mc.player.getOffHandStack().getItem() == Items.PLAYER_HEAD &&
                        mc.player.getOffHandStack().get(DataComponentTypes.ATTRIBUTE_MODIFIERS) != null &&
                        !mc.player.getOffHandStack().get(DataComponentTypes.ATTRIBUTE_MODIFIERS).modifiers().isEmpty())
                    return 40;

                return InventoryUtil.searchItemStack(item ->
                        item.getItem() == Items.PLAYER_HEAD &&
                                item.get(DataComponentTypes.ATTRIBUTE_MODIFIERS) != null &&
                                !item.get(DataComponentTypes.ATTRIBUTE_MODIFIERS).modifiers().isEmpty()
                );
            }
        }
        return -1;
    }
}
