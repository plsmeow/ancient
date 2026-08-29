package tech.onetap.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.MathHelper;
import tech.onetap.event.list.EventAttack;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

@ModuleInformation(moduleName = "Breach Swap", moduleDesc = "Свапает на булаву перед ударом", moduleCategory = ModuleCategory.COMBAT)
public class BreachSwap extends Module {

    // Ванильная скорость атаки булавы: полная зарядка занимает 20 / 0.6 ≈ 33.3 тика
    private static final float MACE_ATTACK_SPEED = 0.6F;

    private int ticksSinceAttack = 1000;

    @EventHandler
    private void onTick(EventTick ignored) {
        if (ticksSinceAttack < 1000) ticksSinceAttack++;
    }

    @EventHandler
    private void onAttack(EventAttack ignored) {
        // Любая атака (ручная или из KillAura) сбрасывает виртуальный кулдаун —
        // так же ведёт себя ванильный lastAttackedTicks
        ticksSinceAttack = 0;
    }

    /**
     * Модуль активен: включён и в хотбаре есть булава с Breach.
     */
    public boolean isActive() {
        return isEnabled() && mc.player != null && mc.world != null && findBreachMaceSlot() != -1;
    }

    /**
     * Виртуальная задержка удара, равная ванильному кулдауну булавы,
     * независимо от предмета в руке (аналог getAttackCooldownProgress(0.5f)).
     */
    public boolean isVirtualCooldownReady() {
        float fullChargeTicks = 20.0F / MACE_ATTACK_SPEED;
        float progress = MathHelper.clamp((ticksSinceAttack + 0.5F) / fullChargeTicks, 0.0F, 1.0F);
        return progress >= 0.98F;
    }

    public int findBreachMaceSlot() {
        if (mc.player == null || mc.world == null) return -1;
        var breach = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get()
                .getEntry(Enchantments.BREACH.getValue()).orElseThrow();

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isOf(Items.MACE) && !stack.getName().getString().contains("1.21 Mace")) continue;
            if (EnchantmentHelper.getLevel(breach, stack) > 0) return slot;
        }
        return -1;
    }

    /**
     * Свап на слот с булавой с Breach. Возвращает предыдущий слот для возврата
     * или -1, если свап не потребовался (уже в руке / нет булавы).
     */
    public int swapToBreachMace() {
        if (!isActive()) return -1;

        int maceSlot = findBreachMaceSlot();
        int previousSlot = mc.player.getInventory().selectedSlot;
        if (previousSlot == maceSlot) return -1;

        mc.player.getInventory().selectedSlot = maceSlot;
        mc.interactionManager.syncSelectedSlot();
        return previousSlot;
    }

    @Override
    public void onEnable() {
        // При включении считаем кулдаун готовым, чтобы не ждать лишний раз
        ticksSinceAttack = 1000;
        super.onEnable();
    }
}
