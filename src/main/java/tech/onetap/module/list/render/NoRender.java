package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ModeListSetting;

@ModuleInformation(moduleName = "Removals", moduleCategory = ModuleCategory.RENDER)
public class NoRender extends Module {

    public final ModeListSetting elements = new ModeListSetting("Убрать элементы",
            new BooleanSetting("Огонь",true),
            new BooleanSetting("Размытие в воде",true),
            new BooleanSetting("Зрение в блоках",true),
            new BooleanSetting("Камераклип",true),
            new BooleanSetting("Тряска камеры",true),
            new BooleanSetting("Слепота",true),
            new BooleanSetting("Тьма",true),
            new BooleanSetting("Тошнота",true)
    );

    @Subscribe
    public void onUpdate(EventTick e) {
        if (mc.player == null) return;

        if (elements.isEnabled("Тряска камеры")) {
            mc.options.getDamageTiltStrength().setValue(0.0);
        } else {
            mc.options.getDamageTiltStrength().setValue(0.5);
        }

        if (elements.isEnabled("Слепота")) {
            StatusEffectInstance blindness = mc.player.getStatusEffect(StatusEffects.BLINDNESS);
            if (blindness != null) {
                mc.player.removeStatusEffect(StatusEffects.BLINDNESS);
            }
        }

        if (elements.isEnabled("Тьма")) {
            StatusEffectInstance darkness = mc.player.getStatusEffect(StatusEffects.DARKNESS);
            if (darkness != null) {
                mc.player.removeStatusEffect(StatusEffects.DARKNESS);
            }
        }

        if (elements.isEnabled("Тошнота")) {
            StatusEffectInstance nausea = mc.player.getStatusEffect(StatusEffects.NAUSEA);
            if (nausea != null) {
                mc.player.removeStatusEffect(StatusEffects.NAUSEA);
            }
        }
    }

    @Override
    public void onDisable() {
        mc.options.getDamageTiltStrength().setValue(0.5);
        super.onDisable();
    }
}