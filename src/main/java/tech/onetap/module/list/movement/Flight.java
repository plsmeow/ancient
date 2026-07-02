package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "Flight", moduleCategory = ModuleCategory.MOVEMENT)
public class Flight extends Module {

    public SliderSetting speed = new SliderSetting("Скорость", 1.0, 0.1, 10.0, 0.1);

    @Subscribe
    public void onUpdate(EventTick event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, speed.getValue() * 0.5, mc.player.getVelocity().z);
        } else if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -speed.getValue() * 0.5, mc.player.getVelocity().z);
        } else {
            mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
        }

        double forward = mc.player.input.movementForward;
        double strafe = mc.player.input.movementSideways;
        float yaw = mc.player.getYaw();
        
        if (forward != 0 || strafe != 0) {
            double angle = Math.atan2(-strafe, forward);
            double finalYaw = Math.toRadians(yaw) + angle;
            double speedVal = speed.getValue();
            
            mc.player.setVelocity(
                -Math.sin(finalYaw) * speedVal,
                mc.player.getVelocity().y,
                Math.cos(finalYaw) * speedVal
            );
        }
        
        mc.player.setOnGround(false);
        mc.player.fallDistance = 0;
    }
}
