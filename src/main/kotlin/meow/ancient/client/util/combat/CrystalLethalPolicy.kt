package meow.ancient.client.util.combat

internal object CrystalLethalPolicy {
    fun mayTrade(targetHealth: Float, minimumDamage: Float, selfDamage: Float,
                 enemyProtected: Boolean, selfProtected: Boolean, recentlyPopped: Boolean): Boolean =
        targetHealth.isFinite() && targetHealth > 0f && minimumDamage.isFinite() && selfDamage.isFinite()
            && selfDamage >= 0f && minimumDamage >= targetHealth + 0.5f
            && !enemyProtected && selfProtected && !recentlyPopped
}
