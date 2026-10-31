package meow.ancient.client.util.movement

import meow.ancient.client.extensions.mc
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.ArrayDeque
import java.util.Deque
import kotlin.math.abs

class EntityExtrapolation {
    class Snapshot(e: Entity) {
        val entity: Entity = e
        val pos: Vec3 = e.position()
        val velocity: Vec3 = e.deltaMovement
        val yaw: Float = e.yRot
        val prevYaw: Float = e.yRotO
        val onGround: Boolean = e.onGround()
        val inWater: Boolean = e.isInWater
        val hasNoGravity: Boolean = e.isNoGravity
        val boundingBox: AABB = e.boundingBox
    }

    private val history = HashMap<Int, Deque<Snapshot>>()

    fun update(entity: Entity?) {
        if (entity == null) return
        val deque = history.computeIfAbsent(entity.id) { ArrayDeque(10) }
        deque.addFirst(Snapshot(entity))
        if (deque.size > 10) deque.removeLast()
    }

    fun remove(entityId: Int) {
        history.remove(entityId)
    }

    fun clear() {
        history.clear()
    }

    fun getExtrapolatedPos(entity: Entity, ticks: Float, autoTicks: Boolean): Vec3 {
        val resolvedTicks = if (autoTicks) getPingTicks(entity) else ticks
        if (resolvedTicks <= 0.001f) {
            return entity.position()
        }

        val snaps = history[entity.id]
        val latest = if (snaps != null && snaps.isNotEmpty()) snaps.peekFirst() else Snapshot(entity)
        return simulate(latest, resolvedTicks)
    }

    fun getPingTicks(entity: Entity): Float {
        val connection = mc.connection ?: return 1.0f
        val playerInfo = connection.getPlayerInfo(entity.uuid)
        val pingMs = playerInfo?.latency ?: 50
        return (pingMs / 50.0f).coerceIn(1.0f, 20.0f)
    }

    private fun simulate(snap: Snapshot, ticks: Float): Vec3 {
        var pos = snap.pos
        var velocity = snap.velocity
        var ground = snap.onGround
        val water = snap.inWater
        var box = snap.boundingBox
        val deltaYaw = snap.yaw - snap.prevYaw

        val fullTicks = ticks.toInt()
        val frac = ticks - fullTicks

        for (i in 0 until fullTicks) {
            if (abs(deltaYaw) > 0.01f && snap.entity !is Projectile) {
                velocity = rotateVelocity(velocity, deltaYaw)
            }

            val res = stepWithCollision(snap.entity, pos, box, velocity, ground)
            pos = res.newPos
            box = res.newBox
            ground = res.onGround
            velocity = res.newVelocity

            velocity = applyFrictionAndGravity(snap.entity, pos, velocity, ground, water, snap.hasNoGravity)
        }

        if (frac > 0.001f) {
            if (abs(deltaYaw) > 0.01f && snap.entity !is Projectile) {
                velocity = rotateVelocity(velocity, deltaYaw)
            }
            val partialVel = velocity.scale(frac.toDouble())
            val res = stepWithCollision(snap.entity, pos, box, partialVel, ground)
            pos = res.newPos
        }

        return pos
    }

    private data class StepResult(val newPos: Vec3, val newBox: AABB, val newVelocity: Vec3, val onGround: Boolean)

    private fun stepWithCollision(entity: Entity, currentPos: Vec3, currentBox: AABB, movement: Vec3, wasOnGround: Boolean): StepResult {
        val level = mc.level ?: return StepResult(currentPos, currentBox, movement, wasOnGround)
        if (movement.lengthSqr() < 1.0e-7) {
            return StepResult(currentPos, currentBox, movement, wasOnGround)
        }

        val adjusted = Entity.collideBoundingBox(entity, movement, currentBox, level, emptyList())
        val xCollide = abs(movement.x - adjusted.x) > 1.0e-5
        val yCollide = abs(movement.y - adjusted.y) > 1.0e-5
        val zCollide = abs(movement.z - adjusted.z) > 1.0e-5
        val onGround = yCollide && movement.y < 0.0

        val newPos = currentPos.add(adjusted)
        val newBox = currentBox.move(adjusted)

        val vx = if (xCollide) 0.0 else movement.x
        val vy = if (yCollide) (if (onGround) 0.0 else movement.y) else movement.y
        val vz = if (zCollide) 0.0 else movement.z

        return StepResult(newPos, newBox, Vec3(vx, vy, vz), onGround)
    }

    private fun applyFrictionAndGravity(entity: Entity, pos: Vec3, vel: Vec3, onGround: Boolean, inWater: Boolean, hasNoGravity: Boolean): Vec3 {
        var vx = vel.x
        var vy = vel.y
        var vz = vel.z

        val level = mc.level
        val friction = if (onGround && level != null) {
            val groundPos = BlockPos.containing(pos.x, pos.y - 0.2, pos.z)
            val state = level.getBlockState(groundPos)
            state.block.friction * 0.91
        } else if (inWater) {
            0.8
        } else {
            if (entity is Projectile) 0.99 else 0.91
        }

        vx *= friction
        vz *= friction

        if (!hasNoGravity) {
            if (inWater) {
                vy = (vy - 0.02) * 0.8
            } else if (onGround) {
                vy = 0.0
            } else {
                val grav = if (entity is Projectile) 0.05 else 0.08
                val drag = if (entity is Projectile) 0.99 else 0.98
                vy = (vy - grav) * drag
            }
        }

        return Vec3(vx, vy, vz)
    }

    private fun rotateVelocity(vel: Vec3, deltaYaw: Float): Vec3 {
        if (abs(deltaYaw) < 0.01f) return vel
        val rad = Math.toRadians(deltaYaw.toDouble())
        val cos = Math.cos(rad)
        val sin = Math.sin(rad)
        val nx = vel.x * cos - vel.z * sin
        val nz = vel.x * sin + vel.z * cos
        return Vec3(nx, vel.y, nz)
    }
}
