package tech.onetap.util.neuro.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.rotation.Rotation;

public final class AIRotationFeatures {

    public static final int INPUT_SIZE = 6;
    public static final int OUTPUT_SIZE = 2;

    private AIRotationFeatures() {
    }

    /**
     * Matches LiquidBounce CombatSample.asInput:
     * total delta, previous rotation velocity, combined horizontal speed, squared boxed distance.
     */
    public static float[] buildInput(
            PlayerEntity player,
            LivingEntity target,
            Rotation currentRotation,
            Rotation targetRotation,
            Rotation previousRotation
    ) {
        float targetDeltaYaw = MathHelper.wrapDegrees(targetRotation.getYaw() - currentRotation.getYaw());
        float targetDeltaPitch = targetRotation.getPitch() - currentRotation.getPitch();
        float previousDeltaYaw = MathHelper.wrapDegrees(currentRotation.getYaw() - previousRotation.getYaw());
        float previousDeltaPitch = currentRotation.getPitch() - previousRotation.getPitch();
        float combinedHorizontalSpeed = horizontalMove(currentVelocity(player)) + horizontalMove(currentVelocity(target));
        float distance = squaredBoxedDistance(player, target);

        return new float[]{
                targetDeltaYaw,
                targetDeltaPitch,
                previousDeltaYaw,
                previousDeltaPitch,
                combinedHorizontalSpeed,
                distance
        };
    }

    public static boolean isValidInput(float[] input) {
        if (input == null || input.length != INPUT_SIZE) return false;
        for (float value : input) {
            if (!Float.isFinite(value)) return false;
        }

        return Math.abs(input[0]) <= 180.0f
                && Math.abs(input[1]) <= 90.0f
                && Math.abs(input[2]) <= 180.0f
                && Math.abs(input[3]) <= 90.0f
                && input[4] >= 0.0f
                && input[4] <= 10.0f
                && input[5] >= 0.0f
                && input[5] <= 1024.0f;
    }

    public static boolean isValidOutput(float[] output) {
        if (output == null || output.length != OUTPUT_SIZE) return false;
        for (float value : output) {
            if (!Float.isFinite(value)) return false;
        }

        return Math.abs(output[0]) <= 180.0f && Math.abs(output[1]) <= 90.0f;
    }

    private static Vec3d currentVelocity(LivingEntity entity) {
        return new Vec3d(entity.getX() - entity.prevX, entity.getY() - entity.prevY, entity.getZ() - entity.prevZ);
    }

    private static float horizontalMove(Vec3d velocity) {
        return (float) Math.hypot(velocity.x, velocity.z);
    }

    private static float squaredBoxedDistance(PlayerEntity player, LivingEntity target) {
        Box playerBox = player.getBoundingBox();
        Box targetBox = target.getBoundingBox();
        double dx = axisDistance(playerBox.minX, playerBox.maxX, targetBox.minX, targetBox.maxX);
        double dy = axisDistance(playerBox.minY, playerBox.maxY, targetBox.minY, targetBox.maxY);
        double dz = axisDistance(playerBox.minZ, playerBox.maxZ, targetBox.minZ, targetBox.maxZ);
        return (float) (dx * dx + dy * dy + dz * dz);
    }

    private static double axisDistance(double firstMin, double firstMax, double secondMin, double secondMax) {
        if (firstMax < secondMin) return secondMin - firstMax;
        if (secondMax < firstMin) return firstMin - secondMax;
        return 0.0;
    }
}
