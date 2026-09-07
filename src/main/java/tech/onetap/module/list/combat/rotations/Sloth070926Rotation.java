package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.module.list.combat.rotations.test2.ApproachController;
import tech.onetap.module.list.combat.rotations.test2.HumanizedRotationController;
import tech.onetap.module.list.combat.rotations.test2.RotationNoise;
import tech.onetap.module.list.combat.rotations.test2.RotationState;
import tech.onetap.module.list.combat.rotations.test2.TargetPointSelector;
import tech.onetap.module.list.combat.rotations.test2.VerticalCorrectionController;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sloth 07.09.26: замороженная копия {@link Test2Rotation} — та же
 * humanized-ротация с фазами и вертикальной доводкой, но без собственных
 * настроек: параметры Test2 захардкожены (снимок значений от 07.09.26).
 * Общие настройки KillAura (дистанция, предикт, MoveFix, отводка и т.д.)
 * действуют как обычно.
 */
public class Sloth070926Rotation extends RotationMode {

    // Снимок настроек Test2 от 07.09.26 — режим заморожен, значения не менять
    private static final float OFFSET_MIN = 15.0F;
    private static final float OFFSET_MAX = 25.0F;
    private static final float APPROACH_SPEED = 485.0F;
    private static final float CORRECTION_SPEED = 580.0F;
    private static final float MIN_DURATION = 0.15F;
    private static final float MAX_DURATION = 0.35F;
    private static final float YAW_ACCEL = 13.5F;
    private static final float PITCH_ACCEL = 2.5F;
    private static final float DAMPING_YAW = 0.95F;
    private static final float DAMPING_PITCH = 0.95F;
    private static final float MICRO_AMP = 0.14F;
    private static final float MICRO_FREQ = 5.0F;
    private static final float OVERSHOOT_STRENGTH = 0.4F;
    private static final float OVERSHOOT_CHANCE = 0.98F;
    private static final boolean DYNAMIC_SPEED = false;
    private static final boolean DYNAMIC_DURATION = true;
    private static final TargetPointSelector.Mode TARGET_POINT = TargetPointSelector.Mode.ABOVE_CENTER;

    private final TargetPointSelector targetPointSelector = new TargetPointSelector();
    private final ApproachController approachController = new ApproachController();
    private final HumanizedRotationController controller = new HumanizedRotationController();
    private final VerticalCorrectionController correctionController = new VerticalCorrectionController();
    private final RotationNoise noise = new RotationNoise();
    private final RotationState state = new RotationState();

    private boolean init;
    private int targetId = Integer.MIN_VALUE;
    private int prevTicksToAttack;
    private float verticalFactor = 0.5f;
    private float cycleMaxSpeedScale = 1.0f;
    private long recoveryUntilMs;
    private long lastFrameNanos;
    private float outYaw;
    private float outPitch;
    // Стабильная точка прицеливания: центр хитбокса сглаживается, скорость
    // цели даёт опережение, офсет XZ фиксирован на весь цикл
    private Vec3d smoothCenter;
    private Vec3d targetVelSmooth = Vec3d.ZERO;
    private float cycleOffsetX;
    private float cycleOffsetZ;
    private Vec3d lastHitPoint;
    private float smoothVelYaw;
    private float smoothVelPitch;

    // Точки для отладочного рендера
    private Vec3d debugActualPoint;
    private Vec3d debugApproachPoint;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (target == null || mc.player == null) return;

        ThreadLocalRandom r = ThreadLocalRandom.current();
        long now = System.currentTimeMillis();
        long nowNanos = System.nanoTime();

        float dtSec = (nowNanos - lastFrameNanos) / 1_000_000_000.0F;
        if (!init) {
            controller.init(mc.player.getYaw(), mc.player.getPitch());
            outYaw = mc.player.getYaw();
            outPitch = mc.player.getPitch();
            lastFrameNanos = nowNanos;
            prevTicksToAttack = ka.ticksToAttack;
            init = true;
            state.reset();
            beginCycle(ka, target, false);
            lastFrameNanos = nowNanos;
            return;
        }
        if (dtSec <= 0.0F || Float.isNaN(dtSec)) {
            lastFrameNanos = nowNanos;
            return;
        }
        dtSec = Math.min(dtSec, 0.1F);
        lastFrameNanos = nowNanos;

        // Смена цели: пересчитать траекторию, не телепортировать и не
        // обнулять состояние без необходимости — через мягкий RECOVERY
        boolean retargeted = targetId != target.getId();
        if (retargeted) {
            controller.scaleVelocity(0.7F);
            noise.scaleAmplitude(0.7F);
            beginCycle(ka, target, true);
        }

        // Фактическая точка попадания: multipoint с вертикальным фактором
        Vec3d hitPoint = resolveHitPoint(ka, target, dtSec);
        var actual = new Rotation(RotationUtil.calculate(hitPoint));
        float actualYaw = actual.getYaw();
        float actualPitch = actual.getPitch();

        // Угловая скорость цели (град/с): опережение для контроллера.
        // Сглаженная, чтобы тиковые шаги позиции не давали рывков
        float targetVelYaw = 0.0F;
        float targetVelPitch = 0.0F;
        if (lastHitPoint != null && dtSec > 0.0F) {
            var lastAngle = new Rotation(RotationUtil.calculate(lastHitPoint));
            float rawVelYaw = MathHelper.wrapDegrees(actualYaw - lastAngle.getYaw()) / dtSec;
            float rawVelPitch = MathHelper.clamp(
                    MathHelper.wrapDegrees(actualPitch - lastAngle.getPitch()) / dtSec, -300.0F, 300.0F);
            float vf = 1.0F - (float) Math.exp(-12.0D * dtSec);
            smoothVelYaw += (rawVelYaw - smoothVelYaw) * vf;
            smoothVelPitch += (rawVelPitch - smoothVelPitch) * vf;
            targetVelYaw = MathHelper.clamp(smoothVelYaw, -240.0F, 240.0F);
            targetVelPitch = MathHelper.clamp(smoothVelPitch, -180.0F, 180.0F);
        }
        lastHitPoint = hitPoint;
        float approachPitch = approachController.approachPitch(actualPitch);

        double distBlocks = mc.player.getEyePos().distanceTo(BestPoint.getNearestPoint(target));
        boolean attackImminent = ka.ticksToAttack <= 0
                && mc.player.getAttackCooldownProgress(0.5f) >= 0.9f
                && distBlocks <= ka.distance.getValue() + 1.5;
        boolean attackFired = prevTicksToAttack <= 1 && ka.ticksToAttack >= 5;

        RotationState.Phase phase = state.getPhase();
        if (phase == RotationState.Phase.IDLE || retargeted) {
            phase = retargeted ? RotationState.Phase.RECOVERY : RotationState.Phase.APPROACH;
        }

        // Атака прошла → плавный возврат к следующей естественной ориентации
        if (attackFired) {
            correctionController.deactivate();
            beginCycle(ka, target, true);
            phase = RotationState.Phase.RECOVERY;
        }

        switch (phase) {
            case RECOVERY -> {
                if (now >= recoveryUntilMs) phase = RotationState.Phase.APPROACH;
            }
            case APPROACH -> {
                if (controller.isTrajectoryFinished() && pitchNear(currentPitchError(approachPitch))) {
                    // Зависящий от скорости overshoot: только при быстром
                    // входе и с шансом — не фиксированный каждый цикл
                    float entrySpeed = Math.abs(controller.getVelocityPitch());
                    if (entrySpeed > 25.0F && r.nextFloat() < OVERSHOOT_CHANCE) {
                        controller.scaleVelocity(1.0F + OVERSHOOT_STRENGTH * 0.6F);
                    }
                    phase = RotationState.Phase.SETTLE;
                }
            }
            case SETTLE -> {
                // Ожидание окна атаки; при его открытии — доводка
                if (attackImminent) {
                    correctionController.begin(controller.getCurrentPitch(), actualPitch,
                            CORRECTION_SPEED);
                    phase = RotationState.Phase.CORRECTION;
                }
            }
            case CORRECTION -> {
                correctionController.update(dtSec);
                float pitchError = Math.abs(controller.getCurrentPitch() - actualPitch);
                if (!attackImminent) {
                    // Окно закрылось (цель ушла/кулдаун сброшен) — без рывка назад
                    correctionController.deactivate();
                    phase = RotationState.Phase.SETTLE;
                } else if (correctionController.isFinished() && pitchError < 1.2F) {
                    phase = RotationState.Phase.ATTACK;
                }
            }
            case ATTACK -> {
                if (!attackImminent) {
                    phase = RotationState.Phase.SETTLE;
                }
            }
            default -> {
            }
        }

        // Цель по pitch зависит от фазы; yaw всегда ведёт к цели плавно
        float pitchTarget;
        HumanizedRotationController.Params params;
        float elytraScale = mc.player.isGliding() ? 0.45F : 1.0F;
        float approachMax = APPROACH_SPEED * cycleMaxSpeedScale * elytraScale;
        float correctionMax = CORRECTION_SPEED * elytraScale;
        float yawAccel = YAW_ACCEL;
        float pitchAccel = PITCH_ACCEL;
        float dampingYaw = DAMPING_YAW;
        float dampingPitch = DAMPING_PITCH;

        switch (phase) {
            case SETTLE -> {
                pitchTarget = approachPitch;
                params = new HumanizedRotationController.Params(
                        9.0F, 4.0F,
                        approachMax * 0.85F, approachMax * 0.5F,
                        yawAccel, pitchAccel * 0.8F,
                        dampingYaw, dampingPitch,
                        6.0F, 6.0F);
            }
            case CORRECTION, ATTACK -> {
                pitchTarget = correctionController.isActive()
                        ? correctionController.samplePitch(actualPitch)
                        : actualPitch;
                params = new HumanizedRotationController.Params(
                        14.0F, 12.0F,
                        Math.max(approachMax * 0.75F, 60.0F), correctionMax,
                        yawAccel, pitchAccel * 1.5F,
                        dampingYaw, Math.max(dampingPitch * 0.7F, 0.2F),
                        3.0F, 2.0F);
            }
            case RECOVERY -> {
                pitchTarget = approachPitch;
                params = new HumanizedRotationController.Params(
                        8.0F, 5.0F,
                        approachMax * 0.9F, approachMax * 0.7F,
                        yawAccel * 0.9F, pitchAccel * 0.8F,
                        dampingYaw, dampingPitch,
                        6.0F, 4.5F);
            }
            default -> {
                pitchTarget = approachPitch;
                params = new HumanizedRotationController.Params(
                        16.0F, 10.0F,
                        approachMax, approachMax,
                        yawAccel, pitchAccel,
                        dampingYaw, dampingPitch,
                        4.0F, 3.0F);
            }
        }

        controller.update(params, dtSec, actualYaw, pitchTarget, targetVelYaw, targetVelPitch);


        // Микродвижение: низкочастотный плавный шум, перед ударом почти выключен
        float microAmp = MICRO_AMP;
        float phaseNoiseScale = (phase == RotationState.Phase.CORRECTION || phase == RotationState.Phase.ATTACK)
                ? 0.25F : 1.0F;
        noise.update(now, dtSec, MICRO_FREQ,
                microAmp * 0.4F * phaseNoiseScale, microAmp * phaseNoiseScale, r);

        // Выход: точное внутреннее состояние + шум, GCD только на выводе
        float gcd = GCDFixer.getGCDValue();
        float rawYaw = controller.getCurrentYaw() + noise.getYaw();
        float rawPitch = controller.getCurrentPitch() + noise.getPitch();
        float newOutYaw = outYaw + Math.round((rawYaw - outYaw) / gcd) * gcd;
        float newOutPitch = outPitch + Math.round((rawPitch - outPitch) / gcd) * gcd;
        newOutPitch = MathHelper.clamp(newOutPitch, -90.0F, 90.0F);
        if (Math.abs(MathHelper.wrapDegrees(newOutYaw - rawYaw)) > 30.0F) {
            newOutYaw = rawYaw;
        }

        Rotation rot = new Rotation(newOutYaw, newOutPitch);
        RotationComponent.update(rot, 360.0F, 360.0F, 360.0F, 360.0F, 0, 1,
                ka.clientLook.getValue(), ka.getMoveFixMode(), ka.otvodkaActive());

        outYaw = newOutYaw;
        outPitch = newOutPitch;
        ka.lastYaw = newOutYaw;
        ka.lastPitch = newOutPitch;
        prevTicksToAttack = ka.ticksToAttack;

        // Снимок состояния (отладка + isReadyForAttack)
        debugActualPoint = hitPoint;
        debugApproachPoint = approachPointWorld(hitPoint, approachPitch);
        state.snapshot(phase,
                newOutYaw, newOutPitch,
                actualYaw, actualPitch,
                approachPitch,
                MathHelper.wrapDegrees(controller.getCurrentYaw() - actualYaw),
                MathHelper.wrapDegrees(controller.getCurrentPitch() - actualPitch),
                controller.getVelocityYaw(), controller.getVelocityPitch(),
                controller.getAccelerationYaw(), controller.getAccelerationPitch(),
                controller.getProgress(),
                phase == RotationState.Phase.ATTACK);
    }
    /**
     * Точка попадания, стабильная в течение цикла: центр хитбокса
     * (сглаженный от тиковых шагов) + опережение по скорости цели +
     * фиксированный на цикл офсет XZ внутри хитбокса + вертикальный
     * фактор. Качающаяся multipoint-точка здесь не используется: при
     * плавном следователе она даёт установившуюся ошибку больше допуска
     * raycast и наведение не сходится.
     *
     * @param dtSec шаг времени для сглаживания (0 — не обновлять фильтр)
     */
    private Vec3d resolveHitPoint(KillAura ka, LivingEntity target, float dtSec) {
        if (target.isGliding() && ka.isElytraPredictActive() && !ka.isTurnaroundActive) {
            return ka.resolveMultipoint(target, PredictUtils.getPredicted(target, ka.predictValue.getValue()), 6);
        }

        Box box = target.getBoundingBox();
        Vec3d center = box.getCenter();
        if (smoothCenter == null) {
            smoothCenter = center;
            targetVelSmooth = target.getVelocity();
        } else if (dtSec > 0.0F) {
            // Центр хитбокса шагает с тиковой частотой — экспоненциальное
            // сглаживание превращает шаги в непрерывное движение
            float centerF = 1.0F - (float) Math.exp(-18.0D * dtSec);
            smoothCenter = smoothCenter.add(center.subtract(smoothCenter).multiply(centerF));
            float velF = 1.0F - (float) Math.exp(-10.0D * dtSec);
            targetVelSmooth = targetVelSmooth.add(target.getVelocity().subtract(targetVelSmooth).multiply(velF));
        }

        // Опережение компенсирует лаг сглаживания и следователя
        Vec3d eyes = mc.player.getEyePos();
        double dist = eyes.distanceTo(center);
        double lead = MathHelper.clamp(dist * 0.025D, 0.03D, 0.12D);
        Vec3d predicted = smoothCenter.add(targetVelSmooth.multiply(lead));

        // Офсет XZ фиксирован на цикл и не выходит за хитбокс
        double halfW = (box.maxX - box.minX) * 0.5D;
        double halfD = (box.maxZ - box.minZ) * 0.5D;
        double ox = MathHelper.clamp(cycleOffsetX, -1.0F, 1.0F) * halfW * 0.6D;
        double oz = MathHelper.clamp(cycleOffsetZ, -1.0F, 1.0F) * halfD * 0.6D;

        float factor = MathHelper.clamp(verticalFactor, 0.05F, 0.95F);
        double height = Math.max(box.maxY - box.minY, 0.1D);
        Vec3d point = new Vec3d(predicted.x + ox, box.minY + height * factor, predicted.z + oz);
        return ka.resolveMultipoint(target, point, 6);
    }

    /** Новый цикл ротации: точка цели, офсеты, длительность и траектория. */
    private void beginCycle(KillAura ka, LivingEntity target, boolean softEntry) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        var mc = ka.mc;
        if (mc.player == null) return;

        boolean retarget = softEntry && targetId != target.getId();
        targetId = target.getId();

        // При смене цели пере-якорим сглаженный центр, при повторном цикле
        // по той же цели фильтр продолжает непрерывно
        if (retarget || smoothCenter == null) {
            smoothCenter = target.getBoundingBox().getCenter();
            targetVelSmooth = target.getVelocity();
        }

        verticalFactor = targetPointSelector.pickVerticalFactor(r, TARGET_POINT);
        cycleOffsetX = (r.nextFloat() - 0.5F) * 2.0F;
        cycleOffsetZ = (r.nextFloat() - 0.5F) * 2.0F;

        Vec3d hitPoint = resolveHitPoint(ka, target, 0.0F);
        var actual = new Rotation(RotationUtil.calculate(hitPoint));

        double distBlocks = mc.player.getEyePos().distanceTo(BestPoint.getNearestPoint(target));
        approachController.newCycle(r,
                OFFSET_MIN, OFFSET_MAX,
                45.0F, distBlocks, target.getHeight(), controller.getCurrentPitch());
        float approachPitch = approachController.approachPitch(actual.getPitch());

        float yawError = MathHelper.wrapDegrees(actual.getYaw() - controller.getCurrentYaw());
        float pitchError = MathHelper.wrapDegrees(approachPitch - controller.getCurrentPitch());
        float angular = (float) Math.hypot(yawError, pitchError);
        if (Float.isNaN(angular)) angular = 30.0F;

        // Длительность от углового расстояния, с вариативностью
        float minDur = MIN_DURATION;
        float maxDur = MAX_DURATION;
        if (maxDur < minDur) maxDur = minDur;
        float durationMs;
        if (DYNAMIC_DURATION) {
            float t = MathHelper.clamp((angular - 15.0F) / 135.0F, 0.0F, 1.0F);
            durationMs = MathHelper.lerp(t, minDur, maxDur) * (0.9F + r.nextFloat() * 0.2F);
        } else {
            durationMs = (minDur + maxDur) * 0.5F;
        }
        if (mc.player.isGliding()) durationMs *= 1.6F;

        if (DYNAMIC_SPEED) {
            cycleMaxSpeedScale = MathHelper.clamp(angular / 50.0F, 0.7F, 1.5F);
        } else {
            cycleMaxSpeedScale = 1.0F;
        }

        controller.beginMovement(durationMs / 1000.0F);
        recoveryUntilMs = System.currentTimeMillis() + (long) Math.min(250.0F, durationMs * 0.35F);
        if (!softEntry) {
            state.snapshot(RotationState.Phase.APPROACH,
                    outYaw, outPitch, actual.getYaw(), actual.getPitch(), approachPitch,
                    0f, 0f, 0f, 0f, 0f, 0f, 0f, false);
        }
    }

    private float currentPitchError(float approachPitch) {
        return MathHelper.wrapDegrees(controller.getCurrentPitch() - approachPitch);
    }

    private boolean pitchNear(float error) {
        return Math.abs(error) < 4.0F;
    }

    /** Мировая точка подхода (для отладочного рендера): инверсия pitch в высоту. */
    private Vec3d approachPointWorld(Vec3d hitPoint, float approachPitch) {
        if (mc.player == null || hitPoint == null) return hitPoint;
        Vec3d eyes = mc.player.getEyePos();
        double dxz = Math.hypot(hitPoint.x - eyes.x, hitPoint.z - eyes.z);
        if (dxz < 0.05D) return hitPoint;
        float clamped = MathHelper.clamp(approachPitch, -89.0F, 89.0F);
        double approachY = eyes.y - Math.tan(Math.toRadians(clamped)) * dxz;
        return new Vec3d(hitPoint.x, approachY, hitPoint.z);
    }

    public RotationState getRotationState() {
        return state;
    }

    public boolean isReadyForAttack() {
        return state.isReadyForAttack();
    }

    public Vec3d getDebugActualPoint() {
        return debugActualPoint;
    }


    public Vec3d getDebugApproachPoint() {
        return debugApproachPoint;
    }

    @Override
    public void reset(KillAura ka) {
        init = false;
        targetId = Integer.MIN_VALUE;
        prevTicksToAttack = 0;
        smoothCenter = null;
        targetVelSmooth = Vec3d.ZERO;
        cycleOffsetX = 0.0F;
        cycleOffsetZ = 0.0F;
        controller.reset();
        noise.reset();
        correctionController.deactivate();
        state.reset();
        debugActualPoint = null;
        lastHitPoint = null;
        smoothVelYaw = 0.0F;
        smoothVelPitch = 0.0F;
        debugApproachPoint = null;
    }
}
