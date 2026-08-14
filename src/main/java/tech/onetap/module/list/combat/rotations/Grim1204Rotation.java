package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.packet.NetworkUtils;
import tech.onetap.util.rotation.Rotation;

import static net.minecraft.util.math.MathHelper.wrapDegrees;

/**
 * Grim 1.20.4 — копия ротации Grim из ThunderHack-Recode
 * Отправляет пакеты ротации перед/после атаки, не применяет к клиенту
 */
public class Grim1204Rotation extends RotationMode {

    private float rotationYaw;
    private float rotationPitch;
    private float pitchAcceleration = 1f;

    // Настройки из ThunderHack Advanced
    private static final int MIN_YAW_STEP = 65;
    private static final int MAX_YAW_STEP = 75;
    private static final float AIMED_PITCH_STEP = 1f;
    private static final float MAX_PITCH_STEP = 8f;
    private static final float PITCH_ACCELERATE = 1.65f;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        if (target == null) {
            if (ka.mc.player != null) {
                rotationYaw = ka.mc.player.getYaw();
                rotationPitch = ka.mc.player.getPitch();
            }
            return;
        }

        // Используем оптимальную точку на хитбоксе
        Vec3d targetVec = target.isGliding()
                ? target.getEyePos()
                : ka.resolveMultipoint(target, BestPoint.getNearestPoint(target), ka.distance.getValue());

        // Плавная ротация с ускорением как в ThunderHack
        calcRotations(ka, target, targetVec);
    }

    private void calcRotations(KillAura ka, LivingEntity target, Vec3d targetVec) {
        if (ka.mc.player == null) return;

        // Проверка ray trace для адаптации скорости pitch (как в ThunderHack)
        boolean lookingAtTarget = checkRayTrace(ka, target);
        pitchAcceleration = lookingAtTarget
                ? AIMED_PITCH_STEP
                : (pitchAcceleration < MAX_PITCH_STEP ? pitchAcceleration * PITCH_ACCELERATE : MAX_PITCH_STEP);

        // Расчёт целевых углов
        float targetYaw = (float) wrapDegrees(Math.toDegrees(Math.atan2(
                targetVec.z - ka.mc.player.getZ(),
                targetVec.x - ka.mc.player.getX()
        )) - 90);

        float targetPitch = (float) (-Math.toDegrees(Math.atan2(
                targetVec.y - (ka.mc.player.getPos().y + ka.mc.player.getEyeHeight(ka.mc.player.getPose())),
                Math.sqrt(Math.pow(targetVec.x - ka.mc.player.getX(), 2) + Math.pow(targetVec.z - ka.mc.player.getZ(), 2))
        )));

        // Дельты с учётом wrap
        float deltaYaw = wrapDegrees(targetYaw - rotationYaw);
        float deltaPitch = targetPitch - rotationPitch;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float yawStep = random.nextFloat(MIN_YAW_STEP, MAX_YAW_STEP);
        float pitchStep = pitchAcceleration + random.nextFloat(-1.0F, 1.0F);

        // Ограничение дельт
        deltaYaw = MathHelper.clamp(deltaYaw, -yawStep, yawStep);
        deltaPitch = MathHelper.clamp(deltaPitch, -pitchStep, pitchStep);

        // Применение с GCD fix (как в ThunderHack)
        float newYaw = rotationYaw + deltaYaw;
        float newPitch = MathHelper.clamp(rotationPitch + deltaPitch, -90.0F, 90.0F);

        double gcdFix = Math.pow(ka.mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2, 3.0) * 1.2;

        rotationYaw = (float) (newYaw - (newYaw - rotationYaw) % gcdFix);
        rotationPitch = (float) (newPitch - (newPitch - rotationPitch) % gcdFix);
    }

    private boolean checkRayTrace(KillAura ka, LivingEntity target) {
        if (ka.mc.player == null || ka.mc.world == null) return false;

        double range = ka.distance.getValue();
        Vec3d start = ka.mc.player.getPos().add(0.0, ka.mc.player.getEyeHeight(ka.mc.player.getPose()), 0.0);
        Vec3d rotationVector = getRotationVector(rotationYaw, rotationPitch);
        Vec3d end = start.add(rotationVector.multiply(range));
        HitResult blockHit = ka.mc.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                ka.mc.player
        ));

        double maxDistanceSquared = blockHit == null
                ? range * range
                : start.squaredDistanceTo(blockHit.getPos());
        maxDistanceSquared = Math.max(maxDistanceSquared, range * range);

        Box entityArea = ka.mc.player.getBoundingBox().stretch(rotationVector).expand(1.0, 1.0, 1.0);
        EntityHitResult entityHit = ProjectileUtil.raycast(
                ka.mc.player,
                start,
                end,
                entityArea,
                entity -> !entity.isSpectator() && entity.canHit() && entity == target,
                maxDistanceSquared
        );

        return entityHit != null && start.squaredDistanceTo(entityHit.getPos()) <= range * range;
    }

    private Vec3d getRotationVector(float yaw, float pitch) {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d(i * j, -k, h * j);
    }

    /**
     * Отправить пакет ротации перед атакой (как preAttack в ThunderHack)
     */
    public void sendRotationPacket(KillAura ka) {
        if (ka.mc.player == null) return;
        
        NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.Full(
                ka.mc.player.getX(),
                ka.mc.player.getY(),
                ka.mc.player.getZ(),
                rotationYaw,
                rotationPitch,
                ka.mc.player.isOnGround(),
                ka.mc.player.horizontalCollision
        ));
    }

    /**
     * Отправить пакет сброса ротации после атаки (как postAttack в ThunderHack)
     */
    public void sendResetPacket(KillAura ka) {
        if (ka.mc.player == null) return;
        
        NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.Full(
                ka.mc.player.getX(),
                ka.mc.player.getY(),
                ka.mc.player.getZ(),
                ka.mc.player.getYaw(),
                ka.mc.player.getPitch(),
                ka.mc.player.isOnGround(),
                ka.mc.player.horizontalCollision
        ));
    }

    public float getRotationYaw() {
        return rotationYaw;
    }

    public float getRotationPitch() {
        return rotationPitch;
    }

    @Override
    public void reset(KillAura killAura) {
        if (killAura.mc.player != null) {
            rotationYaw = killAura.mc.player.getYaw();
            rotationPitch = killAura.mc.player.getPitch();
        }
        pitchAcceleration = 1f;
    }
}
