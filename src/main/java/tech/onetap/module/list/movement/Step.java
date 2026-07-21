package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.attribute.EntityAttributes;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "Step", moduleDesc = "Позволяет подниматься на блоки как по ступенькам", moduleCategory = ModuleCategory.MOVEMENT)
public class Step extends Module {

    private final SliderSetting height = new SliderSetting("Высота", 1.25, 1.0, 10.0, 0.5);

    private double prevStepHeight = 0.5;

    @Subscribe
    private void onTick(EventTick ignored) {
        if (mc.player == null) return;
        mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).setBaseValue(height.getValue());
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.player != null) {
            prevStepHeight = mc.player.getAttributeValue(EntityAttributes.STEP_HEIGHT);
            mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).setBaseValue(height.getValue());
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).setBaseValue(prevStepHeight);
        }
        super.onDisable();
    }
}
