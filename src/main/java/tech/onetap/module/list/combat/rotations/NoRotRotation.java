package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.MoveFixMode;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

/**
 * Ротация "NoRot" (updateLonyJirRotation).
 */
public class NoRotRotation extends RotationMode {

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;

        double time = System.nanoTime() * 1e-9;
        var angle = new Rotation(RotationUtil.calculate(target.getBoundingBox().getCenter().add(0, (float) Math.abs(Math.sin(time * 19)) / 2, 0)));
        var predict = PredictUtils.getPredicted(target, ka.predictValue.getValue() + 2.5f);

        if (target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive) angle = new Rotation(predict);

        if (!RaytraceUtil.rayTrace(mc.player.getRotationVector(), 999, target.getBoundingBox().expand(-0.2f))) {
            ka.speedAcceleration += (float) Math.abs(Math.sin(time * 19)) / 666;
        } else {
            if (ka.speedAcceleration >= 0.02f)
                ka.speedAcceleration -= 0.02f;
        }

        var deltaYaw = MathHelper.wrapDegrees(angle.getYaw() - ka.lastYaw);
        var deltaPitch = angle.getPitch() - ka.lastPitch;

        var smooth = Math.min(Math.max(ka.speedAcceleration, 0), 0.2f);

        var newYaw = ka.lastYaw + deltaYaw * smooth;
        var newPitch = ka.lastPitch + deltaPitch * (smooth / 3);

        newYaw -= (newYaw - ka.lastYaw) % GCDFixer.getGCDValue();
        newPitch -= (newPitch - ka.lastPitch) % GCDFixer.getGCDValue();

        var smoothRot = new Rotation(newYaw, newPitch);

        if (mc.player.isGliding() && target.isGliding())
            RotationComponent.update(smoothRot, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");
        ka.lastYaw = smoothRot.getYaw();
        ka.lastPitch = smoothRot.getPitch();
    }
}
