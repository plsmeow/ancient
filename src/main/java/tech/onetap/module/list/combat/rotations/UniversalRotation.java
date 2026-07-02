package tech.onetap.module.list.combat.rotations;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.FreeLookComponent;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

/**
 * Ротация "Universal".
 *
 * Плавная, человекоподобная наводка для обхода SlothAC:
 *  1. Инерция — скорость плавно разгоняется и тормозит (никаких резких рывков),
 *     движемся долей от остатка, поэтому у цели наводка естественно замедляется.
 *  2. Динамичная скорость — целевая скорость зависит от дистанции до цели + случайная
 *     человеческая вариация, чтобы движения не повторялись.
 *  3. Таргет на разные части тела (живот -> грудь -> голова -> ...) с динамичным интервалом.
 *  4. Мягкое дрожание по таймеру (не каждый тик).
 *  5. Плавная отводка с хитбокса: смещение само по себе плавно лерпится туда и обратно,
 *     поэтому взгляд мягко уходит с цели и так же мягко возвращается.
 */
public class UniversalRotation extends RotationMode {

    private boolean init = false;
    private float curYaw = 0f;
    private float curPitch = 0f;
    private LivingEntity lastTarget = null;

    // сглаженные коэффициенты скорости (0..1) — дают инерцию (разгон/торможение)
    private float yawSpeed = 0f;
    private float pitchSpeed = 0f;

    // мягкое дрожание
    private float jitterYaw = 0f;
    private float jitterPitch = 0f;
    private long lastJitter = 0L;

    // циклическая смена части тела: 0 = живот, 1 = грудь, 2 = голова
    private int bodyPart = 0;
    private long lastBodyPartSwitch = 0L;
    private long bodyPartInterval = 500L;

    // плавная отводка с хитбокса
    private float lookAwayYaw = 0f;
    private float lookAwayPitch = 0f;
    private float lookAwayTargetYaw = 0f;
    private float lookAwayTargetPitch = 0f;
    private long nextLookAwayAt = 0L;

    // горизонтальное смещение точки, чтобы yaw не лип к центру хитбокса
    private double yawOffsetX = 0.0;
    private double yawOffsetZ = 0.0;
    private double yawTargetOffsetX = 0.0;
    private double yawTargetOffsetZ = 0.0;
    private long nextYawOffsetAt = 0L;
    private Vec3d smoothedAimPoint = null;
    private long nextAimPointReselectAt = 0L;

    // искусственная задержка трекинга (Vulcan-style), 150..250 мс
    private final Deque<Long> aimHistoryTimes = new ArrayDeque<>();
    private final Deque<Vec3d> aimHistoryPoints = new ArrayDeque<>();
    private long currentTrackDelayMs = 200L;
    private long nextTrackDelayUpdateAt = 0L;

    // микро-остановки во время поворота на 1-2 тика
    private int microStopTicks = 0;
    private long nextMicroStopAt = 0L;

    // фаза повторного захвата — некоторое время после возврата таргета
    // (или появления нового) скорость наводки ограничена, чтобы не было
    // подозрительного "снапа" обратно на цель
    private long reacquireUntil = 0L;

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
            aimHistoryTimes.clear();
            aimHistoryPoints.clear();
            currentTrackDelayMs = r.nextLong(150L, 251L);
            nextTrackDelayUpdateAt = now + r.nextLong(550L, 1300L);
        }

        if (target == null) {
            updateLostTarget(ka, now, r);
            return;
        }

        if (target != lastTarget) {
            boolean returning = lastTarget == null;
            lastTarget = target;
            yawSpeed *= 0.35f;
            pitchSpeed *= 0.35f;
            microStopTicks = r.nextInt(1, 4);
            nextMicroStopAt = now + r.nextLong(180L, 420L);
            smoothedAimPoint = null;
            nextAimPointReselectAt = 0L;
            aimHistoryTimes.clear();
            aimHistoryPoints.clear();
            currentTrackDelayMs = r.nextLong(150L, 251L);
            nextTrackDelayUpdateAt = now + r.nextLong(550L, 1300L);
            reacquireUntil = now + (returning ? r.nextLong(450L, 900L) : r.nextLong(220L, 480L));
        }

        boolean reacquire = now < reacquireUntil;

        // Фаза доводки: когда удар уже скоро возможен, перестаём добавлять
        // lookAway/задержку, прицел плавно подтягивается к хитбоксу. Без снапа.
        float cooldown = mc.player.getAttackCooldownProgress(0.5f);
        boolean closingIn = !reacquire && cooldown >= 0.75f && ka.ticksToAttack <= 1;

        // 1. Таргет на разные части тела с динамичным интервалом.
        if (now - lastBodyPartSwitch > bodyPartInterval) {
            bodyPart = (bodyPart + 1) % 3;
            lastBodyPartSwitch = now;
            bodyPartInterval = r.nextLong(450L, 950L);
        }

        Box box = target.getBoundingBox();
        double height = target.getHeight();
        double heightFactor = switch (bodyPart) {
            case 2 -> 0.88; // голова
            case 1 -> 0.62; // грудь
            default -> 0.45; // живот
        };
        double horizontalRangeX = Math.max(0.08, (box.maxX - box.minX) * 0.32);
        double horizontalRangeZ = Math.max(0.08, (box.maxZ - box.minZ) * 0.32);
        if (now >= nextYawOffsetAt || now >= nextAimPointReselectAt) {
            yawTargetOffsetX = r.nextDouble(
                -horizontalRangeX,
                horizontalRangeX
            );
            yawTargetOffsetZ = r.nextDouble(
                -horizontalRangeZ,
                horizontalRangeZ
            );

            if (
                Math.abs(yawTargetOffsetX) + Math.abs(yawTargetOffsetZ) < 0.07
            ) {
                yawTargetOffsetX += Math.copySign(
                    0.07,
                    r.nextBoolean() ? 1.0 : -1.0
                );
            }

            nextYawOffsetAt = now + r.nextLong(260L, 720L);
            nextAimPointReselectAt = Long.MAX_VALUE;
        }
        yawOffsetX = MathHelper.lerp(0.08, yawOffsetX, yawTargetOffsetX);
        yawOffsetZ = MathHelper.lerp(0.08, yawOffsetZ, yawTargetOffsetZ);

        Vec3d rawPoint = new Vec3d(
            target.getX() + yawOffsetX,
            box.minY + height * heightFactor,
            target.getZ() + yawOffsetZ
        );
        rawPoint = ka.resolveMultipoint(target, rawPoint, 6);

        if (
            target.isGliding() &&
            ka.predictate.getValue() &&
            !ka.isTurnaroundActive
        ) {
            rawPoint = PredictUtils.getPredicted(
                target,
                ka.predictValue.getValue()
            );
        }

        // Задержка трекинга 150..250 мс: точка для прицеливания берётся из истории,
        // что даёт лаг реакции, как у Vulcan, но плавный, без жёсткого захвата.
        if (now >= nextTrackDelayUpdateAt) {
            currentTrackDelayMs = r.nextLong(150L, 251L);
            nextTrackDelayUpdateAt = now + r.nextLong(550L, 1300L);
        }

        aimHistoryTimes.addLast(now);
        aimHistoryPoints.addLast(rawPoint);

        long cutoff = now - 700L;
        while (!aimHistoryTimes.isEmpty() && aimHistoryTimes.peekFirst() < cutoff) {
            aimHistoryTimes.pollFirst();
            aimHistoryPoints.pollFirst();
        }

        Vec3d delayedPoint = rawPoint;
        if (!closingIn) {
            long target1 = now - currentTrackDelayMs;
            Iterator<Long> itT = aimHistoryTimes.iterator();
            Iterator<Vec3d> itP = aimHistoryPoints.iterator();
            Vec3d best = null;
            long bestTime = Long.MIN_VALUE;
            while (itT.hasNext()) {
                long t = itT.next();
                Vec3d p = itP.next();
                if (t <= target1 && t > bestTime) {
                    bestTime = t;
                    best = p;
                }
            }
            if (best != null) {
                delayedPoint = best;
            } else if (!aimHistoryPoints.isEmpty()) {
                delayedPoint = aimHistoryPoints.peekFirst();
            }
        }

        rawPoint = delayedPoint;

        if (smoothedAimPoint == null) {
            smoothedAimPoint = rawPoint;
        } else {
            double pointJump = smoothedAimPoint.distanceTo(rawPoint);
            if (pointJump > 0.55) {
                nextAimPointReselectAt = now;
                yawTargetOffsetX = r.nextDouble(-horizontalRangeX, horizontalRangeX);
                yawTargetOffsetZ = r.nextDouble(-horizontalRangeZ, horizontalRangeZ);
            }

            double baseLerp = closingIn ? 0.55 : 0.32;
            double maxLerp = closingIn ? 0.85 : 0.75;
            double pointLerp = MathHelper.clamp(baseLerp + pointJump * 0.18, baseLerp, maxLerp);
            smoothedAimPoint = smoothedAimPoint.lerp(rawPoint, pointLerp);
        }

        if (closingIn) {
            // Мягко подтягиваем точку к ближайшей точке внутри хитбокса (без жёсткого клемпа),
            // чтобы прицел не выглядел как телепорт.
            double cx = MathHelper.clamp(smoothedAimPoint.x, box.minX + 0.05, box.maxX - 0.05);
            double cy = MathHelper.clamp(smoothedAimPoint.y, box.minY + 0.05, box.maxY - 0.05);
            double cz = MathHelper.clamp(smoothedAimPoint.z, box.minZ + 0.05, box.maxZ - 0.05);
            smoothedAimPoint = smoothedAimPoint.lerp(new Vec3d(cx, cy, cz), 0.45);
        }

        var ideal = new Rotation(RotationUtil.calculate(smoothedAimPoint));

        // 2. Плавная отводка: целевое смещение меняем редко, само смещение плавно лерпим
        //    к нему, а целевое смещение медленно затухает к нулю — взгляд мягко возвращается.
        if (now >= nextLookAwayAt) {
            lookAwayTargetYaw =
                (r.nextFloat() - 0.5f) * 2f * r.nextFloat(0.8f, 3.0f);
            lookAwayTargetPitch =
                (r.nextFloat() - 0.5f) * 2f * r.nextFloat(0.6f, 1.8f);
            nextLookAwayAt = now + r.nextLong(300L, 800L);
        }
        lookAwayYaw = MathHelper.lerp(0.08f, lookAwayYaw, lookAwayTargetYaw);
        lookAwayPitch = MathHelper.lerp(
            0.08f,
            lookAwayPitch,
            lookAwayTargetPitch
        );
        lookAwayTargetYaw = MathHelper.lerp(0.08f, lookAwayTargetYaw, 0f);
        lookAwayTargetPitch = MathHelper.lerp(0.08f, lookAwayTargetPitch, 0f);

        float targetYaw = ideal.getYaw() + (closingIn ? lookAwayYaw * 0.25f : lookAwayYaw);
        float targetPitch = ideal.getPitch() + (closingIn ? lookAwayPitch * 0.25f : lookAwayPitch);

        // 3. Мягкое дрожание — обновляется по таймеру, а не каждый тик.
        if (now - lastJitter > r.nextInt(45, 100)) {
            jitterYaw = (r.nextFloat() - 0.5f) * 0.8f;
            jitterPitch = (r.nextFloat() - 0.5f) * 0.5f;
            lastJitter = now;
        }

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - curYaw);
        float deltaPitch = targetPitch - curPitch;
        float dist = (float) Math.hypot(deltaYaw, deltaPitch);

        // 4. Динамичная, но плавная скорость.
        //    Целевая скорость зависит от дистанции: далеко -> разгон, близко -> торможение.
        float speedDivisor = closingIn ? 48f : (reacquire ? 130f : 65f);
        float speedMax = closingIn ? 0.55f : (reacquire ? 0.28f : 0.6f);
        float speedMin = closingIn ? 0.10f : (reacquire ? 0.018f : 0.05f);
        float targetSpeed =
            MathHelper.clamp(dist / speedDivisor, speedMin, speedMax) *
            r.nextFloat(0.85f, 1.05f);
        // инерция: плавно подтягиваем текущую скорость к целевой, без быстрого снапа на цель
        float speedLerp = closingIn ? 0.16f : (reacquire ? 0.045f : 0.11f);
        yawSpeed = MathHelper.lerp(speedLerp, yawSpeed, targetSpeed);
        pitchSpeed = MathHelper.lerp(speedLerp * 0.95f, pitchSpeed, targetSpeed);

        if (!closingIn && dist > 3.0f && microStopTicks <= 0 && now >= nextMicroStopAt) {
            microStopTicks = r.nextInt(1, 3);
            nextMicroStopAt = now + r.nextLong(220L, 520L);
        }

        if (microStopTicks > 0) {
            microStopTicks--;
            yawSpeed *= 0.82f;
            pitchSpeed *= 0.82f;

            var rot = new Rotation(curYaw, curPitch);
            RotationComponent.update(
                rot,
                360,
                360,
                360,
                360,
                0,
                1,
                ka.clientLook.getValue()
            );
            ka.lastYaw = curYaw;
            ka.lastPitch = curPitch;
            return;
        }

        // smoothstep — мягкий ease-in/ease-out
        float yawEase = yawSpeed * yawSpeed * (3f - 2f * yawSpeed);
        float pitchEase = pitchSpeed * pitchSpeed * (3f - 2f * pitchSpeed);

        // движемся долей от остатка (естественное замедление у цели)
        float stepYaw = deltaYaw * yawEase + jitterYaw * 0.45f;
        float stepPitch = deltaPitch * (pitchEase * 0.75f) + jitterPitch * 0.45f;

        // мягкое ограничение шага за тик, чтобы исключить резкие рывки
        float maxStepYaw = reacquire ? 3.2f : 9.0f;
        float maxStepPitch = reacquire ? 2.4f : 7.0f;
        stepYaw = MathHelper.clamp(stepYaw, -maxStepYaw, maxStepYaw);
        stepPitch = MathHelper.clamp(stepPitch, -maxStepPitch, maxStepPitch);

        // мёртвая зона возле цели — убираем микродрожь, когда уже наведены
        if (dist < 1.0f) {
            stepYaw *= 0.5f;
            stepPitch *= 0.5f;
        }

        float newYaw = curYaw + stepYaw;
        float newPitch = MathHelper.clamp(curPitch + stepPitch, -89f, 89f);

        // 5. GCD-фикс под сетку чувствительности мыши.
        float gcd = GCDFixer.getGCDValue();
        if (gcd > 0f) {
            newYaw = curYaw + Math.round((newYaw - curYaw) / gcd) * gcd;
            newPitch = curPitch + Math.round((newPitch - curPitch) / gcd) * gcd;
        }

        var rot = new Rotation(newYaw, newPitch);
        RotationComponent.update(
            rot,
            360,
            360,
            360,
            360,
            0,
            1,
            ka.clientLook.getValue()
        );

        curYaw = newYaw;
        curPitch = newPitch;
        ka.lastYaw = newYaw;
        ka.lastPitch = newPitch;
    }

    private void updateLostTarget(KillAura ka, long now, ThreadLocalRandom r) {
        // помечаем, что таргет потерян, чтобы при возврате включилась фаза reacquire
        lastTarget = null;

        float targetYaw = FreeLookComponent.getFreeYaw();
        float targetPitch = FreeLookComponent.getFreePitch();
        float deltaYaw = MathHelper.wrapDegrees(targetYaw - curYaw);
        float deltaPitch = targetPitch - curPitch;
        float dist = (float) Math.hypot(deltaYaw, deltaPitch);

        lookAwayYaw = MathHelper.lerp(0.04f, lookAwayYaw, 0f);
        lookAwayPitch = MathHelper.lerp(0.04f, lookAwayPitch, 0f);
        jitterYaw = MathHelper.lerp(0.18f, jitterYaw, 0f);
        jitterPitch = MathHelper.lerp(0.18f, jitterPitch, 0f);

        float targetSpeed = MathHelper.clamp(dist / 140f, 0.012f, 0.22f) * r.nextFloat(0.65f, 0.95f);
        yawSpeed = MathHelper.lerp(0.028f, yawSpeed, targetSpeed);
        pitchSpeed = MathHelper.lerp(0.026f, pitchSpeed, targetSpeed);

        if (dist > 2.0f && microStopTicks <= 0 && now >= nextMicroStopAt) {
            microStopTicks = r.nextInt(1, 4);
            nextMicroStopAt = now + r.nextLong(260L, 620L);
        }

        if (microStopTicks > 0) {
            microStopTicks--;
            updateRotation(ka, curYaw, curPitch, 2);
            return;
        }

        float yawEase = yawSpeed * yawSpeed * (3f - 2f * yawSpeed);
        float pitchEase = pitchSpeed * pitchSpeed * (3f - 2f * pitchSpeed);
        float stepYaw = MathHelper.clamp(deltaYaw * yawEase, -3.6f, 3.6f);
        float stepPitch = MathHelper.clamp(deltaPitch * (pitchEase * 0.75f), -2.6f, 2.6f);

        if (dist < 0.65f) {
            reset(ka);
            return;
        }

        float newYaw = curYaw + stepYaw;
        float newPitch = MathHelper.clamp(curPitch + stepPitch, -89f, 89f);

        float gcd = GCDFixer.getGCDValue();
        if (gcd > 0f) {
            newYaw = curYaw + Math.round((newYaw - curYaw) / gcd) * gcd;
            newPitch = curPitch + Math.round((newPitch - curPitch) / gcd) * gcd;
        }

        curYaw = newYaw;
        curPitch = newPitch;
        updateRotation(ka, newYaw, newPitch, 2);
        ka.lastYaw = newYaw;
        ka.lastPitch = newPitch;
    }

    private void updateRotation(KillAura ka, float yaw, float pitch, int timeout) {
        RotationComponent.update(
            new Rotation(yaw, pitch),
            360,
            360,
            18,
            14,
            timeout,
            1,
            ka.clientLook.getValue()
        );
    }

    @Override
    public void reset(KillAura ka) {
        init = false;
        lastTarget = null;
        yawSpeed = 0f;
        pitchSpeed = 0f;
        jitterYaw = 0f;
        jitterPitch = 0f;
        lastJitter = 0L;
        bodyPart = 0;
        lastBodyPartSwitch = 0L;
        bodyPartInterval = 500L;
        lookAwayYaw = 0f;
        lookAwayPitch = 0f;
        lookAwayTargetYaw = 0f;
        lookAwayTargetPitch = 0f;
        nextLookAwayAt = 0L;
        yawOffsetX = 0.0;
        yawOffsetZ = 0.0;
        yawTargetOffsetX = 0.0;
        yawTargetOffsetZ = 0.0;
        nextYawOffsetAt = 0L;
        smoothedAimPoint = null;
        nextAimPointReselectAt = 0L;
        aimHistoryTimes.clear();
        aimHistoryPoints.clear();
        currentTrackDelayMs = 200L;
        nextTrackDelayUpdateAt = 0L;
        microStopTicks = 0;
        nextMicroStopAt = 0L;
        reacquireUntil = 0L;
    }
}
