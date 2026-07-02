package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.event.list.EventPopTotem;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.math.StopWatch;
import tech.onetap.util.player.other.InventoryUtil;
import tech.onetap.util.text.ValueUnit;

@ModuleInformation(moduleName = "Auto Totem", moduleCategory = ModuleCategory.COMBAT)
public class AutoTotem extends Module {

    private final ModeSetting mode =
            new ModeSetting("Режим", "Grim",
                    "Vanilla",
                    "Grim",
                    "Polar",
                    "ReallyWorld");

    private final SliderSetting health =
            new SliderSetting("Здоровье", ValueUnit.abbreviation("ХП"), 4, 1, 20, 0.1f);

    private final SliderSetting healthOnElytra =
            new SliderSetting("Здоровье на элитре", ValueUnit.abbreviation("ХП"), 11, 1, 20, 0.1f);

    private final BooleanSetting crystalsCheck =
            new BooleanSetting("Работать на кристалы", false);

    private final BooleanSetting slowness =
            new BooleanSetting("Замедление", false);

    private final SliderSetting slownessDuration =
            new SliderSetting("Длительность замедления", 100, 0, 160, 1)
                    .setVisible(slowness::getValue);

    private int swapBackItem = -1;
    private float cooldownTicks;

    private final StopWatch totemStopWatch = new StopWatch();

    @Subscribe
    private void onUpdate(EventPlayerUpdate e) {
        if (mc.player == null) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        updateSwap();
    }

    @Subscribe
    private void onPopTotem(EventPopTotem e) {
        if (mc.player == null || e.getPlayer() != mc.player) {
            return;
        }

        cooldownTicks = 5;
    }

    private boolean condition() {
        if (mc.player.isCreative() || mc.player.isSpectator()) {
            return false;
        }

        boolean crystalNearby = false;

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity)) {
                continue;
            }

            if (mc.player.getY() >= entity.getY()
                    && mc.player.getEyePos().distanceTo(entity.getBoundingBox().getCenter()) <= 8) {
                crystalNearby = true;
            }
        }

        return (mc.player.getHealth() + mc.player.getAbsorptionAmount()) <= health.getValue()
                || ((mc.player.getHealth() + mc.player.getAbsorptionAmount()) <= healthOnElytra.getValue()
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA)
                || (crystalsCheck.getValue() && crystalNearby);
    }

    private void updateSwap() {
        var offHand = mc.player.getOffHandStack();
        var totemSlot = InventoryUtil.searchItem(Items.TOTEM_OF_UNDYING);

        if (condition()
                && totemStopWatch.isReached(300)
                && totemSlot != -1
                && offHand.getItem() != Items.TOTEM_OF_UNDYING) {

            if (swapBackItem == -1 && !offHand.isEmpty()) {
                swapBackItem = totemSlot;
            }

            if (totemSlot >= 0 && totemSlot <= 8) {
                swapTotem(() ->
                        mc.interactionManager.clickSlot(
                                0,
                                45,
                                totemSlot,
                                SlotActionType.SWAP,
                                mc.player
                        )
                );
            } else if (totemSlot >= 9 && totemSlot <= 45) {
                swapTotem(() ->
                        mc.interactionManager.clickSlot(
                                0,
                                totemSlot,
                                40,
                                SlotActionType.SWAP,
                                mc.player
                        )
                );
            }

            totemStopWatch.reset();
        }

        if (!condition() && swapBackItem != -1 && cooldownTicks == 0) {
            int swapBackSlot = swapBackItem;

            if (swapBackSlot >= 0 && swapBackSlot <= 8) {
                swapTotem(() ->
                        mc.interactionManager.clickSlot(
                                0,
                                45,
                                swapBackSlot,
                                SlotActionType.SWAP,
                                mc.player
                        )
                );
            } else if (swapBackSlot >= 9 && swapBackSlot <= 45) {
                swapTotem(() ->
                        mc.interactionManager.clickSlot(
                                0,
                                swapBackSlot,
                                40,
                                SlotActionType.SWAP,
                                mc.player
                        )
                );
            }

            swapBackItem = -1;
        }
    }

    private void swapTotem(Runnable action) {
        switch (mode.getValue()) {

            case "Vanilla" -> action.run();

            case "Grim" -> {
                InventoryUtil.swapWithBypassGrim(action);
            }

            case "Polar" -> {
                if (slowness.getValue()) {
                    InventoryUtil.swapWithBypassPolar(
                            action,
                            (long) slownessDuration.getValue()
                    );
                } else {
                    InventoryUtil.swapWithBypassPolar(action);
                }
            }

            case "ReallyWorld" -> {
                if (mc.player.isOnGround()) {
                    if (slowness.getValue()) {
                        InventoryUtil.swapWithBypassPolar(
                                action,
                                (long) slownessDuration.getValue()
                        );
                    } else {
                        InventoryUtil.swapWithBypassPolar(action);
                    }
                } else {
                    InventoryUtil.swapWithBypassGrim(action);
                }
            }
        }
    }
}