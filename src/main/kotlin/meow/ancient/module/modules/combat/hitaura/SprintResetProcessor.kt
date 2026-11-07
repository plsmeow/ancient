package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.module.modules.combat.HitAuraModule.SprintResetMode
import meow.ancient.module.modules.movement.InventoryMoveModule

class SprintResetProcessor {
    private var resetTicks = 0
    private var mode = SprintResetMode.OFF

    fun isResettingSprint(): Boolean = resetTicks > 0

    fun reset() {
        resetTicks = 0
        mode = SprintResetMode.OFF
    }

    fun prepareAttack(mode: SprintResetMode, readyNextMovement: Boolean) {
        this.mode = mode
        if (readyNextMovement) {
            val player = mc.player ?: return
            if (player.isSprinting) {
                player.setSprinting(false)
                player.isSprinting = false
                mc.options.keySprint.isDown = false
                resetTicks = 1
            }
        }
    }

    fun preAttack(mode: SprintResetMode): Boolean {
        if (!inGame) return false
        this.mode = mode
        return true
    }

    fun postAttack(executed: Boolean) {
        if (!executed || !inGame || mode == SprintResetMode.OFF) return
        val player = mc.player ?: return
        if (player.isSwimming || player.isUnderWater || player.isInLava || player.isFallFlying) return
        player.setSprinting(false)
        player.isSprinting = false
        mc.options.keySprint.isDown = false
        resetTicks = if (mode == SprintResetMode.LEGIT) 2 else 1
    }

    fun onUpdate() {
        if (resetTicks <= 0) return
        resetTicks--
        if (resetTicks <= 0) {
            val player = mc.player ?: return
            if (!InventoryMoveModule.shouldPreventSprint() && player.input.hasForwardImpulse()) {
                player.setSprinting(true)
            }
        }
    }

    fun onPreMovement() {
    }

    fun onMovementSent() {
    }
}
