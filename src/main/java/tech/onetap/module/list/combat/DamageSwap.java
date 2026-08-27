package tech.onetap.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.player.other.InventoryUtil;

/**
 * Ищет в инвентаре предмет с наибольшим прибавкой к урону при взятии в offhand.
 * Если он даёт больше урона, чем текущий mainhand — свапает его в offhand перед
 * ударом KillAura и возвращает обратно после. Свап через ванильный SWAP-клик
 * (button=40 = swap с offhand), работает из любого слота инвентаря без
 * промежуточного обмена. Учитывает режим обхода GuiMove.
 */
@ModuleInformation(moduleName = "Damage Swap", moduleDesc = "Свап лучшего по урону предмета в offhand на время удара KillAura", moduleCategory = ModuleCategory.COMBAT)
public class DamageSwap extends Module {

    private static final double EPSILON = 1.0E-4;

    /** Через сколько тиков после свапа возвращать предмет (удар происходит в тот же тик). */
    private static final int RETURN_DELAY_TICKS = 1;

    private int returnCountdown = -1;
    private int lastSwappedSlot = -1;

    /**
     * Модуль активен: включён и есть кандидат на свап.
     */
    public boolean isActive() {
        if (!isEnabled() || mc.player == null) return false;
        return findBestOffhandCandidate() != -1;
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (mc.player == null) {
            returnCountdown = -1;
            lastSwappedSlot = -1;
            return;
        }
        // Гарантированный возврат: выполняется сам, даже если KillAura
        // не дошёл до afterAttack() (исключение, смена цели и т.п.)
        if (returnCountdown >= 0 && --returnCountdown < 0) {
            swapBack();
        }
    }

    /**
     * Эффективный множитель урона предмета в указанном слоте:
     * (1 + flat) * (1 + %base) * (1 + %total).
     */
    private static double damageScore(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return 1.0; // пустой слот = базовый урон кулака

        double[] flat = {0.0};
        double[] mulBase = {0.0};
        double[] mulTotal = {0.0};

        stack.applyAttributeModifiers(slot, (attribute, modifier) -> {
            if (attribute != EntityAttributes.ATTACK_DAMAGE) return;
            if (modifier.operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
                flat[0] += modifier.value();
            } else if (modifier.operation() == EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                mulBase[0] += modifier.value();
            } else if (modifier.operation() == EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                mulTotal[0] += modifier.value();
            }
        });

        return (1.0 + flat[0]) * (1.0 + mulBase[0]) * (1.0 + mulTotal[0]);
    }

    /**
     * Слот инвентаря (0-35), чей предмет в offhand даст больше урона, чем текущий
     * предмет в offhand. Mainhand игнорируется полностью: тотем +8 в инвентаре и
     * тотем +2 в offhand → свапаем +8, т.к. 8 > 2, независимо от того, что в руке.
     */
    private int findBestOffhandCandidate() {
        double threshold = damageScore(mc.player.getOffHandStack(), EquipmentSlot.OFFHAND);

        int bestSlot = -1;
        double bestScore = threshold;

        // mainhand игнорируем полностью: сравниваем кандидатов только между собой
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            double score = damageScore(stack, EquipmentSlot.OFFHAND);
            if (score > bestScore + EPSILON) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /** Индекс слота PlayerScreenHandler для индекса PlayerInventory (хотбар 0-8 → 36-44). */
    private static int handlerSlot(int invSlot) {
        return invSlot < 9 ? 36 + invSlot : invSlot;
    }

    /** Ванильный SWAP-клик: handlerSlot ↔ button (номер хотбара или 40 = offhand). */
    private void clickSwap(int handlerSlot, int button) {
        mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,
                handlerSlot,
                button,
                SlotActionType.SWAP,
                mc.player
        );
    }

    private void runBypass(Runnable click) {
        // Vanilla — сразу; Grim/Polar — через SlownessManager согласно режиму GuiMove
        InventoryUtil.clickWithGuiBypass(click);
    }

    /**
     * Свапает найденный предмет в offhand. Вызывается перед ударом KillAura.
     *
     * @return true, если свап выполнен и нужен возврат после удара
     */
    public boolean beforeAttack() {
        if (!isEnabled() || returnCountdown >= 0 || mc.player == null) return false;

        int slot = findBestOffhandCandidate();
        if (slot == -1) return false;

        final int hs = handlerSlot(slot);
        runBypass(() -> clickSwap(hs, 40)); // button=40 — SWAP с offhand

        lastSwappedSlot = slot;
        returnCountdown = RETURN_DELAY_TICKS;
        return true;
    }

    /** Возврат предмета обратно после удара: повторный SWAP того же слота. */
    public void afterAttack() {
        if (lastSwappedSlot == -1) return;
        swapBack();
    }

    private void swapBack() {
        if (mc.player == null || lastSwappedSlot == -1) {
            lastSwappedSlot = -1;
            returnCountdown = -1;
            return;
        }

        final int hs = handlerSlot(lastSwappedSlot);
        runBypass(() -> clickSwap(hs, 40));

        lastSwappedSlot = -1;
        returnCountdown = -1;
    }

    @Override
    public void onDisable() {
        swapBack();
        super.onDisable();
    }
}
