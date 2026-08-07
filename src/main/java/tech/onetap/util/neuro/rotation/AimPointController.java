package tech.onetap.util.neuro.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.BestPoint;

/**
 * Детерминированный контроллер точки прицеливания.
 * Не трогает static-поля BestPoint — держит своё состояние на инстанс.
 * Реализует плавное движение точки с ограничением скорости и ускорения.
 */
public class AimPointController implements AimPointProvider {

    private Vec3d currentPoint = Vec3d.ZERO;
    private Vec3d currentVelocity = Vec3d.ZERO;
    private LivingEntity lastTarget = null;

    private static final double MAX_AIM_VELOCITY = 0.04;  // блоков за тик
    private static final double MAX_AIM_ACCELERATION = 0.015;

    @Override
    public Vec3d update(KillAura ka, LivingEntity target, boolean targetChanged) {
        if (target == null) {
            reset();
            return Vec3d.ZERO;
        }

        // При смене цели сбрасываем состояние
        if (targetChanged || target != lastTarget) {
            currentPoint = target.getPos().add(0, target.getHeight() * 0.65, 0);
            currentVelocity = Vec3d.ZERO;
            lastTarget = target;
        }

        // Получаем желаемую точку через существующую логику
        double range = ka.distance.getValue();
        Vec3d desiredWorld = BestPoint.getMultipoint(target, range);

        // Применяем smartAim visibility check через ka.resolveMultipoint
        desiredWorld = ka.resolveMultipoint(target, desiredWorld, range);

        // Вычисляем желаемое изменение
        Vec3d desiredDelta = desiredWorld.subtract(currentPoint);
        double desiredSpeed = desiredDelta.length();

        if (desiredSpeed < 1e-6) {
            // Уже на месте
            currentPoint = desiredWorld;
            currentVelocity = Vec3d.ZERO;
            return currentPoint;
        }

        Vec3d desiredDirection = desiredDelta.normalize();
        Vec3d desiredVelocity = desiredDirection.multiply(Math.min(desiredSpeed, MAX_AIM_VELOCITY));

        // Ограничиваем ускорение
        Vec3d acceleration = desiredVelocity.subtract(currentVelocity);
        double accelMagnitude = acceleration.length();

        if (accelMagnitude > MAX_AIM_ACCELERATION) {
            acceleration = acceleration.normalize().multiply(MAX_AIM_ACCELERATION);
        }

        currentVelocity = currentVelocity.add(acceleration);

        // Применяем скорость
        currentPoint = currentPoint.add(currentVelocity);

        // Убеждаемся, что не вышли за пределы хитбокса
        currentPoint = clampToHitbox(target, currentPoint);

        return currentPoint;
    }

    @Override
    public void reset() {
        currentPoint = Vec3d.ZERO;
        currentVelocity = Vec3d.ZERO;
        lastTarget = null;
    }

    /**
     * Ограничивает точку границами хитбокса с небольшим margin.
     */
    private Vec3d clampToHitbox(LivingEntity target, Vec3d point) {
        var box = target.getBoundingBox();
        double margin = 0.05;

        return new Vec3d(
                MathHelper.clamp(point.x, box.minX + margin, box.maxX - margin),
                MathHelper.clamp(point.y, box.minY + margin, box.maxY - margin),
                MathHelper.clamp(point.z, box.minZ + margin, box.maxZ - margin)
        );
    }
}
