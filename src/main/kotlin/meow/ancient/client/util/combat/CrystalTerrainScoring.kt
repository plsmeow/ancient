package meow.ancient.client.util.combat

import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import kotlin.math.ceil

internal object CrystalTerrainScoring {
    fun forecastUseful(currentDamage: Float, predictedDamage: Float, minDamage: Float, health: Float, lethalOverride: Boolean): Boolean {
        if (!currentDamage.isFinite() || !predictedDamage.isFinite()) return false
        if (lethalOverride && predictedDamage >= health && health > 0f) return true
        return predictedDamage >= minDamage && predictedDamage >= currentDamage * 0.65f
    }

    fun cycleTicks(miningTicks: Int, pingMs: Int, placeDelay: Int, breakDelay: Int): Int {
        if (miningTicks !in 1..200) return Int.MAX_VALUE
        val roundTrip = ceil(pingMs.coerceIn(0, 4000) / 50.0).toInt()
        return miningTicks + 3 * roundTrip + 3 + 2 * placeDelay.coerceIn(0, 10) + breakDelay.coerceIn(0, 10)
    }

    fun faceMargin(hit: BlockHitResult, boxes: List<AABB>): Double = boxes.filter { it.inflate(1.0E-5).contains(hit.location) }
        .maxOfOrNull { box ->
            val point = hit.location
            when (hit.direction.axis) {
                Direction.Axis.X -> minOf(point.y - box.minY, box.maxY - point.y, point.z - box.minZ, box.maxZ - point.z)
                Direction.Axis.Y -> minOf(point.x - box.minX, box.maxX - point.x, point.z - box.minZ, box.maxZ - point.z)
                Direction.Axis.Z -> minOf(point.x - box.minX, box.maxX - point.x, point.y - box.minY, box.maxY - point.y)
            }.coerceIn(0.0, 0.5)
        } ?: 0.0

    fun score(damage: Float, cycleTicks: Int, faceMargin: Double, reachMargin: Double): Double {
        if (!damage.isFinite() || damage <= 0f || cycleTicks !in 1..500) return Double.NEGATIVE_INFINITY
        val comfort = (faceMargin / 0.25).coerceIn(0.0, 1.0) * 0.7 + reachMargin.coerceIn(0.0, 1.0) * 0.3
        return damage / (1.0 + cycleTicks * 0.05) * (0.85 + 0.15 * comfort)
    }
}
