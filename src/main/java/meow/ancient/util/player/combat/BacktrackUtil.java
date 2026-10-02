package meow.ancient.util.player.combat;

import lombok.Getter;
import meow.ancient.util.IMinecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Утилита BackTrack: отслеживает историю позиций любых сущностей назад во времени (по тикам).
 * Позволяет находить наилучший исторический тик для атаки с учётом дистанции и штрафа за возраст тика.
 */
public class BacktrackUtil implements IMinecraft {

    public record TrackRecord(Vec3d pos, Box box, long timestamp, int tickAge) {}

    private final Map<Integer, List<TrackRecord>> history = new HashMap<>();

    /**
     * Обновляет историю для любой сущности (вызывать каждый тик).
     */
    public void update(Entity entity, int maxHistoryTicks) {
        if (entity == null) return;
        int id = entity.getId();
        List<TrackRecord> records = history.computeIfAbsent(id, k -> new ArrayList<>());

        // Создаем новый снапшот
        TrackRecord current = new TrackRecord(
                entity.getPos(),
                entity.getBoundingBox(),
                System.currentTimeMillis(),
                0
        );

        // Сдвигаем возраст существующих записей
        List<TrackRecord> updated = new ArrayList<>();
        updated.add(current);
        for (TrackRecord rec : records) {
            if (rec.tickAge() + 1 <= maxHistoryTicks) {
                updated.add(new TrackRecord(rec.pos(), rec.box(), rec.timestamp(), rec.tickAge() + 1));
            }
        }
        history.put(id, updated);
    }

    /**
     * Очищает историю сущности.
     */
    public void remove(int entityId) {
        history.remove(entityId);
    }

    /**
     * Очищает всю историю.
     */
    public void clear() {
        history.clear();
    }

    /**
     * Возвращает список исторических записей сущности.
     */
    public List<TrackRecord> getRecords(Entity entity) {
        if (entity == null) return Collections.emptyList();
        List<TrackRecord> recs = history.get(entity.getId());
        return recs != null ? recs : Collections.emptyList();
    }

    /**
     * Находит наилучший исторический снапшот:
     * - Ищет снапшот с минимальной эффективной дистанцией до игрока:
     *   effectiveDist = distance + (tickAge * agePenaltyWeight)
     *
     * @param entity           цель
     * @param maxTicks         максимальная глубина истории в тиках
     * @param maxReach         максимальная досягаемость (дистанция)
     * @param preferRecentWeight штраф за 1 тик старости (в блоках дистанции, например 0.15)
     * @return наилучший TrackRecord или null если истории нет
     */
    public TrackRecord getBestTrack(Entity entity, int maxTicks, double maxReach, double preferRecentWeight) {
        if (entity == null || mc.player == null) return null;

        List<TrackRecord> records = history.get(entity.getId());
        if (records == null || records.isEmpty()) {
            return new TrackRecord(entity.getPos(), entity.getBoundingBox(), System.currentTimeMillis(), 0);
        }

        Vec3d eyePos = mc.player.getEyePos();
        TrackRecord best = null;
        double bestScore = Double.MAX_VALUE;

        for (TrackRecord record : records) {
            if (record.tickAge() > maxTicks) continue;

            Box box = record.box();
            // Находим ближайшую точку хитбокса к глазу игрока
            Vec3d nearest = new Vec3d(
                    MathHelper.clamp(eyePos.x, box.minX, box.maxX),
                    MathHelper.clamp(eyePos.y, box.minY, box.maxY),
                    MathHelper.clamp(eyePos.z, box.minZ, box.maxZ)
            );

            double dist = eyePos.distanceTo(nearest);
            // Если позиция находится вне досягаемости удара, пропускаем
            if (dist > maxReach) continue;

            // Score: дистанция + штраф за возраст тика
            double score = dist + (record.tickAge() * preferRecentWeight);
            if (score < bestScore) {
                bestScore = score;
                best = record;
            }
        }

        if (best == null) {
            // Если в пределах maxReach ничего нет, возвращаем самый свежий (текущий)
            return records.get(0);
        }

        return best;
    }

    /**
     * Получить лучший бокс с учётом бэктрека.
     */
    public Box getBestBox(Entity entity, int maxTicks, double maxReach, double preferRecentWeight) {
        TrackRecord best = getBestTrack(entity, maxTicks, maxReach, preferRecentWeight);
        return best != null ? best.box() : entity.getBoundingBox();
    }

    /**
     * Получить лучший центр с учётом бэктрека.
     */
    public Vec3d getBestCenter(Entity entity, int maxTicks, double maxReach, double preferRecentWeight) {
        TrackRecord best = getBestTrack(entity, maxTicks, maxReach, preferRecentWeight);
        return best != null ? best.box().getCenter() : entity.getBoundingBox().getCenter();
    }
}
