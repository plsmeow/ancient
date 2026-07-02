package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Formatting;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.chat.ChatUtil;
import tech.onetap.util.packet.NetworkUtils;
import tech.onetap.util.player.other.InventoryUtil;

@ModuleInformation(moduleName = "Elytra Helper", moduleCategory = ModuleCategory.PLAYER)
public class ElytraHelper extends Module {
    private final ModeSetting mode = new ModeSetting("Мод", "Vanilla", "Vanilla", "Grim", "Polar");
    private final BindSetting swapKey = new BindSetting("Кнопка свапа", -1);
    private final BindSetting fireworkKey = new BindSetting("Кнопка феерверка", -1);
    private final ModeSetting throwFireworkMode = new ModeSetting("Мод пуска феера", "Обычный", "Обычный", "Легитный");
    private final BooleanSetting autoTakeoff = new BooleanSetting("Автовзлёт", true);

    private boolean fireworkUsed;
    private boolean swapped;

    @Subscribe
    private void onKey(EventKeyInput e) {
        if (e.getAction() == 0) return;
        if (e.getKey() == swapKey.getValue()) swapped = true;
        if (e.getKey() == fireworkKey.getValue() && mc.player.isGliding()) fireworkUsed = true;
    }

    @Subscribe
    private void onPlayerUpdate(EventPlayerUpdate e) {
        if (!swapped) return;
        swapped = false;
        swap(mode, mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA);
    }

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null) return;

        if (autoTakeoff.getValue()) {
            var chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);

            if (chest.getItem() == Items.ELYTRA
                    && !mc.player.isInLava()
                    && !mc.player.isTouchingWater()
                    && mc.player.isOnGround()
                    && !mc.player.hasVehicle()
                    && !mc.player.isGliding()
                    && !mc.player.isSpectator()
                    && !mc.options.jumpKey.isPressed()) {
                mc.player.jump();
            }

            if (chest.getItem() == Items.ELYTRA
                    && !mc.player.isInLava()
                    && !mc.player.isTouchingWater()
                    && !mc.player.isOnGround()
                    && !mc.player.hasVehicle()
                    && !mc.player.isGliding()
                    && !mc.player.isSpectator()) {
                NetworkUtils.sendSilentPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.startGliding();
            }
        }

        if (!fireworkUsed) return;
        if (isEatingWithMainHand()) {
            useFireworkFromOffhand();
            fireworkUsed = false;
            return;
        }

        switch (throwFireworkMode.getValue()) {
            case "Обычный" -> InventoryUtil.swapAndUseHvH(Items.FIREWORK_ROCKET);
            case "Легитный" -> InventoryUtil.swapAndUseLegit(Items.FIREWORK_ROCKET);
        }
        fireworkUsed = false;
    }

    public void swap(ModeSetting mode, boolean chestplate) {
        switch (mode.getValue()) {
            case "Vanilla" -> vanillaSwap(chestplate);
            case "Grim" -> grimSwap(chestplate);
            case "Polar" -> polarSwap(chestplate);
        }
    }

    public void swap(boolean chestplate) {
        swap(mode, chestplate);
    }

    public void swap(String mode, boolean chestplate) {
        switch (mode) {
            case "Vanilla" -> vanillaSwap(chestplate);
            case "Grim" -> grimSwap(chestplate);
            case "Polar" -> polarSwap(chestplate);
        }
    }

    private void vanillaSwap(boolean chestplate) {
        var slot = chestplate ? InventoryUtil.findBestChestplateSlot() : InventoryUtil.findBestElytraSlot();

        if (slot == -1) {
            var info = "Элитра" + Formatting.GRAY + " не найдена в инвентаре";
            if (chestplate) info = "Нагрудник" + Formatting.GRAY + " не найден в инвентаре";
            ChatUtil.send(info);
            return;
        }

        if (slot >= 0 && slot <= 8) mc.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, mc.player);
        else if (slot >= 8 && slot <= 45) {
            mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, 6, 8, SlotActionType.SWAP, mc.player);
            mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
        }
    }

    private void grimSwap(boolean chestplate) {
        var slot = chestplate ? InventoryUtil.findBestChestplateSlot() : InventoryUtil.findBestElytraSlot();

        if (slot == -1) {
            var info = "Элитра" + Formatting.GRAY + " не найдена в инвентаре";
            if (chestplate) info = "Нагрудник" + Formatting.GRAY + " не найден в инвентаре";
            ChatUtil.send(info);
            return;
        }

        if (slot >= 0 && slot <= 8) {
            InventoryUtil.swapWithBypassGrim(() -> mc.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, mc.player));
        } else if (slot >= 8 && slot <= 45) {
            InventoryUtil.swapWithBypassGrim(() -> {
                mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
                mc.interactionManager.clickSlot(0, 6, 8, SlotActionType.SWAP, mc.player);
                mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
            });
        }
    }

    private void polarSwap(boolean chestplate) {
        var slot = chestplate ? InventoryUtil.findBestChestplateSlot() : InventoryUtil.findBestElytraSlot();

        if (slot == -1) {
            var info = "Элитра" + Formatting.GRAY + " не найдена в инвентаре";
            if (chestplate) info = "Нагрудник" + Formatting.GRAY + " не найден в инвентаре";
            ChatUtil.send(info);
            return;
        }

        if (slot >= 0 && slot <= 8) {
            InventoryUtil.swapWithBypassPolar(() -> {
                mc.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, mc.player);
            });
        } else if (slot >= 8 && slot <= 45) {
            InventoryUtil.swapWithBypassPolar(() -> {
                mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
                mc.interactionManager.clickSlot(0, 6, 8, SlotActionType.SWAP, mc.player);
                mc.interactionManager.clickSlot(0, slot, 8, SlotActionType.SWAP, mc.player);
            });
        }
    }

    private boolean isEatingWithMainHand() {
        if (mc.player == null || !mc.player.isUsingItem()) return false;
        if (mc.player.getActiveHand() != Hand.MAIN_HAND) return false;

        UseAction useAction = mc.player.getMainHandStack().getUseAction();
        return useAction == UseAction.EAT || useAction == UseAction.DRINK;
    }

    private void useFireworkFromOffhand() {
        if (mc.player == null || mc.interactionManager == null) return;

        if (mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            return;
        }

        int slot = InventoryUtil.searchItemHotbar(Items.FIREWORK_ROCKET);
        if (slot == -1) {
            slot = InventoryUtil.searchItem(Items.FIREWORK_ROCKET);
        }
        if (slot == -1) return;

        final int fireworkSlot = slot;

        Runnable swapToOffhand = () -> {
            if (fireworkSlot >= 0 && fireworkSlot <= 8) {
                mc.interactionManager.clickSlot(0, 45, fireworkSlot, SlotActionType.SWAP, mc.player);
            } else {
                mc.interactionManager.clickSlot(0, fireworkSlot, 40, SlotActionType.SWAP, mc.player);
            }
        };

        Runnable swapBack = () -> {
            if (fireworkSlot >= 0 && fireworkSlot <= 8) {
                mc.interactionManager.clickSlot(0, 45, fireworkSlot, SlotActionType.SWAP, mc.player);
            } else {
                mc.interactionManager.clickSlot(0, fireworkSlot, 40, SlotActionType.SWAP, mc.player);
            }
        };

        switch (mode.getValue()) {
            case "Vanilla" -> {
                swapToOffhand.run();
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                swapBack.run();
            }
            case "Grim" -> InventoryUtil.swapWithBypassGrim(() -> {
                swapToOffhand.run();
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                swapBack.run();
            });
            case "Polar" -> InventoryUtil.swapWithBypassPolar(() -> {
                swapToOffhand.run();
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                swapBack.run();
            });
        }
    }
}
