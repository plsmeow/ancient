package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.module.modules.combat.HitAuraModule.SprintResetMode

class SprintResetProcessor {
    fun isResettingSprint(): Boolean = false

    fun reset() {
    }

    fun onTick() {
    }

    fun preAttack(mode: SprintResetMode, forceStopSprint: Boolean = false): Boolean {
        if (!inGame) return false
        val player = mc.player ?: return false
        if (forceStopSprint && player.isSprinting) {
            player.isSprinting = false
            return true
        }
        return false
    }

    fun postAttack(mode: SprintResetMode, packetReset: Boolean = false) {
    }

    fun onSprintEvent() {
    }

    fun shouldAllowSprintWhenHungry(): Boolean = inGame && meow.ancient.module.modules.combat.HitAuraModule.sprintReset != SprintResetMode.OFF

    fun shouldPreserveAttackKnockback(): Boolean = inGame && meow.ancient.module.modules.combat.HitAuraModule.sprintReset != SprintResetMode.OFF
}
