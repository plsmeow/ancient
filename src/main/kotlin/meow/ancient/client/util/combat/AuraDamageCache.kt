package meow.ancient.client.util.combat

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/** All candidates in a tick share real terrain shapes and identical damage evaluations. */
internal class AuraDamageCache {
    private data class Key(val id: Int, val position: Vec3, val box: AABB, val explosion: Vec3, val base: BlockPos?,
        val power: Float, val airBase: Boolean, val cleared: Set<BlockPos>)
    private val shapes = Long2ObjectOpenHashMap<VoxelShape>()
    private val damage = HashMap<Key, Float>()
    private var level: ClientLevel? = null
    private var ignoreTerrain = false

    fun beginTick(world: ClientLevel?, ignore: Boolean) {
        level = world; ignoreTerrain = ignore; shapes.clear(); damage.clear()
    }

    fun damage(target: Player, forecast: AuraMotionPrediction.Snapshot, explosion: Vec3, base: BlockPos?,
        power: Float = 12f, airBase: Boolean = false, cleared: Set<BlockPos> = emptySet()): Float {
        val key = Key(target.id, forecast.position, forecast.box, explosion, base, power, airBase, cleared)
        return damage.getOrPut(key) {
            ExplosionDamageUtil.explosionDamage(target, forecast.position, forecast.box, explosion, power,
                ExplosionDamageUtil.RaycastFactory { ray, pos ->
                    val shape = when {
                        pos == base -> if (airBase) Shapes.empty() else Shapes.block()
                        pos in cleared -> Shapes.empty()
                        else -> shape(pos)
                    }
                    shape.clip(ray.start, ray.end, pos)
                })
        }
    }

    private fun shape(pos: BlockPos): VoxelShape {
        val key = pos.asLong()
        shapes[key]?.let { return it }
        val world = level ?: return Shapes.empty()
        val state = world.getBlockState(pos)
        val shape = if (state.isAir || !ignoreTerrain && state.block.explosionResistance < 600f) Shapes.empty()
            else state.getCollisionShape(world, pos)
        shapes.put(key, shape)
        return shape
    }
}
