package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationHelper;

/**
 * HelixWave: база сходится к цели на 68% за тик, поверх — спиральные осцилляции
 * (двойная синусоида по времени). Камера двигается шагом от текущего угла,
 * а не снапом к абсолютному углу.
 */
public class Sloth2Rotation extends RotationMode implements IMinecraft {

    @Override
    public void update(KillAura ka, LivingEntity target) {
        if (mc.player == null || target == null) return;

        Vec3d point = aimPoint(ka, target);
        var base = RotationUtil.calculate(mc.player.getEyePos(), point);
        float targetYaw = base.x;
        float targetPitch = MathHelper.clamp(base.y, -89.9F, 89.9F);

        // Текущие углы — реальная камера, а не внутреннее состояние,
        // иначе RotationComponent снапнет камеру к абсолютному углу за тик
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float diffYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float diffPitch = targetPitch - currentPitch;

        float baseYaw = currentYaw + diffYaw * 0.68F;
        float basePitch = currentPitch + diffPitch * 0.68F;

        double now = System.currentTimeMillis() / 1000.0;
        float helixYaw = MathHelper.sin((float) (now * 18.0)) * 14.0F + MathHelper.cos((float) (now * 9.0)) * 6.0F;
        float helixPitch = MathHelper.cos((float) (now * 18.0)) * 10.0F + MathHelper.sin((float) (now * 9.0)) * 5.0F;

        Rotation rot = new Rotation(baseYaw + helixYaw, MathHelper.clamp(basePitch + helixPitch, -90.0F, 90.0F));
        RotationHelper.apply(rot, ka);

        ka.lastYaw = rot.getYaw();
        ka.lastPitch = rot.getPitch();
    }

    private Vec3d aimPoint(KillAura ka, LivingEntity target) {
        if (target.isGliding() && ka.isElytraPredictActive() && !ka.isTurnaroundActive) {
            return PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }

        Box box = target.getBoundingBox();
        Vec3d point = new Vec3d(
                (box.minX + box.maxX) * 0.5,
                box.minY + (box.maxY - box.minY) * 0.6,
                (box.minZ + box.maxZ) * 0.5
        );

        return ka.resolveMultipoint(target, point, ka.distance.getValue() + 1.0);
    }
}
