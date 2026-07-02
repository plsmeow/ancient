package tech.onetap.module.list.render;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.effect.StatusEffectInstance;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

import static net.minecraft.entity.effect.StatusEffects.NIGHT_VISION;

@ModuleInformation(moduleName = "Full Bright", moduleCategory = ModuleCategory.RENDER)
public class FullBright extends Module {

    @Subscribe
    private void onUpdate(EventTick e) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.hasStatusEffect(NIGHT_VISION)) {
            mc.player.addStatusEffect(new StatusEffectInstance(NIGHT_VISION, -1, 3));
        }
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;
        mc.player.removeStatusEffect(NIGHT_VISION);
        super.onDisable();
    }
}