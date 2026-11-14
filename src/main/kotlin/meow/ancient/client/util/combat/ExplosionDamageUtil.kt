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
import net.minecraft.world.attribute.EnvironmentAttributes
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import java.util.function.BiFunction
import kotlin.math.acos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ExplosionDamageUtil {

    // These are damage diameters (twice the vanilla explosion radius).
    const val CRYSTAL_DIAMETER = 12f
    const val ANCHOR_DIAMETER = 10f

    fun anchorExplodes(level: Level, pos: BlockPos): Boolean =
        !level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, pos)

    class ExposureRaycastContext(val start: Vec3, val end: Vec3)

    fun interface RaycastFactory : BiFunction<ExposureRaycastContext, BlockPos, BlockHitResult?>

    fun getOverridingHitFactory(
        overridePos: BlockPos?,
        overrideState: BlockState?,
        ignoreTerrain: Boolean = false,
        target: Entity? = null
    ): RaycastFactory {
        val level = mc.level
        val collision = if (target == null) CollisionContext.empty() else CollisionContext.of(target)
        return RaycastFactory { context, blockPos ->
            if (level == null) return@RaycastFactory null
            val blockState = if (blockPos == overridePos && overrideState != null) overrideState else level.getBlockState(blockPos)
            if (blockState.isAir) return@RaycastFactory null
            // Exposure is evaluated before terrain destruction, even for fragile blocks.
            if (ignoreTerrain && blockPos != overridePos && blockState.block.explosionResistance < 600f) return@RaycastFactory null
            blockState.getCollisionShape(level, blockPos, collision).clip(context.start, context.end, blockPos)
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
            ignoreTerrain,
            target
        )
        return explosionDamage(target, crystal, CRYSTAL_DIAMETER, predictMovement, factory, ignoreCreative)
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
        val level = mc.level ?: return 0f
        if (!anchorExplodes(level, anchorPos)) return 0f
        val anchorCenter = Vec3.atCenterOf(anchorPos)
        return explosionDamage(
            target,
            anchorCenter,
            ANCHOR_DIAMETER,
            false,
            getOverridingHitFactory(anchorPos, Blocks.AIR.defaultBlockState(), ignoreTerrain, target),
            ignoreCreative,
            level.damageSources().badRespawnPointExplosion(anchorCenter)
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
        ignoreCreative: Boolean = false,
        damageSource: DamageSource? = null
    ): Float {
        if (target is Player && (target.isCreative && !ignoreCreative || target.isSpectator)) return 0f

        val position = if (predictMovement) target.position().add(target.deltaMovement) else target.position()
        var box = target.boundingBox
        if (predictMovement) box = box.move(target.deltaMovement)

        return explosionDamage(target, position, box, explosionPos, power, raycastFactory, ignoreCreative, damageSource)
    }

    fun explosionDamage(
        target: LivingEntity,
        targetPos: Vec3,
        targetBox: AABB,
        explosionPos: Vec3,
        power: Float,
        raycastFactory: RaycastFactory,
        ignoreCreative: Boolean = false,
        damageSource: DamageSource? = null
    ): Float {
        val level = mc.level ?: return 0f
        if (!target.isAlive || target.isRemoved || target is Player &&
            (target.isSpectator || target.abilities.invulnerable && !ignoreCreative)) return 0f
        if (!power.isFinite() || power <= 0f) return 0f
        val dx = targetPos.x - explosionPos.x
        val dy = targetPos.y - explosionPos.y
        val dz = targetPos.z - explosionPos.z
        val modDistance = sqrt(dx * dx + dy * dy + dz * dz)
        if (!modDistance.isFinite() || modDistance > power) return 0f

        val exposure = getExposure(explosionPos, targetBox, raycastFactory).toFloat()
        val impact = (1.0 - (modDistance / power)) * exposure
        val damage = (((impact * impact + impact) / 2.0 * 7.0 * power) + 1.0).toFloat()

        val source = damageSource ?: DamageSource(level.damageSources().explosion(null).typeHolder(), explosionPos)
        return calculateReductions(damage, target, source, targetPos)
    }

    /** Damage against health + absorption; absorption is not subtracted twice by callers. */
    fun calculateReductions(damage: Float, entity: Entity, damageSource: DamageSource, position: Vec3 = entity.position()): Float {
        if (!damage.isFinite() || damage <= 0f) return 0f
        val level = entity.level()
        var d = damage
        // Difficulty scaling happens in Player.hurtServer, not LivingEntity.hurtServer.
        if (entity is Player && damageSource.scalesWithDifficulty()) {
            when (level.difficulty) {
                Difficulty.PEACEFUL -> return 0f
                Difficulty.EASY -> d = min(d / 2f + 1f, d)
                Difficulty.HARD -> d = d * 3f / 2f
                else -> {}
            }
        }

        if (entity is LivingEntity) {
            d -= blockedDamage(entity, d, damageSource, position)
            if (!damageSource.`is`(DamageTypeTags.BYPASSES_ARMOR)) {
                val armor = entity.armorValue.toFloat()
                val toughness = entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS).toFloat()
                d = CombatRules.getDamageAfterAbsorb(entity, d, damageSource, armor, toughness)
            }
            if (!damageSource.`is`(DamageTypeTags.BYPASSES_EFFECTS)) {
                if (!damageSource.`is`(DamageTypeTags.BYPASSES_RESISTANCE)) d = resistanceReduction(entity, d)
                if (!damageSource.`is`(DamageTypeTags.BYPASSES_ENCHANTMENTS)) d = protectionReduction(entity, d, damageSource)
            }
        }

        return max(d, 0f)
    }

    private fun blockedDamage(entity: LivingEntity, damage: Float, source: DamageSource, position: Vec3): Float {
        val stack = entity.itemBlockingWith ?: return 0f
        val blocking = stack.get(DataComponents.BLOCKS_ATTACKS) ?: return 0f
        if (blocking.bypassedBy().map { it.contains(source.typeHolder()) }.orElse(false)) return 0f
        val origin = source.sourcePosition
        val angle = if (origin == null) Math.PI else {
            val delta = origin.subtract(position)
            val direction = Vec3(delta.x, 0.0, delta.z).normalize()
            val view = Vec3.directionFromRotation(0f, entity.yHeadRot)
            acos(direction.dot(view).coerceIn(-1.0, 1.0))
        }
        return blocking.resolveBlockedDamage(source, damage, angle)
    }

    private fun resistanceReduction(player: LivingEntity, damage: Float): Float {
        val resistance = player.getEffect(MobEffects.RESISTANCE)
        if (resistance != null) {
            val lvl = resistance.amplifier + 1
            return max(damage * (25 - lvl * 5) / 25f, 0f)
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

            damageProtection += getEnchantmentLevel(stack, Enchantments.PROTECTION, slot)

            if (source.`is`(DamageTypeTags.IS_FIRE)) {
                damageProtection += 2 * getEnchantmentLevel(stack, Enchantments.FIRE_PROTECTION, slot)
            }
            if (source.`is`(DamageTypeTags.IS_EXPLOSION)) {
                damageProtection += 2 * getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION, slot)
            }
            if (source.`is`(DamageTypeTags.IS_PROJECTILE)) {
                damageProtection += 2 * getEnchantmentLevel(stack, Enchantments.PROJECTILE_PROTECTION, slot)
            }
            if (source.`is`(DamageTypeTags.IS_FALL)) {
                damageProtection += 3 * getEnchantmentLevel(stack, Enchantments.FEATHER_FALLING, slot)
            }
        }

        return CombatRules.getDamageAfterMagicAbsorb(damage, damageProtection.toFloat())
    }

    private fun getEnchantmentLevel(stack: ItemStack, enchantmentKey: ResourceKey<Enchantment>, slot: EquipmentSlot): Int {
        val enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
        for (entry in enchantments.entrySet()) {
            if (entry.key.`is`(enchantmentKey) && entry.key.value().matchingSlot(slot)) {
                return entry.intValue
            }
        }
        return 0
    }

    fun getExposure(source: Vec3, box: AABB, raycastFactory: RaycastFactory): Double {
        if (!source.x.isFinite() || !source.y.isFinite() || !source.z.isFinite()
            || !box.minX.isFinite() || !box.minY.isFinite() || !box.minZ.isFinite()
            || !box.maxX.isFinite() || !box.maxY.isFinite() || !box.maxZ.isFinite()) return 0.0
        val xDiff = box.maxX - box.minX
        val yDiff = box.maxY - box.minY
        val zDiff = box.maxZ - box.minZ

        val xStep = 1.0 / (xDiff * 2.0 + 1.0)
        val yStep = 1.0 / (yDiff * 2.0 + 1.0)
        val zStep = 1.0 / (zDiff * 2.0 + 1.0)

        if (!xStep.isFinite() || !yStep.isFinite() || !zStep.isFinite()
            || xStep <= 0.0 || yStep <= 0.0 || zStep <= 0.0) return 0.0

        var misses = 0
        var hits = 0

        val xOffset = (1.0 - floor(1.0 / xStep) * xStep) * 0.5
        val zOffset = (1.0 - floor(1.0 / zStep) * zStep) * 0.5

        // Match ServerExplosion.getSeenPercent's normalized loop. World-coordinate
        // increments accumulate rounding errors and never advance on a zero-sized axis.
        var x = 0.0
        while (x <= 1.0) {
            var y = 0.0
            while (y <= 1.0) {
                var z = 0.0
                while (z <= 1.0) {
                    val position = Vec3(box.minX + x * xDiff + xOffset,
                        box.minY + y * yDiff, box.minZ + z * zDiff + zOffset)
                    if (raycast(ExposureRaycastContext(position, source), raycastFactory) == null) {
                        misses++
                    }
                    hits++
                    z += zStep
                }
                y += yStep
            }
            x += xStep
        }

        return if (hits > 0) misses.toDouble() / hits.toDouble() else 0.0
    }

    private fun raycast(context: ExposureRaycastContext, raycastFactory: RaycastFactory): BlockHitResult? {
        return MeteorDamageHelper.raycast(context.start, context.end, context, raycastFactory)
    }
}
