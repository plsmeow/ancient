package tech.onetap.util.math;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.rotation.Rotation;

import static java.lang.Math.hypot;
import static java.lang.Math.toDegrees;
import static net.minecraft.util.math.MathHelper.wrapDegrees;

@UtilityClass
public class RotationUtil implements IMinecraft {

    public Vec2f calculate(final Vec3d fromVec, final Vec3d toVec) {
        final double TO_DEGREES = 180.0F / Math.PI;
        final Vec3d diff = toVec.subtract(fromVec);
        final double distance = Math.hypot(diff.x, diff.z);
        float yaw = (float) (MathHelper.atan2(diff.z, diff.x) * TO_DEGREES) - 90.0F;
        final float pitch = (float) (-(MathHelper.atan2(diff.y, distance) * TO_DEGREES));
        return new Vec2f(yaw, pitch);
    }

    public Vec2f calculate(final Entity entity) {
        return calculate(entity.getPos().add(0, entity.getEyeHeight(entity.getPose()), 0));
    }

    public Vec2f calculate(final Vec3d toVec) {
        return calculate(mc.player.getPos().add(0, mc.player.getEyeHeight(mc.player.getPose()), 0), toVec);
    }

    public static float getAngleDifference(float dir, float yaw) {
        float f = Math.abs(yaw - dir) % 360.0f;
        return f > 180.0f ? 360.0f - f : f;
    }

    public static Vec3d getEyesPos(Entity entity) {
        return entity.getPos().add(0, entity.getEyeHeight(entity.getPose()), 0);
    }

    public static float[] calculateAngle(Vec3d to) {
        return calculateAngle(getEyesPos(mc.player), to);
    }

    public static float[] calculateAngle(Vec3d from, Vec3d to) {
        double difX = to.x - from.x;
        double difY = (to.y - from.y) * -1.0;
        double difZ = to.z - from.z;
        double dist = MathHelper.sqrt((float) (difX * difX + difZ * difZ));
        float yD = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0);
        float pD = (float) MathHelper.clamp(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difY, dist))), -90f, 90f);
        return new float[]{yD, pD};
    }

    public Vector3f getDirectionVector(float yaw, float pitch) {
        float yawRadians = (float) Math.toRadians(yaw);
        float pitchRadians = (float) Math.toRadians(pitch);

        float x = -MathHelper.cos(pitchRadians) * MathHelper.sin(yawRadians);
        float y = -MathHelper.sin(pitchRadians);
        float z = MathHelper.cos(pitchRadians) * MathHelper.cos(yawRadians);

        return new Vector3f(x, y, z);
    }

    public float calculateFov(float cameraYaw, float cameraPitch, float targetYaw, float targetPitch) {
        Vector3f cameraDirection = getDirectionVector(cameraYaw, cameraPitch);
        Vector3f targetDirection = getDirectionVector(targetYaw, targetPitch);

        float dotProduct = cameraDirection.dot(targetDirection);
        dotProduct = MathHelper.clamp(dotProduct, -1.0f, 1.0f);

        float angleRadians = (float) Math.acos(dotProduct);

        return (float) Math.toDegrees(angleRadians);
    }

    public Rotation fromVec3d(Vec3d vector) {
        return new Rotation((float) wrapDegrees(toDegrees(Math.atan2(vector.z, vector.x)) - 90), (float) wrapDegrees(toDegrees(-Math.atan2(vector.y, hypot(vector.x, vector.z)))));
    }
}