package tech.onetap.util.player.combat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Отдельная предикт-утилита для режима "HvH Target" модуля Speed.
 * <p>
 * Реализация основана на проверенных подходах к предсказанию позиции в сетевых играх:
 * <ul>
 *     <li><b>Dead reckoning</b> (gamedeveloper.com): базовая экстраполяция —
 *         {@code predicted = pos + velocity * time}, но чем дольше предикт, тем больше
 *         расхождение, поэтому используется симуляция короткими шагами.</li>
 *     <li><b>История позиций вместо velocity</b> (Sector's Edge, Gabriel Gambetta):
 *         у сетевых игроков {@code getVelocity()} — производная от сжатых координат и
 *         ненадёжна. Скорость считается по Δпозиций между тиками.</li>
 *     <li><b>Линейная регрессия по прошлым точкам</b> (IEEE Access, 2021): направление
 *         и скорость восстанавливаются методом наименьших квадратов по N последним
 *         позициям — это устойчиво к шуму одного тика и даёт правильное направление
 *         даже при микро-флуктуациях.</li>
 *     <li><b>Drag-симуляция</b> (MinecraftMotionTools): позиция накапливается по шагам
 *         velocity с затуханием 0.98, как в физике Minecraft, вместо {@code velocity * ticks}.</li>
 * </ul>
 */
public final class HvhTargetPredict {

    private static final double DRAG = 0.98;
    // порог "стоит ли игрок" в блоках за тик (ходьба ~0.1-0.13, бег ~0.17-0.22)
    private static final double STAND_THRESHOLD = 0.06;
    private static final int HISTORY_SIZE = 8;

    // История позиций на каждую цель (по entityId)
    private static final Map<Integer, TargetHistory> HISTORIES = new HashMap<>();

    private HvhTargetPredict() {
    }

    /**
     * Возвращает предсказанную X/Z позицию цели через {@code ticks} тиков вперёд.
     * Если цель стоит — возвращает её текущую позицию (предикт отключён).
     *
     * @param target цель KillAura
     * @param ticks  количество тиков вперёд (сила предикта)
     * @return предсказанная позиция (только X/Z, Y не меняется)
     */
    public static Vec3d predict(LivingEntity target, double ticks) {
        int id = target.getId();
        TargetHistory history = HISTORIES.computeIfAbsent(id, k -> new TargetHistory());

        Vec3d current = target.getPos();
        history.push(current);

        // Стоит ли игрок? Проверяем по регрессионной скорости (самый устойчивый
        // источник) и по мгновенной Δпозиции (на случай резкой остановки).
        double regSpeed = history.getRegressionSpeed();
        double instantSpeed = Math.hypot(current.x - target.prevX, current.z - target.prevZ);

        boolean isStanding = regSpeed < STAND_THRESHOLD && instantSpeed < STAND_THRESHOLD;

        if (isStanding || ticks <= 0.0) {
            return current;
        }

        // Вектор движения из линейной регрессии по истории позиций (за тик).
        Vec3d perTick = history.getRegressionVelocity();
        double moveX = perTick.x;
        double moveZ = perTick.z;

        // Учитываем поворот: если цель разворачивается, её реальный вектор
        // движения поворачивается вместе с yaw. Ротируем вектор по deltaYaw.
        float deltaYaw = target.getYaw() - target.prevYaw;
        if (Math.abs(deltaYaw) > 0.001f) {
            double rad = Math.toRadians(deltaYaw);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double rx = moveX * cos - moveZ * sin;
            double rz = moveX * sin + moveZ * cos;
            moveX = rx;
            moveZ = rz;
        }

        // Drag-симуляция вперёд (как физика Minecraft)
        Vec3d predicted = current;
        double vx = moveX;
        double vz = moveZ;

        int fullTicks = (int) Math.floor(ticks);
        double partial = ticks - fullTicks;

        for (int i = 0; i < fullTicks; i++) {
            predicted = predicted.add(vx, 0.0, vz);
            vx *= DRAG;
            vz *= DRAG;
        }

        if (partial > 0.0) {
            predicted = predicted.add(vx * partial, 0.0, vz * partial);
        }

        return predicted;
    }

    /**
     * Очищает историю для цели (вызывать при смене цели / выключении модуля).
     */
    public static void reset(LivingEntity target) {
        if (target != null) HISTORIES.remove(target.getId());
    }

    public static void resetAll() {
        HISTORIES.clear();
    }

    private static final class TargetHistory {
        // кольцевой буфер позиций по тикам
        private final Deque<Vec3d> positions = new ArrayDeque<>();
        private Vec3d lastPos = null;

        void push(Vec3d pos) {
            positions.addLast(pos);
            while (positions.size() > HISTORY_SIZE) {
                positions.pollFirst();
            }
            lastPos = pos;
        }

        /**
         * Линейная регрессия (МНК) позиции по времени (индексу тика).
         * Возвращает вектор смещения за 1 тик — устойчивое направление + скорость.
         */
        Vec3d getRegressionVelocity() {
            int n = positions.size();
            if (n < 2) {
                if (lastPos == null) return Vec3d.ZERO;
                return Vec3d.ZERO;
            }

            // x = 0..n-1, t = x - meanX
            double meanX = (n - 1) / 2.0;
            double meanPosX = 0.0, meanPosZ = 0.0;
            Vec3d[] arr = positions.toArray(new Vec3d[0]);
            for (int i = 0; i < n; i++) {
                meanPosX += arr[i].x;
                meanPosZ += arr[i].z;
            }
            meanPosX /= n;
            meanPosZ /= n;

            double numX = 0.0, numZ = 0.0, den = 0.0;
            for (int i = 0; i < n; i++) {
                double t = i - meanX;
                numX += t * (arr[i].x - meanPosX);
                numZ += t * (arr[i].z - meanPosZ);
                den += t * t;
            }

            if (den < 1.0E-9) return Vec3d.ZERO;
            return new Vec3d(numX / den, 0.0, numZ / den);
        }

        double getRegressionSpeed() {
            Vec3d v = getRegressionVelocity();
            return Math.hypot(v.x, v.z);
        }
    }
}
