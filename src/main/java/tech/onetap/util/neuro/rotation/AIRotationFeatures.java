package tech.onetap.util.neuro.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.rotation.Rotation;

public final class AIRotationFeatures {

    public static final int INPUT_SIZE = 13;
    public static final int OUTPUT_SIZE = 2;

    private AIRotationFeatures() {
    }

    /**
     * 13 входных фичей:
     *  [0] targetDeltaYaw      — разница углов к цели (yaw)
     *  [1] targetDeltaPitch    — разница углов к цели (pitch)
     *  [2] previousDeltaYaw    — предыдущая скорость поворота (yaw)
     *  [3] previousDeltaPitch  — предыдущая скорость поворота (pitch)
     *  [4] combinedHorizontalSpeed — суммарная горизонтальная скорость игрока + цели
     *  [5] distance            — квадрат расстояния (AABB)
     *  [6] targetHurtTime      — время HurtTime цели (0-1)
     *  [7] attackCooldown      — прогресс кулдауна атаки (0-1)
     *  [8] onTarget            — попадает ли луч в хитбокс (0/1)
     *  [9] aimPointX           — позиция точки удара по X (0-1 внутри бокса)
     * [10] aimPointY           — позиция точки удара по Y (0-1)
     * [11] aimPointZ           — позиция точки удара по Z (0-1)
     * [12] isAttacking         — зажата ли ЛКМ (0/1)
     */
    public static float[] buildInput(
            PlayerEntity player,
            LivingEntity target,
            Rotation currentRotation,
            Rotation targetRotation,
            Rotation previousRotation,
            Vec3d aimPoint
    ) {
        Vec3d currentDir = currentRotation.toVector();
        Vec3d targetDir = targetRotation.toVector();
        Vec3d previousDir = previousRotation.toVector();

        float targetDeltaYaw = computeYawDelta(currentDir, targetDir);
        float targetDeltaPitch = computePitchDelta(currentDir, targetDir);
        float previousDeltaYaw = computeYawDelta(previousDir, currentDir);
        float previousDeltaPitch = computePitchDelta(previousDir, currentDir);

        float combinedHorizontalSpeed = horizontalMove(currentVelocity(player)) + horizontalMove(currentVelocity(target));
        float distance = squaredBoxedDistance(player, target);

        float hurtTime = MathHelper.clamp(target.hurtTime / 10.0f, 0.0f, 1.0f);

        float attackCooldown = 0.0f;
        if (player instanceof net.minecraft.client.network.ClientPlayerEntity) {
            attackCooldown = MinecraftClient.getInstance().player.getAttackCooldownProgress(0.5f);
        }

        float onTarget = 0.0f;
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = currentDir;
        if (RaytraceUtil.rayTrace(lookVec, 6.0, target.getBoundingBox())) {
            onTarget = 1.0f;
        }

        float aimPointX = 0.5f;
        float aimPointY = 0.5f;
        float aimPointZ = 0.5f;
        if (aimPoint != null) {
            Box box = target.getBoundingBox();
            double dx = box.maxX - box.minX;
            double dy = box.maxY - box.minY;
            double dz = box.maxZ - box.minZ;
            if (dx > 0) aimPointX = (float) MathHelper.clamp((aimPoint.x - box.minX) / dx, 0.0, 1.0);
            if (dy > 0) aimPointY = (float) MathHelper.clamp((aimPoint.y - box.minY) / dy, 0.0, 1.0);
            if (dz > 0) aimPointZ = (float) MathHelper.clamp((aimPoint.z - box.minZ) / dz, 0.0, 1.0);
        }

        float isAttacking = 0.0f;
        if (MinecraftClient.getInstance().options.attackKey.isPressed()) {
            isAttacking = 1.0f;
        }

        return new float[]{
                targetDeltaYaw, targetDeltaPitch,
                previousDeltaYaw, previousDeltaPitch,
                combinedHorizontalSpeed, distance,
                hurtTime, attackCooldown, onTarget,
                aimPointX, aimPointY, aimPointZ,
                isAttacking
        };
    }

    private static float computeYawDelta(Vec3d from, Vec3d to) {
        float fromYaw = (float) Math.toDegrees(Math.atan2(from.z, from.x));
        float toYaw = (float) Math.toDegrees(Math.atan2(to.z, to.x));
        return MathHelper.wrapDegrees(toYaw - fromYaw);
    }

    private static float computePitchDelta(Vec3d from, Vec3d to) {
        float fromPitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(from.normalize().y, -1.0f, 1.0f)));
        float toPitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(to.normalize().y, -1.0f, 1.0f)));
        return toPitch - fromPitch;
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
                && input[5] <= 1024.0f
                && input[6] >= 0.0f && input[6] <= 1.0f
                && input[7] >= 0.0f && input[7] <= 1.0f
                && input[8] >= 0.0f && input[8] <= 1.0f
                && input[9] >= 0.0f && input[9] <= 1.0f
                && input[10] >= 0.0f && input[10] <= 1.0f
                && input[11] >= 0.0f && input[11] <= 1.0f
                && input[12] >= 0.0f && input[12] <= 1.0f;
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
