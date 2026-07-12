package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.neuro.rotation.AIRotationFeatures;
import tech.onetap.util.neuro.rotation.AIRotationManager;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

public class NeuroRotation extends RotationMode {

    private float previousDeltaYaw;
    private float previousDeltaPitch;
    private int invalidPredictionTicks;

    private NoRotRotation noRotFallback = new NoRotRotation();

    @Override
    public void update(KillAura ka, LivingEntity target) {
        if (!AIRotationManager.hasModel()) {
            noRotFallback.update(ka, target);
            return;
        }

        var mc = ka.mc;
        if (target == null || mc.player == null) return;

        Vec3d point = ka.resolveMultipoint(target, BestPoint.getMultipoint(target, ka.distance.getValue()), ka.distance.getValue());
        Rotation targetRotation = new Rotation(RotationUtil.calculate(point));

        float currentYaw = ka.lastYaw == 0.0f && ka.lastPitch == 0.0f ? MathHelper.wrapDegrees(mc.player.getYaw()) : ka.lastYaw;
        float currentPitch = ka.lastPitch == 0.0f ? mc.player.getPitch() : ka.lastPitch;
        Rotation currentRotation = new Rotation(currentYaw, currentPitch);

        float[] input = AIRotationFeatures.buildInput(
                mc.player,
                target,
                currentRotation,
                targetRotation,
                new Rotation(currentYaw - previousDeltaYaw, currentPitch - previousDeltaPitch),
                point
        );

        float[] output = AIRotationManager.predict(input);
        float deltaYaw = sanitize(output[0], input[0], 75.0f);
        float deltaPitch = sanitize(output[1], input[1], 55.0f);

        float yawMul = (float) ka.neuroYawMultiplier.getValue();
        float pitchMul = (float) ka.neuroPitchMultiplier.getValue();
        deltaYaw *= yawMul;
        deltaPitch *= pitchMul;

        if (isStalled(deltaYaw, deltaPitch, input[0], input[1])) {
            invalidPredictionTicks++;
            if (invalidPredictionTicks > 8) {
                noRotFallback.update(ka, target);
                return;
            }
            float recovery = invalidPredictionTicks > 4 ? 0.55f : 0.25f;
            deltaYaw = MathHelper.clamp(input[0] * recovery, -45.0f, 45.0f);
            deltaPitch = MathHelper.clamp(input[1] * recovery, -30.0f, 30.0f);
        } else {
            invalidPredictionTicks = 0;
        }

        if (ka.neuroCorrection.getValue()) {
            float t = 0.15f;
            float sigmoid = 1.0f / (1.0f + (float) Math.exp(-0.5 * (t - 0.3)));
            float bezier = t * t * (3.0f - 2.0f * t);
            float blend = sigmoid * bezier;
            deltaYaw = MathHelper.lerp(blend, deltaYaw, input[0]);
            deltaPitch = MathHelper.lerp(blend, deltaPitch, input[1]);
        }

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
}
