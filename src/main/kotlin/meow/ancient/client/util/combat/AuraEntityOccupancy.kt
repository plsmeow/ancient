package meow.ancient.client.util.combat

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySelector
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.phys.AABB
import kotlin.math.ceil
import kotlin.math.floor

/** One nearby entity pass replaces per-cell world queries during position searches. */
internal class AuraEntityOccupancy {
    private val bases = LongOpenHashSet()
    private val occupied = LongOpenHashSet()
    private val crystals = LongOpenHashSet()
    val endCrystals = ArrayList<EndCrystal>()

    fun clear() { bases.clear(); occupied.clear(); crystals.clear(); endCrystals.clear() }

    fun update(entities: Iterable<Entity>, bounds: AABB) {
        clear()
        val crystalBounds = bounds.inflate(2.0)
        for (entity in entities) {
            if (entity.isRemoved || !EntitySelector.NO_SPECTATORS.test(entity)) continue
            val box = entity.boundingBox
            if (entity is EndCrystal && entity.isAlive && box.intersects(crystalBounds)) endCrystals.add(entity)
            if (!box.intersects(bounds)) continue
            add(box, bounds, entity.isAlive, entity.blocksBuilding)
        }
    }

    internal fun add(box: AABB, bounds: AABB, alive: Boolean, blocksBuilding: Boolean) {
        val minX = floor(maxOf(box.minX, bounds.minX)).toInt()
        val maxX = ceil(minOf(box.maxX, bounds.maxX)).toInt() - 1
        val minZ = floor(maxOf(box.minZ, bounds.minZ)).toInt()
        val maxZ = ceil(minOf(box.maxZ, bounds.maxZ)).toInt() - 1
        val minY = floor(maxOf(box.minY, bounds.minY)).toInt()
        val maxY = ceil(minOf(box.maxY, bounds.maxY)).toInt() - 1
        if (blocksBuilding) for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ)
            bases.add(BlockPos.asLong(x, y, z))
        if (alive) {
            for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) occupied.add(BlockPos.asLong(x, y, z))
            for (x in minX..maxX) for (y in minY - 2..maxY - 1) for (z in minZ..maxZ) crystals.add(BlockPos.asLong(x, y, z))
        }
    }

    fun baseBlocked(pos: BlockPos) = bases.contains(pos.asLong())
    fun crystalBlocked(pos: BlockPos) = crystals.contains(pos.asLong())
    fun aliveBlocked(pos: BlockPos) = occupied.contains(pos.asLong())
}
