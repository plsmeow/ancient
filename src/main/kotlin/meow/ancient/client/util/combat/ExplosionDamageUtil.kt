package meow.ancient.client.util.combat

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.Difficulty
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

object ExplosionDamageUtil {

    class SimulatedBlockView(
        private val base: BlockGetter,
        private val overridePos: BlockPos,
        private val overrideState: BlockState
    ) : BlockGetter {
        override fun getBlockEntity(pos: BlockPos): BlockEntity? =
            if (pos == overridePos) null else base.getBlockEntity(pos)

        override fun getBlockState(pos: BlockPos): BlockState =
            if (pos == overridePos) overrideState else base.getBlockState(pos)

        override fun getFluidState(pos: BlockPos): FluidState =
            if (pos == overridePos) Fluids.EMPTY.defaultFluidState() else base.getFluidState(pos)

        override fun getHeight(): Int = base.height
        override fun getMinY(): Int = base.minY
    }

    fun crystalDamage(explosionPos: Vec3, target: LivingEntity): Float =
        computeDamage(explosionPos, target, target.level(), 6.0f)

    fun anchorDamage(anchorPos: BlockPos, target: LivingEntity): Float {
        val simulated = SimulatedBlockView(target.level(), anchorPos, Blocks.AIR.defaultBlockState())
        return computeDamage(Vec3.atCenterOf(anchorPos).add(0.0, 0.5, 0.0), target, simulated, 5.0f)
    }

    private fun computeDamage(
        explosionPos: Vec3,
        target: LivingEntity,
        blockGetter: BlockGetter,
        power: Float
    ): Float {
        if (target is Player && (target.isCreative || target.isSpectator)) return 0.0f
        val maxDist = power * 2.0
        val dist = target.position().distanceTo(explosionPos)
        if (dist > maxDist) return 0.0f

        val box = target.boundingBox
        val exposure = getExposure(blockGetter, explosionPos, box, CollisionContext.of(target))
        return applyReductions(target, dist, exposure, power)
    }

    private fun applyReductions(target: LivingEntity, dist: Double, exposure: Double, power: Float): Float {
        if (target is Player && (target.isCreative || target.isSpectator)) return 0.0f
        val level = target.level()
        val difficulty = if (level is Level) level.difficulty else Difficulty.NORMAL
        val raw = rawDamage(dist, exposure, power, difficulty)
        if (raw <= 0.0f) return 0.0f

        val armor = target.armorValue.toFloat()
        val toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS).toFloat()
        val clampedArmor = (armor - raw / (2.0f + toughness / 4.0f)).coerceIn(armor * 0.2f, 20.0f)
        var damage = raw * (1.0f - clampedArmor / 25.0f)

        val resistance = target.getEffect(MobEffects.RESISTANCE)
        if (resistance != null) {
            val resistanceFactor = (1.0f - (resistance.amplifier + 1) * 0.2f).coerceAtLeast(0.0f)
            damage *= resistanceFactor
        }

        return damage.coerceAtLeast(0.0f)
    }

    private fun rawDamage(dist: Double, exposure: Double, power: Float, difficulty: Difficulty): Float {
        val maxDist = power * 2.0
        if (dist > maxDist) return 0.0f

        val impact = (1.0 - dist / maxDist) * exposure
        val baseDamage = ((impact * impact + impact) * 7.0 * power.toDouble() + 1.0).toFloat()

        return when (difficulty) {
            Difficulty.PEACEFUL -> 0.0f
            Difficulty.EASY -> minOf(baseDamage / 2.0f + 1.0f, baseDamage)
            Difficulty.NORMAL -> baseDamage
            Difficulty.HARD -> baseDamage * 1.5f
        }
    }

    private fun getExposure(
        blockGetter: BlockGetter,
        source: Vec3,
        box: AABB,
        context: CollisionContext
    ): Double {
        val stepX = 1.0 / (box.xsize * 2.0 + 1.0)
        val stepY = 1.0 / (box.ysize * 2.0 + 1.0)
        val stepZ = 1.0 / (box.zsize * 2.0 + 1.0)
        val offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0
        val offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0

        var missed = 0
        var total = 0

        var x = 0.0
        while (x <= 1.0) {
            var y = 0.0
            while (y <= 1.0) {
                var z = 0.0
                while (z <= 1.0) {
                    val point = Vec3(
                        Mth.lerp(x, box.minX, box.maxX) + offsetX,
                        Mth.lerp(y, box.minY, box.maxY),
                        Mth.lerp(z, box.minZ, box.maxZ) + offsetZ
                    )
                    val clip = blockGetter.clip(
                        ClipContext(
                            point,
                            source,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            context
                        )
                    )
                    if (clip.type == HitResult.Type.MISS) {
                        missed++
                    }
                    total++
                    z += stepZ
                }
                y += stepY
            }
            x += stepX
        }

        return if (total == 0) 0.0 else missed.toDouble() / total.toDouble()
    }
}
