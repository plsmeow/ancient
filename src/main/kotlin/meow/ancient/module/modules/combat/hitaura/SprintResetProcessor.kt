package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.module.modules.combat.HitAuraModule.SprintResetMode

class SprintResetProcessor {
    private var suppressSprint = false

    fun isResettingSprint(): Boolean = suppressSprint

    fun reset() {
        suppressSprint = false
    }

    fun onTick() {
        suppressSprint = false
    }

    fun prepareAttack(forceStopSprint: Boolean) {
        suppressSprint = forceStopSprint
        if (!suppressSprint || !inGame) return
        val player = mc.player ?: return
        player.isSprinting = false
        mc.options.keySprint.isDown = false
    }

    fun preAttack(mode: SprintResetMode, forceStopSprint: Boolean = false): Boolean {
        if (!inGame) return false
        val player = mc.player ?: return false
        return !forceStopSprint || !player.isSprinting
    }

    fun postAttack(mode: SprintResetMode, packetReset: Boolean = false) {
    }

    fun onSprintEvent() {
    }

    fun shouldAllowSprintWhenHungry(): Boolean = inGame && meow.ancient.client.util.anticheat.AnticheatManager.isPacketSprintReset

    fun shouldPreserveAttackKnockback(): Boolean = inGame && meow.ancient.client.util.anticheat.AnticheatManager.isPacketSprintReset
}
