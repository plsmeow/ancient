package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.movement.MoveUtil
import meow.ancient.client.util.rotation.MoveFixProcessor
import meow.ancient.module.modules.combat.HitAuraModule.SprintResetMode
import meow.ancient.module.modules.movement.AutoSprintModule

class SprintResetProcessor {
    private var legitResetTicks: Int = 0

    fun isResettingSprint(): Boolean = legitResetTicks > 0

    fun reset() {
        legitResetTicks = 0
    }

    fun onTick() {
        if (!inGame) return
        val localPlayer = mc.player ?: return
        if (legitResetTicks > 0) {
            legitResetTicks--
            if (legitResetTicks == 0) {
                val shouldSprint = mc.options.keySprint.isDown
                    || MoveUtil.isKeyPressed(mc.options.keySprint)
                    || AutoSprintModule.isEnabled()
                    || MoveFixProcessor.hasForwardInput
                if (shouldSprint) {
                    localPlayer.isSprinting = true
                    mc.options.keySprint.isDown = true
                }
            } else {
                localPlayer.isSprinting = false
            }
        }
    }

    fun preAttack(mode: SprintResetMode, forceStopSprint: Boolean = false) {
        if (!inGame) return
        if (forceStopSprint) {
            mc.player?.isSprinting = false
        }
    }

    fun postAttack(mode: SprintResetMode) {
        if (!inGame || mode == SprintResetMode.OFF) return

        mc.player?.isSprinting = false
        legitResetTicks = 1
    }
}
