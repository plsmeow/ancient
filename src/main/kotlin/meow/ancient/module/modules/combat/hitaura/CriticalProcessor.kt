package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.combat.CritTimings
import meow.ancient.module.modules.combat.HitAuraModule.SprintResetMode

class CriticalProcessor {
    fun reset() {}

    fun isReadyForAttack(
        onlyCrits: Boolean,
        smartCrits: Boolean,
        sprintResetMode: SprintResetMode = SprintResetMode.OFF
    ): Boolean {
        if (!onlyCrits) return true
        val player = mc.player ?: return false
        val sprintWillStop = sprintResetMode != SprintResetMode.OFF
        if (CritTimings.critNow(player, sprintWillStop)) return true
        if (!smartCrits) return false
        return !CritTimings.critReachable(player)
    }

    fun shouldPrepareAttack(onlyCrits: Boolean, smartCrits: Boolean): Boolean {
        if (!onlyCrits) return true
        val player = mc.player ?: return false
        return CritTimings.critReachable(player) || !smartCrits
    }

    fun shouldStopSprinting(onlyCrits: Boolean, willClickNextTick: Boolean): Boolean {
        if (!onlyCrits) return false
        val player = mc.player ?: return false
        return CritTimings.shouldStopSprinting(player, willClickNextTick)
    }
}
