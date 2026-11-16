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
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.floor

/** LiquidBounce edge bootstrap, warmup blend and recent placement offsets. */
class ScaffoldMovementPrediction {
    private val offsets = ArrayDeque<Vec3>(4)
    fun reset() = offsets.clear()

    fun onPlace(line: ScaffoldMovementPlanner.Line?, fallOff: Vec3?, position: Vec3) {
        if (line == null || fallOff == null) return
        val angle = atan2(line.direction.z, line.direction.x).toFloat()
        offsets.addLast(position.subtract(fallOff).yRot(angle))
        while (offsets.size > 4) offsets.removeFirst()
    }

    fun predictedPosition(player: LocalPlayer, level: ClientLevel, planner: ScaffoldMovementPlanner,
                          line: ScaffoldMovementPlanner.Line?): Vec3? {
        line ?: return null
        if (!ScaffoldPlacement.hasSupport(player, level, player.boundingBox.move(line.direction.scale(0.05)))) return null
        val edge = fallOffPosition(player, level, line) ?: return null
        val delta = edge.subtract(player.position())
        val bootstrap = if (delta.lengthSqr() < 1.0e-12) edge else edge.subtract(delta.normalize().scale(0.2))
        if (offsets.isEmpty()) return planner.supportReference?.let { bootstrap.add(it.offsetX, 0.0, it.offsetZ) } ?: bootstrap
        val average = offsets.reduce { a, b -> a.add(b) }.scale(1.0 / offsets.size)
        val angle = atan2(line.direction.z, line.direction.x).toFloat()
        val fromHistory = edge.add(average.yRot(-angle))
        val result = bootstrap.lerp(fromHistory, (offsets.size / 2.0).coerceIn(0.0, 1.0))
        // This prediction anticipates horizontal placement. Height always comes from current feet.
        return Vec3(result.x, player.y, result.z)
    }

    fun fallOffPosition(player: LocalPlayer, level: ClientLevel, line: ScaffoldMovementPlanner.Line): Vec3? {
        val from = line.nearest(player.position()).add(0.0, -0.1, 0.0)
        val to = from.add(line.direction.scale(3.0))
        val halfWidth = player.boundingBox.xsize * 0.5
        val union = player.boundingBox.move(from.subtract(player.position()))
            .minmax(player.boundingBox.move(to.subtract(player.position())))
        val boxes = ArrayList<AABB>()
        for (x in floor(union.minX - halfWidth - 1.0e-7).toInt()..floor(union.maxX + halfWidth + 1.0e-7).toInt())
            for (z in floor(union.minZ - halfWidth - 1.0e-7).toInt()..floor(union.maxZ + halfWidth + 1.0e-7).toInt())
                for (y in floor(union.minY - 0.5 - 1.0e-7).toInt()..floor(union.minY + 1.0e-7).toInt()) {
                    val pos = BlockPos(x, y, z)
                    val state = BlockPredictionUtil.serverState(level, pos)
                    for (local in state.getCollisionShape(level, pos).toAabbs()) {
                        val block = local.move(pos)
                        boxes.add(AABB(block.minX - halfWidth, block.minY - 1.0, block.minZ - halfWidth,
                            block.maxX + halfWidth, block.maxY + 0.55, block.maxZ + halfWidth))
                    }
                }
        return edgeCollision(from, to, boxes)?.let { Vec3(it.x, player.y, it.z) }
    }

    companion object {
        /** Port of MovementUtils.findEdgeCollision, separated from world reads for geometry checks. */
        @JvmStatic
        fun edgeCollision(from: Vec3, to: Vec3, boxes: List<AABB>): Vec3? {
            val movement = to.subtract(from)
            if (movement.lengthSqr() <= 1.0e-12) return null
            val remaining = boxes.toMutableList()
            val extendedFrom = from.subtract(movement.scale(1000.0))
            val extendedTo = to.add(movement.scale(1000.0))
            var current = from
            while (true) {
                val containing = remaining.filter { it.contains(current) }
                if (containing.isEmpty()) return current
                if (containing.any { it.contains(to) }) return null
                current = containing.mapNotNull { it.clip(extendedTo, extendedFrom).orElse(null) }
                    .minByOrNull { it.distanceToSqr(to) } ?: return current
                remaining.removeAll(containing.toSet())
            }
        }
    }
}
