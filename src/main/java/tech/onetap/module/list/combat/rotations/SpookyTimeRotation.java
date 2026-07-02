package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.security.SecureRandom;

public class SpookyTimeRotation extends RotationMode {

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (target == null || mc.player == null) return;

        Vec3d point = ka.resolveMultipoint(target, BestPoint.getPoint2(target), 6);
        if (target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive) {
            point = PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }

        var idealRot = new Rotation(RotationUtil.calculate(point));

        float yawDelta = MathHelper.wrapDegrees(idealRot.getYaw() - ka.lastYaw);
        float pitchDelta = idealRot.getPitch() - ka.lastPitch;
        float rotationDifference = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));

        if (rotationDifference < 0.0001f) return;

        boolean canAttack = mc.player.getAttackCooldownProgress(0.5f) >= 0.9f;
        SecureRandom secureRandom = new SecureRandom();
        float speed = canAttack ? 1.0F : (secureRandom.nextBoolean() ? 0.5F : 0.2F);

        float lineYaw   = Math.abs(yawDelta   / rotationDifference) * 190.0F;
        float linePitch = Math.abs(pitchDelta / rotationDifference) * 190.0F;
        float moveYaw   = MathHelper.clamp(yawDelta,   -lineYaw,   lineYaw);
        float movePitch = MathHelper.clamp(pitchDelta, -linePitch, linePitch);

        float lerpVal = MathHelper.lerp(secureRandom.nextFloat(), speed, speed + 0.4F);

        float nextYaw   = MathHelper.lerp(lerpVal, ka.lastYaw,   ka.lastYaw   + moveYaw);
        float nextPitch = MathHelper.lerp(lerpVal, ka.lastPitch, ka.lastPitch + movePitch);

        // applyFunTimeOffsets (SpookyAnka)
        float partialTicks = mc.getRenderTickCounter().getTickDelta(false);
        float age = mc.player.age + partialTicks;

        final float circleSpeed      = 2.0F;
        final float circleRadius     = 2.3F;
        final float shakeIntensity   = 0.5F;
        final float shakeDistance    = 14.0F;

        float circlePhase       = age * circleSpeed;
        float circleYawOffset   = MathHelper.sin(circlePhase) * circleRadius;
        float circlePitchOffset = MathHelper.cos(circlePhase) * circleRadius * 0.5F;

        float shakePhase  = age * shakeIntensity;
        float shakeOffset = MathHelper.sin(shakePhase) * shakeDistance;

        nextYaw   = nextYaw   + circleYawOffset + shakeOffset;
        nextPitch = MathHelper.clamp(nextPitch + circlePitchOffset, -90.0F, 90.0F);

        float gcd = GCDFixer.getGCDValue();
        nextYaw   = ka.lastYaw   + Math.round((nextYaw   - ka.lastYaw)   / gcd) * gcd;
        nextPitch = ka.lastPitch + Math.round((nextPitch - ka.lastPitch) / gcd) * gcd;

        var rot = new Rotation(nextYaw, nextPitch);
        RotationComponent.update(rot, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue());

        ka.lastYaw   = rot.getYaw();
        ka.lastPitch = rot.getPitch();
    }
}
