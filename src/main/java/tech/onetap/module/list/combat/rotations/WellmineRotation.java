package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.rotation.MoveFixMode;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Ротация "Wellmine old".
 */
public class WellmineRotation extends RotationMode {

    private final float RANDOM_STRENGTH = 0.75f;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;

        Box box = target.getBoundingBox();
        Vec3d vector = ka.resolveMultipoint(target, BestPoint.getMultipoint(target, 6), 6);

        if (target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive) {
            vector = PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }

        var angle = RotationUtil.calculate(vector);

        float targetYaw = angle.x;
        float targetPitch = angle.y;

        if (!ka.back) {
            if (ka.speedAcceleration >= 1f) {
                ka.speedAcceleration = 0 ;
            } else {
                if(mc.player.isGliding()){
                    float diff = Math.abs(MathHelper.wrapDegrees(angle.x - mc.player.getYaw()));
                    ka.speedAcceleration += (diff > 40 ? 0.0025f : 0.005f);
                }
                else{
                    ka.speedAcceleration += 0.005f ;
                }
            }

            Vec3d offset = Vec3d.ZERO;
            if (mc.player.isGliding() && target instanceof PlayerEntity && target.isGliding()) {
                offset = PredictUtils.getPredicted(target, ka.predictValue.getValue());
            }

            if (ka.speedAcceleration >= 0.18 || RaytraceUtil.rayTrace(mc.player.getRotationVector(), 6, box.offset(offset).expand(-0.5, -1, -0.5))) {
                ka.back = true;
            }
        } else {
            if (ka.speedAcceleration >= -0.01f) {
                float diff = Math.abs(MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()));
                ka.speedAcceleration -= (diff > 40 ? 0.04f : 0.01f);
            }
            if (ka.speedAcceleration <= -0.01f) ka.back = false;
        }

        float randomYaw = (float) ThreadLocalRandom.current().nextDouble(-RANDOM_STRENGTH, RANDOM_STRENGTH);
        float randomPitch = (float) ThreadLocalRandom.current().nextDouble(-RANDOM_STRENGTH, RANDOM_STRENGTH);

        targetYaw += randomYaw;
        targetPitch += randomPitch;

        float smoothVal = Math.min(Math.max(ka.speedAcceleration, -1), 1);

        float changeYaw = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()) * smoothVal;
        float changePitch = (targetPitch - mc.player.getPitch()) * (smoothVal / 2f);

        var smoothRot = new Rotation(
                mc.player.getYaw() + changeYaw,
                MathHelper.clamp(mc.player.getPitch() + changePitch, -90, 90)
        );

        RotationComponent.update(smoothRot, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");

        ka.lastYaw = smoothRot.getYaw();
        ka.lastPitch = smoothRot.getPitch();
    }
}
