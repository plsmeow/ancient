package tech.onetap.module.list.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.FireworkEvent;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.QuickLogger;

@ModuleInformation(moduleName = "ElytraBooster", moduleCategory = ModuleCategory.MOVEMENT)
public class ElytraBooster extends Module implements QuickLogger {
    private final ModeSetting mode = new ModeSetting("Режим", "Basic", "Basic", "Bravo");
    private final SliderSetting speedSetting = new SliderSetting("Скорость", 2.35f, 1.6f, 5.0f, 0.05f)
            .setVisible(() -> mode.is("Basic"));

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

    private float getBravoSpeedXZ(float pitch, float yaw) {
        float absPitch = Math.abs(pitch);
        float absYaw = Math.abs(yaw);

        float speed;
        if (absPitch >= 38 && absPitch <= 52) speed = 2f;
        else if (absPitch >= 32 && absPitch <= 58) speed = 1.96f;
        else if (absPitch >= 28 && absPitch <= 62) speed = 1.95f;
        else if ((absYaw >= 29 && absYaw <= 61) || (absPitch >= 29 && absPitch <= 61)) speed = 1.963f;
        else if ((absYaw >= 28 && absYaw <= 60) || (absPitch >= 28 && absPitch <= 60)) speed = 1.954f;
        else if ((absYaw >= 26 && absYaw <= 64) || (absPitch >= 26 && absPitch <= 64)) speed = 1.874f;
        else if ((absYaw >= 24 && absYaw <= 66) || (absPitch >= 24 && absPitch <= 66)) speed = 1.77f;
        else if ((absYaw >= 15 && absYaw <= 75) || (absPitch >= 15 && absPitch <= 75)) speed = 1.70f;
        else if ((absYaw >= 13 && absYaw <= 77) || (absPitch >= 13 && absPitch <= 77)) speed = 1.68f;
        else if ((absYaw >= 12 && absYaw <= 78) || (absPitch >= 12 && absPitch <= 78)) speed = 1.68f;
        else if ((absYaw >= 8 && absYaw <= 82) || (absPitch >= 11 && absPitch <= 79)) speed = 1.66f;
        else if ((absYaw >= 5 && absYaw <= 85) || (absPitch >= 8 && absPitch <= 82)) speed = 1.635f;
        else if (absYaw <= 90 || absPitch <= 90) speed = 1.622f;
        else speed = 1.621f;

        return pitch > 15 ? speed - 0.068f : speed;
    }

    private float getBravoSpeedY(float pitch) {
        if (Math.abs(pitch) >= 37 && Math.abs(pitch) <= 38) return 2.03f;
        if (Math.abs(pitch) >= 25 && Math.abs(pitch) <= 30) return 2f;
        if (Math.abs(pitch) >= 35 && Math.abs(pitch) <= 45) return 1.99f;
        if (Math.abs(pitch) >= 40 && Math.abs(pitch) <= 50) return 1.97f;
        if (Math.abs(pitch) >= 50 && Math.abs(pitch) <= 60) return 1.96f;
        if (Math.abs(pitch) >= 51 && Math.abs(pitch) <= 61) return 1.87f;
        if (Math.abs(pitch) >= 52 && Math.abs(pitch) <= 65) return 1.70f;
        return 1.59f;
    }

    private float normalizeAngle(float angle) {
        float n = angle % 180f;
        if (n > 90f) n -= 180f;
        else if (n < -90f) n += 180f;
        return n;
    }

    @EventHandler
    private void onFirework(FireworkEvent event) {
        if (event.getBoostedEntity() != mc.player) return;
        if (mode.is("Bravo")) {
            float pitch = normalizeAngle(mc.player.getPitch());
            float yaw = normalizeAngle(mc.player.getYaw());
            event.setSpeedXZ(getBravoSpeedXZ(pitch, yaw));
            event.setSpeedY(getBravoSpeedY(pitch));
        } else {
            float speed = Math.max(getSpeed(), 1.6f);
            event.setSpeedXZ(speed);
            event.setSpeedY(speed);
        }
    }
}
