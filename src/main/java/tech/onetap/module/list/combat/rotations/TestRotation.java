package tech.onetap.module.list.combat.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.BestPoint;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.player.combat.PredictUtils;
import tech.onetap.util.player.combat.RaytraceUtil;
import tech.onetap.util.render.math.GCDFixer;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Test: yaw — поведение Sloth (разгон/торможение, реакция, усталость прицела),
 * pitch — плавный свип по всему диапазону -90..90: у горизонта скорость
 * максимальна, у крайних углов замирает и разворачивается. Pitch ведёт
 * следователь с ограничением скорости и ускорения (град/с, град/с², движение
 * привязано к реальному времени) — рывков нет. Внутреннее состояние точное,
 * GCD-квантование применяется только к отправляемому углу, поэтому на малых
 * скоростях доводка не залипает на полшага GCD. Перед ударом свип уступает
 * место плавной доводке к ближайшему краю хитбокса (минимальное смещение,
 * паттерн Universal), поэтому удар всегда наведён в цель.
 */
public class TestRotation extends RotationMode {

    // Состояние yaw-логики (как в SlothRotation)
    private float yawSpeed = 0.0F;
    private float aimFatigue = 0.0F;
    private int idleTicks = 0;
    private int reactionDelay = 0;
    private float jitterYaw = 0.0F;
    private float jitterPitch = 0.0F;
    private long lastJitterTime = 0L;

    // Состояние вертикального свипа -90..90
    private float sweepPhase = 0.0F;
    private long sweepPeriodMs = 4500L;
    private long lastFrameNanos = 0L;
    private boolean init = false;

    // Следователь pitch
    private float pitchVel = 0.0F;
    private float finishVmax = 500.0F;

    private static final float FREE_VMAX_FACTOR = 1.9F;
    private static final float FREE_ACCEL = 1300.0F;
    private static final float FINISH_ACCEL = 2800.0F;
    private static final float FREE_GAIN = 8.0F;
    private static final float FINISH_GAIN = 9.0F;

    private float currentYaw = 0.0F;
    private float currentPitch = 0.0F;
    private float outPitch = 0.0F;

    @Override
    public void update(KillAura ka, LivingEntity target) {
        var mc = ka.mc;
        if (target == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        long nowNanos = System.nanoTime();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // Инициализация при первом вызове или смене цели
        if (!init) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
            outPitch = currentPitch;
            sweepPeriodMs = r.nextLong(3500L, 6000L);
            finishVmax = r.nextFloat(460.0F, 540.0F);
            // Стартовая фаза под текущий pitch — свип начинается без рывка
            float rel = MathHelper.clamp(currentPitch / 90.0F, -1.0F, 1.0F);
            sweepPhase = (float) Math.asin(rel);
            if (r.nextBoolean()) sweepPhase = (float) (Math.PI - sweepPhase);
            lastFrameNanos = nowNanos;
            init = true;
        }

        // Движение привязано к реальному времени, а не к числу вызовов
        float dtSec = Math.min((nowNanos - lastFrameNanos) / 1_000_000_000.0F, 0.1F);
        lastFrameNanos = nowNanos;

        // Целевая точка наведения
        Vec3d point = ka.resolveMultipoint(target, BestPoint.getPoint2(target), 6);
        boolean predicted = false;
        if (target.isGliding() && ka.isElytraPredictActive() && !ka.isTurnaroundActive) {
            point = PredictUtils.getPredicted(target, ka.predictValue.getValue());
            predicted = true;
        }

        var targetAngle = new Rotation(RotationUtil.calculate(point));
        float targetYaw = targetAngle.getYaw();

        boolean isElytra = mc.player.isGliding();

        // Доводка перед ударом (как в Universal): свип уступает место
        // плавному подтягиванию к ближайшему краю хитбокса
        boolean preHit = ka.ticksToAttack <= 0
                && mc.player.getAttackCooldownProgress(0.5f) >= 0.9f
                && mc.player.getEyePos().distanceTo(BestPoint.getNearestPoint(target)) <= ka.distance.getValue() + 1.5;

        // Период свипа (на элитрах медленнее) и пиковая скорость синусоиды
        float periodSec = sweepPeriodMs / 1000.0F * (isElytra ? 2.2F : 1.0F);
        float sweepPeakVel = (float) (Math.PI * 2.0D * 90.0D / periodSec);

        float targetPitch;
        float targetPitchVel = 0.0F;
        float vmax;
        float accel;
        float gain;
        if (preHit) {
            // Вертикальная полоса хитбокса в колонне точки прицеливания
            double lowY;
            double highY;
            if (predicted) {
                lowY = point.y - target.getHeight();
                highY = point.y;
            } else {
                Box box = target.getBoundingBox();
                lowY = box.minY;
                highY = box.maxY;
            }
            float pitchLow = new Rotation(RotationUtil.calculate(new Vec3d(point.x, lowY, point.z))).getPitch();
            float pitchHigh = new Rotation(RotationUtil.calculate(new Vec3d(point.x, highY, point.z))).getPitch();
            float lo = Math.min(pitchLow, pitchHigh);
            float hi = Math.max(pitchLow, pitchHigh);
            // Ближайший край полосы с отступом: квантование GCD не выбьет
            // за хитбокс, смещение минимально
            float inset = Math.min(0.75F, (hi - lo) * 0.15F);
            float loInset = lo + inset;
            float hiInset = hi - inset;
            if (hiInset < loInset) {
                loInset = hiInset = (lo + hi) * 0.5F;
            }
            if (currentPitch < loInset) targetPitch = loInset;
            else if (currentPitch > hiInset) targetPitch = hiInset;
            else targetPitch = currentPitch;
            vmax = finishVmax;
            accel = FINISH_ACCEL;
            gain = FINISH_GAIN;
        } else {
            // Свободный свип по всему диапазону: 90 * sin(phase).
            // Скорость = производная (90 * cos(phase) * peak): у горизонта
            // максимальна, у -90 и +90 замирает
            sweepPhase += (float) (Math.PI * 2.0D * dtSec / periodSec);
            if (sweepPhase > Math.PI * 6.0F) {
                sweepPhase -= (float) (Math.PI * 2.0D);
            }
            targetPitch = 90.0F * MathHelper.sin(sweepPhase);
            targetPitchVel = 90.0F * MathHelper.cos(sweepPhase) * sweepPeakVel;
            vmax = sweepPeakVel * FREE_VMAX_FACTOR;
            accel = FREE_ACCEL;
            gain = FREE_GAIN;
        }

        // Следователь pitch: опережение скоростью цели + пропорциональное
        // подтягивание, ограничение скорости и ускорения — всегда плавно.
        // Контроллер ведёт точное (неквантованное) состояние
        float dist = targetPitch - currentPitch;
        float desiredVel = MathHelper.clamp(targetPitchVel + dist * gain, -vmax, vmax);
        pitchVel = MathHelper.clamp(desiredVel, pitchVel - accel * dtSec, pitchVel + accel * dtSec);
        currentPitch = MathHelper.clamp(currentPitch + pitchVel * dtSec, -90.0F, 90.0F);
        if (currentPitch >= 89.99F && pitchVel > 0.0F) pitchVel = 0.0F;
        if (currentPitch <= -89.99F && pitchVel < 0.0F) pitchVel = 0.0F;

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float yawDist = Math.abs(deltaYaw);

        boolean hasTrace = RaytraceUtil.rayTrace(mc.player.getRotationVector(), 999.0, target.getBoundingBox());

        // Микро-джиттер (обновляется каждые 30-80 мс; перед ударом pitch не дрожит)
        if (now - lastJitterTime > r.nextInt(30, 80)) {
            jitterYaw = (r.nextFloat() - 0.5F) * 0.12F;
            jitterPitch = preHit ? 0.0F : (r.nextFloat() - 0.5F) * 0.08F;
            lastJitterTime = now;
        }

        // Логика разгона / торможения yaw (как в Sloth)
        if (!hasTrace) {
            int reactionThreshold = 2 + Math.min(idleTicks / 3, 5) + r.nextInt(2);
            if (reactionDelay < reactionThreshold) {
                reactionDelay++;
                deltaYaw += (r.nextFloat() - 0.5F) * 1.2F;
            } else {
                float targetSpeed = yawDist > 12.0F ? 0.72F : 0.35F;
                yawSpeed = MathHelper.lerp(0.035F, yawSpeed, targetSpeed);
            }
            idleTicks = 0;
        } else {
            reactionDelay = 0;
            yawSpeed = MathHelper.lerp(0.55F, yawSpeed, 0.0F);
            idleTicks++;
        }

        // Усталость прицела — только от работы yaw (pitch по задумке далеко)
        aimFatigue = MathHelper.clamp(aimFatigue + yawDist * 0.00008F, 0.0F, 0.32F);
        if (hasTrace) aimFatigue *= 0.97F;

        float baseYawSpeed = r.nextFloat(8.0F, 14.0F) / (isElytra ? 2.2F : 1.0F);

        // Smoothstep
        float yawS = yawSpeed * yawSpeed * (3.0F - 2.0F * yawSpeed);
        float yawStep = baseYawSpeed * yawS * (1.0F - aimFatigue);

        // Замедление в воздухе
        if (!mc.player.isOnGround()) {
            deltaYaw *= 0.85F;
        }

        // Случайный сдвиг при долгом удержании прицела
        if (hasTrace && idleTicks > 3 && r.nextFloat() < 0.15F) {
            deltaYaw += (r.nextFloat() - 0.5F) * 2.0F;
        }

        float clampedYaw = MathHelper.clamp(deltaYaw, -yawStep, yawStep);
        if (hasTrace && Math.abs(clampedYaw) < 0.35F) clampedYaw = 0.0F;

        float newYaw = currentYaw + clampedYaw + jitterYaw;

        // GCD-фикс: квантуем только отправляемые углы относительно прошлого вывода
        float gcd = GCDFixer.getGCDValue();
        newYaw = currentYaw + Math.round((newYaw - currentYaw) / gcd) * gcd;
        float newOutPitch = outPitch + Math.round((currentPitch + jitterPitch - outPitch) / gcd) * gcd;
        newOutPitch = MathHelper.clamp(newOutPitch, -90.0F, 90.0F);

        Rotation rot = new Rotation(newYaw, newOutPitch);
        RotationComponent.update(rot, 360.0F, 360.0F, 360.0F, 360.0F, 0, 1, ka.clientLook.getValue(), ka.getMoveFixMode(), ka.otvodkaActive());

        currentYaw = newYaw;
        outPitch = newOutPitch;
        ka.lastYaw = newYaw;
        ka.lastPitch = newOutPitch;
    }

    @Override
    public void reset(KillAura ka) {
        init = false;
        pitchVel = 0.0F;
        yawSpeed = 0.0F;
        aimFatigue = 0.0F;
        idleTicks = 0;
        reactionDelay = 0;
    }
}
