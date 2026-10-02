package meow.ancient.util.player.combat;

import net.minecraft.block.BlockState;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import meow.ancient.util.IMinecraft;

import java.util.*;

/**
 * EntityExtrapolation — утилита предсказания позиции любых сущностей (игроков, снарядов, мобов)
 * с учётом сетевой задержки (ping), гравитации, трения и коллизий блоков.
 */
public class EntityExtrapolation implements IMinecraft {

    /** Снимок состояния энтити за один тик. */
    public static final class Snapshot {
        public final Entity entity;
        public final Vec3d pos;
        public final Vec3d velocity;
        public final float yaw;
        public final float prevYaw;
        public final boolean onGround;
        public final boolean inWater;
        public final boolean hasNoGravity;
        public final Box boundingBox;

        public Snapshot(Entity e) {
            this.entity       = e;
            this.pos          = e.getPos();
            this.velocity     = e.getVelocity();
            this.yaw          = e.getYaw();
            this.prevYaw      = e.prevYaw;
            this.onGround     = e.isOnGround();
            this.inWater      = e.isTouchingWater();
            this.hasNoGravity = e.hasNoGravity();
            this.boundingBox  = e.getBoundingBox();
        }
    }

    private static final int HISTORY_SIZE = 10;
    private final Map<Integer, Deque<Snapshot>> history = new HashMap<>();

    public void update(Entity entity) {
        if (entity == null) return;
        Deque<Snapshot> deque = history.computeIfAbsent(entity.getId(), k -> new ArrayDeque<>(HISTORY_SIZE));
        deque.addFirst(new Snapshot(entity));
        if (deque.size() > HISTORY_SIZE) deque.removeLast();
    }

    public void remove(int entityId) {
        history.remove(entityId);
    }

    public void clear() {
        history.clear();
    }

    /**
     * Возвращает экстраполированную позицию сущности.
     */
    public Vec3d getExtrapolatedPos(Entity entity, float ticks, boolean autoTicks) {
        float resolvedTicks = autoTicks ? getPingTicks(entity) : ticks;
        if (resolvedTicks <= 0.001f) {
            return entity.getPos();
        }

        Deque<Snapshot> snaps = history.get(entity.getId());
        Snapshot latest = (snaps != null && !snaps.isEmpty()) ? snaps.peekFirst() : new Snapshot(entity);
        return simulate(latest, resolvedTicks);
    }

    /**
     * Возвращает хитбокс для экстраполированной позиции.
     */
    public Box getExtrapolatedBox(Entity entity, float ticks, boolean autoTicks) {
        Vec3d extrapolated = getExtrapolatedPos(entity, ticks, autoTicks);
        Vec3d offset = extrapolated.subtract(entity.getPos());
        return entity.getBoundingBox().offset(offset);
    }

    /**
     * Возвращает центр экстраполированного хитбокса.
     */
    public Vec3d getExtrapolatedCenter(Entity entity, float ticks, boolean autoTicks) {
        return getExtrapolatedBox(entity, ticks, autoTicks).getCenter();
    }

    /**
     * Вычисляет количество тиков экстраполяции по пингу.
     */
    public float getPingTicks(Entity entity) {
        if (mc.getNetworkHandler() == null) return 1.0f;

        int pingMs = 0;
        if (entity instanceof PlayerEntity player) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null) pingMs = entry.getLatency();
        }

        if (pingMs <= 0 && mc.player != null) {
            PlayerListEntry self = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (self != null) pingMs = self.getLatency();
        }

        if (pingMs <= 0) return 1.0f;

        // Половина RTT (односторонняя задержка в тиках)
        float ticks = pingMs / 50.0f * 0.5f;
        return Math.max(0.5f, Math.min(ticks, 8.0f));
    }

    // ─────────────────────────── Симуляция физики ─────────────────────────────

    private Vec3d simulate(Snapshot snap, float ticks) {
        Vec3d pos      = snap.pos;
        Vec3d velocity = snap.velocity;
        boolean ground = snap.onGround;
        boolean water  = snap.inWater;
        Box box        = snap.boundingBox;

        float deltaYaw = snap.yaw - snap.prevYaw;

        int fullTicks = (int) ticks;
        float frac    = ticks - fullTicks;

        for (int i = 0; i < fullTicks; i++) {
            // Для сущностей с вращением поворачиваем скорость по yaw
            if (Math.abs(deltaYaw) > 0.01f && !(snap.entity instanceof ProjectileEntity)) {
                velocity = rotateVelocity(velocity, deltaYaw);
            }

            StepResult res = stepWithCollision(snap.entity, pos, box, velocity, ground);
            pos      = res.newPos;
            box      = res.newBox;
            ground   = res.onGround;
            velocity = res.newVelocity;

            velocity = applyFrictionAndGravity(snap.entity, pos, velocity, ground, water, snap.hasNoGravity);
        }

        if (frac > 0.001f) {
            if (Math.abs(deltaYaw) > 0.01f && !(snap.entity instanceof ProjectileEntity)) {
                velocity = rotateVelocity(velocity, deltaYaw);
            }
            Vec3d partialVel = velocity.multiply(frac);
            StepResult res = stepWithCollision(snap.entity, pos, box, partialVel, ground);
            pos = res.newPos;
        }

        return pos;
    }

    private record StepResult(Vec3d newPos, Box newBox, Vec3d newVelocity, boolean onGround) {}

    private StepResult stepWithCollision(Entity entity, Vec3d currentPos, Box currentBox, Vec3d movement, boolean wasOnGround) {
        if (mc.world == null || movement.lengthSquared() < 1.0E-7) {
            return new StepResult(currentPos, currentBox, movement, wasOnGround);
        }

        List<VoxelShape> emptyList = Collections.emptyList();
        Vec3d adjusted = Entity.adjustMovementForCollisions(entity, movement, currentBox, mc.world, emptyList);

        boolean xCollide = !MathHelper.approximatelyEquals(movement.x, adjusted.x);
        boolean yCollide = !MathHelper.approximatelyEquals(movement.y, adjusted.y);
        boolean zCollide = !MathHelper.approximatelyEquals(movement.z, adjusted.z);

        boolean onGround = yCollide && movement.y < 0.0;
        float stepHeight = entity.getStepHeight();

        // Step up
        if (stepHeight > 0.0f && (wasOnGround || onGround) && (xCollide || zCollide)) {
            Vec3d stepAdjust = Entity.adjustMovementForCollisions(entity,
                    new Vec3d(movement.x, stepHeight, movement.z),
                    currentBox, mc.world, emptyList);
            Vec3d stepOffset = Entity.adjustMovementForCollisions(entity,
                    new Vec3d(0.0, stepHeight, 0.0),
                    currentBox.stretch(movement.x, 0.0, movement.z), mc.world, emptyList);
            Vec3d combined = Entity.adjustMovementForCollisions(entity,
                    new Vec3d(movement.x, 0.0, movement.z),
                    currentBox.offset(stepOffset), mc.world, emptyList).add(stepOffset);

            if (stepOffset.y < stepHeight && combined.horizontalLengthSquared() > stepAdjust.horizontalLengthSquared()) {
                stepAdjust = combined;
            }
            if (stepAdjust.horizontalLengthSquared() > adjusted.horizontalLengthSquared()) {
                adjusted = stepAdjust.add(Entity.adjustMovementForCollisions(entity,
                        new Vec3d(0.0, -stepAdjust.y + movement.y, 0.0),
                        currentBox.offset(stepAdjust), mc.world, emptyList));
                xCollide = !MathHelper.approximatelyEquals(movement.x, adjusted.x);
                yCollide = !MathHelper.approximatelyEquals(movement.y, adjusted.y);
                zCollide = !MathHelper.approximatelyEquals(movement.z, adjusted.z);
                onGround = yCollide && movement.y < 0.0;
            }
        }

        Vec3d newPos = currentPos.add(adjusted);
        Box newBox = currentBox.offset(adjusted);

        double vx = xCollide ? 0.0 : movement.x;
        double vy = yCollide ? (onGround ? 0.0 : movement.y) : movement.y;
        double vz = zCollide ? 0.0 : movement.z;

        return new StepResult(newPos, newBox, new Vec3d(vx, vy, vz), onGround);
    }

    private Vec3d applyFrictionAndGravity(Entity entity, Vec3d pos, Vec3d vel, boolean onGround, boolean inWater, boolean hasNoGravity) {
        double vx = vel.x;
        double vy = vel.y;
        double vz = vel.z;

        double friction;
        if (onGround && mc.world != null) {
            BlockPos groundPos = BlockPos.ofFloored(pos.x, pos.y - 0.2, pos.z);
            BlockState state = mc.world.getBlockState(groundPos);
            float slipperiness = state.getBlock().getSlipperiness();
            friction = slipperiness * 0.91;
        } else if (inWater) {
            friction = 0.8;
        } else {
            friction = (entity instanceof ProjectileEntity) ? 0.99 : 0.91;
        }

        vx *= friction;
        vz *= friction;

        if (!hasNoGravity) {
            if (inWater) {
                vy = (vy - 0.02) * 0.8;
            } else if (onGround) {
                vy = 0.0;
            } else {
                // Снаряды обычно имеют гравитацию 0.03-0.05, сущности 0.08
                double grav = (entity instanceof ProjectileEntity) ? 0.05 : 0.08;
                double drag = (entity instanceof ProjectileEntity) ? 0.99 : 0.98;
                vy = (vy - grav) * drag;
            }
        }

        return new Vec3d(vx, vy, vz);
    }

    private Vec3d rotateVelocity(Vec3d vel, float deltaYaw) {
        if (Math.abs(deltaYaw) < 0.01f) return vel;
        double rad = Math.toRadians(deltaYaw);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double nx = vel.x * cos - vel.z * sin;
        double nz = vel.x * sin + vel.z * cos;
        return new Vec3d(nx, vel.y, nz);
    }
}
