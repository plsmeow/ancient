package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.FireworkEvent;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.QuickLogger;

@ModuleInformation(moduleName = "ElytraBooster", moduleCategory = ModuleCategory.MOVEMENT)
public class ElytraBooster extends Module implements QuickLogger {
    private final SliderSetting speedSetting = new SliderSetting("Скорость", 2.35f, 1.6f, 5.0f, 0.05f);

    public float getSpeed() {
        LivingEntity entity = mc.player;
        float yaw = Math.abs((entity.getYaw() - 360) % 360);
        float maxSpeed = speedSetting.getFloatValue();

        float[] centers = {45f, 135f, 225f, 315f};
        float center = centers[0];
        float minDiff = 9999f;

        for (float c : centers) {
            float d = Math.abs(yaw - c);
            if (d < minDiff) {
                minDiff = d;
                center = c;
            }
        }

        float diff = Math.abs(yaw - center);

        float speed = maxSpeed - (diff * 0.05f);
        Vec3d vec3d = entity.getRotationVector();
        Vec3d oldVelocity = Vec3d.fromPolar(entity.getPitch(), entity.getYaw()).multiply(Math.max(speed, 1.61f));
        float f = entity.getPitch() * (float) (Math.PI / 180.0);
        double d = Math.sqrt(vec3d.x * vec3d.x + vec3d.z * vec3d.z);
        double e = oldVelocity.horizontalLength();
        boolean bl = entity.getVelocity().y <= 0.0;
        double g = bl && entity.hasStatusEffect(StatusEffects.SLOW_FALLING) ? Math.min(entity.getFinalGravity(), 0.01) : entity.getFinalGravity();
        double h = MathHelper.square(Math.cos(f));
        oldVelocity = oldVelocity.add(0.0, g * (-1.0 + h * 0.75), 0.0);
        if (oldVelocity.y < 0.0 && d > 0.0) {
            double i = oldVelocity.y * -0.1 * h;
            oldVelocity = oldVelocity.add(vec3d.x * i / d, i, vec3d.z * i / d);
        }

        if (f < 0.0F && d > 0.0) {
            double i = e * -MathHelper.sin(f) * 0.04;
            oldVelocity = oldVelocity.add(-vec3d.x * i / d, i * 3.2, -vec3d.z * i / d);
        }

        if (d > 0.0) {
            oldVelocity = oldVelocity.add((vec3d.x / d * e - oldVelocity.x) * 0.1, 0.0, (vec3d.z / d * e - oldVelocity.z) * 0.1);
        }

        oldVelocity = oldVelocity.multiply(0.99F, 0.98F, 0.99F);

        return (Math.max((float) new Vec3d(oldVelocity.x, oldVelocity.y, oldVelocity.z).length(), 1.5f));
    }

    @Subscribe
    private void onFirework(FireworkEvent event) {
        if (event.getBoostedEntity() != mc.player) return;
        event.setSpeed(Math.max(getSpeed(), 1.6f));
    }
}
