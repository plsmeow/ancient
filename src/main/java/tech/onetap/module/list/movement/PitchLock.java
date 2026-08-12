package tech.onetap.module.list.movement;

import meteordevelopment.orbit.EventHandler;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.rotation.FreeLookComponent;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

@ModuleInformation(moduleName = "PitchLock", moduleDesc = "Ставит pitch не меняя yaw", moduleCategory = ModuleCategory.MOVEMENT)
public class PitchLock extends Module {

    private static final int MAX_PRIORITY = Integer.MAX_VALUE;

    private final SliderSetting pitch = new SliderSetting("Pitch", 0, -90, 90, 0.5);

    @Override
    public void onDisable() {
        super.onDisable();
        RotationComponent.getInstance().stopRotation();
    }

    @EventHandler
    private void onPlayerUpdate(EventPlayerUpdate e) {
        if (mc.player == null || mc.world == null) return;

        RotationComponent.update(
                new Rotation(FreeLookComponent.getFreeYaw(), pitch.getFloatValue()),
                360, 360, 360, 360,
                0, MAX_PRIORITY, false
        );
    }
}
