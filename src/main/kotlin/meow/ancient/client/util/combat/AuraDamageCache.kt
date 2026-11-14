package meow.ancient.client.util.combat

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/** Reuse terrain per victim collision context, and damage only within the current tick. */
internal class AuraDamageCache {
    private data class Key(val id: Int, val position: Vec3, val box: AABB, val explosion: Vec3, val base: BlockPos?,
        val power: Float, val airBase: Boolean, val cleared: Set<BlockPos>, val ignoreCreative: Boolean,
        val placed: Set<BlockPos>)
    private val shapes = HashMap<Pair<Int, Double>, Long2ObjectOpenHashMap<VoxelShape>>()
    private val damage = HashMap<Key, Float>()
    private var level: ClientLevel? = null
    private var ignoreTerrain = false

    fun beginTick(world: ClientLevel?, ignore: Boolean) {
        level = world; ignoreTerrain = ignore; shapes.clear(); damage.clear()
    }

    fun damage(target: Player, forecast: AuraMotionPrediction.Snapshot, explosion: Vec3, base: BlockPos?,
        power: Float = ExplosionDamageUtil.CRYSTAL_DIAMETER, airBase: Boolean = false,
        cleared: Set<BlockPos> = emptySet(), ignoreCreative: Boolean = false, placed: Set<BlockPos> = emptySet()): Float {
        val world = level ?: return 0f
        if (airBase && base != null && !ExplosionDamageUtil.anchorExplodes(world, base)) return 0f
        val key = Key(target.id, forecast.position, forecast.box, explosion, base, power, airBase, cleared.toSet(), ignoreCreative, placed.toSet())
        return damage.getOrPut(key) {
            val collision = CollisionContext.withPosition(target, forecast.box.minY)
            val terrain = shapes.getOrPut(target.id to forecast.box.minY) { Long2ObjectOpenHashMap() }
            ExplosionDamageUtil.explosionDamage(target, forecast.position, forecast.box, explosion, power,
                ExplosionDamageUtil.RaycastFactory { ray, pos ->
                    val shape = when {
                        pos == base -> if (airBase) Shapes.empty() else Shapes.block()
                        pos in placed -> Shapes.block()
                        pos in cleared -> Shapes.empty()
                        else -> shape(pos, collision, terrain)
                    }
                    shape.clip(ray.start, ray.end, pos)
                }, ignoreCreative, if (airBase) world.damageSources().badRespawnPointExplosion(explosion) else null)
        }
    }

    private fun shape(pos: BlockPos, collision: CollisionContext, terrain: Long2ObjectOpenHashMap<VoxelShape>): VoxelShape {
        val key = pos.asLong()
        terrain[key]?.let { return it }
        val world = level ?: return Shapes.empty()
        val state = world.getBlockState(pos)
        val shape = if (state.isAir || ignoreTerrain && state.block.explosionResistance < 600f) Shapes.empty()
            else state.getCollisionShape(world, pos, collision)
        terrain.put(key, shape)
        return shape
    }
}
