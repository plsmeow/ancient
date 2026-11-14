package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.combat.CritTimings
import meow.ancient.client.util.combat.ServerCritState
import meow.ancient.module.modules.combat.HitAuraModule.SprintResetMode

class CriticalProcessor(private val thunderHackTiming: Boolean = false) {
    fun reset() {}

    fun isReadyForAttack(
        onlyCrits: Boolean,
        smartCrits: Boolean,
        sprintResetMode: SprintResetMode = SprintResetMode.OFF,
        smashReady: Boolean = false
    ): Boolean {
        // A mace smash uses fall distance, rather than normal melee crit/cooldown conditions.
        if (smashReady || !onlyCrits) return true
        val player = mc.player ?: return false
        if (!requiresCritical(player, onlyCrits, smartCrits, smashReady)) return true
        if (thunderHackTiming) return ThunderHackTiming.critical(player.fallDistance, player.onGround())
        val sprintWillStop = sprintResetMode == SprintResetMode.PACKET
            || sprintResetMode != SprintResetMode.OFF && !player.isSprinting
        if (CritTimings.critNow(player, sprintWillStop)) return true
        return false
    }

    fun shouldPrepareAttack(onlyCrits: Boolean, smartCrits: Boolean, smashReady: Boolean = false): Boolean {
        if (smashReady || !onlyCrits) return true
        val player = mc.player ?: return false
        if (!requiresCritical(player, onlyCrits, smartCrits, smashReady)) return true
        return !player.onGround() || player.input.keyPresses.jump()
    }

    private fun requiresCritical(player: net.minecraft.client.player.LocalPlayer, onlyCrits: Boolean,
                                 smartCrits: Boolean, smash: Boolean): Boolean {
        val jump = mc.options.keyJump.isDown || player.input.keyPresses.jump()
        val blocked = CritTimings.isCritBlocked(player)
        if (thunderHackTiming) return ThunderHackTiming.requiresCrit(onlyCrits, smartCrits, smash, blocked, jump)
        return AttackTiming.requiresCrit(onlyCrits, smartCrits, smash, blocked, player.onGround(), jump,
            ServerCritState.serverOnGround)
    }

    fun shouldStopSprinting(onlyCrits: Boolean, willClickNextTick: Boolean): Boolean {
        if (!onlyCrits) return false
        val player = mc.player ?: return false
        return CritTimings.shouldStopSprinting(player, willClickNextTick)
    }

    fun shouldPrepareSprintReset(onlyCrits: Boolean, smartCrits: Boolean, ready: Boolean, smash: Boolean): Boolean {
        if (!ready) return false
        val player = mc.player ?: return false
        return !requiresCritical(player, onlyCrits, smartCrits, smash) || CritTimings.shouldStopSprinting(player, true)
    }
}
