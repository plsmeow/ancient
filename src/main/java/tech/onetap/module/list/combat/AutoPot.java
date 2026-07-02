package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.math.StopWatch;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;
import tech.onetap.util.text.ValueUnit;

@ModuleInformation(moduleName = "AutoPot", moduleCategory = ModuleCategory.COMBAT)
public class AutoPot extends Module {

    private final BooleanSetting strength = new BooleanSetting("Strength", true);
    private final BooleanSetting speed = new BooleanSetting("Speed", true);
    private final BooleanSetting fire = new BooleanSetting("FireResistance", true);

    private final BooleanSetting heal = new BooleanSetting("InstantHealing", true);
    private final SliderSetting healthH = new SliderSetting("Health", ValueUnit.abbreviation("ХП"), 8, 0, 20, 0.5f)
            .setVisible(heal::getValue);

    private final BooleanSetting regen = new BooleanSetting("Regeneration", true);
    private final ModeSetting triggerOn = new ModeSetting("Trigger", "LackOfRegen", "LackOfRegen", "Health")
            .setVisible(regen::getValue);
    private final SliderSetting healthR = new SliderSetting("HP", ValueUnit.abbreviation("ХП"), 8, 0, 20, 0.5f)
            .setVisible(() -> regen.getValue() && triggerOn.is("Health"));

    private final BooleanSetting onDaGround = new BooleanSetting("OnlyOnGround", true);
    private final BooleanSetting pauseAura = new BooleanSetting("PauseAura", false);

    private final StopWatch timer = new StopWatch();
    private boolean spoofed = false;

    private KillAura getKillAura() {
        return Onetap.getInstance().getModuleStorage().get(KillAura.class);
    }

    public int getPotionSlot(Potions potion) {
        for (int i = 0; i < 9; ++i) {
            if (isStackPotion(mc.player.getInventory().getStack(i), potion)) return i;
        }
        return -1;
    }

    public boolean isPotionOnHotBar(Potions potions) {
        return getPotionSlot(potions) != -1;
    }

    public boolean isStackPotion(ItemStack stack, Potions potion) {
        if (stack == null) return false;

        if (stack.getItem() instanceof SplashPotionItem) {
            PotionContentsComponent potionContentsComponent = stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);

            RegistryEntry<StatusEffect> id = null;

            switch (potion) {
                case STRENGTH -> id = StatusEffects.STRENGTH;
                case SPEED -> id = StatusEffects.SPEED;
                case FIRERES -> id = StatusEffects.FIRE_RESISTANCE;
                case HEAL -> id = StatusEffects.INSTANT_HEALTH;
                case REGEN -> id = StatusEffects.REGENERATION;
            }

            for (StatusEffectInstance effect : potionContentsComponent.getEffects()) {
                if (effect.getEffectType() == id) return true;
            }
        }
        return false;
    }

    private boolean shouldThrow() {
        if (mc.player == null) return false;

        return (!mc.player.hasStatusEffect(StatusEffects.SPEED) && isPotionOnHotBar(Potions.SPEED) && speed.getValue())
                || (!mc.player.hasStatusEffect(StatusEffects.STRENGTH) && isPotionOnHotBar(Potions.STRENGTH) && strength.getValue())
                || (!mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE) && isPotionOnHotBar(Potions.FIRERES) && fire.getValue())
                || (mc.player.getHealth() + mc.player.getAbsorptionAmount() < healthH.getValue() && isPotionOnHotBar(Potions.HEAL) && heal.getValue())
                || (!mc.player.hasStatusEffect(StatusEffects.REGENERATION) && triggerOn.is("LackOfRegen") && isPotionOnHotBar(Potions.REGEN) && regen.getValue())
                || (mc.player.getHealth() + mc.player.getAbsorptionAmount() < healthR.getValue() && triggerOn.is("Health") && isPotionOnHotBar(Potions.REGEN) && regen.getValue());
    }

    /* Используем проверенное событие EventPlayerUpdate (как в вашем AutoTotem).
       Оно обрабатывает и установку ротаций пакетов, и сам бросок зелий за один такт.
    */
    @Subscribe
    private void onPlayerUpdate(EventPlayerUpdate e) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        KillAura aura = getKillAura();
        if (aura != null && aura.getTarget() != null && mc.player.getAttackCooldownProgress(1) > 0.5f) return;

        if (onDaGround.getValue() && !mc.player.isOnGround()) return;

        if (mc.player.age > 80 && shouldThrow()) {

            // Сначала отправляем ротацию "в пол" на сервер через ваш RotationComponent
            RotationComponent.update(new Rotation(mc.player.getYaw(), 90.0f), 360, 360, 360, 360, 0, 1, false);
            spoofed = true;

            // Если задержка таймера прошла, выполняем бросок
            if (timer.isReached(1000) && spoofed) {

                if (!mc.player.hasStatusEffect(StatusEffects.SPEED) && isPotionOnHotBar(Potions.SPEED) && speed.getValue())
                    throwPotion(Potions.SPEED);

                if (!mc.player.hasStatusEffect(StatusEffects.STRENGTH) && isPotionOnHotBar(Potions.STRENGTH) && strength.getValue())
                    throwPotion(Potions.STRENGTH);

                if (!mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE) && isPotionOnHotBar(Potions.FIRERES) && fire.getValue())
                    throwPotion(Potions.FIRERES);

                if (mc.player.getHealth() + mc.player.getAbsorptionAmount() < healthH.getValue() && heal.getValue() && isPotionOnHotBar(Potions.HEAL))
                    throwPotion(Potions.HEAL);

                if (((!mc.player.hasStatusEffect(StatusEffects.REGENERATION) && triggerOn.is("LackOfRegen"))
                        || (mc.player.getHealth() + mc.player.getAbsorptionAmount() < healthR.getValue() && triggerOn.is("Health")))
                        && isPotionOnHotBar(Potions.REGEN) && regen.getValue()) {
                    throwPotion(Potions.REGEN);
                }

                // Возвращаем хотбар на исходный слот
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
                timer.reset();
                spoofed = false;
            }
        }
    }

    public void throwPotion(Potions potion) {
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(getPotionSlot(potion)));
        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), 90.0f));
    }

    public enum Potions {
        STRENGTH, SPEED, FIRERES, HEAL, REGEN
    }
}