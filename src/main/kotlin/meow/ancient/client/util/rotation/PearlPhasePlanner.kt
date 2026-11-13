package meow.ancient.client.util.rotation

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** Short pearl trajectories, including 26.3's gravity-before-drag and old-position teleport. */
object PearlPhasePlanner {
    data class Launch(val feet: Vec3, val eyeHeight: Double, val movement: Vec3,
                      val onGround: Boolean, val width: Double, val height: Double)
    data class Wall(val pos: BlockPos, val boxes: List<AABB>)
    data class Shot(val rotation: Rotation, val wall: Wall, val teleport: Vec3,
                    val flightTicks: Int, val depth: Double)

    private const val SPEED = 1.5
    private const val GRAVITY = 0.03
    private const val DRAG = 0.99
    // Projectile.shoot uses triangular noise with deviation 0.0172275 per axis.
    private const val NOISE = SPEED * 0.0172275

    fun find(launch: Launch, walls: List<Wall>, cameraYaw: Float,
             quantize: (Rotation) -> Rotation,
             collision: (Vec3, Vec3) -> HitResult?): Shot? {
        var best: Shot? = null
        var bestScore = Double.NEGATIVE_INFINITY
        fun tryAngle(wall: Wall, yaw: Float, pitch: Float) {
            val rotation = quantize(Rotation(yaw, pitch))
            val shot = predict(launch, wall, rotation, Vec3.ZERO, collision) ?: return
            if (shot.depth < 0.035) return
            val turn = abs(rotation.normalizeTo(Rotation(cameraYaw, 0f)).yaw - cameraYaw)
            val initialScore = shot.depth * 2 - shot.flightTicks * 0.01 - turn * 0.0002
            if (initialScore <= bestScore) return
            var worstDepth = shot.depth
            for (x in listOf(-NOISE, NOISE)) for (y in listOf(-NOISE, NOISE)) for (z in listOf(-NOISE, NOISE)) {
                val perturbed = predict(launch, wall, rotation, Vec3(x, y, z), collision) ?: return
                if (perturbed.depth < 0.005) return
                worstDepth = min(worstDepth, perturbed.depth)
            }
            val score = shot.depth + worstDepth - shot.flightTicks * 0.01 - turn * 0.0002
            if (score > bestScore) { best = shot; bestScore = score }
        }
        for (wall in walls) {
            for (box in wall.boxes) {
                val center = box.center
                val nearest = Vec3(launch.feet.x.coerceIn(box.minX + 0.001, box.maxX - 0.001),
                    center.y, launch.feet.z.coerceIn(box.minZ + 0.001, box.maxZ - 0.001))
                val yaws = listOf(yawTo(launch.feet, nearest), yawTo(launch.feet, center))
                for (yaw in yaws) for (offset in listOf(0f, -4f, 4f)) {
                    for (pitch in 60..88) tryAngle(wall, yaw + offset, pitch.toFloat())
                }
            }
        }
        val coarse = best ?: return null
        for (yawOffset in -2..2) for (pitchOffset in -6..6) {
            tryAngle(coarse.wall, coarse.rotation.yaw + yawOffset * 0.5f,
                (coarse.rotation.pitch + pitchOffset * 0.125f).coerceIn(60f, 88f))
        }
        return best
    }

    fun validate(launch: Launch, shot: Shot, collision: (Vec3, Vec3) -> HitResult?): Boolean {
        val actual = predict(launch, shot.wall, shot.rotation, Vec3.ZERO, collision) ?: return false
        return actual.depth >= 0.035 && actual.teleport.distanceToSqr(shot.teleport) < 0.01
    }

    private fun yawTo(from: Vec3, to: Vec3) = Math.toDegrees(atan2(to.z - from.z, to.x - from.x)).toFloat() - 90f

    private fun predict(launch: Launch, wall: Wall, rotation: Rotation, noise: Vec3,
                        collision: (Vec3, Vec3) -> HitResult?): Shot? {
        var position = launch.feet.add(0.0, launch.eyeHeight - 0.1, 0.0)
        var velocity = rotation.toDirectionVec().scale(SPEED).add(noise).add(
            launch.movement.x, if (launch.onGround) 0.0 else launch.movement.y, launch.movement.z)
        for (tick in 1..4) {
            velocity = velocity.add(0.0, -GRAVITY, 0.0).scale(DRAG)
            val next = position.add(velocity)
            if (collision(position, next) != null) {
                // ThrownEnderpearl.onHit teleports to oldPosition(), not the hit intersection.
                if (abs(position.y - launch.feet.y) > 0.35 || position.distanceToSqr(launch.feet) > 1.5 * 1.5) return null
                val radius = launch.width * 0.5
                val body = AABB(position.x - radius, position.y + 0.001, position.z - radius,
                    position.x + radius, position.y + launch.height - 0.001, position.z + radius)
                var depth = 0.0
                for (box in wall.boxes) {
                    val dy = min(body.maxY, box.maxY) - max(body.minY, box.minY)
                    if (dy < 0.15) continue
                    val dx = min(body.maxX, box.maxX) - max(body.minX, box.minX)
                    val dz = min(body.maxZ, box.maxZ) - max(body.minZ, box.minZ)
                    depth = max(depth, min(dx, dz))
                }
                if (depth <= 0.0) return null
                return Shot(rotation, wall, position, tick, depth)
            }
            position = next
        }
        return null
    }
}
