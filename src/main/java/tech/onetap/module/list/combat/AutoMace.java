package tech.onetap.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.list.movement.GroundSpoof;
import tech.onetap.module.list.player.ElytraHelper;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.base.Instance;

@ModuleInformation(moduleName = "AutoMace", moduleDesc = "Автоматически берёт булаву в руку при атаке KillAura", moduleCategory = ModuleCategory.COMBAT)
public class AutoMace extends Module {

    public final BooleanSetting forceAutoMace = new BooleanSetting("AutoMace без задержки", true);
    public final BooleanSetting syncHurtTime = new BooleanSetting("Синхронизация с HurtTime", false)
            .setVisible(forceAutoMace::getValue);
    public final ModeSetting macePriority = new ModeSetting("Приоритет булавы", "Нет",
            "Нет", "Плотность", "Пробитие", "Ветер");
    public final BooleanSetting autoMaceElytra = new BooleanSetting("AutoMace (элитра)", false);
    public final BooleanSetting autoMaceElytraBack = new BooleanSetting("Возврат элитры после AutoMace", false)
            .setVisible(autoMaceElytra::getValue);
    public final SliderSetting autoMaceElytraBackDelay = new SliderSetting("Задержка возврата элитры", 0, 0, 10, 1)
            .setVisible(() -> autoMaceElytra.getValue() && autoMaceElytraBack.getValue());

    private boolean autoMaceElytraSwapped;
    private boolean autoMaceElytraSwappedThisAttack;
    private int autoMaceElytraBackTicks;

    @EventHandler
    private void onUpdate(EventTick ignored) {
        if (mc.player == null) return;
        updateAutoMaceElytraBack();
    }

    public void prepareAttack() {
        autoMaceElytraSwappedThisAttack = false;
    }

    public int swapToMace() {
        if (!isEnabled()) return -1;
        if (mc.player.isGliding() && !autoMaceElytra.getValue()) return -1;

        // Проверяем, включен ли GroundSpoof через хранилище модулей
        boolean isGroundSpoofActive = Onetap.getInstance().getModuleStorage().get(GroundSpoof.class).isEnabled()
                || Onetap.getInstance().getModuleStorage().get(MaceKill.class).isEnabled();

        // Если GroundSpoof/MaceKill выключены, оставляем стандартную проверку на дистанцию падения
        if (!isGroundSpoofActive && mc.player.fallDistance < 1.8f) return -1;

        int maceSlot = findBestMaceSlot();
        if (maceSlot == -1) return -1;

        int previousSlot = mc.player.getInventory().selectedSlot;
        if (previousSlot == maceSlot) {
            swapElytraForAutoMace();
            return -1;
        }

        swapElytraForAutoMace();

        mc.player.getInventory().selectedSlot = maceSlot;
        mc.interactionManager.syncSelectedSlot();
        return previousSlot;
    }

    private int findBestMaceSlot() {
        int firstMaceSlot = -1;
        int bestSlot = -1;
        int bestPriorityLevel = -1;

        var density = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get()
                .getEntry(Enchantments.DENSITY.getValue()).orElseThrow();
        var breach = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get()
                .getEntry(Enchantments.BREACH.getValue()).orElseThrow();
        var windBurst = mc.world.getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT).get()
                .getEntry(Enchantments.WIND_BURST.getValue()).orElseThrow();

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isOf(Items.MACE) && !stack.getName().getString().contains("1.21 Mace")) continue;

            if (firstMaceSlot == -1) firstMaceSlot = slot;
            if (macePriority.is("Нет")) continue;

            int level = 0;
            switch (macePriority.getValue()) {
                case "Плотность" -> level = EnchantmentHelper.getLevel(density, stack);
                case "Пробитие" -> level = EnchantmentHelper.getLevel(breach, stack);
                case "Ветер" -> level = EnchantmentHelper.getLevel(windBurst, stack);
            }

            if (level > bestPriorityLevel) {
                bestPriorityLevel = level;
                bestSlot = slot;
            }
        }

        if (macePriority.is("Нет")) return firstMaceSlot;
        return bestSlot != -1 ? bestSlot : firstMaceSlot;
    }

    private void swapElytraForAutoMace() {
        if (!autoMaceElytra.getValue()) return;
        if (!mc.player.isGliding()) return;
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) return;

        Instance.get(ElytraHelper.class).swap(true);
        autoMaceElytraSwapped = true;
        autoMaceElytraSwappedThisAttack = true;
    }

    public void scheduleAutoMaceElytraBack() {
        if (!autoMaceElytraBack.getValue()) return;
        if (!autoMaceElytraSwappedThisAttack) return;

        autoMaceElytraBackTicks = (int) autoMaceElytraBackDelay.getValue();
        if (autoMaceElytraBackTicks <= 0) {
            swapBackElytraForAutoMace();
        }
    }

    private void updateAutoMaceElytraBack() {
        if (!autoMaceElytraSwapped) return;
        if (autoMaceElytraBackTicks <= 0) return;

        autoMaceElytraBackTicks--;
        if (autoMaceElytraBackTicks <= 0) {
            swapBackElytraForAutoMace();
        }
    }

    private void swapBackElytraForAutoMace() {
        Instance.get(ElytraHelper.class).swap(false);
        autoMaceElytraSwapped = false;
        autoMaceElytraSwappedThisAttack = false;
        autoMaceElytraBackTicks = 0;
    }

    private boolean isMaceAttackReady() {
        boolean isGroundSpoofActive = Onetap.getInstance().getModuleStorage().get(GroundSpoof.class).isEnabled();

        return isEnabled()
                && (!mc.player.isGliding() || autoMaceElytra.getValue())
                && (isGroundSpoofActive || mc.player.fallDistance >= 1.8f)
                && findBestMaceSlot() != -1;
    }

    public boolean isForceAutoMaceReady(LivingEntity target) {
        if (!isEnabled() || !forceAutoMace.getValue()) return false;
        if (!isMaceAttackReady()) return false;
        if (syncHurtTime.getValue() && target != null && target.hurtTime > 1) return false;
        return true;
    }

    @Override
    public void onDisable() {
        if (mc.player != null && autoMaceElytraSwapped) {
            swapBackElytraForAutoMace();
        }
        super.onDisable();
    }
}
