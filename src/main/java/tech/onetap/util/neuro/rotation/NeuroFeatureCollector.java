package tech.onetap.util.neuro.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.rotation.Rotation;

/**
 * Собирает 33 фичи для одного временного шага.
 * Состояние на инстанс, без static-полей.
 */
public class NeuroFeatureCollector implements IMinecraft {

    private float prevAttackCooldown = 1.0f;
    private Vec3d prevAimPoint = Vec3d.ZERO;

    public void reset() {
        prevAttackCooldown = 1.0f;
        prevAimPoint = Vec3d.ZERO;
    }

    /**
     * Собирает фичи в dest начиная с offset.
     * @param dest целевой массив
     * @param offset смещение для записи
     * @param player игрок
     * @param target цель
     * @param currentRotation текущий поворот
     * @param aimPoint точка прицеливания в мире
     * @param targetChanged смена цели на этом тике
     */
    public void collect(float[] dest, int offset,
                        PlayerEntity player, LivingEntity target,
                        Rotation currentRotation, Vec3d aimPoint,
                        boolean targetChanged) {

        // Player velocity
        Vec3d playerVel = new Vec3d(
                player.getX() - player.prevX,
                player.getY() - player.prevY,
                player.getZ() - player.prevZ
        );
        dest[offset + NeuroFeatureSchema.PLAYER_VEL_X] = (float) playerVel.x;
        dest[offset + NeuroFeatureSchema.PLAYER_VEL_Y] = (float) playerVel.y;
        dest[offset + NeuroFeatureSchema.PLAYER_VEL_Z] = (float) playerVel.z;

        // Player input (поле input есть только на ClientPlayerEntity)
        float forwardInput = 0.0f;
        float sidewaysInput = 0.0f;
        if (player instanceof net.minecraft.client.network.ClientPlayerEntity clientPlayer) {
            forwardInput = clientPlayer.input.movementForward;
            sidewaysInput = clientPlayer.input.movementSideways;
        }
        dest[offset + NeuroFeatureSchema.PLAYER_FORWARD_INPUT] = forwardInput;
        dest[offset + NeuroFeatureSchema.PLAYER_SIDEWAYS_INPUT] = sidewaysInput;

        // Player state
        dest[offset + NeuroFeatureSchema.PLAYER_ON_GROUND] = player.isOnGround() ? 1.0f : 0.0f;
        dest[offset + NeuroFeatureSchema.PLAYER_SPRINTING] = player.isSprinting() ? 1.0f : 0.0f;
        dest[offset + NeuroFeatureSchema.PLAYER_SNEAKING] = player.isSneaking() ? 1.0f : 0.0f;
        dest[offset + NeuroFeatureSchema.PLAYER_FALL_DISTANCE] = player.fallDistance;

        // Target relative position — поворот в yaw-фрейм игрока
        Vec3d targetPos = target.getPos();
        Vec3d eyePos = player.getEyePos();
        Vec3d relWorld = targetPos.subtract(eyePos);

        float yawRad = -currentRotation.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);

        double relX = relWorld.x * cosYaw - relWorld.z * sinYaw;
        double relZ = relWorld.x * sinYaw + relWorld.z * cosYaw;

        dest[offset + NeuroFeatureSchema.TARGET_REL_X] = (float) relX;
        dest[offset + NeuroFeatureSchema.TARGET_REL_Y] = (float) relWorld.y;
        dest[offset + NeuroFeatureSchema.TARGET_REL_Z] = (float) relZ;

        // Target velocity — тоже в yaw-фрейме
        Vec3d targetVel = new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ
        );
        double velX = targetVel.x * cosYaw - targetVel.z * sinYaw;
        double velZ = targetVel.x * sinYaw + targetVel.z * cosYaw;

        dest[offset + NeuroFeatureSchema.TARGET_VEL_X] = (float) velX;
        dest[offset + NeuroFeatureSchema.TARGET_VEL_Y] = (float) targetVel.y;
        dest[offset + NeuroFeatureSchema.TARGET_VEL_Z] = (float) velZ;

        // Target metadata
        Box box = target.getBoundingBox();
        dest[offset + NeuroFeatureSchema.TARGET_DISTANCE] = (float) eyePos.distanceTo(targetPos);
        dest[offset + NeuroFeatureSchema.TARGET_WIDTH] = (float) (box.maxX - box.minX);
        dest[offset + NeuroFeatureSchema.TARGET_HEIGHT] = (float) (box.maxY - box.minY);
        dest[offset + NeuroFeatureSchema.TARGET_ON_GROUND] = target.isOnGround() ? 1.0f : 0.0f;

        // Rotation deltas — вычисляются caller-ом, здесь заполняются нулями
        // Caller должен перезаписать их на основе истории
        dest[offset + NeuroFeatureSchema.PREV_DELTA_YAW] = 0.0f;
        dest[offset + NeuroFeatureSchema.PREV_DELTA_PITCH] = 0.0f;

        // Геометрическая дельта к aim point
        Rotation targetRotation = new Rotation(aimPoint);
        float targetDeltaYaw = MathHelper.wrapDegrees(targetRotation.getYaw() - currentRotation.getYaw());
        float targetDeltaPitch = targetRotation.getPitch() - currentRotation.getPitch();
        dest[offset + NeuroFeatureSchema.TARGET_DELTA_YAW] = targetDeltaYaw;
        dest[offset + NeuroFeatureSchema.TARGET_DELTA_PITCH] = targetDeltaPitch;

        // Aim point — нормализованное пространство хитбокса (X −1..1, Y 0..1, Z −1..1)
        double dx = box.maxX - box.minX;
        double dy = box.maxY - box.minY;
        double dz = box.maxZ - box.minZ;

        float aimX = 0.0f;
        float aimY = 0.5f;
        float aimZ = 0.0f;

        if (aimPoint != null) {
            if (dx > 0) {
                double norm = (aimPoint.x - box.minX) / dx;
                aimX = (float) MathHelper.clamp(norm * 2.0 - 1.0, -1.0, 1.0);
            }
            if (dy > 0) {
                aimY = (float) MathHelper.clamp((aimPoint.y - box.minY) / dy, 0.0, 1.0);
            }
            if (dz > 0) {
                double norm = (aimPoint.z - box.minZ) / dz;
                aimZ = (float) MathHelper.clamp(norm * 2.0 - 1.0, -1.0, 1.0);
            }
        }

        dest[offset + NeuroFeatureSchema.AIM_X] = aimX;
        dest[offset + NeuroFeatureSchema.AIM_Y] = aimY;
        dest[offset + NeuroFeatureSchema.AIM_Z] = aimZ;

        // Aim velocity
        if (aimPoint != null && !prevAimPoint.equals(Vec3d.ZERO)) {
            Vec3d aimVel = aimPoint.subtract(prevAimPoint);
            // Нормализованная скорость (в пространстве хитбокса per tick)
            dest[offset + NeuroFeatureSchema.AIM_VEL_X] = (float) (dx > 0 ? aimVel.x / dx : 0.0);
            dest[offset + NeuroFeatureSchema.AIM_VEL_Y] = (float) (dy > 0 ? aimVel.y / dy : 0.0);
            dest[offset + NeuroFeatureSchema.AIM_VEL_Z] = (float) (dz > 0 ? aimVel.z / dz : 0.0);
        } else {
            dest[offset + NeuroFeatureSchema.AIM_VEL_X] = 0.0f;
            dest[offset + NeuroFeatureSchema.AIM_VEL_Y] = 0.0f;
            dest[offset + NeuroFeatureSchema.AIM_VEL_Z] = 0.0f;
        }

        if (aimPoint != null) {
            prevAimPoint = aimPoint;
        }

        // Environment
        Vec3d lookVec = currentRotation.toVector();
        double range = 6.0;
        boolean onTarget = RaytraceUtil.rayTrace(lookVec, range, box);
        dest[offset + NeuroFeatureSchema.LINE_OF_SIGHT] = onTarget ? 1.0f : 0.0f;

        // targetVisible — упрощённо приравнивается к lineOfSight
        dest[offset + NeuroFeatureSchema.TARGET_VISIBLE] = onTarget ? 1.0f : 0.0f;

        // targetChanged
        dest[offset + NeuroFeatureSchema.TARGET_CHANGED] = targetChanged ? 1.0f : 0.0f;

        // Attack cooldown с детектора атаки. Берём переданного игрока, а не
        // mc.player: дамп-рекордер собирает фичи чужих игроков (у них
        // cooldown локально не сбрасывается — фича будет ~1.0, это нормально).
        float currentCooldown = player.getAttackCooldownProgress(0.5f);
        dest[offset + NeuroFeatureSchema.ATTACK_COOLDOWN] = currentCooldown;

        prevAttackCooldown = currentCooldown;
    }
}
