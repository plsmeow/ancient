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

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor

/** LiquidBounce NORMAL offsets and stabilized face targeting. */
object ScaffoldTargetGeometry {
    val normalOffsets: List<BlockPos> = buildList {
        for (x in intArrayOf(0, -1, 1)) for (z in intArrayOf(0, -1, 1)) {
            add(BlockPos(x, 0, z)); add(BlockPos(x, -1, z))
        }
    }.sortedWith(compareBy<BlockPos> { it.x * it.x + it.y * it.y + it.z * it.z }
        .thenBy { it.y }.thenBy { it.x }.thenBy { it.z })

    @JvmStatic
    fun targetY(feetY: Double, fixedY: Int?, stationaryJump: Boolean): Int =
        if (stationaryJump || fixedY == null) floor(feetY).toInt() - 1 else fixedY

    fun candidates(target: BlockPos, predicted: Vec3, line: ScaffoldMovementPlanner.Line?, sameY: Boolean): List<BlockPos> {
        val comparator = if (line == null) compareBy<BlockPos> { Vec3.atCenterOf(it).distanceToSqr(predicted) }
            else compareBy<BlockPos> { line.distanceSqr(Vec3.atCenterOf(it)) }
                .thenBy { Vec3.atCenterOf(it).distanceToSqr(predicted) }
        return normalOffsets.asSequence().filter { !sameY || it.y == 0 }.map { target.offset(it) }
            .sortedWith(comparator).toList()
    }

    private fun face(box: AABB, side: Direction): AABB = when (side) {
        Direction.DOWN -> AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ)
        Direction.UP -> AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ)
        Direction.NORTH -> AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ)
        Direction.SOUTH -> AABB(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ)
        Direction.WEST -> AABB(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.maxZ)
        Direction.EAST -> AABB(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
    }

    @JvmStatic
    fun facePoint(box: AABB, side: Direction, eyes: Vec3, rotationDirection: Vec3,
                  position: Vec3, line: ScaffoldMovementPlanner.Line?): Vec3 {
        val original = face(box, side)
        val dx = original.xsize * 0.15; val dy = original.ysize * 0.15; val dz = original.zsize * 0.15
        val trimmed = AABB(original.minX + dx, original.minY + dy, original.minZ + dz,
            original.maxX - dx, original.maxY - dy, original.maxZ - dz)
        val cropped = line?.let {
            val normal = Vec3(side.stepX.toDouble(), side.stepY.toDouble(), side.stepZ.toDouble())
            val denominator = normal.dot(it.direction)
            if (abs(denominator) < 1.0e-10) return@let null
            val collision = eyes.add(it.direction.scale(normal.dot(trimmed.center.subtract(eyes)) / denominator))
            val away = position.subtract(it.nearest(position)).normalize()
            val b = position.add(away.scale(2.0))
            val crop = AABB(collision.x, position.y - 2.0, collision.z, b.x, position.y + 1.0, b.z)
            val low = Vec3(maxOf(trimmed.minX, crop.minX), maxOf(trimmed.minY, crop.minY), maxOf(trimmed.minZ, crop.minZ))
            val high = Vec3(minOf(trimmed.maxX, crop.maxX), minOf(trimmed.maxY, crop.maxY), minOf(trimmed.maxZ, crop.maxZ))
            if (low.x > high.x || low.y > high.y || low.z > high.z) return@let null
            val clamped = AABB(low, high)
            val area = when (side.axis) {
                Direction.Axis.X -> clamped.ysize * clamped.zsize
                Direction.Axis.Y -> clamped.xsize * clamped.zsize
                Direction.Axis.Z -> clamped.xsize * clamped.ysize
            }
            clamped.takeIf { area >= 0.0001 }
        } ?: trimmed
        return nearestPointToLine(cropped, eyes, rotationDirection)
    }

    private fun nearestPointToLine(face: AABB, eyes: Vec3, direction: Vec3): Vec3 {
        val normalAxis = if (face.xsize == 0.0) 0 else if (face.ysize == 0.0) 1 else 2
        fun coord(v: Vec3, axis: Int) = when (axis) { 0 -> v.x; 1 -> v.y; else -> v.z }
        val denominator = coord(direction, normalAxis)
        val points = ArrayList<Vec3>()
        if (abs(denominator) > 1.0e-10) {
            val t = (coord(face.center, normalAxis) - coord(eyes, normalAxis)) / denominator
            val point = eyes.add(direction.scale(t))
            if (point.x >= face.minX - 1.0e-8 && point.x <= face.maxX + 1.0e-8
                && point.y >= face.minY - 1.0e-8 && point.y <= face.maxY + 1.0e-8
                && point.z >= face.minZ - 1.0e-8 && point.z <= face.maxZ + 1.0e-8) points.add(point)
        }
        val vertices = when (normalAxis) {
            0 -> listOf(Vec3(face.minX, face.minY, face.minZ), Vec3(face.minX, face.maxY, face.minZ),
                Vec3(face.minX, face.maxY, face.maxZ), Vec3(face.minX, face.minY, face.maxZ))
            1 -> listOf(Vec3(face.minX, face.minY, face.minZ), Vec3(face.maxX, face.minY, face.minZ),
                Vec3(face.maxX, face.minY, face.maxZ), Vec3(face.minX, face.minY, face.maxZ))
            else -> listOf(Vec3(face.minX, face.minY, face.minZ), Vec3(face.maxX, face.minY, face.minZ),
                Vec3(face.maxX, face.maxY, face.minZ), Vec3(face.minX, face.maxY, face.minZ))
        }
        for (i in vertices.indices) {
            val a = vertices[i]; val edge = vertices[(i + 1) % vertices.size].subtract(a)
            val w = a.subtract(eyes)
            val edgeProjection = edge.subtract(direction.scale(edge.dot(direction)))
            val projectedW = w.subtract(direction.scale(w.dot(direction)))
            val length = edgeProjection.lengthSqr()
            val t = if (length <= 1.0e-12) 0.0 else (-projectedW.dot(edgeProjection) / length).coerceIn(0.0, 1.0)
            points.add(a.add(edge.scale(t)))
        }
        return points.minBy { point ->
            val delta = point.subtract(eyes)
            delta.subtract(direction.scale(delta.dot(direction))).lengthSqr()
        }
    }
}
