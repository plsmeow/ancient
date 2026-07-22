package tech.onetap.util.rotation;

import lombok.experimental.UtilityClass;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.module.list.combat.KillAura;
import tech.onetap.util.math.RotationUtil;
import tech.onetap.util.render.math.GCDFixer;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Утилита для человекоподобных ротаций.
 * Централизует всю математику, которую раньше каждый модуль дублировал:
 *   - smoothRotation (clamp delta по скорости)
 *   - applyGCD (стабильный GCD-фикс)
 *   - deadZone (нулевая зона при попадании)
 *   - calculateRotation (из Vec3d → Rotation)
 *   - addTremor (физиологический тремор ~9 Гц, только yaw)
 *   - addDrift (инерционный low-pass дрейф, медленно течёт)
 *   - smoothStep (квадратичный smoothstep)
 *   - microStop (замедление в середине пути)
 */
@UtilityClass
public class RotationHelper {

    // ─── Базовые математические операции ────────────────────────────────────

    /**
     * Угловая дистанция между двумя углами (wrapDegrees).
     */
    public static float angleDelta(float from, float to) {
        return MathHelper.wrapDegrees(to - from);
    }

    /**
     * Квадратичный smoothstep: плавное ускорение/замедление.
     * Вход: 0..1, выход: 0..1. Нелинейная интерполяция.
     */
    public static float smoothStep(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    /**
     * Ограничение pitch: всегда [-89, 89].
     */
    public static float clampPitch(float pitch) {
        return MathHelper.clamp(pitch, -89f, 89f);
    }

    // ─── GCD-фикс ──────────────────────────────────────────────────────────

    /**
     * Стабильный GCD-фикс: округляет шаг до ближайшего кратного GCD.
     * Важно: GCD должен быть стабильным (Intave RotationSensitivityHeuristic).
     * Если GCD слишком мал, возвращает from (шаг = 0).
     */
    public static float applyGCD(float from, float to) {
        float gcd = GCDFixer.getGCDValue();
        if (gcd <= 0f) return to;
        float delta = to - from;
        if (Math.abs(delta) < gcd * 0.5f) return from;
        return from + Math.round(delta / gcd) * gcd;
    }

    // ─── Smooth rotation (основной метод) ───────────────────────────────────

    /**
     * Плавный поворот к цели: clamp(deltaYaw, -yawSpeed, yawSpeed).
     * Возвращает новое значение yaw/pitch.
     *
     * Паттерн использования:
     *   float newYaw = RotationHelper.smoothRotation(curYaw, targetYaw, yawSpeed);
     *   float newPitch = RotationHelper.smoothRotation(curPitch, targetPitch, pitchSpeed);
     *   newPitch = RotationHelper.clampPitch(newPitch);
     *   newYaw = RotationHelper.applyGCD(curYaw, newYaw);
     *   newPitch = RotationHelper.applyGCD(curPitch, newPitch);
     */
    public static float smoothRotation(float current, float target, float speed) {
        float delta = angleDelta(current, target);
        float clamped = MathHelper.clamp(delta, -speed, speed);
        return current + clamped;
    }

    /**
     * Плавный поворот с автоматическим GCD-фиксом.
     */
    public static float smoothRotationGCD(float current, float target, float speed) {
        float next = smoothRotation(current, target, speed);
        return applyGCD(current, next);
    }

    // ─── Dead zone ──────────────────────────────────────────────────────────

    /**
     * Мёртвая зона: если |delta| < zone, возвращает 0.
     * Убирает микро-дрожь при наведении.
     */
    public static float deadZone(float delta, float zone) {
        return Math.abs(delta) < zone ? 0f : delta;
    }

    // ─── Calculate rotation ─────────────────────────────────────────────────

    /**
     * Вычислить Rotation из точки в мире (от позиции игрока).
     */
    public static Rotation calculateRotation(Vec3d to) {
        return new Rotation(RotationUtil.calculate(to));
    }

    /**
     * Вычислить Rotation между двумя точками.
     */
    public static Rotation calculateRotation(Vec3d from, Vec3d to) {
        return new Rotation(RotationUtil.calculate(from, to));
    }

    // ─── Tremor (физиологический, ~9 Гц, только yaw) ───────────────────────

    /**
     * Физиологический тремор: синусоида ~9 Гц, минимальная амплитуда.
     * Только yaw — pitch не трогаем (вертикаль требует стабильности).
     *
     * @param phaseCounter счётчик тиков (инкрементировать каждый тик)
     * @param tickMillis   текущее время в мс (System.currentTimeMillis)
     * @param speedScale   амплитуда масштабируется со скоростью (0..1)
     */
    public static float tremor(long phaseCounter, long tickMillis, float speedScale) {
        float time = (phaseCounter % 1000000L + (tickMillis % 1000L) * 0.001f) * 0.011f;
        float amp = 0.025f + Math.min(0.04f, speedScale * 0.006f);
        return (float) Math.sin(time) * amp;
    }

    // ─── Inertial drift (low-pass фильтр) ──────────────────────────────────

    /**
     * Инерционный дрейф (low-pass): каждые N мс выбирается новая маленькая
     * целевая точка, камера плавно лерпится к ней. Не дёргается.
     *
     * @param drift         текущее значение дрейфа (обновляется)
     * @param driftTarget   текущая цель дрейфа (обновляется)
     * @param nextDriftAt   следующее время смены цели (обновляется)
     * @param now           текущее время мс
     * @param r             ThreadLocalRandom
     * @param amplitude     максимальная амплитуда (±amplitude)
     * @param intervalMs    интервал смены цели (мс)
     * @param lerpSpeed     скорость лерпа к цели (0..1, меньше = медленнее)
     * @param decaySpeed    скорость стягивания цели к 0 (0..1)
     * @return новое значение дрейфа
     */
    public static float updateDrift(float drift, float driftTarget, long[] nextDriftAt,
                                     long now, ThreadLocalRandom r,
                                     float amplitude, long intervalMs,
                                     float lerpSpeed, float decaySpeed) {
        if (now >= nextDriftAt[0]) {
            driftTarget = (r.nextFloat() - 0.5f) * 2f * amplitude;
            nextDriftAt[0] = now + r.nextLong(intervalMs, intervalMs + 80L);
        }
        drift = MathHelper.lerp(lerpSpeed, drift, driftTarget);
        driftTarget = MathHelper.lerp(decaySpeed, driftTarget, 0f);
        return drift;
    }

    // ─── Speed ramp (Fitts-like разгон/торможение) ─────────────────────────

    /**
     * Оценка «целевой скорости» по угловой дистанции (Fitts-like).
     * Далеко → быстрее, близко → медленнее. Возвращает 0..1.
     */
    public static float fittsSpeedFactor(float angularDist) {
        if (angularDist < 5f) return 0.15f;
        if (angularDist > 60f) return 1f;
        return smoothStep(MathHelper.clamp(angularDist / 60f, 0f, 1f)) * 0.85f + 0.15f;
    }

    // ─── Overshoot detection ────────────────────────────────────────────────

    /**
     * Проверяет, пора ли делать overshoot (пролёт мимо цели).
     * Человек иногда пролетает на 2-4° за целью при быстрых поворотах.
     *
     * @return overshoot-дельту (0 если overshoot не нужен)
     */
    public static float checkOvershoot(float yawDelta, float errDist, ThreadLocalRandom r) {
        float absDelta = Math.abs(yawDelta);
        if (absDelta > 40f && absDelta < 120f && errDist < 20f && r.nextFloat() < 0.12f) {
            return Math.copySign(r.nextFloat(2f, 4f), yawDelta);
        }
        return 0f;
    }

    // ─── Удобная обёртка для RotationComponent.apply ────────────────────────

    /**
     * Применить ротацию через RotationComponent (360/360/360/360 = мгновенно на сервере,
     * плавность обеспечивает сам модуль).
     */
    public static void apply(Rotation rot, KillAura ka) {
        RotationComponent.update(
            rot, 360f, 360f, 360f, 360f,
            0, 1,
            ka.clientLook.getValue(),
            ka.getMoveFixMode(),
            "KillAura"
        );
    }

    /**
     * Применить ротацию с настраиваемой скоростью (для Sloth2-стиля).
     */
    public static void apply(Rotation rot, KillAura ka,
                             float yawSpeed, float pitchSpeed,
                             float yawReturn, float pitchReturn) {
        RotationComponent.update(
            rot, yawSpeed, pitchSpeed, yawReturn, pitchReturn,
            0, 1,
            ka.clientLook.getValue(),
            ka.getMoveFixMode(),
            "KillAura"
        );
    }
}
