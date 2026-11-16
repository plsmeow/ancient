/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
// Adapted for Ancient Alpha / Minecraft 26.3 from LiquidBounce commit
// 0eccefdb56d5afaca6443ec79016be2c3529dbce. See docs/scaffold-port.md.
package meow.ancient.module.modules.movement

import meow.ancient.client.util.inventory.BlockPredictionUtil
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.round

/** LiquidBounce's support reference, placement history and eight-direction movement line. */
class ScaffoldMovementPlanner {
    data class Line(val position: Vec3, val direction: Vec3) {
        fun nearest(point: Vec3): Vec3 = position.add(direction.scale(point.subtract(position).dot(direction)))
        fun distanceSqr(point: Vec3): Double = nearest(point).distanceToSqr(point)
    }
    data class SupportReference(val blockPos: BlockPos, val offsetX: Double, val offsetZ: Double)
    private data class SupportCandidate(val pos: BlockPos, val overlap: Double, val surfaceDelta: Double,
                                        val distanceSqr: Double) : Comparable<SupportCandidate> {
        override fun compareTo(other: SupportCandidate): Int = when {
            surfaceDelta + 1.0e-3 < other.surfaceDelta -> -1
            other.surfaceDelta + 1.0e-3 < surfaceDelta -> 1
            overlap > other.overlap + 0.02 -> -1
            overlap + 0.02 < other.overlap -> 1
            else -> distanceSqr.compareTo(other.distanceSqr)
        }
        fun stableComparedTo(other: SupportCandidate) = surfaceDelta <= other.surfaceDelta + 1.0e-3
            && overlap + 0.02 >= other.overlap
    }
    private val lastPlaced = ArrayDeque<BlockPos>(4)
    private var lastPosition: BlockPos? = null
    private var lastDirectionAngle = Float.NaN
    var supportReference: SupportReference? = null
        private set

    fun chooseDirection(direction: Vec3): Vec3 {
        val angle = Math.toDegrees(atan2(-direction.x, direction.z)).toFloat()
        if (lastDirectionAngle.isNaN() || Mth.degreesDifferenceAbs(angle, lastDirectionAngle) > 30f)
            lastDirectionAngle = Mth.wrapDegrees((round(angle / 180f * 4f + 4f) - 4f) / 4f * 180f)
        return Vec3.directionFromRotation(0f, lastDirectionAngle)
    }

    fun movementLine(player: LocalPlayer, level: ClientLevel, input: Vec3): Line? {
        if (input.horizontalDistanceSqr() < 1.0e-8) return null
        val direction = chooseDirection(input)
        val support = findSupport(player, level) ?: return null
        supportReference = support
        val history = if (lastPlaced.size < 2) null else {
            val a = lastPlaced[lastPlaced.size - 2]
            val b = lastPlaced.last()
            // Vertical stair placement must not tilt a horizontal movement line.
            val from = Vec3(a.x + 0.5, player.y, a.z + 0.5)
            val to = Vec3(b.x + 0.5, player.y, b.z + 0.5)
            val delta = to.subtract(from)
            if (delta.horizontalDistanceSqr() < 1.0e-8) null
            else Line(from.add(to).scale(0.5), delta.normalize())
        }
        val anchor = if (history != null && history.direction.dot(direction) >= 0.5)
            history.nearest(player.position())
        else Vec3(support.blockPos.x + 0.5 + support.offsetX, player.y,
            support.blockPos.z + 0.5 + support.offsetZ)
        return Line(Vec3(anchor.x, player.y, anchor.z), direction)
    }

    private fun findSupport(player: LocalPlayer, level: ClientLevel): SupportReference? {
        val box = player.boundingBox
        val y = floor(box.minY).toInt() - 1
        val candidates = HashMap<BlockPos, SupportCandidate>()
        for (x in floor(box.minX).toInt()..floor(box.maxX).toInt())
            for (z in floor(box.minZ).toInt()..floor(box.maxZ).toInt()) {
                val pos = BlockPos(x, y, z)
                val shape = BlockPredictionUtil.serverState(level, pos).getCollisionShape(level, pos)
                var surface = Double.POSITIVE_INFINITY
                var overlap = 0.0
                for (local in shape.toAabbs()) {
                    val block = local.move(pos)
                    val overlapX = minOf(box.maxX, block.maxX) - maxOf(box.minX, block.minX)
                    val overlapZ = minOf(box.maxZ, block.maxZ) - maxOf(box.minZ, block.minZ)
                    if (overlapX <= 0.0 || overlapZ <= 0.0) continue
                    val delta = abs(box.minY - block.maxY)
                    if (delta + 1.0e-3 < surface) { surface = delta; overlap = overlapX * overlapZ }
                    else if (abs(delta - surface) <= 1.0e-3) overlap += overlapX * overlapZ
                }
                if (surface.isFinite()) candidates[pos] = SupportCandidate(pos, overlap, surface,
                    Vec3(pos.x + 0.5, player.y, pos.z + 0.5).distanceToSqr(player.position()))
            }
        val best = candidates.values.minOrNull() ?: run { supportReference = null; lastPosition = null; return null }
        val chosen = candidates[lastPlaced.lastOrNull()]?.takeIf { it.stableComparedTo(best) }
            ?: candidates[lastPosition]?.takeIf { it.stableComparedTo(best) } ?: best
        lastPosition = chosen.pos
        return SupportReference(chosen.pos, player.x - (chosen.pos.x + 0.5), player.z - (chosen.pos.z + 0.5))
    }

    fun trackPlacedBlock(pos: BlockPos) {
        if (pos == lastPlaced.lastOrNull()) return
        while (lastPlaced.size >= 4) lastPlaced.removeFirst()
        lastPlaced.addLast(pos.immutable())
    }
    fun reset() { lastPlaced.clear(); lastPosition = null; supportReference = null; lastDirectionAngle = Float.NaN }
}
