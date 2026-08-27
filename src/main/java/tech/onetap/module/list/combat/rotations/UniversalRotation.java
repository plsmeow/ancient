package tech.onetap.module.list.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationHelper;

public class UniversalRotation extends RotationMode {

    private boolean init = false;
    private LivingEntity lastTarget = null;
    private int lastPlanAge = Integer.MIN_VALUE;

    private float planYaw = 0f;
    private float planPitch = 0f;
    private float residualYaw = 0f;
    private float residualPitch = 0f;
    private float stepYaw = 0f;
    private float stepPitch = 0f;

    private float noiseYaw1 = 0f;
    private float noiseYaw2 = 0f;
    private float noisePitch1 = 0f;
    private float noisePitch2 = 0f;
    private float noiseA1Yaw = 0f;
    private float noiseA2Yaw = 0f;
    private float noiseA1Pitch = 0f;
    private float noiseA2Pitch = 0f;

    private float gainNoiseYaw = 0f;
    private float gainNoisePitch = 0f;

    private float velYaw = 0f;
    private float velPitch = 0f;
    private boolean hasPrevTarget = false;
    private float prevTargetYaw = 0f;
    private float prevTargetPitch = 0f;

    private boolean hasLag = false;
    private float lagYaw = 0f;
    private float lagPitch = 0f;
    private int lagTicks = 0;

    private int stopTicks = 0;
    private int stopCooldown = 0;
    private int overTicks = 0;
    private float overDelta = 0f;
    private int reactionTicks = 0;
    private int refractoryTicks = 0;
    private int flickCooldown = 0;

    private float lagLerp = 0.30f;
    private float velLerp = 0.65f;
    private float feedForward = 0.20f;
    private float inertiaYaw = 0.28f;
    private float inertiaPitch = 0.20f;
    private float noiseSigmaYaw = 0.45f;
    private float noiseSigmaPitch = 0.90f;
    private float gainYaw = 0.95f;
    private float gainPitch = 0.65f;
    private float gainRho = 0.30f;
    private float gainSigma = 0.10f;
    private float deliverFraction = 0.35f;
    private float stopChance = 0.035f;
    private float stopMinError = 4.0f;
    private float overMinError = 25.0f;
    private float overChance = 0.10f;
    private float speedCapYaw = 26.0f;
    private float speedCapPitch = 10.0f;
    private float trackRateYaw = 28.0f;
    private float trackRatePitch = 11.0f;
    private float flickMinError = 65.0f;
    private float flickRateYaw = 110.0f;
    private float flickCapYaw = 105.0f;

    private double aimOffsetX = 0.0;
    private double aimOffsetY = 0.0;
    private double aimOffsetZ = 0.0;
    private double aimOffsetTargetX = 0.0;
    private double aimOffsetTargetZ = 0.0;
    private long nextAimOffsetUpdate = 0L;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        if (!init) {
            init = true;
            pickPersonality(r);
            lastPlanAge = Integer.MIN_VALUE;
        }

        if (target != lastTarget) {
            lastTarget = target;
            onTargetChanged(ka, target, now, r);
        }

        int age = mc.player.age;
        if (age != lastPlanAge) {
            lastPlanAge = age;
            if (target != null) {
                plan(ka, target, now, r);
                deliver(ka);
            } else {
                // Нет цели — не вмешиваемся в камеру, сбрасываем накопленные шаги
                planYaw = 0f;
                planPitch = 0f;
                residualYaw = 0f;
                residualPitch = 0f;
                stepYaw = 0f;
                stepPitch = 0f;
                velYaw = 0f;
                velPitch = 0f;
                stopTicks = 0;
                overTicks = 0;
                reactionTicks = 0;
                refractoryTicks = 0;
                flickCooldown = 0;
            }
        }
    }

    private void onTargetChanged(KillAura ka, LivingEntity target, long now, ThreadLocalRandom r) {
        planYaw = 0f;
        planPitch = 0f;
        stepYaw = 0f;
        stepPitch = 0f;
        noiseYaw1 = 0f;
        noiseYaw2 = 0f;
        noisePitch1 = 0f;
        noisePitch2 = 0f;
        gainNoiseYaw = 0f;
        gainNoisePitch = 0f;
        velYaw = 0f;
        velPitch = 0f;
        hasPrevTarget = false;
        hasLag = false;
        lagTicks = 0;
        stopTicks = 0;
        stopCooldown = 0;
        overTicks = 0;
        overDelta = 0f;
        refractoryTicks = 0;
        flickCooldown = 0;
        reactionTicks = target != null ? 1 + r.nextInt(3) : 0;

        pickPersonality(r);

        if (target != null) {
            pickNewOffsets(target, r);
            aimOffsetX = aimOffsetTargetX;
            aimOffsetZ = aimOffsetTargetZ;
            nextAimOffsetUpdate = now + r.nextLong(2500L, 5000L);
        }
    }

    private void pickPersonality(ThreadLocalRandom r) {
        float style = r.nextFloat();

        lagLerp = r.nextFloat(0.30f, 0.50f);
        velLerp = r.nextFloat(0.55f, 0.75f);
        feedForward = r.nextFloat(0.12f, 0.30f);
        inertiaYaw = r.nextFloat(lerp(0.14f, 0.02f, style), lerp(0.24f, 0.10f, style));
        inertiaPitch = r.nextFloat(0.06f, 0.20f);
        noiseSigmaYaw = r.nextFloat(lerp(0.50f, 1.00f, style), lerp(1.10f, 1.80f, style));
        noiseSigmaPitch = r.nextFloat(0.80f, 1.50f);
        gainYaw = r.nextFloat(0.85f, 1.05f);
        gainPitch = r.nextFloat(0.58f, 0.80f);
        gainRho = r.nextFloat(0.20f, 0.40f);
        gainSigma = r.nextFloat(0.06f, 0.14f);
        deliverFraction = r.nextFloat(0.30f, 0.40f);
        stopChance = r.nextFloat(0.025f, 0.050f);
        stopMinError = r.nextFloat(3.0f, 5.0f);
        overMinError = r.nextFloat(20.0f, 30.0f);
        overChance = r.nextFloat(0.06f, 0.14f);
        speedCapYaw = r.nextFloat(22.0f, 32.0f);
        speedCapPitch = r.nextFloat(8.0f, 12.0f);
        trackRateYaw = r.nextFloat(24.0f, 32.0f);
        trackRatePitch = r.nextFloat(9.0f, 13.0f);
        flickMinError = r.nextFloat(55.0f, 75.0f);
        flickRateYaw = r.nextFloat(90.0f, 130.0f);
        flickCapYaw = r.nextFloat(85.0f, 125.0f);

        float rhoYaw = r.nextFloat(0.75f, 0.90f);
        float periodYaw = r.nextFloat(lerp(4.5f, 2.8f, style), lerp(6.5f, 4.2f, style));
        noiseA1Yaw = (float) (2.0 * rhoYaw * Math.cos(2.0 * Math.PI / periodYaw));
        noiseA2Yaw = -rhoYaw * rhoYaw;

        float rhoPitch = r.nextFloat(0.45f, 0.70f);
        float periodPitch = r.nextFloat(3.5f, 6.0f);
        noiseA1Pitch = (float) (2.0 * rhoPitch * Math.cos(2.0 * Math.PI / periodPitch));
        noiseA2Pitch = -rhoPitch * rhoPitch;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private void plan(KillAura ka, LivingEntity target, long now, ThreadLocalRandom r) {
        var mc = ka.mc;

        if (now >= nextAimOffsetUpdate) {
            pickNewOffsets(target, r);
            nextAimOffsetUpdate = now + r.nextLong(2500L, 5000L);
        }
        aimOffsetX = MathHelper.lerp(0.12, aimOffsetX, aimOffsetTargetX);
        aimOffsetZ = MathHelper.lerp(0.12, aimOffsetZ, aimOffsetTargetZ);

        Vec3d aimPoint = getAimPoint(ka, target);
        if (target.isGliding() && ka.isElytraPredictActive() && !ka.isTurnaroundActive) {
            Vec3d predicted = PredictUtils.getPredicted(target, ka.predictValue.getValue());
            aimPoint = aimPoint.lerp(predicted, 0.5f);
        }

        Rotation aimRotation = RotationHelper.calculateRotation(aimPoint);
        float targetYaw = aimRotation.getYaw();
        float targetPitch = aimRotation.getPitch();

        if (!hasPrevTarget) {
            hasPrevTarget = true;
            prevTargetYaw = targetYaw;
            prevTargetPitch = targetPitch;
        }
        float rawVelYaw = clampAbs(RotationHelper.angleDelta(prevTargetYaw, targetYaw), trackRateYaw);
        float rawVelPitch = clampAbs(targetPitch - prevTargetPitch, trackRatePitch);
        prevTargetYaw = targetYaw;
        prevTargetPitch = targetPitch;
        velYaw = MathHelper.lerp(velLerp, velYaw, rawVelYaw);
        velPitch = MathHelper.lerp(velLerp, velPitch, rawVelPitch);

        if (reactionTicks > 0) {
            reactionTicks--;
            hasLag = false;
            return;
        }

        boolean flick = flickCooldown <= 0
                && Math.abs(RotationHelper.angleDelta(mc.player.getYaw(), targetYaw)) > flickMinError;
        if (flickCooldown > 0) flickCooldown--;

        if (!hasLag || lagTicks <= 0) {
            hasLag = true;
            lagYaw = mc.player.getYaw();
            lagPitch = mc.player.getPitch();
            lagTicks = 1 + r.nextInt(3);
        }
        lagTicks--;
        float lagRateYaw = flick ? flickRateYaw : trackRateYaw;
        float lagRatePitch = flick ? trackRatePitch * 2.5f : trackRatePitch;
        lagYaw += clampAbs(RotationHelper.angleDelta(lagYaw, targetYaw) * lagLerp, lagRateYaw);
        lagPitch += clampAbs((targetPitch - lagPitch) * lagLerp, lagRatePitch);

        float noiseYaw = noiseA1Yaw * noiseYaw1 + noiseA2Yaw * noiseYaw2
                + (float) r.nextGaussian() * noiseSigmaYaw;
        noiseYaw2 = noiseYaw1;
        noiseYaw1 = noiseYaw;

        float noisePitch = noiseA1Pitch * noisePitch1 + noiseA2Pitch * noisePitch2
                + (float) r.nextGaussian() * noiseSigmaPitch;
        noisePitch2 = noisePitch1;
        noisePitch1 = noisePitch;

        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();
        float errorYaw = RotationHelper.angleDelta(curYaw, lagYaw + noiseYaw);
        float errorPitch = (lagPitch + noisePitch) - curPitch;
        float errorDist = (float) Math.hypot(errorYaw, errorPitch);

        if (stopCooldown > 0) stopCooldown--;
        if (stopTicks <= 0 && stopCooldown <= 0 && errorDist > stopMinError && r.nextFloat() < stopChance) {
            stopTicks = 1 + r.nextInt(2);
            stopCooldown = 5 + r.nextInt(7);
        }
        if (stopTicks > 0) {
            stopTicks--;
            stepYaw *= inertiaYaw;
            stepPitch *= inertiaPitch;
            return;
        }

        if (overTicks <= 0 && Math.abs(errorYaw) > overMinError && r.nextFloat() < overChance) {
            overTicks = 2 + r.nextInt(2);
            overDelta = Math.copySign(r.nextFloat(2f, 4f), errorYaw);
        }
        float overshoot = 0f;
        if (overTicks > 0) {
            overTicks--;
            overshoot = overDelta * (overTicks / 3f);
        }

        gainNoiseYaw = gainRho * gainNoiseYaw + (float) r.nextGaussian() * gainSigma;
        gainNoisePitch = gainRho * gainNoisePitch + (float) r.nextGaussian() * gainSigma;

        float wantYaw = (errorYaw + overshoot) * gainYaw * (float) Math.exp(gainNoiseYaw)
                + velYaw * feedForward;
        float wantPitch = errorPitch * gainPitch * (float) Math.exp(gainNoisePitch)
                + velPitch * feedForward;

        stepYaw = stepYaw * inertiaYaw + wantYaw * (1f - inertiaYaw);
        stepPitch = stepPitch * inertiaPitch + wantPitch * (1f - inertiaPitch);

        float capYaw = flick ? flickCapYaw : speedCapYaw;
        float capPitch = speedCapPitch * (flick ? 2.2f : 1f);
        if (!flick && refractoryTicks > 0) {
            refractoryTicks--;
            capYaw *= 0.35f;
            capPitch *= 0.45f;
        }

        float emitYaw = clampAbs(stepYaw, capYaw);
        float emitPitch = clampAbs(stepPitch, capPitch);
        if (flick && Math.abs(stepYaw) > speedCapYaw) {
            flickCooldown = 25 + r.nextInt(36);
        } else if (!flick && Math.abs(emitYaw) > speedCapYaw * 0.6f) {
            refractoryTicks = 1 + r.nextInt(2);
        }

        planYaw = clampAbs(planYaw + emitYaw, capYaw);
        planPitch = clampAbs(planPitch + emitPitch, capPitch);
    }

    private static float clampAbs(float value, float limit) {
        return Math.abs(value) > limit ? Math.copySign(limit, value) : value;
    }

    private void deliver(KillAura ka) {
        var mc = ka.mc;
        float gcd = GCDFixer.getGCDValue();
        if (gcd <= 0f) gcd = 0.15f;

        float portionYaw = planYaw * deliverFraction;
        float portionPitch = planPitch * deliverFraction;
        planYaw -= portionYaw;
        planPitch -= portionPitch;

        residualYaw += portionYaw;
        residualPitch += portionPitch;

        float newYaw = mc.player.getYaw();
        float newPitch = mc.player.getPitch();

        int stepsYaw = Math.round(residualYaw / gcd);
        if (stepsYaw != 0) {
            residualYaw -= stepsYaw * gcd;
            newYaw += stepsYaw * gcd;
        }

        int stepsPitch = Math.round(residualPitch / gcd);
        if (stepsPitch != 0) {
            residualPitch -= stepsPitch * gcd;
            newPitch = RotationHelper.clampPitch(newPitch + stepsPitch * gcd);
        }

        RotationHelper.apply(new Rotation(newYaw, newPitch), ka);
        ka.lastYaw = newYaw;
        ka.lastPitch = newPitch;
    }

    private Vec3d getAimPoint(KillAura ka, LivingEntity target) {
        Box box = target.getBoundingBox();
        double h = target.getHeight();
        double baseY = box.minY + h * (0.55 + aimOffsetY);

        return ka.resolveMultipoint(target, new Vec3d(
            target.getX() + aimOffsetX,
            baseY,
            target.getZ() + aimOffsetZ
        ), 6);
    }

    private void pickNewOffsets(LivingEntity target, ThreadLocalRandom r) {
        Box box = target.getBoundingBox();
        double halfW = (box.maxX - box.minX) * 0.3;
        double halfD = (box.maxZ - box.minZ) * 0.3;
        aimOffsetTargetX = (r.nextDouble() - 0.5) * 2.0 * halfW;
        aimOffsetTargetZ = (r.nextDouble() - 0.5) * 2.0 * halfD;
        aimOffsetY = r.nextDouble(0.0, 0.2);
    }

    @Override
    public void reset(KillAura ka) {
        init = false;
        lastTarget = null;
        lastPlanAge = Integer.MIN_VALUE;
        planYaw = 0f;
        planPitch = 0f;
        residualYaw = 0f;
        residualPitch = 0f;
        stepYaw = 0f;
        stepPitch = 0f;
        noiseYaw1 = 0f;
        noiseYaw2 = 0f;
        noisePitch1 = 0f;
        noisePitch2 = 0f;
        gainNoiseYaw = 0f;
        gainNoisePitch = 0f;
        velYaw = 0f;
        velPitch = 0f;
        hasPrevTarget = false;
        hasLag = false;
        lagTicks = 0;
        stopTicks = 0;
        stopCooldown = 0;
        overTicks = 0;
        overDelta = 0f;
        reactionTicks = 0;
        refractoryTicks = 0;
        flickCooldown = 0;
        aimOffsetX = 0.0;
        aimOffsetY = 0.0;
        aimOffsetZ = 0.0;
        aimOffsetTargetX = 0.0;
        aimOffsetTargetZ = 0.0;
        nextAimOffsetUpdate = 0L;
    }
}
