package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "ElytraFly", moduleDesc = "Gives you more control over your elytra", moduleCategory = ModuleCategory.MOVEMENT)
public class ElytraFly extends Module {

    public final SliderSetting horizontalSpeed = new SliderSetting("Горизонт. скорость", 1.0, 0, 5, 0.1);
    public final SliderSetting verticalSpeed = new SliderSetting("Вертик. скорость", 1.0, 0, 5, 0.1);

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @Subscribe
    private void onPlayerUpdate(EventPlayerUpdate event) {
        if (mc.player == null) return;
        if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) return;
        if (!mc.player.isGliding()) return;

        Vec3d forward = Vec3d.fromPolar(0, mc.player.getYaw()).multiply(0.1);
        Vec3d right = Vec3d.fromPolar(0, mc.player.getYaw() + 90).multiply(0.1);

        double velX = 0;
        double velY = 0;
        double velZ = 0;

        boolean a = false, b = false;

        if (mc.options.forwardKey.isPressed()) {
            velX += forward.x * horizontalSpeed.getValue() * 10;
            velZ += forward.z * horizontalSpeed.getValue() * 10;
            a = true;
        } else if (mc.options.backKey.isPressed()) {
            velX -= forward.x * horizontalSpeed.getValue() * 10;
            velZ -= forward.z * horizontalSpeed.getValue() * 10;
            a = true;
        }

        if (mc.options.rightKey.isPressed()) {
            velX += right.x * horizontalSpeed.getValue() * 10;
            velZ += right.z * horizontalSpeed.getValue() * 10;
            b = true;
        } else if (mc.options.leftKey.isPressed()) {
            velX -= right.x * horizontalSpeed.getValue() * 10;
            velZ -= right.z * horizontalSpeed.getValue() * 10;
            b = true;
        }

        if (a && b) {
            double diagonal = 1 / Math.sqrt(2);
            velX *= diagonal;
            velZ *= diagonal;
        }

        if (mc.options.jumpKey.isPressed()) velY = 0.5 * verticalSpeed.getValue();
        else if (mc.options.sneakKey.isPressed()) velY = -0.5 * verticalSpeed.getValue();
        else velY = 0;

        mc.player.setVelocity(velX, velY, velZ);
    }
}
