package meow.ancient.client.util.combat

/** Mining may supersede a ready placement only when its gain justifies the extra work. */
internal object CrystalTerrainPriority {
    fun shouldBoost(damage: Float, availableDamage: Float, health: Float, minimumGain: Float, miningTicks: Int): Boolean {
        if (!damage.isFinite() || !availableDamage.isFinite() || !health.isFinite() || !minimumGain.isFinite()) return false
        if (availableDamage <= 0f || health <= 0f || availableDamage >= health || miningTicks !in 1..20) return false
        val gain = maxOf(minimumGain.coerceAtLeast(1f), availableDamage * 0.25f)
        return damage >= availableDamage + gain
    }
}
