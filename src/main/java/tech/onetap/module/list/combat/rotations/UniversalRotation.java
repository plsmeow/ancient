package tech.onetap.module.list.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.player.combat.RaytraceUtil;
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
    private int holdTicks = 0;

    private float lagLerp = 0.55f;
    private float velLerp = 0.65f;
    private float feedForward = 0.25f;
    private float inertiaYaw = 0.28f;
    private float inertiaPitch = 0.20f;
    private float noiseSigmaYaw = 0.45f;
    private float noiseSigmaPitch = 0.90f;
    private float gainRho = 0.30f;
    private float gainSigma = 0.10f;
    private float stopChance = 0.035f;
    private float stopMinError = 4.0f;
    private float overMinError = 25.0f;
    private float overChance = 0.10f;
    private float trackRateYaw = 40.0f;
    private float trackRatePitch = 15.0f;
    private float flickMinError = 65.0f;
    private float flickRateYaw = 110.0f;
    private float flickCapYaw = 105.0f;

    private float speedKYaw = 0.52f;
    private float speedKPitch = 0.42f;
    private float minSpeedYaw = 1.6f;
    private float minSpeedPitch = 1.1f;
    private float capMinYaw = 4.0f;
    private float capMinPitch = 2.6f;
    private float capMaxYaw = 65.0f;
    private float capMaxPitch = 30.0f;
    private float rampNearYaw = 8.0f;
    private float rampNearPitch = 6.0f;
    private float rampFarYaw = 48.0f;
    private float rampFarPitch = 30.0f;
    private float finishZoneYaw = 2.4f;
    private float finishZonePitch = 1.8f;
    private float flickSpeedK = 0.75f;
    private float holdShiftChance = 0.15f;
    private int holdShiftDelay = 3;
    private float holdShiftYaw = 1.0f;
    private float holdShiftPitch = 0.5f;
    private float holdJitterYaw = 0.06f;
    private float holdJitterPitch = 0.04f;

    private double aimOffsetX = 0.0;
    private double aimOffsetY = 0.0;
    private double aimOffsetZ = 0.0;
    private double aimOffsetTargetX = 0.0;
    private double aimOffsetTargetZ = 0.0;
    private long nextAimOffsetUpdate = 0L;

    // Сегмент кривой траектории: каждое крупное движение идёт по своей дуге
    private boolean hasSegment = false;
    private float segStartDist = 0f;
    private float segProgress = 0f;
    private float arcDirYaw = 0f;
    private float arcDirPitch = 0f;
    private float arcMag = 0f;
    private float arcPeak = 0.5f;
    private boolean arcSShape = false;
    private float arcSkew = 1f;

    // Тик следующей смены «характера» движения
    private int personalityRefreshAt = 0;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (!init) {
            init = true;
            pickPersonality(r);
            personalityRefreshAt = mc.player.age + 300 + r.nextInt(600);
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
                holdTicks = 0;
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
        holdTicks = 0;
        hasSegment = false;
        segStartDist = 0f;
        segProgress = 0f;
        arcMag = 0f;
        personalityRefreshAt = ka.mc.player.age + 300 + r.nextInt(600);
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

        lagLerp = r.nextFloat(0.45f, 0.65f);
        velLerp = r.nextFloat(0.55f, 0.75f);
        feedForward = r.nextFloat(0.15f, 0.35f);
        inertiaYaw = r.nextFloat(lerp(0.14f, 0.02f, style), lerp(0.24f, 0.10f, style));
        inertiaPitch = r.nextFloat(0.06f, 0.20f);
        noiseSigmaYaw = r.nextFloat(lerp(0.50f, 1.00f, style), lerp(1.10f, 1.80f, style));
        noiseSigmaPitch = r.nextFloat(0.80f, 1.50f);
        gainRho = r.nextFloat(0.20f, 0.40f);
        gainSigma = r.nextFloat(0.06f, 0.14f);
        stopChance = r.nextFloat(0.025f, 0.050f);
        stopMinError = r.nextFloat(3.0f, 5.0f);
        overMinError = r.nextFloat(20.0f, 30.0f);
        overChance = r.nextFloat(0.06f, 0.14f);
        trackRateYaw = r.nextFloat(34.0f, 48.0f);
        trackRatePitch = r.nextFloat(13.0f, 17.0f);
        flickMinError = r.nextFloat(55.0f, 75.0f);
        flickRateYaw = r.nextFloat(90.0f, 130.0f);
        flickCapYaw = r.nextFloat(85.0f, 125.0f);

        speedKYaw = r.nextFloat(0.45f, 0.60f);
        speedKPitch = r.nextFloat(0.35f, 0.50f);
        minSpeedYaw = r.nextFloat(1.2f, 2.0f);
        minSpeedPitch = r.nextFloat(0.8f, 1.4f);
        capMinYaw = r.nextFloat(3.0f, 5.0f);
        capMinPitch = r.nextFloat(2.0f, 3.2f);
        capMaxYaw = r.nextFloat(55.0f, 80.0f);
        capMaxPitch = r.nextFloat(25.0f, 38.0f);
        rampNearYaw = r.nextFloat(6.0f, 10.0f);
        rampNearPitch = r.nextFloat(4.0f, 7.0f);
        rampFarYaw = r.nextFloat(40.0f, 55.0f);
        rampFarPitch = r.nextFloat(24.0f, 36.0f);
        finishZoneYaw = r.nextFloat(1.8f, 3.0f);
        finishZonePitch = r.nextFloat(1.2f, 2.2f);
        flickSpeedK = r.nextFloat(0.65f, 0.85f);

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

    private void applyCustom(KillAura ka) {
        if (!ka.universalCustom.getValue()) return;
        speedKYaw = ka.universalSpeedYaw.getFloatValue();
        speedKPitch = ka.universalSpeedPitch.getFloatValue();
        minSpeedYaw = ka.universalMinSpeedYaw.getFloatValue();
        minSpeedPitch = ka.universalMinSpeedPitch.getFloatValue();
        capMinYaw = ka.universalCapMinYaw.getFloatValue();
        capMinPitch = ka.universalCapMinPitch.getFloatValue();
        capMaxYaw = ka.universalCapMaxYaw.getFloatValue();
        capMaxPitch = ka.universalCapMaxPitch.getFloatValue();
        rampNearYaw = ka.universalRampNearYaw.getFloatValue();
        rampNearPitch = ka.universalRampNearPitch.getFloatValue();
        rampFarYaw = Math.max(rampNearYaw + 1f, ka.universalRampFarYaw.getFloatValue());
        rampFarPitch = Math.max(rampNearPitch + 1f, ka.universalRampFarPitch.getFloatValue());
        finishZoneYaw = ka.universalFinishZoneYaw.getFloatValue();
        finishZonePitch = ka.universalFinishZonePitch.getFloatValue();
        lagLerp = ka.universalLagLerp.getFloatValue();
        feedForward = ka.universalFeedForward.getFloatValue();
        velLerp = ka.universalVelLerp.getFloatValue();
        inertiaYaw = ka.universalInertiaYaw.getFloatValue();
        inertiaPitch = ka.universalInertiaPitch.getFloatValue();
        noiseSigmaYaw = ka.universalNoiseYaw.getFloatValue();
        noiseSigmaPitch = ka.universalNoisePitch.getFloatValue();
        gainRho = ka.universalGainRho.getFloatValue();
        gainSigma = ka.universalGainSigma.getFloatValue();
        stopChance = ka.universalStopChance.getFloatValue();
        stopMinError = ka.universalStopMinError.getFloatValue();
        overChance = ka.universalOverChance.getFloatValue();
        overMinError = ka.universalOverMinError.getFloatValue();
        trackRateYaw = ka.universalTrackYaw.getFloatValue();
        trackRatePitch = ka.universalTrackPitch.getFloatValue();
        flickMinError = ka.universalFlickMin.getFloatValue();
        flickRateYaw = ka.universalFlickRate.getFloatValue();
        flickCapYaw = ka.universalFlickCap.getFloatValue();
        flickSpeedK = ka.universalFlickSpeed.getFloatValue();
        holdShiftChance = ka.universalHoldShiftChance.getFloatValue();
        holdShiftDelay = ka.universalHoldShiftDelay.getIntValue();
        holdShiftYaw = ka.universalHoldShiftYaw.getFloatValue();
        holdShiftPitch = ka.universalHoldShiftPitch.getFloatValue();
        holdJitterYaw = ka.universalHoldJitterYaw.getFloatValue();
        holdJitterPitch = ka.universalHoldJitterPitch.getFloatValue();
    }

    private void plan(KillAura ka, LivingEntity target, long now, ThreadLocalRandom r) {
        var mc = ka.mc;
        applyCustom(ka);

        // Периодическая смена «характера» между движениями: скорость, тремор,
        // инерция со временем дрейфуют — долгий бой не выглядит зацикленным.
        // Со своими значениями параметры закреплены пользователем — не трогаем.
        if (!ka.universalCustom.getValue() && !hasSegment && mc.player.age >= personalityRefreshAt) {
            pickPersonality(r);
            personalityRefreshAt = mc.player.age + 300 + r.nextInt(600);
        }

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
        float targetPitch = clampPitchSpan(ka, target, aimPoint, aimRotation.getPitch());

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

        boolean preHit = ka.universalFinishHit.getValue()
                && ka.ticksToAttack <= 0
                && mc.player.getAttackCooldownProgress(0.5f) >= 0.9f
                && mc.player.getEyePos().distanceTo(BestPoint.getNearestPoint(target)) <= ka.distance.getValue() + 1.5;
        if (preHit) {
            Vec3d hitPoint = ka.resolveMultipoint(target, BestPoint.getPoint2(target), 6);
            Rotation hitRot = RotationHelper.calculateRotation(hitPoint);
            float finSpeed = ka.universalFinishHitSpeed.getFloatValue();
            planYaw = clampAbs(RotationHelper.angleDelta(mc.player.getYaw(), hitRot.getYaw()), finSpeed);
            planPitch = clampAbs(clampPitchSpan(ka, target, hitPoint, hitRot.getPitch()) - mc.player.getPitch(), finSpeed);
            stepYaw = planYaw;
            stepPitch = planPitch;
            return;
        }

        if (reactionTicks > 0) {
            reactionTicks--;
            hasLag = false;
            planYaw = 0f;
            planPitch = 0f;
            stepYaw = 0f;
            stepPitch = 0f;
            return;
        }

        boolean onTarget = RaytraceUtil.rayTrace(mc.player.getRotationVector(), 999.0, target.getBoundingBox());
        if (onTarget) {
            holdTicks++;
            stepYaw *= inertiaYaw;
            stepPitch *= inertiaPitch;
            planYaw = (r.nextFloat() - 0.5f) * 2f * holdJitterYaw;
            planPitch = (r.nextFloat() - 0.5f) * 2f * holdJitterPitch;
            if (holdTicks > holdShiftDelay && r.nextFloat() < holdShiftChance) {
                planYaw += (r.nextFloat() - 0.5f) * 2f * holdShiftYaw;
                planPitch += (r.nextFloat() - 0.5f) * 2f * holdShiftPitch;
            }
            hasLag = false;
            return;
        }
        holdTicks = 0;

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
        float directYaw = RotationHelper.angleDelta(curYaw, lagYaw + noiseYaw);
        float directPitch = (lagPitch + noisePitch) - curPitch;
        float directDist = (float) Math.hypot(directYaw, directPitch);
        // Дуга: виртуальная цель смещается в сторону от прямой и возвращается к концу
        // движения — камера идёт по кривой, а не по лучу к цели.
        updateArcSegment(ka, r, directYaw, directPitch, directDist);
        float arcScale = hasSegment ? arcHump(segProgress) * arcMag : 0f;
        float errorYaw = directYaw + arcDirYaw * arcScale;
        float errorPitch = directPitch + arcDirPitch * arcScale;
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
            planYaw = clampAbs(stepYaw, capMinYaw);
            planPitch = clampAbs(stepPitch, capMinPitch);
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

        boolean finishYaw = Math.abs(errorYaw) < finishZoneYaw;
        boolean finishPitch = Math.abs(errorPitch) < finishZonePitch;

        float kYaw = flick ? flickSpeedK : speedKYaw;
        float kPitch = flick ? Math.min(1f, speedKPitch * 1.6f) : speedKPitch;
        float wantYaw = dynamicStep(errorYaw, kYaw, minSpeedYaw, capMinYaw, capMaxYaw, rampNearYaw, rampFarYaw, finishZoneYaw);
        float wantPitch = dynamicStep(errorPitch, kPitch, minSpeedPitch, capMinPitch, capMaxPitch, rampNearPitch, rampFarPitch, finishZonePitch);
        if (!finishYaw) {
            wantYaw = wantYaw * (float) Math.exp(gainNoiseYaw) + velYaw * feedForward + overshoot * kYaw;
        }
        if (!finishPitch) {
            wantPitch = wantPitch * (float) Math.exp(gainNoisePitch) + velPitch * feedForward;
        }

        stepYaw = finishYaw ? wantYaw : MathHelper.lerp(inertiaYaw, stepYaw, wantYaw);
        stepPitch = finishPitch ? wantPitch : MathHelper.lerp(inertiaPitch, stepPitch, wantPitch);

        float capYaw = dynamicCap(Math.abs(errorYaw), capMinYaw,
                flick ? Math.max(flickCapYaw, capMaxYaw) : capMaxYaw, rampNearYaw, rampFarYaw);
        float capPitch = dynamicCap(Math.abs(errorPitch), capMinPitch,
                flick ? capMaxPitch * 2.2f : capMaxPitch, rampNearPitch, rampFarPitch);
        if (!flick && refractoryTicks > 0) {
            refractoryTicks--;
            capYaw *= 0.45f;
            capPitch *= 0.55f;
        }

        float emitYaw = clampAbs(stepYaw, capYaw);
        float emitPitch = clampAbs(stepPitch, capPitch);
        if (flick && Math.abs(emitYaw) > capMaxYaw * 0.8f) {
            flickCooldown = 25 + r.nextInt(36);
        } else if (!flick && Math.abs(emitYaw) > capMaxYaw * 0.7f) {
            refractoryTicks = 1 + r.nextInt(2);
        }

        planYaw = emitYaw;
        planPitch = emitPitch;
    }

    /**
     * Жизненный цикл дуги: сегмент живёт, пока идёт крупное движение к цели.
     * Дошли до зоны доводки — сегмент закрывается, следующее движение получит
     * новую случайную дугу. Ошибка резко выросла (цель ушла/флик) — сегмент
     * перерождается с новой кривой.
     */
    private void updateArcSegment(KillAura ka, ThreadLocalRandom r, float errYaw, float errPitch,
                                  float errDist) {
        float strengthScale = ka.universalCurveStrength.getFloatValue();
        boolean curveOn = ka.universalCurve.getValue() && strengthScale > 0f;

        if (!curveOn) {
            hasSegment = false;
            return;
        }

        if (hasSegment) {
            if (errDist > segStartDist * 1.3f + 3f) {
                // Цель резко ушла — старая дуга неактуальна, начинаем новую
                hasSegment = false;
            } else if (errDist < Math.max(finishZoneYaw, finishZonePitch)) {
                // Дошли до зоны доводки — сегмент завершён
                hasSegment = false;
                return;
            } else {
                float t = 1f - errDist / segStartDist;
                segProgress = Math.max(segProgress, MathHelper.clamp(t, 0f, 1f));
                return;
            }
        }

        // Дуга включается только на заметных движениях: мелкое слежение
        // за стрейфящей целью остаётся на шуме и лаге.
        if (errDist > 6f) {
            startArcSegment(r, errYaw, errPitch, errDist, strengthScale);
        }
    }

    /** Новая случайная дуга: сторона, сила, форма и перекос не повторяются. */
    private void startArcSegment(ThreadLocalRandom r, float errYaw, float errPitch,
                                 float errDist, float strengthScale) {
        hasSegment = true;
        segStartDist = Math.max(errDist, 1.0E-3f);
        segProgress = 0f;

        // Сила изгиба — доля от начальной ошибки с абсолютным потолком;
        // изредка почти прямое движение, чтобы «всегда кривая» сама
        // по себе не стала паттерном.
        float roll = r.nextFloat();
        float frac = roll < 0.15f ? r.nextFloat(0.02f, 0.08f) : r.nextFloat(0.10f, 0.42f);
        arcMag = Math.min(frac * segStartDist, r.nextFloat(3.5f, 9.0f)) * strengthScale;

        // Направление: перпендикуляр к прямому пути + случайный наклон,
        // сторона изгиба случайна.
        float len = (float) Math.hypot(errYaw, errPitch);
        float pathYaw = len < 1.0E-4f ? 1f : errYaw / len;
        float pathPitch = len < 1.0E-4f ? 0f : errPitch / len;
        float tilt = (r.nextFloat() - 0.5f) * 0.6f;
        float side = r.nextBoolean() ? 1f : -1f;
        arcDirYaw = side * (-pathPitch + tilt * pathYaw);
        arcDirPitch = side * (pathYaw + tilt * pathPitch);

        // Форма горба: асимметричный пик или S-кривая со своим перекосом.
        arcPeak = r.nextFloat(0.25f, 0.75f);
        arcSShape = r.nextFloat() < 0.25f;
        arcSkew = r.nextFloat(0.6f, 1.7f);
    }

    /** Профиль дуги: 0 в начале и в конце движения, посередине — отклонение. */
    private float arcHump(float t) {
        if (arcSShape) {
            return (float) Math.sin(2.0 * Math.PI * Math.pow(t, arcSkew));
        }
        if (t < arcPeak) {
            return (float) Math.sin(0.5 * Math.PI * (t / arcPeak));
        }
        return (float) Math.sin(0.5 * Math.PI * (1f - (t - arcPeak) / (1f - arcPeak)));
    }

    private static float clampAbs(float value, float limit) {
        return Math.abs(value) > limit ? Math.copySign(limit, value) : value;
    }

    private float clampPitchSpan(KillAura ka, LivingEntity target, Vec3d point, float pitch) {
        if (!ka.universalYawTrack.getValue()) return pitch;
        var mc = ka.mc;
        Vec3d eye = mc.player.getEyePos();
        double dist = eye.distanceTo(point);
        float threshold = ka.universalYawTrackDistance.getFloatValue();
        if (dist <= threshold) return pitch;
        float blend = RotationHelper.smoothStep((float) ((dist - threshold) / 0.7));
        if (blend <= 0f) return pitch;

        Box box = target.getBoundingBox();
        double dx = point.x - eye.x;
        double dz = point.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0E-4) return pitch;
        float pitchTop = (float) -Math.toDegrees(Math.atan2(box.maxY - eye.y, horiz));
        float pitchBottom = (float) -Math.toDegrees(Math.atan2(box.minY - eye.y, horiz));
        float desired = MathHelper.clamp(mc.player.getPitch(), pitchTop, pitchBottom);
        return lerp(pitch, desired, blend);
    }

    private static float dynamicCap(float absError, float capMin, float capMax, float rampNear, float rampFar) {
        float t = MathHelper.clamp((absError - rampNear) / Math.max(1f, rampFar - rampNear), 0f, 1f);
        return lerp(capMin, capMax, RotationHelper.smoothStep(t));
    }

    private static float dynamicStep(float error, float speedK, float minSpeed, float capMin,
                                     float capMax, float rampNear, float rampFar, float finishZone) {
        float absError = Math.abs(error);
        if (absError < finishZone) return error;
        float cap = dynamicCap(absError, capMin, capMax, rampNear, rampFar);
        return Math.copySign(MathHelper.clamp(absError * speedK, minSpeed, cap), error);
    }

    private void deliver(KillAura ka) {
        var mc = ka.mc;
        float gcd = GCDFixer.getGCDValue();
        if (gcd <= 0f) gcd = 0.15f;

        residualYaw += planYaw;
        residualPitch += planPitch;
        planYaw = 0f;
        planPitch = 0f;

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
        holdTicks = 0;
        aimOffsetX = 0.0;
        aimOffsetY = 0.0;
        aimOffsetZ = 0.0;
        aimOffsetTargetX = 0.0;
        aimOffsetTargetZ = 0.0;
        nextAimOffsetUpdate = 0L;
        hasSegment = false;
        segStartDist = 0f;
        segProgress = 0f;
        arcDirYaw = 0f;
        arcDirPitch = 0f;
        arcMag = 0f;
        personalityRefreshAt = 0;
    }
}
