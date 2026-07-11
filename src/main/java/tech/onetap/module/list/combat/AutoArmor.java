package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.screen.slot.SlotActionType;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.math.StopWatch;
import tech.onetap.util.player.other.InventoryUtil;

@ModuleInformation(moduleName = "Auto Armor", moduleDesc = "Автоматически экипирует броню", moduleCategory = ModuleCategory.COMBAT)
public class AutoArmor extends Module {

    private final StopWatch equipArmorCooldownHelmet = new StopWatch();
    private final StopWatch equipArmorCooldownChestplate = new StopWatch();
    private final StopWatch equipArmorCooldownLeggings = new StopWatch();
    private final StopWatch equipArmorCooldownBoots = new StopWatch();

    @Subscribe
    private void onUpdate(final EventPlayerUpdate ignored) {
        if (mc.player == null) return;

        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;

        if (mc.player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) swapArmor(EquipmentSlot.HEAD);
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) swapArmor(EquipmentSlot.CHEST);
        if (mc.player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) swapArmor(EquipmentSlot.LEGS);
        if (mc.player.getEquippedStack(EquipmentSlot.FEET).isEmpty()) swapArmor(EquipmentSlot.FEET);
    }

    private void swapArmor(EquipmentSlot equipmentSlot) {
        if (equipmentSlot == EquipmentSlot.HEAD && !equipArmorCooldownHelmet.isReached(50)
                || equipmentSlot == EquipmentSlot.CHEST && !equipArmorCooldownChestplate.isReached(50)
                || equipmentSlot == EquipmentSlot.LEGS && !equipArmorCooldownLeggings.isReached(50)
                || equipmentSlot == EquipmentSlot.FEET && !equipArmorCooldownBoots.isReached(50)) return;

        var slot = InventoryUtil.getBestArmorSlot(equipmentSlot);

        if (slot == -1) return;

        if (slot < 9) slot += 36;

        int finalSlot = slot;

        InventoryUtil.clickWithGuiBypass(() -> mc.interactionManager.clickSlot(0, finalSlot, 0, SlotActionType.QUICK_MOVE, mc.player));

        if (equipmentSlot == EquipmentSlot.HEAD) equipArmorCooldownHelmet.reset();
        if (equipmentSlot == EquipmentSlot.CHEST) equipArmorCooldownChestplate.reset();
        if (equipmentSlot == EquipmentSlot.LEGS) equipArmorCooldownLeggings.reset();
        if (equipmentSlot == EquipmentSlot.FEET) equipArmorCooldownBoots.reset();
    }
}
