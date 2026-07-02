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

public class FuntimeRotation extends RotationMode {

    private int ftimeHitCounter = 0;
    private long ftimeLastHitTime = 0;
    private boolean ftimeWasAttacking = false;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (target == null) return;

        Vec3d point = ka.resolveMultipoint(target, BestPoint.getPoint2(target), 6);
        if (target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive) {
            point = PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }

        var idealRot = new Rotation(RotationUtil.calculate(point));
        long nowMs = System.currentTimeMillis();
        boolean canAttack = mc.player.getAttackCooldownProgress(0.5f) >= 0.9f;

        float deltaYaw = MathHelper.wrapDegrees(idealRot.getYaw() - ka.lastYaw);
        float deltaPitch = idealRot.getPitch() - ka.lastPitch;
        float total = (float) Math.hypot(deltaYaw, deltaPitch);

        if (total < 0.001f) {
            var rot = new Rotation(ka.lastYaw, ka.lastPitch);
            RotationComponent.update(rot, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue());
            return;
        }

        float maxStepYaw = (Math.abs(deltaYaw) / total) * 130f;
        float maxStepPitch = (Math.abs(deltaPitch) / total) * 130f;

        float stepYaw = MathHelper.clamp(deltaYaw, -maxStepYaw, maxStepYaw);
        float stepPitch = MathHelper.clamp(deltaPitch, -maxStepPitch, maxStepPitch);

        float nextYaw = ka.lastYaw + stepYaw;
        float nextPitch = ka.lastPitch + stepPitch;

        boolean isNewHit = canAttack && !ftimeWasAttacking;
        if (isNewHit) {
            ftimeHitCounter++;
            ftimeLastHitTime = nowMs;
        }
        ftimeWasAttacking = canAttack;

        if (canAttack) {
            nextYaw = ka.lastYaw + 0.85f * (nextYaw - ka.lastYaw);
            nextPitch = ka.lastPitch + 0.85f * (nextPitch - ka.lastPitch);

            if (isNewHit && ftimeHitCounter % 86 == 0 && (nowMs - ftimeLastHitTime) < 250) {
                nextPitch = -90f;
            }
        } else {
            long sinceLastHit = nowMs - ftimeLastHitTime;

            if (sinceLastHit >= 535) {
                float shakeYaw = (18f + (float) Math.random() * 10f) * (float) Math.sin(nowMs / 60.0);
                float shakePitch = (6f + (float) Math.random() * 10f) * (float) Math.cos(nowMs / 60.0);

                nextYaw = MathHelper.clamp(ka.lastYaw + shakeYaw, ka.lastYaw - 45f, ka.lastYaw + 45f);
                nextPitch = MathHelper.clamp(ka.lastPitch + shakePitch, ka.lastPitch - 45f, ka.lastPitch + 45f);
            } else {
                nextYaw = ka.lastYaw;
                nextPitch = ka.lastPitch;
            }
        }

        nextPitch = MathHelper.clamp(nextPitch, -89f, 90f);

        float gcd = GCDFixer.getGCDValue();
        nextYaw = ka.lastYaw + Math.round((nextYaw - ka.lastYaw) / gcd) * gcd;
        nextPitch = ka.lastPitch + Math.round((nextPitch - ka.lastPitch) / gcd) * gcd;

        var funRot = new Rotation(nextYaw, nextPitch);
        RotationComponent.update(funRot, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue());

        ka.lastYaw = funRot.getYaw();
        ka.lastPitch = funRot.getPitch();
    }

    @Override
    public void reset(KillAura ka) {
        ftimeHitCounter = 0;
        ftimeLastHitTime = 0;
        ftimeWasAttacking = false;
    }
}
