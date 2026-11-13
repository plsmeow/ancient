package meow.ancient.client.util.combat

import meow.ancient.client.extensions.mc
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.Difficulty
import net.minecraft.world.damagesource.CombatRules
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.item.enchantment.ItemEnchantments
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.function.BiFunction
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ExplosionDamageUtil {

    class ExposureRaycastContext(val start: Vec3, val end: Vec3)

    fun interface RaycastFactory : BiFunction<ExposureRaycastContext, BlockPos, BlockHitResult?>

    fun getOverridingHitFactory(
        overridePos: BlockPos?,
        overrideState: BlockState?,
        ignoreTerrain: Boolean = false
    ): RaycastFactory {
        return RaycastFactory { context, blockPos ->
            val level = mc.level ?: return@RaycastFactory null
            val blockState = if (blockPos == overridePos && overrideState != null) overrideState else level.getBlockState(blockPos)
            if (blockState.isAir) return@RaycastFactory null
            if (!ignoreTerrain && blockPos != overridePos && blockState.block.explosionResistance < 600f) return@RaycastFactory null
            blockState.getCollisionShape(level, blockPos).clip(context.start, context.end, blockPos)
        }
    }

    fun crystalDamage(
        target: LivingEntity,
        crystal: Vec3,
        predictMovement: Boolean = false,
        obsidianPos: BlockPos? = null,
        ignoreTerrain: Boolean = false,
        ignoreCreative: Boolean = false
    ): Float {
        val factory = getOverridingHitFactory(
            obsidianPos,
            if (obsidianPos != null) Blocks.OBSIDIAN.defaultBlockState() else null,
            ignoreTerrain
        )
        return explosionDamage(target, crystal, 12f, predictMovement, factory, ignoreCreative)
    }

    fun crystalDamage(
        explosionPos: Vec3,
        target: LivingEntity,
        obsidianPos: BlockPos? = null,
        ignoreTerrain: Boolean = false,
        ignoreCreative: Boolean = false
    ): Float {
        return crystalDamage(target, explosionPos, false, obsidianPos, ignoreTerrain, ignoreCreative)
    }

    fun anchorDamage(target: LivingEntity, anchorPos: BlockPos, ignoreTerrain: Boolean = false, ignoreCreative: Boolean = false): Float {
        val anchorCenter = Vec3(anchorPos.x + 0.5, anchorPos.y + 1.0, anchorPos.z + 0.5)
        return explosionDamage(
            target,
            anchorCenter,
            10f,
            false,
            getOverridingHitFactory(anchorPos, Blocks.AIR.defaultBlockState(), ignoreTerrain),
            ignoreCreative
        )
    }

    fun anchorDamage(anchorPos: BlockPos, target: LivingEntity, ignoreTerrain: Boolean = false, ignoreCreative: Boolean = false): Float {
        return anchorDamage(target, anchorPos, ignoreTerrain, ignoreCreative)
    }

    fun explosionDamage(
        target: LivingEntity,
        explosionPos: Vec3,
        power: Float,
        predictMovement: Boolean,
        raycastFactory: RaycastFactory,
        ignoreCreative: Boolean = false
    ): Float {
        if (target is Player && (target.isCreative && !ignoreCreative || target.isSpectator)) return 0f

        val position = if (predictMovement) target.position().add(target.deltaMovement) else target.position()
        var box = target.boundingBox
        if (predictMovement) box = box.move(target.deltaMovement)

        return explosionDamage(target, position, box, explosionPos, power, raycastFactory)
    }

    fun explosionDamage(
        target: LivingEntity,
        targetPos: Vec3,
        targetBox: AABB,
        explosionPos: Vec3,
        power: Float,
        raycastFactory: RaycastFactory
    ): Float {
        val level = mc.level ?: return 0f
        val dx = targetPos.x - explosionPos.x
        val dy = targetPos.y - explosionPos.y
        val dz = targetPos.z - explosionPos.z
        val modDistance = sqrt(dx * dx + dy * dy + dz * dz)
        if (modDistance > power) return 0f

        val exposure = getExposure(explosionPos, targetBox, raycastFactory)
        val impact = (1.0 - (modDistance / power)) * exposure
        val damage = (((impact * impact + impact) / 2.0 * 7.0 * 12.0) + 1.0).toFloat()

        return calculateReductions(damage, target, level.damageSources().explosion(null))
    }

    fun calculateReductions(damage: Float, entity: Entity, damageSource: DamageSource): Float {
        val level = mc.level ?: return damage
        var d = damage
        if (damageSource.scalesWithDifficulty()) {
            when (level.difficulty) {
                Difficulty.EASY -> d = min(d / 2f + 1f, d)
                Difficulty.HARD -> d *= 1.5f
                else -> {}
            }
        }

        if (entity is LivingEntity) {
            val armor = floor(entity.getAttributeValue(Attributes.ARMOR)).toFloat()
            val toughness = entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS).toFloat()
            d = CombatRules.getDamageAfterAbsorb(entity, d, damageSource, armor, toughness)
            d = resistanceReduction(entity, d)
            d = protectionReduction(entity, d, damageSource)
        }

        return max(d, 0f)
    }

    private fun resistanceReduction(player: LivingEntity, damage: Float): Float {
        val resistance = player.getEffect(MobEffects.RESISTANCE)
        if (resistance != null) {
            val lvl = resistance.amplifier + 1
            return max(damage * (1f - (lvl * 0.2f)), 0f)
        }
        return max(damage, 0f)
    }

    private val ARMOR_SLOTS = listOf(
        EquipmentSlot.FEET,
        EquipmentSlot.LEGS,
        EquipmentSlot.CHEST,
        EquipmentSlot.HEAD
    )

    private fun protectionReduction(player: LivingEntity, damage: Float, source: DamageSource): Float {
        if (source.`is`(DamageTypeTags.BYPASSES_INVULNERABILITY)) return damage

        var damageProtection = 0

        for (slot in ARMOR_SLOTS) {
            val stack = player.getItemBySlot(slot)
            if (stack.isEmpty) continue

            damageProtection += getEnchantmentLevel(stack, Enchantments.PROTECTION)

            if (source.`is`(DamageTypeTags.IS_FIRE)) {
                damageProtection += 2 * getEnchantmentLevel(stack, Enchantments.FIRE_PROTECTION)
            }
            if (source.`is`(DamageTypeTags.IS_EXPLOSION)) {
                damageProtection += 2 * getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION)
            }
            if (source.`is`(DamageTypeTags.IS_PROJECTILE)) {
                damageProtection += 2 * getEnchantmentLevel(stack, Enchantments.PROJECTILE_PROTECTION)
            }
            if (source.`is`(DamageTypeTags.IS_FALL)) {
                damageProtection += 3 * getEnchantmentLevel(stack, Enchantments.FEATHER_FALLING)
            }
        }

        return CombatRules.getDamageAfterMagicAbsorb(damage, damageProtection.toFloat())
    }

    private fun getEnchantmentLevel(stack: ItemStack, enchantmentKey: ResourceKey<Enchantment>): Int {
        val enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
        for (entry in enchantments.entrySet()) {
            if (entry.key.`is`(enchantmentKey)) {
                return entry.intValue
            }
        }
        return 0
    }

    fun getExposure(source: Vec3, box: AABB, raycastFactory: RaycastFactory): Double {
        val xDiff = box.maxX - box.minX
        val yDiff = box.maxY - box.minY
        val zDiff = box.maxZ - box.minZ

        val xStep = 1.0 / (xDiff * 2.0 + 1.0)
        val yStep = 1.0 / (yDiff * 2.0 + 1.0)
        val zStep = 1.0 / (zDiff * 2.0 + 1.0)

        if (xStep <= 0.0 || yStep <= 0.0 || zStep <= 0.0) return 0.0

        var misses = 0
        var hits = 0

        val xOffset = (1.0 - floor(1.0 / xStep) * xStep) * 0.5
        val zOffset = (1.0 - floor(1.0 / zStep) * zStep) * 0.5

        val actualXStep = xStep * xDiff
        val actualYStep = yStep * yDiff
        val actualZStep = zStep * zDiff

        val startX = box.minX + xOffset
        val startY = box.minY
        val startZ = box.minZ + zOffset
        val endX = box.maxX + xOffset
        val endY = box.maxY
        val endZ = box.maxZ + zOffset

        var x = startX
        while (x <= endX) {
            var y = startY
            while (y <= endY) {
                var z = startZ
                while (z <= endZ) {
                    val position = Vec3(x, y, z)
                    if (raycast(ExposureRaycastContext(position, source), raycastFactory) == null) {
                        misses++
                    }
                    hits++
                    z += actualZStep
                }
                y += actualYStep
            }
            x += actualXStep
        }

        return if (hits > 0) misses.toDouble() / hits.toDouble() else 0.0
    }

    private fun raycast(context: ExposureRaycastContext, raycastFactory: RaycastFactory): BlockHitResult? {
        return MeteorDamageHelper.raycast(context.start, context.end, context, raycastFactory)
    }
}
