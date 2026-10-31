package meow.ancient.client.util.movement

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.ArrayDeque
import java.util.Deque
import kotlin.math.floor
import kotlin.math.hypot

object HvhTargetPredict {
    private const val DRAG = 0.98
    private const val MAX_PREDICT_DIST = 8.0
    private const val STAND_THRESHOLD = 0.06
    private const val HISTORY_SIZE = 8

    private val histories = HashMap<Int, TargetHistory>()

    fun predict(target: LivingEntity, ticks: Double, tickDelta: Float = 0.0f): Vec3 {
        val id = target.id
        val history = histories.computeIfAbsent(id) { TargetHistory() }

        val current = getInterpolatedPos(target, tickDelta)
        if (tickDelta <= 0.0f) history.push(current)

        val regSpeed = history.getRegressionSpeed()
        val instantSpeed = hypot(current.x - target.xOld, current.z - target.zOld)

        val isStanding = regSpeed < STAND_THRESHOLD && instantSpeed < STAND_THRESHOLD
        if (isStanding || ticks <= 0.0) {
            return current
        }

        val perTick = history.getRegressionVelocity()
        var moveX = perTick.x
        var moveZ = perTick.z

        val deltaYaw = target.yRot - target.yRotO
        if (Math.abs(deltaYaw) > 0.001f) {
            val rad = Math.toRadians(deltaYaw.toDouble())
            val cos = Math.cos(rad)
            val sin = Math.sin(rad)
            val rx = moveX * cos - moveZ * sin
            val rz = moveX * sin + moveZ * cos
            moveX = rx
            moveZ = rz
        }

        var predicted = current
        var vx = moveX
        var vz = moveZ

        val fullTicks = floor(ticks).toInt()
        val partial = ticks - fullTicks

        for (i in 0 until fullTicks) {
            predicted = predicted.add(vx, 0.0, vz)
            vx *= DRAG
            vz *= DRAG
        }

        if (partial > 0.0) {
            predicted = predicted.add(vx * partial, 0.0, vz * partial)
        }

        val real = getInterpolatedPos(target, 0.0f)
        val dxL = predicted.x - real.x
        val dzL = predicted.z - real.z
        val distL = hypot(dxL, dzL)
        if (distL > MAX_PREDICT_DIST) {
            val scale = MAX_PREDICT_DIST / distL
            predicted = Vec3(real.x + dxL * scale, predicted.y, real.z + dzL * scale)
        }

        return predicted
    }

    private fun getInterpolatedPos(target: LivingEntity, tickDelta: Float): Vec3 {
        if (tickDelta <= 0.0f) return target.position()
        val x = target.xOld + (target.x - target.xOld) * tickDelta
        val y = target.yOld + (target.y - target.yOld) * tickDelta
        val z = target.zOld + (target.z - target.zOld) * tickDelta
        return Vec3(x, y, z)
    }

    fun reset(target: LivingEntity?) {
        if (target != null) histories.remove(target.id)
    }

    fun resetAll() {
        histories.clear()
    }

    private class TargetHistory {
        private val positions: Deque<Vec3> = ArrayDeque()
        private var lastPos: Vec3? = null

        fun push(pos: Vec3) {
            positions.addLast(pos)
            while (positions.size > HISTORY_SIZE) {
                positions.pollFirst()
            }
            lastPos = pos
        }

        fun getRegressionVelocity(): Vec3 {
            val n = positions.size
            if (n < 2) return Vec3.ZERO

            val meanX = (n - 1) / 2.0
            var meanPosX = 0.0
            var meanPosZ = 0.0
            val arr = positions.toTypedArray()
            for (i in 0 until n) {
                meanPosX += arr[i].x
                meanPosZ += arr[i].z
            }
            meanPosX /= n
            meanPosZ /= n

            var numX = 0.0
            var numZ = 0.0
            var den = 0.0
            for (i in 0 until n) {
                val t = i - meanX
                numX += t * (arr[i].x - meanPosX)
                numZ += t * (arr[i].z - meanPosZ)
                den += t * t
            }

            if (den < 1.0e-9) return Vec3.ZERO
            return Vec3(numX / den, 0.0, numZ / den)
        }

        fun getRegressionSpeed(): Double {
            val v = getRegressionVelocity()
            return hypot(v.x, v.z)
        }
    }
}
