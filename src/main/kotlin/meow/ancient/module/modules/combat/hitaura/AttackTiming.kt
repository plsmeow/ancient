package meow.ancient.module.modules.combat.hitaura

/** Timing decisions contain no world access, so prediction cannot mutate the attack clock. */
internal object AttackTiming {
    const val COOLDOWN_THRESHOLD = 1f
    // Update runs before physics: one tick predicts the current attack, two prepare its prior movement.
    const val ATTACK_PREPARATION_TICKS = 1
    const val SPRINT_PREPARATION_TICKS = 2

    fun charge(ticks: Int, attackSpeed: Double, ahead: Float = 0f): Float {
        if (!attackSpeed.isFinite() || attackSpeed <= 0.0) return 0f
        return ((ticks.coerceAtLeast(0) + 0.5 + ahead.coerceAtLeast(0f)) * attackSpeed / 20.0)
            .toFloat().coerceIn(0f, 1f)
    }

    fun requiresCrit(onlyCrits: Boolean, smartCrits: Boolean, smash: Boolean, blocked: Boolean,
                     onGround: Boolean, jump: Boolean, serverOnGround: Boolean): Boolean =
        onlyCrits && !smash && !blocked && !(smartCrits && onGround && serverOnGround && !jump)

    fun fallingCrit(fall: Double, onGround: Boolean, sprinting: Boolean, sprintWillStop: Boolean): Boolean =
        !onGround && fall.isFinite() && fall > 0.0 && (!sprinting || sprintWillStop)

    fun sprintReady(localSprinting: Boolean, serverSprinting: Boolean, movementSprinting: Boolean): Boolean =
        !localSprinting && !serverSprinting && !movementSprinting
}

/** Aura.autoCrit/getAttackCooldown/getHitTicks from Pan4ur/ThunderHack-Recode (49c76bcc). */
internal object ThunderHackTiming {
    const val COOLDOWN_THRESHOLD = 0.9f
    const val HIT_TICKS = 11
    const val PREPARATION_TICKS = 1

    fun requiresCrit(onlyCrits: Boolean, smartCrits: Boolean, smash: Boolean, blocked: Boolean,
                     jump: Boolean): Boolean =
        onlyCrits && !smash && !blocked && !(smartCrits && !jump)

    fun critical(fall: Double, onGround: Boolean): Boolean =
        !onGround && fall.isFinite() && fall > 0.0 && !(fall > 1.0 && fall < 1.14)

    fun charged(ticks: Int, attackSpeed: Double): Boolean =
        AttackTiming.charge(ticks, attackSpeed) >= COOLDOWN_THRESHOLD
}

internal class AttackTickClock {
    private var remaining = 0

    fun reset() { remaining = 0 }
    fun tick() { if (remaining > 0) remaining-- }
    fun clicked(ticks: Int) { remaining = ticks.coerceAtLeast(0) }
    fun ready(ahead: Int = 0): Boolean = remaining <= ahead.coerceAtLeast(0)
}

/** Real time limits charge even when client ticks run faster or another accepted action resets it. */
internal class AttackCooldownClock {
    private var restartNanos: Long? = null

    fun reset() { restartNanos = null }
    fun restart(now: Long) { restartNanos = now }
    fun elapsedTicks(now: Long, aheadNanos: Long = 0L): Int = restartNanos?.let {
        val elapsed = (now - it + aheadNanos.coerceAtLeast(0L)).coerceAtLeast(0L)
        // Round to a whole server tick: 599 ms between client ticks must not become eleven sword ticks.
        val whole = elapsed / 50_000_000L
        val fraction = if (elapsed % 50_000_000L >= 25_000_000L) 1 else 0
        (whole + fraction).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    } ?: Int.MAX_VALUE
}

internal class AttackClickClock {
    private var lastClick: Long? = null
    private var delayNanos = 0L

    fun reset() { lastClick = null; delayNanos = 0L }
    fun ready(now: Long, aheadNanos: Long = 0L): Boolean =
        lastClick?.let { now - it + aheadNanos.coerceAtLeast(0L) >= delayNanos } ?: true

    fun clicked(now: Long, delayMs: Long) {
        lastClick = now
        delayNanos = delayMs.coerceIn(1L, 1000L) * 1_000_000L
    }
}
