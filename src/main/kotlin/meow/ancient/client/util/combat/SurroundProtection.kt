package meow.ancient.client.util.combat

import meow.ancient.client.util.anticheat.AnticheatManager
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** A real explosion, or a known placement that will subsequently be detonated. */
internal data class SurroundExplosion(
    val center: Vec3,
    val diameter: Float,
    val base: BlockPos? = null,
    val anchor: Boolean = false,
    val prospective: Boolean = false,
    val reserved: Set<BlockPos> = emptySet(),
    val targetId: Int? = null,
    val minTargetDamage: Float = 0f,
    val lethalOverride: Boolean = false,
    val immediate: Boolean = true
)

/** Compare hypothetical cover using the same terrain and reduction rules as the auras. */
internal class SurroundProtection {
    data class Gain(val riskReduction: Double, val damageReduction: Float)
    private data class Threat(val explosion: SurroundExplosion, val damage: Float, val target: Player?, val targetDamage: Float)
    private val cache = AuraDamageCache()
    private var player: Player? = null
    private var safeDamage = 0f
    private var threats = emptyList<Threat>()
    private var attacks = emptyList<Threat>()
    private var reserved = emptySet<BlockPos>()
    val hasThreats get() = threats.isNotEmpty()

    fun clear() {
        player = null; threats = emptyList(); attacks = emptyList(); reserved = emptySet()
        cache.beginTick(null, false)
    }

    fun update(level: ClientLevel, victim: Player, explosions: List<SurroundExplosion>, safe: Float) {
        player = victim
        safeDamage = minOf(safe, (victim.health + victim.absorptionAmount - 0.5f).coerceAtLeast(0f))
        cache.beginTick(level, false)
        reserved = explosions.filter { it.targetId != null }.flatMap { it.reserved }.toSet()
        val evaluated = explosions.map { explosion ->
            val target = explosion.targetId?.let { level.getEntity(it) as? Player }?.takeIf { it.isAlive }
            Threat(explosion, damage(explosion, victim), target, target?.let { damage(explosion, it, ignoreCreative = true) } ?: 0f)
        }
        attacks = evaluated.filter { it.target != null }
        // Rank by actual exposure/damage, so nearby crystals behind a wall cannot hide a dangerous source.
        threats = evaluated.filter { it.damage > safeDamage }.sortedByDescending {
            val risk = (it.damage - safeDamage).toDouble()
            risk * risk * if (it.explosion.immediate) 2.0 else 1.0
        }.take(16)
    }

    fun gain(placed: Set<BlockPos>): Gain? {
        val victim = player ?: return null
        if (placed.any { it in reserved } || threats.isEmpty()) return null
        for (attack in attacks) {
            val target = attack.target ?: continue
            if (!couldShield(target.boundingBox, attack.explosion.center, placed)) continue
            val after = damage(attack.explosion, target, placed, ignoreCreative = true)
            val health = AnticheatManager.getHealth(target)
            val required = if (attack.explosion.lethalOverride && health > 0f) minOf(attack.explosion.minTargetDamage, health)
                else attack.explosion.minTargetDamage
            if (after + 1.0E-4f < minOf(required, attack.targetDamage)) return null
        }
        var reduction = 0.0
        var bestReduction = 0f
        for (threat in threats) {
            val explosion = threat.explosion
            if (!couldShield(victim.boundingBox, explosion.center, placed)
                && !(explosion.prospective && placed.any { it in explosion.reserved })) continue
            val after = damage(explosion, victim, placed)
            val difference = threat.damage - after
            if (difference <= 0f) continue
            val beforeRisk = (threat.damage - safeDamage).coerceAtLeast(0f).toDouble()
            val afterRisk = (after - safeDamage).coerceAtLeast(0f).toDouble()
            reduction += (beforeRisk * beforeRisk - afterRisk * afterRisk) * if (explosion.immediate) 2.0 else 1.0
            bestReduction = maxOf(bestReduction, difference)
        }
        return if (reduction > 0.0) Gain(reduction, bestReduction) else null
    }

    private fun damage(explosion: SurroundExplosion, victim: Player, placed: Set<BlockPos> = emptySet(),
                       ignoreCreative: Boolean = false): Float {
        // Filling the spawn cell prevents a hypothetical enemy crystal from being placed.
        if (explosion.prospective && explosion.targetId == null && placed.any { it in explosion.reserved }) return 0f
        return cache.damage(victim, AuraMotionPrediction.Snapshot(victim.position(), victim.boundingBox),
            explosion.center, explosion.base, explosion.diameter, airBase = explosion.anchor,
            ignoreCreative = ignoreCreative, placed = placed)
    }

    private fun couldShield(box: AABB, source: Vec3, placed: Set<BlockPos>): Boolean {
        val rays = AABB(minOf(box.minX, source.x), minOf(box.minY, source.y), minOf(box.minZ, source.z),
            maxOf(box.maxX, source.x), maxOf(box.maxY, source.y), maxOf(box.maxZ, source.z)).inflate(1.0E-5)
        return placed.any { AABB(it).intersects(rays) }
    }
}
