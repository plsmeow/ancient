package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class Sloth2Rotation extends RotationMode implements IMinecraft {

    private final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    private LivingEntity trackedTarget;
    private boolean initialized;
    private int sessionSeed;
    private float sessionYawAmp;
    private float sessionPitchAmp;
    private float sessionInertia;
    private float sessionFov;
    private float sessionEngageFov;
    private float sessionSoftFov;
    private float sessionResponse;
    private float jitterYaw;
    private float jitterPitch;
    private float jitterTargetYaw;
    private float jitterTargetPitch;
    private long nextJitterTime;
    private float biasYaw;
    private float biasPitch;
    private long nextBiasTime;
    private float brownianYaw;
    private float brownianPitch;
    private float wanderYaw;
    private float wanderPitch;
    private int wanderTicks;
    private float lookAwayYaw;
    private float lookAwayPitch;
    private int lookAwayTicks;
    private int lookAwayCd;
    private float smoothYawStep;
    private float smoothPitchStep;
    private float lastOutputYaw;
    private float lastOutputPitch;
    private float speed;
    private int overshootPhase;
    private float overshootYaw;
    private float overshootPitch;
    private int overshootTicks;
    private int midJerkPhase;
    private int midJerkTicks;
    private int midJerkReturnTicks;
    private int midJerkCd;
    private float midJerkYaw;
    private float midJerkPitch;
    private float midJerkStartError;
    private boolean midJerkArmed;
    private int midJerkChain;
    private int midJerkPassMask;
    private int lastJerkMode;
    private float lastJerkDesiredYaw;
    private float lastJerkDesiredPitch;
    private float trackJerkYaw;
    private float trackJerkPitch;
    private int trackJerkTicks;
    private int trackJerkCd;
    private int idleTicks;
    private int skipTicks;
    private int combatTicks;
    private int tick;
    private Vec3d stableAim;
    private Vec3d attackPoint;
    private int pointSwitchTicks;
    private int samePointHits;
    private int postHitTicks;
    private int attackRandTicks;
    private float attackRandMul;
    private float microJitterYaw;
    private float microJitterPitch;
    private long nextMicroBurst;
    private int targetSwitchTicks;
    private int targetSwitchDuration;
    private float switchEase = 1.0f;
    private float lastYaw;
    private float lastPitch;
    private float lastCloseAirBlend;
    private Vec3d fixedCloseLocal;
    private boolean elytraAimActive;

    public void onAttack() {
        jitterYaw *= 0.4f + rnd.nextFloat() * 0.12f;
        jitterPitch *= 0.4f + rnd.nextFloat() * 0.12f;
        biasYaw *= 0.3f + rnd.nextFloat() * 0.14f;
        biasPitch *= 0.3f + rnd.nextFloat() * 0.14f;
        microJitterYaw *= 0.35f + rnd.nextFloat() * 0.1f;
        microJitterPitch *= 0.35f + rnd.nextFloat() * 0.1f;
        postHitTicks = 6 + rnd.nextInt(8);
        if (rnd.nextFloat() < 0.22f) {
            pointSwitchTicks = 0;
            attackPoint = null;
            stableAim = null;
        } else {
            pointSwitchTicks = Math.max(pointSwitchTicks, 5 + rnd.nextInt(5));
        }
        samePointHits++;
        idleTicks = rnd.nextFloat() < 0.38f ? 1 : 0;
        attackRandMul = 0.86f + rnd.nextFloat() * 0.28f;
        attackRandTicks = 5 + rnd.nextInt(7);
        lastOutputYaw *= 0.48f + rnd.nextFloat() * 0.12f;
        lastOutputPitch *= 0.44f + rnd.nextFloat() * 0.1f;
        smoothYawStep *= 0.54f + rnd.nextFloat() * 0.1f;
        smoothPitchStep *= 0.5f + rnd.nextFloat() * 0.1f;
        if (samePointHits >= 3 + rnd.nextInt(4)) {
            samePointHits = 0;
            if (rnd.nextFloat() < 0.48f) startLookAway(0.36f + rnd.nextFloat() * 0.18f);
            pickWanderOffset();
        }
        if (rnd.nextFloat() < 0.14f && lastCloseAirBlend < 0.55f) startOvershoot();
        if (lastCloseAirBlend < 0.65f && rnd.nextFloat() < 0.12f) {
            startTrackJerk(rnd.nextInt(10), 0.72f + rnd.nextFloat() * 0.18f);
            trackJerkCd = 0;
        }
    }

    @Override
    public void update(KillAura ka, LivingEntity target) {
        if (mc.player == null || target == null) return;

        boolean readyToAttack = mc.player.getAttackCooldownProgress(0.5f) >= 0.98f && ka.ticksToAttack <= 0;
        if (trackedTarget != target) {
            trackedTarget = target;
            initialized = false;
            stableAim = null;
            attackPoint = null;
            fixedCloseLocal = null;
            pointSwitchTicks = 0;
            samePointHits = 0;
            postHitTicks = 0;
            combatTicks = 0;
            tick = 0;
            initSession();
            float switchFov = calculateFovFromCamera(target);
            targetSwitchTicks = targetSwitchDuration = switchFov > sessionSoftFov ? 10 + rnd.nextInt(5) : 7 + rnd.nextInt(4);
            switchEase = 0.0f;
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
            softenMotionOnSwitch();
        }
        if (!initialized) initialized = true;

        tick++;
        combatTicks++;
        updateSwitchEase();
        if (postHitTicks > 0) postHitTicks--;

        float fov = calculateFovFromCamera(target);
        boolean playerElytra = mc.player.isGliding();
        float maxFov = playerElytra ? Math.max(sessionFov, 90.0f) : sessionFov;
        if (fov > maxFov) return;

        float fovFactor = computeFovLegitFactor(fov, readyToAttack);
        if (playerElytra && fov > sessionFov) {
            float span = Math.max(1.0f, maxFov - sessionFov);
            fovFactor = Math.max(fovFactor, MathHelper.clamp(1.0f - (fov - sessionFov) / span, 0.4f, 1.0f));
        }
        if (fovFactor <= 0.02f && !playerElytra) return;

        boolean combatZone = readyToAttack && fov <= sessionSoftFov;
        float effectiveFovFactor = combatZone ? Math.max(fovFactor, fov <= sessionEngageFov ? 1.0f : 0.84f) : fovFactor;
        if (attackRandTicks > 0) attackRandTicks--;
        else attackRandMul = MathHelper.lerp(0.1f, attackRandMul, 1.0f);
        if (skipTicks > 0) {
            skipTicks--;
            if (!readyToAttack) return;
        }
        if (idleTicks > 0 && !readyToAttack) {
            idleTicks--;
            return;
        }
        if (idleTicks > 0) idleTicks--;

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        Vec3d eye = mc.player.getEyePos();
        double dist = eye.distanceTo(target.getPos());
        float press = (float) MathHelper.clamp((2.8 - dist) / 1.4, 0.0, 1.0);
        boolean pressed = press > 0.42f;
        boolean gliding = mc.player.isGliding() || target.isGliding();
        boolean airborne = !gliding && (!mc.player.isOnGround() || mc.player.fallDistance > 0.05f || !target.isOnGround() || Math.abs(mc.player.getVelocity().y) > 0.08);
        boolean closeCombat = dist < 1.42 || pressed;
        boolean jumpCombo = airborne && closeCombat;
        float closeAirBlend = lastCloseAirBlend = jumpCombo ? 1.0f : (airborne ? 0.58f : (closeCombat ? 0.4f : 0.0f));
        boolean onTarget = RaytraceUtil.rayTrace(new Rotation(currentYaw, currentPitch).toVector(), Math.max(dist, 2.5) + 0.6, target.getBoundingBox());
        double targetSpeedSq = target.getVelocity().lengthSquared();
        boolean movingTarget = targetSpeedSq > 0.003;
        boolean ultraClose = dist < 0.4 && !gliding;
        boolean closeMoving = (closeCombat || dist < 1.42) && movingTarget && !gliding && !jumpCombo;
        if (!ultraClose || movingTarget) fixedCloseLocal = null;

        boolean chasing = movingTarget && (!onTarget || readyToAttack) && switchEase > 0.52f && fov <= sessionSoftFov + 8.0f && closeAirBlend < 0.72f && (!ultraClose || movingTarget);
        float engage = smoothEngage(readyToAttack, onTarget, pressed, dist, movingTarget);
        engage *= readyToAttack ? MathHelper.lerp(0.92f, 1.0f, effectiveFovFactor) : effectiveFovFactor;
        if (switchEase < 1.0f) engage *= MathHelper.lerp(0.7f, 1.0f, switchEase);

        updateLookAway(engage, onTarget, chasing || jumpCombo || ultraClose && !movingTarget);
        updateWander(engage, chasing || jumpCombo || ultraClose && !movingTarget);
        updateBrownian(chasing || jumpCombo || ultraClose && !movingTarget);
        updateOvershoot();

        boolean useElytraAim = playerElytra;
        if (useElytraAim && !elytraAimActive) {
            stableAim = null;
            attackPoint = null;
            pointSwitchTicks = 0;
        } else if (!useElytraAim && elytraAimActive) {
            stableAim = null;
        }
        elytraAimActive = useElytraAim;

        Vec3d aimPoint = useElytraAim ? resolveElytraAimPoint(ka, target, eye, dist) : (ultraClose && !movingTarget ? resolveFixedCloseAim(target) : resolveAimPoint(target, dist, press, engage, onTarget, readyToAttack, movingTarget, closeAirBlend));
        double predictScale = gliding ? (mc.player.isGliding() ? 1.15 : 0.38) : (readyToAttack ? (movingTarget ? 0.62 : 0.48) : (movingTarget ? 0.28 : 0.18));
        predictScale *= MathHelper.lerp(combatZone ? 0.72f : 0.42f, 1.0f, switchEase);
        predictScale *= MathHelper.lerp(1.0f, 0.26f, closeAirBlend);
        if (targetSpeedSq > 0.002 && !useElytraAim) {
            Vec3d motion = target.getVelocity();
            if (closeAirBlend > 0.0f) motion = new Vec3d(motion.x, motion.y * MathHelper.lerp(1.0f, 0.18f, closeAirBlend), motion.z);
            if (!ultraClose || movingTarget) aimPoint = aimPoint.add(motion.multiply(predictScale));
            if (movingTarget && readyToAttack && switchEase > 0.62f && closeAirBlend < 0.85f) aimPoint = aimPoint.add(motion.multiply(0.26 * Math.max(switchEase, effectiveFovFactor)));
        }

        Vec2f baseAngle = RotationUtil.calculate(eye, aimPoint);
        float targetYaw = baseAngle.x;
        float targetPitch = MathHelper.clamp(baseAngle.y, -89.0f, 89.0f);
        refreshBias(onTarget, engage, dist, readyToAttack, closeAirBlend);
        updateJitter(onTarget, engage, dist, currentYaw, currentPitch, targetYaw, targetPitch, readyToAttack, closeAirBlend);
        updateMicroJitter(onTarget, engage, readyToAttack, closeAirBlend);

        float jitterScale = readyToAttack ? (onTarget ? 0.32f : (chasing ? 0.42f : 0.52f)) : (chasing ? 0.82f : 1.0f);
        jitterScale *= MathHelper.lerp(combatZone ? 0.82f : 0.62f, 1.0f, effectiveFovFactor);
        jitterScale *= MathHelper.lerp(1.0f, 1.22f, closeAirBlend * 0.55f);
        if (onTarget && readyToAttack) jitterScale *= 0.68f;
        float decorScale = (chasing ? 0.3f : (readyToAttack ? 0.55f : 0.85f)) * MathHelper.lerp(combatZone ? 0.68f : 0.48f, 1.0f, effectiveFovFactor);
        decorScale *= MathHelper.lerp(1.0f, 0.42f, closeAirBlend);
        if (ultraClose && !movingTarget) {
            decorScale *= 0.28f;
            jitterScale *= 0.55f;
        } else if (closeMoving) decorScale *= 0.62f;

        float aimYaw = targetYaw + (biasYaw + jitterYaw + microJitterYaw) * jitterScale + (brownianYaw + wanderYaw + lookAwayYaw) * decorScale;
        float aimPitch = MathHelper.clamp(targetPitch + (biasPitch + jitterPitch + microJitterPitch) * jitterScale + (brownianPitch + wanderPitch + lookAwayPitch) * decorScale, -89.0f, 89.0f);
        if (overshootPhase > 0 && !(readyToAttack && (onTarget || chasing))) {
            aimYaw += overshootYaw;
            aimPitch = MathHelper.clamp(aimPitch + overshootPitch, -89.0f, 89.0f);
        }

        float converge = MathHelper.clamp((engage * 0.32f + (onTarget ? 0.26f : 0.0f) + (chasing ? 0.1f : 0.0f)) * MathHelper.lerp(1.0f, 0.68f, closeAirBlend), 0.0f, chasing ? 0.36f : (readyToAttack ? (onTarget ? 0.58f : 0.48f) : 0.48f));
        float residualJitter = readyToAttack ? (chasing ? 0.14f : (onTarget ? 0.1f : 0.16f)) + closeAirBlend * 0.08f : 0.4f;
        aimYaw = MathHelper.lerp(converge, aimYaw, targetYaw + (biasYaw + jitterYaw + microJitterYaw) * residualJitter);
        aimPitch = MathHelper.lerp(converge, aimPitch, targetPitch + (biasPitch + jitterPitch + microJitterPitch) * residualJitter);

        float desiredYawDelta = MathHelper.wrapDegrees(aimYaw - currentYaw);
        float desiredPitchDelta = aimPitch - currentPitch;
        float angularError = (float) Math.sqrt(desiredYawDelta * desiredYawDelta + desiredPitchDelta * desiredPitchDelta);
        float fatigue = MathHelper.clamp((float) combatTicks / 420.0f, 0.0f, 0.14f);
        float legitStrength = MathHelper.clamp((onTarget ? 0.38f : 0.34f) + engage * 0.22f + fatigue * 0.2f + (readyToAttack ? 0.1f : 0.0f) + (chasing ? 0.05f : 0.0f), 0.2f, 0.64f);
        float randMul = MathHelper.clamp(attackRandMul, 0.94f, 1.14f);
        float urgency = MathHelper.clamp(angularError / 40.0f, 0.0f, 1.0f) * (combatZone ? Math.max(effectiveFovFactor, 0.86f) : effectiveFovFactor);
        float attackMul = (readyToAttack ? 1.14f + effectiveFovFactor * 0.06f : 1.0f) * MathHelper.lerp(1.0f, 0.9f, closeAirBlend);
        float chaseMul = (chasing ? 1.12f : 1.0f) * MathHelper.lerp(1.0f, 0.82f, closeAirBlend);
        float switchMul = combatZone ? MathHelper.lerp(0.82f, 1.0f, switchEase) : MathHelper.lerp(0.62f, 1.0f, switchEase);
        float legitMul = MathHelper.lerp(0.88f, 1.0f, legitStrength) * (combatZone ? MathHelper.lerp(0.92f, 1.0f, effectiveFovFactor) : MathHelper.lerp(0.76f, 1.0f, effectiveFovFactor));
        float trackResponse = MathHelper.lerp(chasing ? 0.62f : (combatZone ? 0.53f : 0.42f), chasing ? 0.94f : (combatZone ? 0.84f : 0.68f), legitStrength) * sessionResponse * effectiveFovFactor * switchMul;
        if (chasing || angularError > 10.0f) trackResponse *= (1.08f + Math.min(angularError, 44.0f) * 0.003f) * MathHelper.lerp(1.0f, 0.84f, closeAirBlend);
        if (onTarget && !chasing && angularError < 14.0f) trackResponse *= 0.9f;
        trackResponse *= MathHelper.lerp(1.0f, 0.78f, closeAirBlend * 0.85f);
        if (ultraClose && !movingTarget) trackResponse *= 0.58f;
        else if (closeMoving) trackResponse *= 0.72f;

        float rawYawStep = desiredYawDelta * trackResponse;
        float rawPitchStep = desiredPitchDelta * trackResponse * (readyToAttack ? 0.84f : 0.7f) * MathHelper.lerp(1.0f, 0.46f, closeAirBlend);
        float maxStepYaw = computeFovStepLimit(fov, effectiveFovFactor, angularError, readyToAttack, gliding, true) * switchMul;
        float maxStepPitch = computeFovStepLimit(fov, effectiveFovFactor, angularError, readyToAttack, gliding, false) * switchMul;
        if (combatZone && (chasing || movingTarget) && closeAirBlend < 0.55f) {
            maxStepYaw *= 1.14f;
            maxStepPitch *= 1.12f;
        }
        if (closeAirBlend > 0.0f) {
            maxStepYaw *= MathHelper.lerp(1.0f, 0.86f, closeAirBlend);
            maxStepPitch *= MathHelper.lerp(1.0f, 0.52f, closeAirBlend);
            if (Math.abs(desiredPitchDelta) > 5.5f) maxStepPitch = Math.min(maxStepPitch, 2.8f + Math.abs(desiredPitchDelta) * 0.14f);
        }
        if (ultraClose && !movingTarget) {
            maxStepYaw *= 0.68f;
            maxStepPitch *= 0.48f;
        }
        rawYawStep = MathHelper.clamp(rawYawStep, -maxStepYaw, maxStepYaw);
        rawPitchStep = MathHelper.clamp(rawPitchStep, -maxStepPitch, maxStepPitch);

        float yawInertia = MathHelper.clamp(sessionInertia + (onTarget && !chasing ? 0.12f : 0.05f) + fatigue * 0.1f - (chasing ? 0.1f : 0.0f) + closeAirBlend * 0.1f, chasing ? 0.32f : 0.37f, 0.9f);
        float pitchInertia = MathHelper.clamp(yawInertia + 0.04f + closeAirBlend * 0.14f, chasing ? 0.36f : 0.42f, 0.94f);
        if (ultraClose && !movingTarget) {
            yawInertia = MathHelper.clamp(yawInertia + 0.18f, 0.52f, 0.88f);
            pitchInertia = MathHelper.clamp(pitchInertia + 0.16f, 0.56f, 0.92f);
        } else if (closeMoving) {
            yawInertia = MathHelper.clamp(yawInertia + 0.14f, 0.46f, 0.86f);
            pitchInertia = MathHelper.clamp(pitchInertia + 0.12f, 0.5f, 0.9f);
        }
        smoothYawStep = smoothYawStep * yawInertia + rawYawStep * (1.0f - yawInertia);
        smoothPitchStep = smoothPitchStep * pitchInertia + rawPitchStep * (1.0f - pitchInertia);

        float playbackYaw = MathHelper.lerp(legitStrength, chasing ? 0.66f : 0.58f, chasing ? 0.9f : 0.82f);
        float playbackPitch = MathHelper.lerp(legitStrength, chasing ? 0.6f : 0.52f, chasing ? 0.84f : 0.76f) * MathHelper.lerp(1.0f, 0.62f, closeAirBlend);
        if (ultraClose && !movingTarget) {
            playbackYaw = MathHelper.clamp(playbackYaw * 0.62f, 0.34f, 0.52f);
            playbackPitch = MathHelper.clamp(playbackPitch * 0.58f, 0.3f, 0.48f);
        } else if (closeMoving) {
            playbackYaw = MathHelper.clamp(playbackYaw * 0.74f, 0.4f, 0.62f);
            playbackPitch = MathHelper.clamp(playbackPitch * 0.7f, 0.36f, 0.56f);
        }
        float outputYaw = lastOutputYaw + (smoothYawStep - lastOutputYaw) * playbackYaw;
        float outputPitch = lastOutputPitch + (smoothPitchStep - lastOutputPitch) * playbackPitch;

        if (ultraClose && !movingTarget) {
            resetMidAimJerk();
            trackJerkYaw *= 0.35f;
            trackJerkPitch *= 0.35f;
        } else if (!onTarget || angularError >= 12.0f) {
            updateMidAimJerk(angularError, desiredYawDelta, desiredPitchDelta, smoothYawStep, readyToAttack, chasing, closeAirBlend, switchEase, onTarget);
            updateTrackJerks(readyToAttack, chasing, closeAirBlend, switchEase, angularError, onTarget, closeMoving);
        } else {
            resetMidAimJerk();
            trackJerkYaw *= 0.3f;
            trackJerkPitch *= 0.3f;
        }

        float jerkMul = onTarget && angularError < 14.0f ? 0.42f : 1.0f;
        if (midJerkPhase > 0) {
            outputYaw += midJerkYaw * jerkMul;
            outputPitch += midJerkPitch * jerkMul;
        }
        outputYaw += trackJerkYaw * jerkMul;
        outputPitch += trackJerkPitch * jerkMul;
        lastOutputYaw = outputYaw;
        lastOutputPitch = outputPitch;

        float yawSpeed = MathHelper.clamp((9.0f + angularError * 0.4f * effectiveFovFactor + engage * 8.4f + speed * 26.5f) * attackMul * chaseMul * switchMul * randMul * legitMul, 7.8f, gliding ? 46.0f : (combatZone ? 42.0f : 33.0f)) * MathHelper.lerp(1.0f, 0.88f, closeAirBlend);
        float pitchSpeed = MathHelper.clamp((6.0f + Math.abs(desiredPitchDelta) * 0.36f * effectiveFovFactor + engage * 5.4f + speed * 16.5f) * attackMul * chaseMul * switchMul * randMul * legitMul, 5.4f, gliding ? 24.5f : (combatZone ? 21.5f : 18.0f)) * MathHelper.lerp(1.0f, 0.54f, closeAirBlend);
        if (ultraClose && !movingTarget) {
            yawSpeed = Math.min(yawSpeed, 9.5f + angularError * 0.12f);
            pitchSpeed = Math.min(pitchSpeed, 4.8f + Math.abs(desiredPitchDelta) * 0.1f);
        } else if (closeMoving) {
            yawSpeed = Math.min(yawSpeed, 14.0f + angularError * 0.16f);
            pitchSpeed = Math.min(pitchSpeed, 7.2f + Math.abs(desiredPitchDelta) * 0.14f);
        }
        if (angularError > 8.0f && angularError < 22.0f && onTarget && !chasing) {
            yawSpeed *= 0.86f;
            pitchSpeed *= 0.82f;
        }
        if (jumpCombo && Math.abs(desiredPitchDelta) > 4.0f) pitchSpeed = Math.min(pitchSpeed, 10.0f + Math.abs(desiredPitchDelta) * 0.28f);
        speed += (MathHelper.clamp(0.11f + urgency * 0.14f + engage * 0.1f, 0.09f, 0.28f) - speed) * 0.12f;

        float newYaw = currentYaw + outputYaw;
        float newPitch = MathHelper.clamp(currentPitch + outputPitch, -89.0f, 89.0f);
        float gcd = GCDFixer.getGCDValue();
        if (gcd > 0.0f) {
            newYaw = currentYaw + Math.round((newYaw - currentYaw) / gcd) * gcd;
            newPitch = currentPitch + Math.round((newPitch - currentPitch) / gcd) * gcd;
        }
        newPitch = MathHelper.clamp(newPitch, -89.0f, 89.0f);
        lastYaw = newYaw;
        lastPitch = newPitch;
        ka.lastYaw = newYaw;
        ka.lastPitch = newPitch;

        float yawReturnSpeed = MathHelper.clamp(yawSpeed * 0.86f, 4.4f, gliding ? 38.0f : 33.0f);
        float pitchReturnSpeed = MathHelper.clamp(pitchSpeed * 0.88f, 3.8f, gliding ? 22.0f : 19.0f);
        RotationComponent.update(new Rotation(newYaw, newPitch), yawSpeed, pitchSpeed, yawReturnSpeed, pitchReturnSpeed, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), "KillAura");

        planNextIdle(onTarget, engage, angularError, readyToAttack);
        float skipChance = switchEase < 0.72f || chasing ? 0.0f : (readyToAttack ? 0.04f : (onTarget ? 0.12f : 0.16f));
        if (angularError > 9.0f) skipChance *= 0.25f;
        if (rnd.nextFloat() < skipChance) skipTicks = 1;
    }

    @Override
    public void reset(KillAura ka) {
        trackedTarget = null;
        initialized = false;
        sessionSeed = 0;
        targetSwitchTicks = 0;
        targetSwitchDuration = 0;
        switchEase = 1.0f;
        elytraAimActive = false;
        stableAim = null;
        attackPoint = null;
        pointSwitchTicks = 0;
        samePointHits = 0;
        postHitTicks = 0;
        combatTicks = 0;
        tick = 0;
        clearMotionState();
        if (mc.player != null) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        } else {
            lastYaw = 0.0f;
            lastPitch = 0.0f;
        }
    }

    private void initSession() {
        sessionSeed = rnd.nextInt(Short.MAX_VALUE);
        sessionYawAmp = 0.82f + rnd.nextFloat() * 0.36f;
        sessionPitchAmp = 0.78f + rnd.nextFloat() * 0.34f;
        sessionInertia = 0.48f + rnd.nextFloat() * 0.22f;
        sessionEngageFov = 46.0f + rnd.nextFloat() * 16.0f;
        sessionSoftFov = sessionEngageFov + 30.0f + rnd.nextFloat() * 12.0f;
        sessionFov = MathHelper.clamp(sessionSoftFov + 22.0f + rnd.nextFloat() * 14.0f, 112.0f, 138.0f);
        sessionResponse = 1.1f + rnd.nextFloat() * 0.2f;
        attackRandTicks = 0;
        attackRandMul = 1.0f;
        microJitterYaw = 0.0f;
        microJitterPitch = 0.0f;
        nextMicroBurst = 0L;
    }

    private void clearMotionState() {
        jitterYaw = jitterPitch = jitterTargetYaw = jitterTargetPitch = 0.0f;
        nextJitterTime = 0L;
        biasYaw = biasPitch = 0.0f;
        nextBiasTime = 0L;
        brownianYaw = brownianPitch = wanderYaw = wanderPitch = 0.0f;
        wanderTicks = 0;
        lookAwayYaw = lookAwayPitch = 0.0f;
        lookAwayTicks = lookAwayCd = 0;
        smoothYawStep = smoothPitchStep = lastOutputYaw = lastOutputPitch = 0.0f;
        speed = 0.0f;
        overshootPhase = 0;
        overshootYaw = overshootPitch = 0.0f;
        overshootTicks = 0;
        fixedCloseLocal = null;
        resetMidAimJerk();
        resetTrackJerk();
        idleTicks = skipTicks = attackRandTicks = 0;
        attackRandMul = 1.0f;
        microJitterYaw = microJitterPitch = 0.0f;
        nextMicroBurst = 0L;
        targetSwitchTicks = targetSwitchDuration = 0;
        switchEase = 1.0f;
    }

    private void softenMotionOnSwitch() {
        jitterYaw *= 0.46f;
        jitterPitch *= 0.46f;
        jitterTargetYaw *= 0.4f;
        jitterTargetPitch *= 0.4f;
        biasYaw *= 0.34f;
        biasPitch *= 0.34f;
        microJitterYaw *= 0.38f;
        microJitterPitch *= 0.38f;
        brownianYaw *= 0.3f;
        brownianPitch *= 0.3f;
        wanderYaw *= 0.28f;
        wanderPitch *= 0.28f;
        wanderTicks = 0;
        lookAwayYaw = lookAwayPitch = 0.0f;
        lookAwayTicks = 0;
        lookAwayCd = 6 + rnd.nextInt(10);
        smoothYawStep *= 0.32f;
        smoothPitchStep *= 0.32f;
        lastOutputYaw *= 0.36f;
        lastOutputPitch *= 0.36f;
        speed *= 0.42f;
        overshootPhase = 0;
        overshootYaw = overshootPitch = 0.0f;
        overshootTicks = 0;
        fixedCloseLocal = null;
        resetMidAimJerk();
        resetTrackJerk();
        idleTicks = skipTicks = attackRandTicks = 0;
        attackRandMul = 1.0f;
        nextJitterTime = nextBiasTime = nextMicroBurst = 0L;
    }

    private void resetMidAimJerk() {
        midJerkPhase = midJerkTicks = midJerkReturnTicks = midJerkCd = 0;
        midJerkYaw = midJerkPitch = midJerkStartError = 0.0f;
        midJerkArmed = false;
        midJerkChain = midJerkPassMask = 0;
        lastJerkMode = -1;
    }

    private void resetTrackJerk() {
        trackJerkYaw = trackJerkPitch = 0.0f;
        trackJerkTicks = trackJerkCd = 0;
    }

    private void updateMidAimJerk(float angularError, float desiredYawDelta, float desiredPitchDelta, float smoothYawStep, boolean readyToAttack, boolean chasing, float closeAirBlend, float switchEase, boolean onTarget) {
        if (onTarget && angularError < 14.0f) return;
        if (midJerkCd > 0) midJerkCd--;
        if (midJerkPhase == 1) {
            if (--midJerkTicks <= 0) {
                midJerkPhase = 2;
                midJerkReturnTicks = 2 + rnd.nextInt(3);
            }
            return;
        }
        if (midJerkPhase == 2) {
            float pull = 0.4f + rnd.nextFloat() * 0.1f;
            midJerkYaw = MathHelper.lerp(pull, midJerkYaw, 0.0f);
            midJerkPitch = MathHelper.lerp(pull, midJerkPitch, 0.0f);
            if (--midJerkReturnTicks <= 0 || Math.abs(midJerkYaw) < 0.05f && Math.abs(midJerkPitch) < 0.04f) {
                if (midJerkChain > 0 && closeAirBlend < 0.62f) {
                    midJerkChain--;
                    startMidAimJerk(lastJerkDesiredYaw, lastJerkDesiredPitch, true);
                    return;
                }
                resetMidAimJerk();
                midJerkCd = 16 + rnd.nextInt(14);
            }
            return;
        }
        boolean headingToward = Math.abs(desiredYawDelta) > 0.3f && Math.signum(desiredYawDelta) == Math.signum(smoothYawStep) && Math.abs(smoothYawStep) > 0.1f;
        boolean activeAim = switchEase > 0.65f && closeAirBlend < 0.75f && !chasing && readyToAttack && angularError > 14.0f && angularError < 38.0f && headingToward;
        if (!activeAim) {
            midJerkStartError = 0.0f;
            midJerkArmed = false;
            midJerkPassMask = 0;
            return;
        }
        if (midJerkStartError < 1.0f) {
            midJerkStartError = angularError;
            midJerkArmed = true;
            midJerkPassMask = 0;
            return;
        }
        if (!midJerkArmed || midJerkCd > 0) return;
        float progress = 1.0f - angularError / midJerkStartError;
        if (progress < 0.42f || progress > 0.56f || (midJerkPassMask & 2) != 0) return;
        if (rnd.nextFloat() > 0.18f) {
            midJerkArmed = false;
            midJerkStartError = 0.0f;
            return;
        }
        midJerkPassMask |= 2;
        startMidAimJerk(desiredYawDelta, desiredPitchDelta, false);
        midJerkArmed = false;
        midJerkStartError = 0.0f;
    }

    private void startMidAimJerk(float desiredYawDelta, float desiredPitchDelta, boolean forceDifferent) {
        midJerkPhase = 1;
        midJerkTicks = 1 + rnd.nextInt(2);
        lastJerkDesiredYaw = desiredYawDelta;
        lastJerkDesiredPitch = desiredPitchDelta;
        float ampYaw = (1.0f + rnd.nextFloat() * 1.6f) * sessionYawAmp * 0.42f;
        float ampPitch = (0.5f + rnd.nextFloat() * 1.0f) * sessionPitchAmp * 0.38f;
        int mode = rnd.nextInt(10);
        if (forceDifferent && lastJerkMode >= 0) mode = (lastJerkMode + 3 + rnd.nextInt(5)) % 10;
        lastJerkMode = mode;
        float[] offset = new float[2];
        applyJerkDirection(mode, desiredYawDelta, desiredPitchDelta, ampYaw, ampPitch, offset);
        midJerkYaw = offset[0];
        midJerkPitch = offset[1];
        if (!forceDifferent) midJerkChain = rnd.nextFloat() < 0.06f ? 1 : 0;
    }

    private void applyJerkDirection(int mode, float desiredYawDelta, float desiredPitchDelta, float ampYaw, float ampPitch, float[] out) {
        float len = (float) Math.sqrt(desiredYawDelta * desiredYawDelta + desiredPitchDelta * desiredPitchDelta);
        float yaw;
        float pitch;
        switch (mode) {
            case 0 -> {
                yaw = len > 0.08f ? -desiredPitchDelta / len * ampYaw : ampYaw;
                pitch = len > 0.08f ? desiredYawDelta / len * ampPitch : ampPitch * 0.35f;
            }
            case 1 -> {
                yaw = len > 0.08f ? desiredPitchDelta / len * ampYaw : -ampYaw;
                pitch = len > 0.08f ? -desiredYawDelta / len * ampPitch : -ampPitch * 0.35f;
            }
            case 2 -> {
                yaw = -Math.signum(desiredYawDelta == 0.0f ? (rnd.nextBoolean() ? 1.0f : -1.0f) : desiredYawDelta) * ampYaw;
                pitch = (rnd.nextFloat() - 0.5f) * ampPitch * 0.45f;
            }
            case 3 -> {
                yaw = Math.signum(desiredYawDelta == 0.0f ? (rnd.nextBoolean() ? 1.0f : -1.0f) : desiredYawDelta) * ampYaw * 0.72f;
                pitch = -ampPitch * 0.55f;
            }
            case 4 -> {
                yaw = (rnd.nextFloat() - 0.5f) * ampYaw * 0.42f;
                pitch = -ampPitch;
            }
            case 5 -> {
                yaw = (rnd.nextFloat() - 0.5f) * ampYaw * 0.42f;
                pitch = ampPitch;
            }
            case 6 -> {
                yaw = ampYaw * 0.78f;
                pitch = ampPitch * 0.68f;
            }
            case 7 -> {
                yaw = -ampYaw * 0.78f;
                pitch = -ampPitch * 0.68f;
            }
            case 8 -> {
                yaw = (rnd.nextBoolean() ? 1.0f : -1.0f) * ampYaw;
                pitch = 0.0f;
            }
            default -> {
                yaw = (rnd.nextFloat() - 0.5f) * ampYaw * 2.0f;
                pitch = (rnd.nextFloat() - 0.5f) * ampPitch * 2.0f;
            }
        }
        out[0] = yaw;
        out[1] = MathHelper.clamp(pitch, -5.5f, 5.5f);
    }

    private void updateTrackJerks(boolean readyToAttack, boolean chasing, float closeAirBlend, float switchEase, float angularError, boolean onTarget, boolean closeMoving) {
        if (trackJerkTicks > 0) {
            trackJerkTicks--;
            float decay = closeMoving ? 0.5f + rnd.nextFloat() * 0.08f : 0.56f + rnd.nextFloat() * 0.1f;
            trackJerkYaw *= decay;
            trackJerkPitch *= decay;
            if (trackJerkTicks <= 0 || Math.abs(trackJerkYaw) < 0.04f && Math.abs(trackJerkPitch) < 0.03f) trackJerkYaw = trackJerkPitch = 0.0f;
            return;
        }
        if (trackJerkCd > 0) {
            trackJerkCd--;
            return;
        }
        boolean canJerk = switchEase > 0.62f && closeAirBlend < 0.7f && readyToAttack && angularError > 4.0f && angularError < 36.0f && (!chasing || onTarget || closeMoving);
        if (!canJerk || onTarget && angularError < 12.0f) return;
        float chance = closeMoving ? 0.14f : (chasing ? 0.06f : (onTarget ? 0.08f : 0.11f));
        if (rnd.nextFloat() > chance) return;
        startTrackJerk(rnd.nextInt(10), closeMoving ? 0.78f + rnd.nextFloat() * 0.14f : 0.82f);
        trackJerkCd = closeMoving ? 6 + rnd.nextInt(6) : 8 + rnd.nextInt(8);
    }

    private void startTrackJerk(int mode, float strengthMul) {
        float ampYaw = (0.7f + rnd.nextFloat() * 1.4f) * sessionYawAmp * 0.38f * strengthMul;
        float ampPitch = (0.4f + rnd.nextFloat() * 0.9f) * sessionPitchAmp * 0.34f * strengthMul;
        float[] offset = new float[2];
        applyJerkDirection(mode, lastJerkDesiredYaw, lastJerkDesiredPitch, ampYaw, ampPitch, offset);
        if (Math.abs(offset[0]) < 0.08f && Math.abs(offset[1]) < 0.06f) applyJerkDirection((mode + 4) % 10, 0.0f, 0.0f, ampYaw, ampPitch, offset);
        trackJerkYaw = offset[0];
        trackJerkPitch = offset[1];
        trackJerkTicks = 1 + rnd.nextInt(2);
    }

    private void updateSwitchEase() {
        if (targetSwitchTicks <= 0) {
            switchEase = 1.0f;
            return;
        }
        targetSwitchTicks--;
        float progress = 1.0f - (float) targetSwitchTicks / Math.max(1.0f, (float) targetSwitchDuration);
        switchEase = progress * progress * (3.0f - 2.0f * progress);
    }

    private float computeFovLegitFactor(float fov, boolean readyToAttack) {
        if (fov <= sessionEngageFov) return 1.0f;
        if (fov >= sessionFov) return 0.0f;
        if (fov <= sessionSoftFov) {
            float t = (fov - sessionEngageFov) / Math.max(1.0f, sessionSoftFov - sessionEngageFov);
            return MathHelper.lerp(t * t, 1.0f, readyToAttack ? 0.68f : 0.46f);
        }
        float t = (fov - sessionSoftFov) / Math.max(1.0f, sessionFov - sessionSoftFov);
        return MathHelper.lerp(t * t, readyToAttack ? 0.68f : 0.46f, 0.0f);
    }

    private float computeFovStepLimit(float fov, float fovFactor, float angularError, boolean readyToAttack, boolean gliding, boolean yawAxis) {
        float base = yawAxis ? (readyToAttack ? 4.1f : 3.0f) : (readyToAttack ? 3.0f : 2.2f);
        float engageBonus = yawAxis ? (readyToAttack ? 6.6f : 4.0f) : (readyToAttack ? 4.7f : 3.0f);
        float errorBonus = Math.min(angularError, yawAxis ? 42.0f : 28.0f) * (yawAxis ? 0.085f : 0.07f) * fovFactor;
        float step = base + engageBonus * fovFactor + errorBonus;
        if (fov > sessionEngageFov) {
            float wideT = MathHelper.clamp((fov - sessionEngageFov) / Math.max(1.0f, sessionSoftFov - sessionEngageFov), 0.0f, 1.0f);
            float wideCap = yawAxis ? MathHelper.lerp(wideT, readyToAttack ? 11.0f : 8.0f, readyToAttack ? 5.2f : 3.8f) : MathHelper.lerp(wideT, readyToAttack ? 7.6f : 5.4f, readyToAttack ? 3.6f : 2.6f);
            step = Math.min(step, wideCap);
        }
        if (fov > sessionSoftFov) {
            float outerT = MathHelper.clamp((fov - sessionSoftFov) / Math.max(1.0f, sessionFov - sessionSoftFov), 0.0f, 1.0f);
            float outerCap = yawAxis ? MathHelper.lerp(outerT, readyToAttack ? 4.8f : 3.4f, 1.8f) : MathHelper.lerp(outerT, readyToAttack ? 3.4f : 2.4f, 1.4f);
            step = Math.min(step, outerCap);
        }
        float max = yawAxis ? (gliding ? 14.8f : (readyToAttack ? 14.2f : 9.6f)) : (gliding ? 8.2f : (readyToAttack ? 7.6f : 5.8f));
        return MathHelper.clamp(step, yawAxis ? 2.0f : 1.5f, max);
    }

    private float smoothEngage(boolean attackReady, boolean onTarget, boolean pressed, double dist, boolean movingTarget) {
        float raw = 0.0f;
        if (attackReady) raw += 0.44f;
        if (onTarget) raw += 0.28f;
        if (pressed) raw += 0.18f;
        if (dist < 2.0) raw += 0.12f;
        if (movingTarget) raw += 0.1f;
        return MathHelper.clamp(raw + (rnd.nextFloat() - 0.5f) * 0.06f, 0.0f, 1.0f);
    }

    private void planNextIdle(boolean onTarget, float engage, float angularError, boolean attackReady) {
        if (attackReady || engage > 0.58f || angularError > 12.0f) return;
        if (onTarget && rnd.nextFloat() < 0.08f) idleTicks = 1 + rnd.nextInt(2);
        else if (!onTarget && rnd.nextFloat() < 0.05f) idleTicks = 1 + rnd.nextInt(2);
    }

    private void updateMicroJitter(boolean onTarget, float engage, boolean attackReady, float closeAirBlend) {
        long now = System.currentTimeMillis();
        double swayPeriodYaw = attackReady ? 118.0 + sessionSeed % 37 : 142.0 + sessionSeed % 41;
        double swayPeriodPitch = attackReady ? 136.0 + sessionSeed % 29 : 158.0 + sessionSeed % 33;
        float swayAmpYaw = (attackReady ? 0.04f : 0.07f) * sessionYawAmp * MathHelper.lerp(1.0f, 0.62f, engage);
        float swayAmpPitch = (attackReady ? 0.028f : 0.045f) * sessionPitchAmp * MathHelper.lerp(1.0f, 0.58f, engage) * MathHelper.lerp(1.0f, 1.34f, closeAirBlend);
        if (onTarget) {
            swayAmpYaw *= 0.72f;
            swayAmpPitch *= 0.68f;
        }
        float swayYaw = (float) Math.sin(now / swayPeriodYaw + tick * 0.31f) * swayAmpYaw;
        float swayPitch = (float) Math.cos(now / swayPeriodPitch + tick * 0.27f) * swayAmpPitch;
        if (now >= nextMicroBurst) {
            microJitterYaw += (rnd.nextFloat() - 0.5f) * swayAmpYaw * 2.4f;
            microJitterPitch += (rnd.nextFloat() - 0.5f) * swayAmpPitch * 2.2f;
            nextMicroBurst = now + rnd.nextLong(48L, 128L);
        }
        microJitterYaw = MathHelper.clamp(microJitterYaw * 0.78f + swayYaw, -0.42f, 0.42f);
        microJitterPitch = MathHelper.clamp(microJitterPitch * 0.76f + swayPitch, -0.28f, 0.28f);
    }

    private void updateBrownian(boolean chasing) {
        float ampScale = chasing ? 0.42f : 1.0f;
        brownianYaw = MathHelper.clamp((brownianYaw + (rnd.nextFloat() - 0.5f) * 0.08f * sessionYawAmp * ampScale) * 0.86f, -0.35f, 0.35f);
        brownianPitch = MathHelper.clamp((brownianPitch + (rnd.nextFloat() - 0.5f) * 0.05f * sessionPitchAmp * ampScale) * 0.84f, -0.22f, 0.22f);
    }

    private void pickWanderOffset() {
        wanderYaw = (rnd.nextFloat() - 0.5f) * 3.2f * sessionYawAmp;
        wanderPitch = (rnd.nextFloat() - 0.5f) * 1.6f * sessionPitchAmp;
        wanderTicks = 6 + rnd.nextInt(14);
    }

    private void updateWander(float engage, boolean chasing) {
        if (chasing) {
            wanderYaw *= 0.62f;
            wanderPitch *= 0.6f;
            wanderTicks = 0;
            return;
        }
        if (wanderTicks <= 0) {
            wanderYaw *= 0.82f;
            wanderPitch *= 0.8f;
            if (rnd.nextFloat() < 0.08f + engage * 0.04f) pickWanderOffset();
            return;
        }
        wanderTicks--;
        float pull = 0.06f + engage * 0.04f;
        wanderYaw = MathHelper.lerp(pull, wanderYaw, 0.0f);
        wanderPitch = MathHelper.lerp(pull, wanderPitch, 0.0f);
    }

    private void startLookAway(float strength) {
        lookAwayYaw = (rnd.nextFloat() - 0.5f) * (8.0f + rnd.nextFloat() * 14.0f) * strength * sessionYawAmp;
        lookAwayPitch = (rnd.nextFloat() - 0.5f) * (4.0f + rnd.nextFloat() * 8.0f) * strength * sessionPitchAmp;
        lookAwayTicks = 3 + rnd.nextInt(8);
        lookAwayCd = 10 + rnd.nextInt(18);
    }

    private void updateLookAway(float engage, boolean onTarget, boolean chasing) {
        if (chasing) {
            lookAwayYaw *= 0.55f;
            lookAwayPitch *= 0.52f;
            lookAwayTicks = 0;
            return;
        }
        if (lookAwayTicks > 0) {
            lookAwayTicks--;
            float decay = 0.14f + engage * 0.06f;
            lookAwayYaw = MathHelper.lerp(decay, lookAwayYaw, 0.0f);
            lookAwayPitch = MathHelper.lerp(decay, lookAwayPitch, 0.0f);
            if (lookAwayTicks <= 0) lookAwayYaw = lookAwayPitch = 0.0f;
            return;
        }
        if (lookAwayCd > 0) {
            lookAwayCd--;
            return;
        }
        if (engage > 0.58f && onTarget) return;
        if (rnd.nextFloat() <= 0.1f + (postHitTicks > 0 ? 0.14f : 0.0f)) startLookAway(0.75f + rnd.nextFloat() * 0.35f);
    }

    private void startOvershoot() {
        overshootPhase = 1;
        overshootTicks = 2 + rnd.nextInt(3);
        overshootYaw = (rnd.nextBoolean() ? 1.0f : -1.0f) * (1.2f + rnd.nextFloat() * 2.8f);
        overshootPitch = (rnd.nextBoolean() ? 1.0f : -1.0f) * (0.6f + rnd.nextFloat() * 1.6f);
    }

    private void updateOvershoot() {
        if (overshootPhase <= 0) return;
        overshootTicks--;
        if (overshootPhase == 1) {
            overshootYaw *= 0.72f;
            overshootPitch *= 0.7f;
            if (overshootTicks <= 0) {
                overshootPhase = 2;
                overshootTicks = 2 + rnd.nextInt(4);
                overshootYaw = -overshootYaw * 0.55f;
                overshootPitch = -overshootPitch * 0.5f;
            }
        } else {
            overshootYaw *= 0.58f;
            overshootPitch *= 0.56f;
            if (overshootTicks <= 0 || Math.abs(overshootYaw) < 0.08f && Math.abs(overshootPitch) < 0.06f) {
                overshootPhase = 0;
                overshootYaw = overshootPitch = 0.0f;
            }
        }
    }

    private void refreshBias(boolean onTarget, float engage, double distance, boolean attackReady, float closeAirBlend) {
        long now = System.currentTimeMillis();
        if (now < nextBiasTime) return;
        float yawAmp = (onTarget ? 0.22f : 0.36f) * sessionYawAmp * MathHelper.lerp(1.0f, attackReady ? 0.48f : 0.55f, engage);
        float pitchAmp = (onTarget ? 0.1f : 0.18f) * sessionPitchAmp * MathHelper.lerp(1.0f, attackReady ? 0.46f : 0.52f, engage);
        if (distance < 1.35) {
            yawAmp *= attackReady ? 0.54f : 0.46f;
            pitchAmp *= attackReady ? 0.52f : 0.44f;
        }
        if (closeAirBlend > 0.0f) {
            yawAmp *= MathHelper.lerp(1.0f, 1.18f, closeAirBlend);
            pitchAmp *= MathHelper.lerp(1.0f, 1.28f, closeAirBlend);
        }
        if (postHitTicks > 0) {
            yawAmp *= 1.12f;
            pitchAmp *= 1.08f;
        }
        biasYaw = (rnd.nextFloat() - 0.5f) * yawAmp * 2.0f;
        biasPitch = (rnd.nextFloat() - 0.5f) * pitchAmp * 2.0f;
        nextBiasTime = now + rnd.nextLong(attackReady ? 90L : 110L, attackReady ? 220L : 260L);
    }

    private void updateJitter(boolean onTarget, float engage, double distance, float currentYaw, float currentPitch, float targetYaw, float targetPitch, boolean attackReady, float closeAirBlend) {
        long now = System.currentTimeMillis();
        float yawDelta = Math.abs(MathHelper.wrapDegrees(targetYaw - currentYaw));
        float pitchDelta = Math.abs(targetPitch - currentPitch);
        float yawAmp = (onTarget ? 0.1f : 0.16f) * sessionYawAmp * MathHelper.lerp(1.0f, attackReady ? 0.5f : 0.58f, engage);
        float pitchAmp = (onTarget ? 0.05f : 0.09f) * sessionPitchAmp * MathHelper.lerp(1.0f, attackReady ? 0.48f : 0.55f, engage);
        if (distance < 1.35) {
            yawAmp *= attackReady ? 0.58f : 0.5f;
            pitchAmp *= attackReady ? 0.56f : 0.48f;
        }
        if (closeAirBlend > 0.0f) {
            yawAmp *= MathHelper.lerp(1.0f, 1.2f, closeAirBlend);
            pitchAmp *= MathHelper.lerp(1.0f, 1.36f, closeAirBlend);
        }
        if (yawDelta < 6.0f) yawAmp *= attackReady ? 0.62f : 0.5f;
        if (pitchDelta < 4.0f) pitchAmp *= attackReady ? 0.6f : 0.48f;
        if (postHitTicks > 0) {
            yawAmp *= 1.14f;
            pitchAmp *= 1.1f;
        }
        if (now >= nextJitterTime) {
            jitterTargetYaw = (rnd.nextFloat() - 0.5f) * yawAmp * 2.0f;
            jitterTargetPitch = (rnd.nextFloat() - 0.5f) * pitchAmp * 2.0f;
            nextJitterTime = now + rnd.nextLong(attackReady ? 28L : 34L, attackReady ? 78L : 92L);
        }
        float jitterSmooth = (attackReady ? 0.14f : 0.11f) + rnd.nextFloat() * 0.08f;
        jitterYaw += (jitterTargetYaw - jitterYaw) * jitterSmooth;
        jitterPitch += (jitterTargetPitch - jitterPitch) * jitterSmooth;
    }

    private Vec3d resolveFixedCloseAim(LivingEntity target) {
        if (fixedCloseLocal == null) {
            Box box = target.getBoundingBox();
            Vec3d torso = new Vec3d(box.getCenter().x, box.minY + target.getHeight() * (0.56 + rnd.nextDouble() * 0.06), box.getCenter().z);
            fixedCloseLocal = torso.subtract(target.getPos());
        }
        return target.getPos().add(fixedCloseLocal);
    }

    private Vec3d resolveElytraAimPoint(KillAura ka, LivingEntity target, Vec3d eye, double dist) {
        if (target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive) return PredictUtils.getPredicted(target, ka.predictValue.getValue());
        Vec3d body = BestPoint.getNearestPoint(target);
        if (body == null) {
            Box box = target.getBoundingBox();
            body = new Vec3d(box.getCenter().x, box.minY + target.getHeight() * 0.58, box.getCenter().z);
        }
        Vec3d dir = mc.player.getVelocity();
        if (dir.lengthSquared() > 1.0E-4) {
            Vec3d guided = eye.add(dir.normalize().multiply(MathHelper.clamp(dist, 2.5, 10.0)));
            return body.lerp(guided, 0.38);
        }
        return body;
    }

    private Vec3d resolveAimPoint(LivingEntity target, double dist, float press, float engage, boolean onTarget, boolean attackReady, boolean movingTarget, float closeAirBlend) {
        Vec3d body = BestPoint.getNearestPoint(target);
        Box box = target.getBoundingBox();
        if (body == null) body = new Vec3d(box.getCenter().x, box.minY + target.getHeight() * 0.62, box.getCenter().z);
        if (closeAirBlend > 0.35f) {
            double torsoY = box.minY + (box.maxY - box.minY) * MathHelper.lerp(closeAirBlend, 0.56, 0.62);
            body = new Vec3d(box.getCenter().x, torsoY, box.getCenter().z);
        }
        Vec3d multipoint = getMultipoint(target, dist, engage, onTarget, attackReady, movingTarget, closeAirBlend);
        float bodyWeight = MathHelper.clamp(engage * (attackReady ? 0.5f : 0.34f) + press * (attackReady ? 0.34f : 0.22f) + (movingTarget && attackReady ? 0.12f : 0.0f), 0.0f, attackReady ? (movingTarget ? 0.82f : 0.74f) : 0.58f);
        bodyWeight = MathHelper.clamp(bodyWeight + closeAirBlend * 0.18f, 0.0f, 0.88f);
        if (onTarget && attackReady) bodyWeight = MathHelper.clamp(bodyWeight + 0.16f, 0.0f, 0.92f);
        if (switchEase < 1.0f) bodyWeight *= MathHelper.lerp(0.58f, 1.0f, switchEase);
        Vec3d raw = multipoint.lerp(body, bodyWeight);
        if (stableAim == null) return stableAim = raw;
        float blend = (attackReady ? (movingTarget ? 0.16f : 0.11f) : 0.08f) + press * 0.05f + engage * (attackReady ? (movingTarget ? 0.14f : 0.1f) : 0.08f);
        blend *= MathHelper.lerp(1.0f, 0.62f, closeAirBlend);
        if (onTarget && attackReady) blend *= 0.52f;
        if (switchEase < 1.0f) blend *= MathHelper.lerp(0.55f, 1.0f, switchEase);
        if (postHitTicks > 0) blend = Math.max(blend, attackReady ? 0.18f : 0.14f);
        if (lookAwayTicks > 0 && !attackReady) blend *= 0.62f;
        stableAim = stableAim.lerp(raw, blend);
        return stableAim;
    }

    private Vec3d getMultipoint(LivingEntity target, double dist, float engage, boolean onTarget, boolean attackReady, boolean movingTarget, float closeAirBlend) {
        if (--pointSwitchTicks > 0 && attackPoint != null) return attackPoint;
        Box box = target.getBoundingBox().expand(-0.05);
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = box.getCenter();
        double width = Math.max(0.05, (box.maxX - box.minX) * 0.5);
        double height = Math.max(0.15, box.maxY - box.minY);
        double depth = Math.max(0.05, (box.maxZ - box.minZ) * 0.5);
        float phase = (tick + sessionSeed % 97) * 0.17f;
        double sideDrift = attackReady ? 0.0 : MathHelper.clamp(dist / 10.0, 0.08, 0.34);
        double orbitX = Math.cos(phase) * width * sideDrift;
        double orbitZ = Math.sin(phase * 1.31f) * depth * sideDrift;
        ArrayList<Vec3d> candidates = new ArrayList<>(56);
        populateMultipointCandidates(candidates, box, center, width, height, depth, orbitX, orbitZ, target);
        Collections.shuffle(candidates, new Random((long) sessionSeed ^ (long) tick * 1315423911L));
        int pool = Math.min(candidates.size(), attackReady ? (closeAirBlend > 0.55f ? 6 + rnd.nextInt(4) : (movingTarget ? 6 + rnd.nextInt(4) : (onTarget ? 9 + rnd.nextInt(5) : 12 + rnd.nextInt(7)))) : (onTarget ? 12 + rnd.nextInt(7) : 18 + rnd.nextInt(10)));
        List<Vec3d> poolList = candidates.subList(0, Math.max(1, pool));
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        float bestWeight = -1.0f;
        Vec3d picked = poolList.get(0);
        for (int i = 0; i < poolList.size(); i++) {
            Vec3d p = poolList.get(i);
            Vec3d clamped = new Vec3d(MathHelper.clamp(p.x, box.minX, box.maxX), MathHelper.clamp(p.y, box.minY, box.maxY), MathHelper.clamp(p.z, box.minZ, box.maxZ));
            Vec2f rot = RotationUtil.calculate(eye, clamped);
            float yawDiff = Math.abs(MathHelper.wrapDegrees(rot.x - currentYaw));
            float pitchDiff = Math.abs(rot.y - currentPitch);
            float randomSpread = attackReady ? (closeAirBlend > 0.55f ? 0.22f + rnd.nextFloat() * 0.28f : (movingTarget ? 0.28f + rnd.nextFloat() * 0.32f : 0.42f + rnd.nextFloat() * 0.55f)) : 0.55f + rnd.nextFloat() * 0.9f;
            float weight = 1.0f / (1.0f + yawDiff * (movingTarget && attackReady && closeAirBlend < 0.55f ? 0.028f : 0.04f) + pitchDiff * (movingTarget && attackReady && closeAirBlend < 0.55f ? 0.034f : 0.05f)) * randomSpread;
            weight *= 1.0f - engage * (attackReady ? (movingTarget ? 0.58f : 0.48f) : 0.35f);
            if (attackReady && i == 0) weight *= closeAirBlend > 0.55f ? 1.42f : (movingTarget ? 1.32f : 1.18f);
            else if (i > 0) weight *= 1.08f + i * (attackReady ? 0.03f : 0.04f);
            if (weight > bestWeight) {
                bestWeight = weight;
                picked = clamped;
            }
        }
        attackPoint = picked;
        int switchTicks = attackReady ? (closeAirBlend > 0.55f ? 2 + rnd.nextInt(2) : (movingTarget ? 3 + rnd.nextInt(2) : (onTarget ? 5 + rnd.nextInt(4) : 4 + rnd.nextInt(3)))) : (onTarget ? 5 + rnd.nextInt(4) : 6 + rnd.nextInt(4));
        if (postHitTicks > 0) switchTicks = Math.max(switchTicks, onTarget ? 4 + rnd.nextInt(3) : 2);
        pointSwitchTicks = switchTicks;
        return attackPoint;
    }

    private void populateMultipointCandidates(List<Vec3d> out, Box box, Vec3d center, double width, double height, double depth, double orbitX, double orbitZ, LivingEntity target) {
        double cx = center.x;
        double cz = center.z;
        double minY = box.minY;
        for (double h : new double[]{0.32, 0.36, 0.4, 0.44, 0.48, 0.52, 0.56, 0.6, 0.64, 0.68, 0.72, 0.76, 0.8, 0.86, 0.9}) out.add(new Vec3d(cx, minY + height * h, cz));
        double[] sideHeights = {0.4, 0.48, 0.56, 0.64, 0.72, 0.8};
        double[] sideFracs = {0.22, 0.35, 0.48, 0.58, 0.66, 0.74};
        for (double h : sideHeights) {
            double y = minY + height * h;
            for (double s : sideFracs) {
                out.add(new Vec3d(cx + width * s, y, cz));
                out.add(new Vec3d(cx - width * s, y, cz));
            }
            out.add(new Vec3d(cx, y, cz + depth * sideFracs[rnd.nextInt(sideFracs.length)]));
            out.add(new Vec3d(cx, y, cz - depth * sideFracs[rnd.nextInt(sideFracs.length)]));
        }
        double[] cornerHeights = {0.44, 0.56, 0.68, 0.78};
        double[] cornerFracs = {0.28, 0.42, 0.55};
        for (double h : cornerHeights) {
            double y = minY + height * h;
            for (double sx : cornerFracs) {
                double sz = cornerFracs[rnd.nextInt(cornerFracs.length)];
                out.add(new Vec3d(cx + width * sx, y, cz + depth * sz));
                out.add(new Vec3d(cx - width * sx, y, cz - depth * sz));
            }
        }
        for (double h : new double[]{0.46, 0.58, 0.66, 0.74}) {
            double y = minY + height * h;
            out.add(new Vec3d(cx + orbitX, y, cz + orbitZ));
            out.add(new Vec3d(cx - orbitX * 0.84, y, cz - orbitZ * 0.84));
            out.add(new Vec3d(cx + orbitX * 0.62, y, cz - orbitZ * 0.72));
            out.add(new Vec3d(cx - orbitX * 0.72, y, cz + orbitZ * 0.62));
        }
        Vec3d closest = BestPoint.getNearestPoint(target);
        if (closest != null) out.add(closest);
    }

    private float calculateFovFromCamera(LivingEntity target) {
        if (mc.player == null) return 180.0f;
        Vec3d point = BestPoint.getNearestPoint(target);
        if (point == null) point = target.getBoundingBox().getCenter();
        Vec2f rotation = RotationUtil.calculate(mc.player.getEyePos(), point);
        return RotationUtil.calculateFov(mc.player.getYaw(), mc.player.getPitch(), rotation.x, rotation.y);
    }
}
