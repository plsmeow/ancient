package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.neuro.rotation.AIRotationFeatures;
import tech.onetap.util.neuro.rotation.AIRotationManager;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

public class NeuroRotation extends RotationMode {

    private float previousDeltaYaw;
    private float previousDeltaPitch;
    private int invalidPredictionTicks;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (target == null || mc.player == null) return;

        Vec3d point = ka.resolveMultipoint(target, BestPoint.getMultipoint(target, ka.distance.getValue()), ka.distance.getValue());
        if (target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive) {
            point = PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }

        Rotation targetRotation = new Rotation(RotationUtil.calculate(point));
        float currentYaw = ka.lastYaw == 0.0f && ka.lastPitch == 0.0f ? MathHelper.wrapDegrees(mc.player.getYaw()) : ka.lastYaw;
        float currentPitch = ka.lastYaw == 0.0f && ka.lastPitch == 0.0f ? mc.player.getPitch() : ka.lastPitch;
        Rotation currentRotation = new Rotation(currentYaw, currentPitch);

        float[] input = AIRotationFeatures.buildInput(
                mc.player,
                target,
                currentRotation,
                targetRotation,
                new Rotation(currentYaw - previousDeltaYaw, currentPitch - previousDeltaPitch)
        );

        float deltaYaw = 0.0f;
        float deltaPitch = 0.0f;

        if (AIRotationManager.hasModel()) {
            float[] output = AIRotationManager.predict(input);
            deltaYaw = sanitize(output[0], input[0], 75.0f);
            deltaPitch = sanitize(output[1], input[1], 55.0f);
        }

        if (!AIRotationManager.hasModel() || isStalled(deltaYaw, deltaPitch, input[0], input[1])) {
            invalidPredictionTicks++;
            float recovery = invalidPredictionTicks > 4 ? 0.55f : 0.25f;
            deltaYaw = MathHelper.clamp(input[0] * recovery, -45.0f, 45.0f);
            deltaPitch = MathHelper.clamp(input[1] * recovery, -30.0f, 30.0f);
        } else {
            invalidPredictionTicks = 0;
        }

        deltaYaw = avoidOvershoot(deltaYaw, input[0]);
        deltaPitch = avoidOvershoot(deltaPitch, input[1]);

        float gcd = GCDFixer.getGCDValue();
        float nextYaw = currentYaw + Math.round(deltaYaw / gcd) * gcd;
        float nextPitch = currentPitch + Math.round(deltaPitch / gcd) * gcd;
        nextPitch = MathHelper.clamp(nextPitch, -89.0f, 90.0f);

        Rotation rotation = new Rotation(nextYaw, nextPitch);
        RotationComponent.update(rotation, 360, 360, 360, 360, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");

        ka.lastYaw = rotation.getYaw();
        ka.lastPitch = rotation.getPitch();
        previousDeltaYaw = MathHelper.wrapDegrees(rotation.getYaw() - currentYaw);
        previousDeltaPitch = rotation.getPitch() - currentPitch;
    }

    @Override
    public void reset(KillAura ka) {
        previousDeltaYaw = 0.0f;
        previousDeltaPitch = 0.0f;
        invalidPredictionTicks = 0;
    }

    private float sanitize(float value, float fallback, float limit) {
        if (!Float.isFinite(value)) {
            return MathHelper.clamp(fallback, -limit, limit);
        }

        return MathHelper.clamp(value, -limit, limit);
    }

    private boolean isStalled(float deltaYaw, float deltaPitch, float targetDeltaYaw, float targetDeltaPitch) {
        return (Math.abs(targetDeltaYaw) > 3.0f || Math.abs(targetDeltaPitch) > 1.5f)
                && Math.abs(deltaYaw) < 0.01f
                && Math.abs(deltaPitch) < 0.01f;
    }

    private float avoidOvershoot(float delta, float remaining) {
        if (Math.signum(delta) == Math.signum(remaining) && Math.abs(delta) > Math.abs(remaining)) {
            return remaining;
        }
        return delta;
    }
}
