package tech.onetap.module.list.combat.rotations;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.rotation.FreeLookComponent;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationHelper;

/**
 * Universal Rotation v5 — стабильная человекоподобная наводка.
 *
 * v5: Исправлен tracking — relative offset (смещение хранится ОТНОСИТЕЛЬНО цели,
 *     а не как мировая координата). Цель двигается — точка едет вместе с ней.
 *     Убран tracking lag (150мс) — он давал дополнительный отстав.
 *     Убран distScale — замедление на малой дистанции убивало трекинг.
 */
public class UniversalRotation extends RotationMode {

    private boolean init = false;
    private float curYaw = 0f;
    private float curPitch = 0f;
    private LivingEntity lastTarget = null;

    private float yawSpeedSmooth = 0f;
    private float pitchSpeedSmooth = 0f;

    private float windYaw = 0f;
    private float aimFatigue = 0f;

    private int microStopTicks = 0;
    private long nextMicroStopAt = 0L;

    private float lookAwayDelta = 0f;
    private long nextLookAwayAt = 0L;

    private boolean overshootActive = false;
    private int overshootTicksLeft = 0;
    private float overshootYawDelta = 0f;

    private int reactionTicksLeft = -1;
    private float moveAngularDist = 0f;

    // Минимальный tracking lag (60-120мс).
    private long trackDelayMs = 90L;
    private long lastAimTime = 0L;
    private Vec3d lastAimPoint = null;

    // ─── Относительное смещение прицеливания (v5) ─────────────────────────
    // Храним СДВИГ от центра хитбокса (в блоках), а не мировую координату.
    // Каждый тик вычисляем: aimPoint = target.pos + offset.
    // Меняем offset раз в 2.5-5 сек — плавно, не прыгая.
    private double aimOffsetX = 0.0;
    private double aimOffsetY = 0.0;  // от нижней границы хитбокса (в долях высоты)
    private double aimOffsetZ = 0.0;
    private double aimOffsetTargetX = 0.0;
    private double aimOffsetTargetZ = 0.0;
    private long nextAimOffsetUpdate = 0L;

    // Маленький дрейф (±1°).
    private float aimDriftYaw = 0f;
    private float aimDriftPitch = 0f;
    private long nextAimDriftAt = 0L;

    private float jitterYaw = 0f;
    private long nextJitterAt = 0L;
    private long tremorPhase = 0L;

    private int ticksSinceTarget = 0;
    private float lastDistanceToTarget = 999f;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        if (!init) {
            curYaw = mc.player.getYaw();
            curPitch = mc.player.getPitch();
            init = true;
            reactionTicksLeft = -1;
            nextMicroStopAt = now + r.nextLong(200L, 500L);
        }

        if (target == null) {
            updateLostTarget(ka, now, r);
            return;
        }

        if (target != lastTarget) {
            lastTarget = target;
            windYaw = 0f;
            aimFatigue = 0f;
            lookAwayDelta = 0f;
            overshootActive = false;
            reactionTicksLeft = 1 + r.nextInt(3);
            microStopTicks = 0;
            nextMicroStopAt = now + r.nextLong(200L, 500L);
            ticksSinceTarget = 0;
            yawSpeedSmooth = 0.2f;
            pitchSpeedSmooth = 0.2f;

            // Стабильный random offset от центра хитбокса (меняется раз в 2.5-5 сек).
            pickNewOffsets(target, r);
            aimOffsetX = aimOffsetTargetX;
            aimOffsetZ = aimOffsetTargetZ;
            nextAimOffsetUpdate = now + r.nextLong(2500L, 5000L);

            lastDistanceToTarget = (float) mc.player.getEyePos().distanceTo(getAimPoint(ka, target));
        }

        ticksSinceTarget++;

        boolean canAttack = mc.player.getAttackCooldownProgress(0.5f) >= 0.9f && ka.ticksToAttack <= 0;

        // ─── 1. Точка прицеливания: offset от текущей позиции цели ──────────
        // Плавно меняем offset раз в 2.5-5 сек.
        if (now >= nextAimOffsetUpdate) {
            pickNewOffsets(target, r);
            nextAimOffsetUpdate = now + r.nextLong(2500L, 5000L);
        }
        // Плавно лерпим offset к целевому. Не 3% — слишком медленно для движущейся цели,
        // лучше 12% за тик (за ~10 тиков приходит).
        aimOffsetX = MathHelper.lerp(0.12, aimOffsetX, aimOffsetTargetX);
        aimOffsetZ = MathHelper.lerp(0.12, aimOffsetZ, aimOffsetTargetZ);

        Vec3d freshAimPoint = getAimPoint(ka, target);

        // Предикт элитры.
        if (target.isGliding() && ka.predictate.getValue() && !ka.isTurnaroundActive) {
            Vec3d predicted = PredictUtils.getPredicted(target, ka.predictValue.getValue());
            freshAimPoint = freshAimPoint.lerp(predicted, 0.5f);
        }

        // ─── Минимальный tracking lag (60-120мс) ────────────────────────────
        // Сэмплируем "свежую" точку раз в 60-120мс. Между сэмплами лерпим
        // — это даёт плавное отслеживание с лёгкой задержкой (характерно для
        // реального игрока, который не реагирует мгновенно).
        if (lastAimTime == 0L || now - lastAimTime >= trackDelayMs) {
            lastAimPoint = freshAimPoint;
            lastAimTime = now;
            trackDelayMs = r.nextLong(60L, 120L);
        }
        // Лерп от прошлого сэмпла к свежему (плавное подтягивание).
        Vec3d aimPoint = lastAimPoint != null
            ? lastAimPoint.lerp(freshAimPoint, 0.5f)
            : freshAimPoint;

        // Дрейф: быстрее обновляется, не затухает полностью (даёт живой lock).
        if (now >= nextAimDriftAt) {
            aimDriftYaw = (r.nextFloat() - 0.5f) * 2f * r.nextFloat(0.5f, 1.5f);
            aimDriftPitch = (r.nextFloat() - 0.5f) * 2f * r.nextFloat(0.3f, 1.0f);
            nextAimDriftAt = now + r.nextLong(70L, 180L);
        } else {
            // Слабое затухание — дрейф течёт, но не прыгает.
            aimDriftYaw = MathHelper.lerp(0.7f, aimDriftYaw, 0f);
            aimDriftPitch = MathHelper.lerp(0.7f, aimDriftPitch, 0f);
        }

        float targetYaw = RotationHelper.calculateRotation(aimPoint).getYaw() + aimDriftYaw;
        float targetPitch = RotationHelper.calculateRotation(aimPoint).getPitch() + aimDriftPitch;

        // ─── 2. Look-away ───────────────────────────────────────────────────
        lookAwayDelta = MathHelper.lerp(0.12f, lookAwayDelta, 0f);
        if (lookAwayDelta < 0.1f) lookAwayDelta = 0f;

        if (now >= nextLookAwayAt && !overshootActive && canAttack && lookAwayDelta == 0f) {
            float yawDiff = Math.abs(RotationHelper.angleDelta(curYaw, targetYaw));
            if (yawDiff > 80f) {
                lookAwayDelta = (r.nextFloat() - 0.5f) * 2f * r.nextFloat(3f, 8f);
                nextLookAwayAt = now + r.nextLong(400L, 800L);
            }
        }

        // ─── 3. Overshoot ───────────────────────────────────────────────────
        if (!overshootActive && canAttack && lookAwayDelta == 0f) {
            float yawDiff = RotationHelper.angleDelta(curYaw, targetYaw);
            overshootYawDelta = RotationHelper.checkOvershoot(yawDiff, moveAngularDist, r);
            if (overshootYawDelta != 0f) {
                overshootActive = true;
                overshootTicksLeft = 2 + r.nextInt(2);
            }
        }

        float effectiveYaw = targetYaw;
        if (overshootActive) {
            overshootTicksLeft--;
            if (overshootTicksLeft <= 0) {
                overshootActive = false;
            } else {
                effectiveYaw = targetYaw + overshootYawDelta * ((float) overshootTicksLeft / 3f);
            }
        }

        // ─── 4. Реакция (Go-сигнал) ────────────────────────────────────────
        if (reactionTicksLeft > 0) {
            reactionTicksLeft--;
            var rot = new Rotation(curYaw, curPitch);
            RotationHelper.apply(rot, ka);
            ka.lastYaw = curYaw;
            ka.lastPitch = curPitch;
            return;
        }

        // ─── 5. Наводка ─────────────────────────────────────────────────────
        float deltaYaw = RotationHelper.angleDelta(curYaw, effectiveYaw + lookAwayDelta);
        float deltaPitch = targetPitch - curPitch;
        float errDist = (float) Math.hypot(deltaYaw, deltaPitch);

        lastDistanceToTarget = (float) mc.player.getEyePos().distanceTo(aimPoint);

        aimFatigue = MathHelper.clamp(aimFatigue + errDist * 0.00008f, 0f, 0.32f);

        // WindMouse.
        if (errDist > 3f) {
            float windScale = MathHelper.clamp((errDist - 3f) / 27f, 0f, 1f);
            float windForce = (r.nextFloat() - 0.5f) * 2f * 1.2f * windScale;
            windYaw = MathHelper.lerp(0.5f, windYaw, windForce);
        } else {
            windYaw = MathHelper.lerp(0.3f, windYaw, 0f);
        }
        windYaw = MathHelper.clamp(windYaw, -1.5f, 1.5f);

        // ─── 6. Разгон скорости ─────────────────────────────────────────────
        // Первые ~15 тиков — плавный разгон. Потом — полная.
        float rampUp = MathHelper.clamp(ticksSinceTarget / 15f, 0f, 1f);
        rampUp = rampUp * rampUp * (3f - 2f * rampUp);

        float baseYawSpeed = r.nextFloat(8f, 14f);
        float basePitchSpeed = r.nextFloat(4f, 8f);

        float yawS = RotationHelper.smoothStep(yawSpeedSmooth);
        float pitchS = RotationHelper.smoothStep(pitchSpeedSmooth);

        float yawSpeedEff = baseYawSpeed * yawS * (1f - aimFatigue) * rampUp;
        float pitchSpeedEff = basePitchSpeed * pitchS * (1f - aimFatigue) * rampUp;

        if (errDist > 60f) {
            float fittsScale = MathHelper.clamp(60f / errDist, 0.5f, 1f);
            yawSpeedEff *= fittsScale;
            pitchSpeedEff *= fittsScale;
        }

        // ─── Микро-остановки ────────────────────────────────────────────────
        float progress = moveAngularDist > 0f ? (moveAngularDist - errDist) / moveAngularDist : 1f;
        if (microStopTicks <= 0 && now >= nextMicroStopAt && errDist > 3f) {
            boolean inMidpoint = moveAngularDist > 60f && progress > 0.42f && progress < 0.62f;
            if (r.nextFloat() < 0.06f || inMidpoint) {
                microStopTicks = 1 + r.nextInt(2);
                nextMicroStopAt = now + r.nextLong(250L, 550L);
            }
        }

        if (microStopTicks > 0) {
            microStopTicks--;
            yawSpeedSmooth *= 0.5f;
            pitchSpeedSmooth *= 0.5f;
            var rot = new Rotation(curYaw, curPitch);
            RotationHelper.apply(rot, ka);
            ka.lastYaw = curYaw;
            ka.lastPitch = curPitch;
            return;
        }

        // ─── Clamp + Dead zone ──────────────────────────────────────────────
        float stepYaw = RotationHelper.smoothRotation(curYaw, effectiveYaw, yawSpeedEff) - curYaw;
        float clampedYaw = RotationHelper.deadZone(stepYaw + windYaw, 0.35f);
        float clampedPitch = RotationHelper.deadZone(
            RotationHelper.smoothRotation(curPitch, targetPitch, pitchSpeedEff) - curPitch,
            0.18f
        );

        // Pitch: на маленькой ошибке (≤1°) уменьшаем шаг вдвое, но НЕ замораживаем полностью —
        // иначе прицел "лочится" на точке.
        if (Math.abs(targetPitch - curPitch) <= 1f) {
            clampedPitch *= 0.5f;
        }

        // ─── Джиттер + тремор ───────────────────────────────────────────────
        if (now >= nextJitterAt) {
            jitterYaw = (r.nextFloat() - 0.5f) * 0.12f;
            nextJitterAt = now + r.nextLong(60L, 120L);
        } else {
            jitterYaw *= 0.85f;
        }

        float tremorY = RotationHelper.tremor(tremorPhase++, now, yawSpeedEff / 14f);

        // ─── Итоговый шаг ───────────────────────────────────────────────────
        float newYaw = curYaw + clampedYaw + jitterYaw + tremorY;
        float newPitch = MathHelper.clamp(curPitch + clampedPitch, -89f, 89f);

        newYaw = RotationHelper.applyGCD(curYaw, newYaw);
        newPitch = RotationHelper.applyGCD(curPitch, newPitch);
        newPitch = RotationHelper.clampPitch(newPitch);

        yawSpeedSmooth = MathHelper.lerp(0.035f, yawSpeedSmooth,
            RotationHelper.fittsSpeedFactor(errDist));
        pitchSpeedSmooth = MathHelper.lerp(0.035f, pitchSpeedSmooth,
            RotationHelper.fittsSpeedFactor(Math.abs(deltaPitch)));
        moveAngularDist = errDist;

        var rot = new Rotation(newYaw, newPitch);
        RotationHelper.apply(rot, ka);

        curYaw = newYaw;
        curPitch = newPitch;
        ka.lastYaw = newYaw;
        ka.lastPitch = newPitch;
    }

    /**
     * Вычисляет точку прицеливания = текущая позиция цели + стабильный offset.
     * Offset хранится в блоках от центра хитбокса и меняется раз в 2.5-5 сек.
     * Точка ВСЕГДА едет вместе с целью.
     */
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

    /**
     * Генерирует новые случайные offset-ы от центра хитбокса.
     * Маленькие: ±30% ширины/глубины, высота 55-75%.
     */
    private void pickNewOffsets(LivingEntity target, ThreadLocalRandom r) {
        Box box = target.getBoundingBox();
        double halfW = (box.maxX - box.minX) * 0.3;
        double halfD = (box.maxZ - box.minZ) * 0.3;
        aimOffsetTargetX = (r.nextDouble() - 0.5) * 2.0 * halfW;
        aimOffsetTargetZ = (r.nextDouble() - 0.5) * 2.0 * halfD;
        aimOffsetY = 0.0 + r.nextDouble(0.0, 0.2);
    }

    private void updateLostTarget(KillAura ka, long now, ThreadLocalRandom r) {
        lastTarget = null;

        float targetYaw = FreeLookComponent.getFreeYaw();
        float targetPitch = FreeLookComponent.getFreePitch();
        float deltaYaw = RotationHelper.angleDelta(curYaw, targetYaw);
        float deltaPitch = targetPitch - curPitch;
        float dist = (float) Math.hypot(deltaYaw, deltaPitch);

        float targetSpeed = MathHelper.clamp(dist / 140f, 0.012f, 0.22f) * r.nextFloat(0.65f, 0.95f);
        yawSpeedSmooth = MathHelper.lerp(0.008f, yawSpeedSmooth, targetSpeed);
        pitchSpeedSmooth = MathHelper.lerp(0.006f, pitchSpeedSmooth, targetSpeed);

        yawSpeedSmooth = Math.min(yawSpeedSmooth, 0.55f);
        pitchSpeedSmooth = Math.min(pitchSpeedSmooth, 0.45f);

        if (microStopTicks <= 0 && now >= nextMicroStopAt && dist > 2f) {
            microStopTicks = r.nextInt(1, 4);
            nextMicroStopAt = now + r.nextLong(260L, 620L);
        }

        if (microStopTicks > 0) {
            microStopTicks--;
            RotationHelper.apply(new Rotation(curYaw, curPitch), ka);
            ka.lastYaw = curYaw;
            ka.lastPitch = curPitch;
            return;
        }

        float yawIn = MathHelper.clamp(Math.abs(yawSpeedSmooth) / 6f, 0f, 1f);
        float pitchIn = MathHelper.clamp(Math.abs(pitchSpeedSmooth) / 4f, 0f, 1f);
        float stepYaw = Math.copySign(RotationHelper.smoothStep(yawIn) * 6f, yawSpeedSmooth);
        float stepPitch = Math.copySign(RotationHelper.smoothStep(pitchIn) * 4f, pitchSpeedSmooth) * 0.75f;

        if (dist < 0.65f) {
            reset(ka);
            return;
        }

        float newYaw = RotationHelper.applyGCD(curYaw, curYaw + stepYaw);
        float newPitch = RotationHelper.applyGCD(curPitch, curPitch + stepPitch);
        newPitch = RotationHelper.clampPitch(newPitch);

        curYaw = newYaw;
        curPitch = newPitch;
        RotationHelper.apply(new Rotation(newYaw, newPitch), ka);
        ka.lastYaw = newYaw;
        ka.lastPitch = newPitch;
    }

    @Override
    public void reset(KillAura ka) {
        init = false;
        lastTarget = null;
        yawSpeedSmooth = 0f;
        pitchSpeedSmooth = 0f;
        windYaw = 0f;
        aimFatigue = 0f;
        microStopTicks = 0;
        nextMicroStopAt = 0L;
        lookAwayDelta = 0f;
        nextLookAwayAt = 0L;
        overshootActive = false;
        overshootTicksLeft = 0;
        overshootYawDelta = 0f;
        reactionTicksLeft = -1;
        moveAngularDist = 0f;
        trackDelayMs = 90L;
        lastAimTime = 0L;
        lastAimPoint = null;
        aimOffsetX = 0.0;
        aimOffsetY = 0.0;
        aimOffsetZ = 0.0;
        aimOffsetTargetX = 0.0;
        aimOffsetTargetZ = 0.0;
        nextAimOffsetUpdate = 0L;
        aimDriftYaw = 0f;
        aimDriftPitch = 0f;
        nextAimDriftAt = 0L;
        jitterYaw = 0f;
        nextJitterAt = 0L;
        tremorPhase = 0L;
        ticksSinceTarget = 0;
        lastDistanceToTarget = 999f;
    }
}
