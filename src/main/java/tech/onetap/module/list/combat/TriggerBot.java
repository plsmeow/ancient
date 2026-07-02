package tech.onetap.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Hand;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;

@SuppressWarnings("all")
@ModuleInformation(moduleName = "Trigger Bot", moduleCategory = ModuleCategory.COMBAT)
public class TriggerBot extends Module {
    public final BooleanSetting pauseEating = new BooleanSetting("Остановка при еде",true);
    public final BooleanSetting onlyCriticals = new BooleanSetting("Только криты",true);
    public final BooleanSetting spaceOnly = new BooleanSetting("Только с пробелом",false);

    private int delay;

    @Subscribe
    public void onEvent(EventTick e) {
        if (mc.player == null) return;

            if (mc.player.isUsingItem() && pauseEating.getValue()) {
                return;
            }

            if (delay > 0) {
                delay--;
                return;
            }

            if (!autoCrit()) return;

            Entity ent = mc.targetedEntity;
            if (ent != null) {
                mc.interactionManager.attackEntity(mc.player, ent);
                mc.player.swingHand(Hand.MAIN_HAND);
                delay = 10;
            }
    }

    @Override
    public void onDisable() {
        delay = 0;
        super.onDisable();
    }

    private boolean autoCrit() {
        boolean reasonForSkipCrit = !onlyCriticals.getValue()
                || mc.player.getAbilities().flying
                || mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || mc.world.getBlockState(mc.player.getBlockPos()).getBlock() == Blocks.LADDER;

        if (mc.player.getAttackCooldownProgress(0.5f) < (mc.player.isOnGround() ? 1f : 0.9f))
            return false;

        boolean mergeWithSpeed = mc.player.isOnGround();

        if (!mc.options.jumpKey.isPressed() && mergeWithSpeed && spaceOnly.getValue())
            return true;

        if (mc.player.isInLava())
            return true;

        if (!reasonForSkipCrit)
            return !mc.player.isOnGround() && mc.player.fallDistance > 0.0f;
        return true;
    }
}
