package meow.ancient.client.util.combat

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import kotlin.math.floor

internal object SurroundGeometry {
    /** Cover every occupied foot cell, including a player straddling a block boundary. */
    fun ring(box: AABB): Set<BlockPos> {
        val y = floor(box.minY + 1.0E-5).toInt()
        val occupied = BlockPos.betweenClosed(floor(box.minX + 1.0E-5).toInt(), y, floor(box.minZ + 1.0E-5).toInt(),
            floor(box.maxX - 1.0E-5).toInt(), y, floor(box.maxZ - 1.0E-5).toInt()).map { it.immutable() }.toSet()
        return buildSet {
            for (pos in occupied) for (direction in Direction.Plane.HORIZONTAL)
                pos.relative(direction).takeUnless { it in occupied }?.let { add(it) }
        }
    }

    /** Nearby cover at feet/head height, overhead and under the player; not a fixed ring. */
    fun protectionShell(box: AABB): Set<BlockPos> {
        val minX = floor(box.minX + 1.0E-5).toInt()
        val maxX = floor(box.maxX - 1.0E-5).toInt()
        val minZ = floor(box.minZ + 1.0E-5).toInt()
        val maxZ = floor(box.maxZ - 1.0E-5).toInt()
        val feet = floor(box.minY + 1.0E-5).toInt()
        val roof = kotlin.math.ceil(box.maxY - 1.0E-5).toInt()
        return buildSet {
            for (x in minX - 1..maxX + 1) for (z in minZ - 1..maxZ + 1) for (y in feet..roof) {
                val pos = BlockPos(x, y, z)
                if (!AABB(pos).intersects(box)) add(pos)
            }
            for (x in minX..maxX) for (z in minZ..maxZ) {
                BlockPos(x, feet - 1, z).takeUnless { AABB(it).intersects(box) }?.let { add(it) }
            }
        }
    }

    /** Short support chains can approach overhead cover from a side instead of through the player. */
    fun supportPaths(root: BlockPos): Sequence<List<BlockPos>> = sequence {
        val sides = listOf(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)
        for (side in sides) yield(listOf(root, root.relative(side)))
        for (first in sides) for (second in sides) {
            val neighbor = root.relative(first)
            val end = neighbor.relative(second)
            if (end != root) yield(listOf(root, neighbor, end))
        }
    }
}
