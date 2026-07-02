package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.player.other.InventoryUtil;

@ModuleInformation(moduleName = "Auto Tool", moduleDesc = "Выбирает лучший инструмент для добычи блоков", moduleCategory = ModuleCategory.PLAYER)
public class AutoTool extends Module {

    private final ModeSetting mode = new ModeSetting("Мод", "Vanilla", "Vanilla", "Grim");

    private int itemIndex = -1, oldSlot = -1;
    private boolean status;

    @Subscribe
    private void onTick(final EventPlayerUpdate ignored) {
        if (mc.player == null || mc.player.isCreative()) {
            itemIndex = -1;
            return;
        }

        if (mc.player.isUsingItem()) return;

        if (isMousePressed()) {
            if (oldSlot == -1) {
                itemIndex = findBestToolSlot();

                if (itemIndex != -1) {
                    if (itemIndex >= 0 && itemIndex <= 8) {
                        oldSlot = mc.player.getInventory().selectedSlot;
                        mc.player.getInventory().selectedSlot = itemIndex;
                        mc.interactionManager.syncSelectedSlot();
                        status = true;
                    } else {
                        switch (mode.getValue()) {
                            case "Vanilla" -> swap();
                            case "Grim" -> InventoryUtil.swapWithBypassGrim(this::swap);
                            case "ReallyWorld" -> {
                                if (mc.player.isOnGround()) InventoryUtil.swapWithBypassPolar(this::swap);
                                else InventoryUtil.swapWithBypassGrim(this::swap);
                            }
                        }
                        oldSlot = itemIndex;
                    }
                }
            }
        } else if (oldSlot != -1) {
            if (oldSlot >= 0 && oldSlot <= 8) {
                mc.player.getInventory().selectedSlot = oldSlot;
                mc.interactionManager.syncSelectedSlot();
                oldSlot = -1;
                status = false;
            } else {
                switch (mode.getValue()) {
                    case "Vanilla" -> swapBack();
                    case "Grim" -> InventoryUtil.swapWithBypassGrim(this::swapBack);
                    case "ReallyWorld" -> {
                        if (mc.player.isOnGround()) InventoryUtil.swapWithBypassPolar(this::swapBack);
                        else InventoryUtil.swapWithBypassGrim(this::swapBack);
                    }
                }
            }
        }
    }

    private void swap() {
        if (itemIndex == -1) return;
        status = true;
        mc.interactionManager.clickSlot(0, itemIndex, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
    }

    private void swapBack() {
        if (oldSlot == -1 || !status) return;
        mc.interactionManager.clickSlot(0, oldSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
        oldSlot = -1;
        status = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        status = false;
        itemIndex = -1;
        oldSlot = -1;
    }

    private int findBestToolSlot() {
        if (mc.crosshairTarget instanceof BlockHitResult blockHitResult) {
            BlockPos pos = blockHitResult.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);

            int bestSlot = -1;
            float bestSpeed = 1.0f;

            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = mc.player.getInventory().getStack(slot);
                float speed = stack.getMiningSpeedMultiplier(state);

                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = slot;
                }
            }
            return bestSlot;
        }
        return -1;
    }


    private boolean isMousePressed() {
        return mc.crosshairTarget != null && mc.options.attackKey.isPressed();
    }
}