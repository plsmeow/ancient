package tech.onetap.util.player.combat;

import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import tech.onetap.util.IMinecraft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Состояние кристаллов между тиками: уже убитые (чтобы не бить дважды),
 * зависшие атаки и позиции, где кристалл так и не появился.
 */
public class CrystalTracker implements IMinecraft {
    private final Map<Integer, Long> dead = new ConcurrentHashMap<>();
    private final Map<Integer, Attempt> attacked = new ConcurrentHashMap<>();
    private final Map<BlockPos, Attempt> awaiting = new ConcurrentHashMap<>();

    private int maxAttempts = 5;
    private int ping;

    public void configure(int maxAttempts, int ping) {
        this.maxAttempts = maxAttempts;
        this.ping = ping;
    }

    public void update() {
        long now = System.currentTimeMillis();
        long deadLifetime = Math.max(ping * 2L, 50L);

        dead.entrySet().removeIf(entry -> now - entry.getValue() > deadLifetime);
        attacked.values().removeIf(Attempt::shouldRemove);
        awaiting.values().removeIf(Attempt::shouldRemove);
    }

    public void reset() {
        dead.clear();
        attacked.clear();
        awaiting.clear();
    }

    public boolean isDead(int id) {
        return dead.containsKey(id);
    }

    public void setDead(int id) {
        dead.putIfAbsent(id, System.currentTimeMillis());
    }

    public boolean isCrystalStuck(int id) {
        Attempt attempt = attacked.get(id);
        return attempt != null && attempt.exhausted();
    }

    public boolean isPositionStuck(BlockPos pos) {
        Attempt attempt = awaiting.get(pos);
        return attempt != null && attempt.exhausted();
    }

    public void onAttack(EndCrystalEntity crystal, boolean failsafe) {
        setDead(crystal.getId());

        attacked.compute(crystal.getId(), (id, attempt) -> {
            if (attempt == null) return new Attempt(crystal.getPos());
            if (failsafe) attempt.attempts++;
            return attempt;
        });
    }

    public void onPlace(BlockPos pos, boolean failsafe) {
        awaiting.compute(pos, (key, attempt) -> {
            if (attempt == null) return new Attempt(pos.toCenterPos());
            if (failsafe) attempt.attempts++;
            return attempt;
        });
    }

    public void confirmSpawn(BlockPos pos) {
        awaiting.remove(pos);
    }

    public boolean confirmSpawn(Vec3d crystalPos) {
        for (BlockPos pos : awaiting.keySet()) {
            if (pos.toCenterPos().squaredDistanceTo(crystalPos.x, crystalPos.y - 0.5, crystalPos.z) < 0.3) {
                awaiting.remove(pos);
                return true;
            }
        }
        return false;
    }

    private class Attempt {
        private final Vec3d pos;
        private final double distance;
        private int attempts = 1;

        private Attempt(Vec3d pos) {
            this.pos = pos;
            this.distance = mc.player == null ? 0.0 : mc.player.squaredDistanceTo(pos);
        }

        /** Сдвинулись сами — предыдущие попытки больше ничего не говорят о позиции. */
        private boolean shouldRemove() {
            if (mc.player == null) return true;
            return Math.abs(distance - mc.player.squaredDistanceTo(pos)) >= 1.0;
        }

        private boolean exhausted() {
            return attempts >= Math.max(maxAttempts, ping / 25);
        }
    }
}
