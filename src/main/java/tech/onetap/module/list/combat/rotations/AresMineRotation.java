package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

public class AresMineRotation extends RotationMode implements IMinecraft {

    private LivingEntity trackedTarget;
    private float currentYaw;
    private float currentPitch;
    private float velocityYaw;
    private float velocityPitch;
    private double aimPointX;
    private double aimPointY;
    private double aimPointZ;
    private float noiseAngle;
    private final float noiseAmplitude = 1.8f;
    private int hitPhase;
    private int hitTimer;
    private float pitchBeforeHit;
    private long firstSeenTime;
    private int reactionMs;
    private boolean reactionComplete;
    private float lastSentYaw;
    private float lastSentPitch;
    private float smoothYaw;
    private float smoothPitch;

    @Override
    public void reset(KillAura ka) {
        trackedTarget = null;
        velocityYaw = velocityPitch = 0.0f;
        aimPointX = aimPointY = aimPointZ = 0.0;
        noiseAngle = 0.0f;
        hitPhase = hitTimer = 0;
        firstSeenTime = 0L;
        reactionComplete = false;
        reactionMs = 0;

        if (mc.player != null) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
            lastSentYaw = currentYaw;
            lastSentPitch = currentPitch;
            smoothYaw = currentYaw;
            smoothPitch = currentPitch;
        } else {
            currentYaw = currentPitch = 0.0f;
            lastSentYaw = lastSentPitch = 0.0f;
            smoothYaw = smoothPitch = 0.0f;
        }
    }

    private float calcGcd() {
        double s = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return (float) (s * s * s * 1.2);
    }

    private void pickAimPoint(LivingEntity entity) {
        Box bb = entity.getBoundingBox();
        double width = bb.maxX - bb.minX;
        double height = bb.maxY - bb.minY;
        double depth = bb.maxZ - bb.minZ;

        aimPointX = (Math.random() - 0.5) * width * 0.12;
        aimPointY = (Math.random() - 0.5) * height * 0.11;
        aimPointZ = (Math.random() - 0.5) * depth * 0.12;
    }

    public void onAttack() {
        hitPhase = 1;
        hitTimer = 0;
        pitchBeforeHit = currentPitch;
    }

    private float measureAngle(LivingEntity entity) {
        if (mc.player == null) return 0.0f;

        Vec3d eyes = mc.player.getEyePos();
        Vec3d mid = entity.getBoundingBox().getCenter();
        Vec3d delta = mid.subtract(eyes);

        float targetYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength())));

        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()));
        float pitchDiff = Math.abs(targetPitch - mc.player.getPitch());

        return yawDiff + pitchDiff;
    }

    private int computeReaction(float angle) {
        if (angle > 130.0f) {
            return 140 + (int) (Math.random() * 90.0);
        } else if (angle > 70.0f) {
            return 90 + (int) (Math.random() * 60.0);
        } else if (angle > 30.0f) {
            return 45 + (int) (Math.random() * 35.0);
        } else {
            return 12 + (int) (Math.random() * 20.0);
        }
    }

    private boolean isMovingForward() {
        return mc.player != null && mc.options.forwardKey.isPressed();
    }

    private boolean isOvertakingTarget(LivingEntity target) {
        if (mc.player == null || target == null) return false;

        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = target.getPos();

        Vec3d playerVel = new Vec3d(
                mc.player.getX() - mc.player.prevX,
                mc.player.getY() - mc.player.prevY,
                mc.player.getZ() - mc.player.prevZ
        );

        Vec3d targetVel = new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ
        );

        Vec3d toTarget = targetPos.subtract(playerPos).normalize();
        double playerSpeedToTarget = playerVel.dotProduct(toTarget);
        double targetSpeedToPlayer = targetVel.dotProduct(toTarget.multiply(-1.0));
        double relativeSpeed = playerSpeedToTarget + targetSpeedToPlayer;

        double distance = Math.sqrt(
                Math.pow(playerPos.x - targetPos.x, 2.0) +
                Math.pow(playerPos.z - targetPos.z, 2.0)
        );

        return relativeSpeed > 0.05 && distance < 4.0;
    }

    private float[] generateNoise(float distance) {
        noiseAngle = noiseAngle + (0.042f + (float) (Math.random() * 0.018f));
        float scale = MathHelper.clamp(distance / 4.5f, 0.25f, 1.0f);
        float amp = noiseAmplitude * scale;

        float n1 = (float) Math.sin(noiseAngle * 0.87) * 0.38f;
        float n2 = (float) Math.sin(noiseAngle * 1.43 + 0.75) * 0.28f;
        float n3 = (float) Math.cos(noiseAngle * 1.18 + 0.35) * 0.32f;
        float n4 = (float) Math.cos(noiseAngle * 1.76 + 1.42) * 0.23f;

        float yawNoise = (n1 + n2) * amp;
        float pitchNoise = (n3 + n4) * amp * 0.52f;

        yawNoise += ((float) Math.random() - 0.5f) * amp * 0.13f;
        pitchNoise += ((float) Math.random() - 0.5f) * amp * 0.09f;

        return new float[]{yawNoise, pitchNoise};
    }

    private float smoothStep(float x) {
        x = MathHelper.clamp(x, 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private float accelCurve(float x) {
        x = MathHelper.clamp(x, 0.0f, 1.0f);
        return 1.0f - (1.0f - x) * (1.0f - x);
    }

    private float springInterp(float current, float target, float velocity, float stiffness, float damping) {
        float diff = target - current;
        float acc = diff * stiffness - velocity * damping;
        return velocity + acc;
    }

    private float smoothLerp(float from, float to, float alpha) {
        alpha = MathHelper.clamp(alpha, 0.0f, 1.0f);
        float delta = MathHelper.wrapDegrees(to - from);
        return from + delta * alpha;
    }

    private float calculateCurrentAngle(float targetYaw, float targetPitch) {
        float dYaw = Math.abs(MathHelper.wrapDegrees(targetYaw - currentYaw));
        float dPitch = Math.abs(targetPitch - currentPitch);
        return dYaw + dPitch;
    }

    private Vec3d getPredictedPoint(KillAura ka, LivingEntity target, Vec3d center) {
        if (target.isGliding() && ka.predictate.getValue()) {
            return PredictUtils.getPredicted(target, ka.predictValue.getValue());
        }
        return center;
    }

    private boolean shouldUseElytraPredict(KillAura ka, LivingEntity target) {
        return target.isGliding() && ka.predictate.getValue();
    }

    @Override
    public void update(KillAura ka, LivingEntity target) {
        if (mc.player == null || target == null) return;

        boolean playerFlying = mc.player.isGliding();

        if (trackedTarget != target) {
            trackedTarget = target;
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
            lastSentYaw = currentYaw;
            lastSentPitch = currentPitch;
            smoothYaw = currentYaw;
            smoothPitch = currentPitch;
            velocityYaw = 0.0f;
            velocityPitch = 0.0f;
            pickAimPoint(target);
            hitPhase = 0;
            hitTimer = 0;
            noiseAngle = (float) (Math.random() * Math.PI * 2.0);
            float angleDiff = measureAngle(target);
            reactionMs = computeReaction(angleDiff);
            firstSeenTime = System.currentTimeMillis();
            reactionComplete = false;
        }

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetCenter = getPredictedPoint(ka, target, target.getBoundingBox().getCenter());
        float distance = (float) eyePos.distanceTo(targetCenter);
        float gcd = calcGcd();

        if (!reactionComplete) {
            long elapsed = System.currentTimeMillis() - firstSeenTime;
            if (elapsed < reactionMs) {
                float jitterY = ((float) Math.random() - 0.5f) * 0.22f;
                float jitterP = ((float) Math.random() - 0.5f) * 0.14f;

                float outY = lastSentYaw + jitterY;
                float outP = MathHelper.clamp(lastSentPitch + jitterP, -89.0f, 89.0f);

                outY -= (outY - lastSentYaw) % gcd;
                outP -= (outP - lastSentPitch) % gcd;

                lastSentYaw = outY;
                lastSentPitch = outP;

                ka.lastYaw = outY;
                ka.lastPitch = outP;
                RotationComponent.update(new Rotation(outY, outP), 360.0f, 45.0f, 45.0f, 45.0f, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");
                return;
            }

            reactionComplete = true;
        }

        float[] noise = generateNoise(distance);

        if (hitPhase > 0) {
            hitTimer++;

            int upDuration = 25;
            int downDuration = 20;
            float targetPitchUp = -89.0f;

            if (hitPhase == 1) {
                float t = (float) hitTimer / upDuration;
                t = MathHelper.clamp(t, 0.0f, 1.0f);
                float curved = accelCurve(t);

                currentPitch = MathHelper.lerp(curved, pitchBeforeHit, targetPitchUp);

                if (hitTimer >= upDuration) {
                    hitPhase = 2;
                    hitTimer = 0;
                }
            } else if (hitPhase == 2) {
                float goal = pitchBeforeHit;
                float t = (float) hitTimer / downDuration;
                t = MathHelper.clamp(t, 0.0f, 1.0f);
                float curved = smoothStep(t);

                currentPitch = MathHelper.lerp(curved, targetPitchUp, goal);

                if (hitTimer >= downDuration) {
                    hitPhase = 0;
                    hitTimer = 0;
                }
            }

            float outY = currentYaw + noise[0];
            float outP = MathHelper.clamp(currentPitch + noise[1], -89.0f, 89.0f);

            outY -= (outY - lastSentYaw) % gcd;
            outP -= (outP - lastSentPitch) % gcd;

            lastSentYaw = outY;
            lastSentPitch = outP;

            ka.lastYaw = outY;
            ka.lastPitch = outP;
            RotationComponent.update(new Rotation(outY, outP), 360.0f, 45.0f, 45.0f, 45.0f, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");
            return;
        }

        if (Math.random() < 0.015) {
            pickAimPoint(target);
        }

        Vec3d targetVel = new Vec3d(
                target.getX() - target.prevX,
                target.getY() - target.prevY,
                target.getZ() - target.prevZ
        );

        int predictTicks = shouldUseElytraPredict(ka, target) ? 0 : 2;
        Vec3d predictedCenter = targetCenter.add(targetVel.multiply(predictTicks));
        Vec3d aimPos = predictedCenter.add(aimPointX, aimPointY, aimPointZ);
        Vec3d direction = aimPos.subtract(eyePos);

        float wantYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
        float wantPitch = (float) (-Math.toDegrees(Math.atan2(direction.y, direction.horizontalLength())));

        float diffYaw = MathHelper.wrapDegrees(wantYaw - currentYaw);
        float diffPitch = wantPitch - currentPitch;

        float speedMultiplier = 1.0f;

        if (playerFlying) {
            float currentAngle = calculateCurrentAngle(wantYaw, wantPitch);

            if (currentAngle > 120.0f) {
                speedMultiplier = 0.18f;
            } else if (currentAngle > 80.0f) {
                float t = (currentAngle - 80.0f) / 40.0f;
                speedMultiplier = MathHelper.lerp(smoothStep(t), 0.35f, 0.18f);
            } else if (currentAngle > 25.0f) {
                float t = (currentAngle - 25.0f) / 55.0f;
                speedMultiplier = MathHelper.lerp(smoothStep(t), 0.65f, 0.35f);
            } else {
                speedMultiplier = 0.65f + 0.35f * (1.0f - currentAngle / 25.0f);
            }
        } else {
            boolean movingForward = isMovingForward();
            boolean overtaking = isOvertakingTarget(target);

            if (movingForward || overtaking) {
                speedMultiplier = 0.5f;
            }
        }

        float stiffness = (0.038f + (float) Math.random() * 0.009f) * speedMultiplier;
        float damping = 0.68f + 0.12f * (1.0f - speedMultiplier);

        float totalDiff = (float) Math.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);
        if (totalDiff > 32.0f) {
            stiffness += 0.018f * speedMultiplier;
        } else if (totalDiff < 4.2f) {
            stiffness *= 0.48f;
        }

        stiffness += MathHelper.clamp((distance - 1.6f) / 7.5f, 0.0f, 0.045f) * speedMultiplier;

        velocityYaw = springInterp(currentYaw, currentYaw + diffYaw, velocityYaw, stiffness, damping);
        velocityPitch = springInterp(currentPitch, wantPitch, velocityPitch, stiffness * 0.87f, damping);

        float maxVelYaw = 7.5f * speedMultiplier;
        float maxVelPitch = 5.8f * speedMultiplier;

        velocityYaw = MathHelper.clamp(velocityYaw, -maxVelYaw, maxVelYaw);
        velocityPitch = MathHelper.clamp(velocityPitch, -maxVelPitch, maxVelPitch);

        currentYaw += velocityYaw;
        currentPitch += velocityPitch;
        currentPitch = MathHelper.clamp(currentPitch, -89.0f, 89.0f);

        float smoothFactor = playerFlying ? 0.3f + speedMultiplier * 0.4f : 0.85f;
        smoothYaw = smoothLerp(smoothYaw, currentYaw, smoothFactor);
        smoothPitch = smoothLerp(smoothPitch, currentPitch, smoothFactor * 0.95f);

        float outY = smoothYaw + noise[0];
        float outP = smoothPitch + noise[1];
        outP = MathHelper.clamp(outP, -89.0f, 89.0f);

        outY -= (outY - lastSentYaw) % gcd;
        outP -= (outP - lastSentPitch) % gcd;

        lastSentYaw = outY;
        lastSentPitch = outP;

        ka.lastYaw = outY;
        ka.lastPitch = outP;
        RotationComponent.update(new Rotation(outY, outP), 360.0f, 45.0f, 45.0f, 45.0f, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");
    }
}
