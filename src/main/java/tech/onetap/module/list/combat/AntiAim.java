package tech.onetap.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.util.concurrent.ThreadLocalRandom;

@ModuleInformation(moduleName = "AntiAim", moduleDesc = "Applies the lowest-priority anti-aim rotation", moduleCategory = ModuleCategory.COMBAT)
public class AntiAim extends Module {
    private static final String OWNER = "AntiAim";
    private static final int PRIORITY = 0;

    public final ModeSetting yawMode = new ModeSetting("Yaw", "Static", "None", "Static", "Jitter", "Random");
    public final ModeSetting pitchMode = new ModeSetting("Pitch", "Static", "None", "Static", "Jitter", "Random");
    public final SliderSetting staticYaw = new SliderSetting("Static yaw", 180, -180, 180, 1);
    public final SliderSetting staticPitch = new SliderSetting("Static pitch", 90, -90, 90, 1);
    public final SliderSetting yawJitter = new SliderSetting("Yaw jitter", 30, 0, 180, 1);
    public final SliderSetting pitchJitter = new SliderSetting("Pitch jitter", 30, 0, 90, 1);

    @EventHandler
    public void onTick(EventTick ignored) {
        if (mc.player == null || mc.world == null) return;

        float yaw = switch (yawMode.getValue()) {
            case "Static" -> staticYaw.getFloatValue();
            case "Jitter" -> staticYaw.getFloatValue() + randomBetween(-yawJitter.getFloatValue(), yawJitter.getFloatValue());
            case "Random" -> randomBetween(-180.0f, 180.0f);
            default -> mc.player.getYaw();
        };
        float pitch = switch (pitchMode.getValue()) {
            case "Static" -> staticPitch.getFloatValue();
            case "Jitter" -> staticPitch.getFloatValue() + randomBetween(-pitchJitter.getFloatValue(), pitchJitter.getFloatValue());
            case "Random" -> randomBetween(-90.0f, 90.0f);
            default -> mc.player.getPitch();
        };

        RotationComponent.update(
                new Rotation(MathHelper.wrapDegrees(yaw), legalPitch(pitch)),
                360, 360, 360, 360, 1, PRIORITY, false, null, OWNER
        );
    }

    @Override
    public void onDisable() {
        RotationComponent component = RotationComponent.getInstance();
        if (OWNER.equals(component.currentOwner())) {
            component.clearMoveFixMode(OWNER);
            component.stopRotation();
        }
        super.onDisable();
    }

    private static float legalPitch(float pitch) {
        return MathHelper.clamp(pitch, -90.0f, 90.0f);
    }

    private static float randomBetween(float min, float max) {
        return (float) ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
    }
}
