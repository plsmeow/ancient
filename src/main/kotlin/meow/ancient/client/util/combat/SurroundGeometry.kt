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
}
