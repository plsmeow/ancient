package meow.ancient.client.util.combat

import meow.ancient.client.extensions.mc
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** Samples once per tick; evaluating more candidates must never advance the motion history. */
internal class AuraMotionPrediction {
    data class Snapshot(val position: Vec3, val box: AABB)
    private data class Track(var position: Vec3, var velocity: Vec3, var tick: Int)
    private val tracks = HashMap<Int, Track>()
    private val forecasts = HashMap<Pair<Int, Double>, Snapshot>()
    private val paths = HashMap<Int, Trajectory>()

    fun clear() { tracks.clear(); forecasts.clear(); paths.clear() }

    fun record(id: Int, position: Vec3, reported: Vec3, tick: Int): Vec3 {
        val old = tracks[id]
        if (old?.tick == tick) return old.velocity
        forecasts.clear()
        paths.clear()
        val velocity = if (old == null) limited(reported) else {
            val delta = position.subtract(old.position).scale(1.0 / (tick - old.tick).coerceAtLeast(1))
            when {
                position.distanceToSqr(old.position) > 16.0 || tick - old.tick > 5 -> Vec3.ZERO
                delta.lengthSqr() < 1.0E-6 -> Vec3.ZERO
                delta.dot(old.velocity) < 0.0 -> limited(delta)
                else -> limited(old.velocity.lerp(delta, 0.75))
            }
        }
        tracks[id] = Track(position, velocity, tick)
        tracks.entries.removeIf { tick - it.value.tick > 40 }
        return velocity
    }

    fun update(entity: LivingEntity, tick: Int): Vec3 {
        val reported = entity.deltaMovement.takeIf { it.lengthSqr() > 1.0E-6 }
            ?: entity.position().subtract(Vec3(entity.xOld, entity.yOld, entity.zOld))
        return record(entity.id, entity.position(), reported, tick)
    }

    fun predict(entity: LivingEntity, ticks: Double): Snapshot {
        val lead = if (ticks.isFinite()) ticks.coerceIn(0.0, 40.0) else 0.0
        val origin = entity.position()
        val box = entity.boundingBox
        if (paths[entity.id]?.let { it.origin != origin || it.initialBox != box } == true) {
            paths.remove(entity.id)
            forecasts.keys.removeIf { it.first == entity.id }
        }
        return forecasts.getOrPut(entity.id to lead) {
            paths.getOrPut(entity.id) {
                val level = mc.level
                val velocity = tracks[entity.id]?.velocity ?: limited(entity.deltaMovement)
                // One nearby player query for every horizon, rather than one per simulated step.
                val collisions = if (level != null && (velocity.lengthSqr() > 1.0E-9 || !entity.onGround()))
                    level.getEntityCollisions(entity, box.inflate(4.1)) else emptyList()
                Trajectory(origin, box, velocity, entity.onGround(), entity.isNoGravity || entity.isFallFlying,
                    entity.isInWater) { body, movement ->
                    if (level == null) movement else Entity.collideBoundingBox(entity, movement, body, level, collisions)
                }
            }.at(lead)
        }
    }

    /** Reuse whole simulation steps when many terrain candidates need different horizons. */
    internal class Trajectory(val origin: Vec3, val initialBox: AABB, velocity: Vec3, onGround: Boolean,
        private val noGravity: Boolean, private val inWater: Boolean, private val collide: (AABB, Vec3) -> Vec3) {
        private data class State(val snapshot: Snapshot, val motion: Vec3, val grounded: Boolean)
        private val states = arrayListOf(State(Snapshot(origin, initialBox), limited(velocity), onGround))

        fun at(ticks: Double): Snapshot {
            val lead = if (ticks.isFinite()) ticks.coerceIn(0.0, 40.0) else 0.0
            val whole = lead.toInt()
            while (states.size <= whole) states.add(step(states.last(), 1.0))
            val state = states[whole]
            val fraction = lead - whole
            return if (fraction < 1.0E-9) state.snapshot else step(state, fraction).snapshot
        }

        private fun step(state: State, fraction: Double): State {
            if (state.motion.lengthSqr() < 1.0E-12 && (state.grounded || noGravity)
                || state.snapshot.position.distanceToSqr(origin) >= 16.0 - 1.0E-6) return state
            val wanted = state.motion.scale(fraction)
            var allowed = collide(state.snapshot.box, wanted)
            val remaining = (4.0 - state.snapshot.position.distanceTo(origin)).coerceAtLeast(0.0)
            if (allowed.length() > remaining) allowed = allowed.normalize().scale(remaining)
            val current = state.snapshot.position.add(allowed)
            val box = state.snapshot.box.move(allowed)
            val xBlocked = abs(allowed.x - wanted.x) > 1.0E-6
            val yBlocked = abs(allowed.y - wanted.y) > 1.0E-6
            val zBlocked = abs(allowed.z - wanted.z) > 1.0E-6
            val grounded = wanted.y < 0.0 && yBlocked || state.grounded &&
                collide(box, Vec3(0.0, -0.05, 0.0)).y > -0.05 + 1.0E-6
            val y = if (yBlocked || grounded) 0.0 else state.motion.y
            val vertical = when {
                noGravity -> y
                inWater -> (y - 0.02) * 0.8
                grounded -> 0.0
                else -> (y - 0.08) * 0.98
            }
            return State(Snapshot(current, box), Vec3(if (xBlocked) 0.0 else state.motion.x * 0.99,
                vertical, if (zBlocked) 0.0 else state.motion.z * 0.99), grounded)
        }
    }

    companion object {
        private fun limited(value: Vec3): Vec3 {
            if (!value.x.isFinite() || !value.y.isFinite() || !value.z.isFinite()) return Vec3.ZERO
            val horizontal = value.horizontalDistance()
            val scale = if (horizontal > 1.5) 1.5 / horizontal else 1.0
            return Vec3(value.x * scale, value.y.coerceIn(-1.0, 1.0), value.z * scale)
        }

        fun simulate(position: Vec3, box: AABB, velocity: Vec3, ticks: Double, onGround: Boolean,
                     noGravity: Boolean, inWater: Boolean, collide: (AABB, Vec3) -> Vec3): Snapshot {
            return Trajectory(position, box, velocity, onGround, noGravity, inWater, collide).at(ticks)
        }
    }
}
