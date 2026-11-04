package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.combat.CritTimings
import meow.ancient.client.util.combat.CritTimings.SelectionMode
import meow.ancient.module.modules.combat.CriticalsModule

class CriticalProcessor {
    fun reset() {}

    fun isReadyForAttack(onlyCrits: Boolean, smartCrits: Boolean): Boolean {
        if (!inGame) return false
        if (!onlyCrits) return true
        return CriticalsModule.canPrepareCrit() || CritTimings.isCriticalHit(mc.player ?: return false, mode(smartCrits))
    }

    fun shouldPrepareAttack(onlyCrits: Boolean, smartCrits: Boolean): Boolean = isReadyForAttack(onlyCrits, smartCrits)

    fun shouldStopSprinting(onlyCrits: Boolean, willClickNextTick: Boolean): Boolean {
        if (!inGame || !onlyCrits) return false
        val player = mc.player ?: return false
        return CritTimings.shouldStopSprinting(player, SelectionMode.SMART, true, willClickNextTick)
    }

    private fun mode(smartCrits: Boolean): SelectionMode =
        if (smartCrits) SelectionMode.SMART else SelectionMode.ALWAYS
}
