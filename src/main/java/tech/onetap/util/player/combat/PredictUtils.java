package tech.onetap.util.player.combat;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

@UtilityClass
public class PredictUtils {
    private static final double DEFAULT_DAMPING = 0.98;
    private static final float DEFAULT_YAW_MULTIPLIER = 1.5f;
    private static final double MIN_PREDICT_SPEED_BPS = 20.0;

    public static Vec3d getSmartPredictedPosition(Entity entity, Vec3d base, double ticksAhead, double damping, float yawMultiplier) {
        Vec3d predictedPos = base;
        Vec3d velocity = entity.getVelocity();

        double speedBps = Math.hypot(entity.prevX - entity.getX(), entity.prevZ - entity.getZ()) * 20.0;
        if (speedBps <= MIN_PREDICT_SPEED_BPS) {
            return predictedPos.add(0, entity.getHeight(), 0);
        }

        float prevYaw = entity.prevYaw;
        float yaw = entity.getYaw();

        float deltaYaw = Math.abs(yaw - prevYaw);
        float yawFactor = Math.min(deltaYaw / 25.0f, 3.0f);
        double yawBasedReduction = 1.0 - yawFactor * 0.9;

        for (int i = 0; i < (int) ticksAhead; i++) {
            float progress = (float) i / (float) ticksAhead;
            double currentDamping = 1.0 - (1.0 - damping) * (1.0 - progress);

            velocity = rotateVelocityByYaw(velocity, prevYaw, yaw, (float) (yawMultiplier * yawBasedReduction));
            predictedPos = predictedPos.add(velocity);
            velocity = velocity.multiply(currentDamping);
        }

        double lerp = ticksAhead - (int) ticksAhead;
        if (lerp > 0.0) {
            float progress = (float) (1.0 - lerp);
            double currentDamping = 1.0 - (1.0 - damping) * (1.0 - progress);

            velocity = rotateVelocityByYaw(velocity, prevYaw, yaw, (float) (yawMultiplier * yawBasedReduction));
            predictedPos = predictedPos.add(velocity.multiply(lerp * currentDamping));
        }

        return predictedPos.add(0, entity.getHeight(), 0);
    }

    private static Vec3d rotateVelocityByYaw(Vec3d velocity, float prevYaw, float yaw, float multiplier) {
        float deltaYaw = (yaw - prevYaw) * multiplier;

        if (Math.abs(deltaYaw) < 0.001f) {
            return velocity;
        }

        double rad = Math.toRadians(deltaYaw);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double x = velocity.x * cos - velocity.z * sin;
        double z = velocity.x * sin + velocity.z * cos;

        return new Vec3d(x, velocity.y, z);
    }

    public static Vec3d getPredicted(Entity entity, double ticks) {
        return getSmartPredictedPosition(entity, entity.getPos(), ticks, DEFAULT_DAMPING, DEFAULT_YAW_MULTIPLIER);
    }

    public static Vec3d getPredictedRender(Entity entity, double ticks, float partialTicks) {
        return getSmartPredictedPosition(entity, entity.getLerpedPos(partialTicks), ticks, DEFAULT_DAMPING, DEFAULT_YAW_MULTIPLIER);
    }

    public static Vec3d predict(Entity entity, double ticks) {
        return getPredicted(entity, ticks);
    }
}
