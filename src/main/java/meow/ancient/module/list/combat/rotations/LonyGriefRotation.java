package meow.ancient.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import meow.ancient.module.list.combat.KillAura;
import meow.ancient.util.math.BestPoint;
import meow.ancient.util.math.RotationUtil;
import meow.ancient.util.player.combat.PredictUtils;
import meow.ancient.util.player.combat.RaytraceUtil;
import meow.ancient.util.render.math.GCDFixer;
import meow.ancient.util.rotation.MoveFixMode;
import meow.ancient.util.rotation.Rotation;
import meow.ancient.util.rotation.RotationComponent;

public class LonyGriefRotation extends RotationMode {

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;

        Vec3d point = target.isGliding() && ka.isElytraPredictActive() && !ka.isTurnaroundActive ? PredictUtils.getPredicted(target, ka.predictValue.getValue()) : ka.resolveMultipoint(target, BestPoint.getPoint(target), 6);

        var angle = new Rotation(RotationUtil.calculate(point));
        float targetYaw = angle.getYaw();
        float targetPitch = angle.getPitch();

        if (!ka.back) {
            float pon = mc.player.isGliding() ? 1.35f : 1f;
            ka.speedAcceleration += (Math.abs(MathHelper.wrapDegrees(targetYaw - ka.lastYaw)) > 40 ? 0.005f / pon : 0.0038f / pon);

            boolean isLooking = RaytraceUtil.rayTrace(mc.player.getRotationVector(), 6, target.getBoundingBox().expand(-0.2, -0.3, -0.2));
            if (ka.speedAcceleration >= 0.16f / pon || isLooking) {
                ka.back = true;
            }
        } else {
            if (ka.speedAcceleration >= -0.01f) {
                ka.speedAcceleration -= (Math.abs(MathHelper.wrapDegrees(targetYaw - ka.lastYaw)) > 60 ? 0.06f : 0.01f);
            }
            if (ka.speedAcceleration <= -0.01f) {
                ka.back = false;
            }
        }

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - ka.lastYaw);
        float deltaPitch = targetPitch - ka.lastPitch;
        float smooth = Math.max(ka.speedAcceleration, 0);

        float newYaw = ka.lastYaw + deltaYaw * Math.min(Math.max(smooth, 0), 1);
        float newPitch = ka.lastPitch + deltaPitch * Math.min(Math.max(smooth / 2, 0), 1);

        float gcdValue = GCDFixer.getGCDValue();
        newYaw -= (newYaw - ka.lastYaw) % gcdValue;
        newPitch -= (newPitch - ka.lastPitch) % gcdValue;

        var smoothRot = new Rotation(newYaw, newPitch);
        RotationComponent.update(smoothRot, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), ka.otvodkaActive());

        ka.lastYaw = smoothRot.getYaw();
        ka.lastPitch = smoothRot.getPitch();
    }
}
