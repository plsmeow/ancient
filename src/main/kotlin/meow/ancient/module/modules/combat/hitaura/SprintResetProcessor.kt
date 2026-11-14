package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.combat.ServerCritState
import meow.ancient.module.modules.combat.HitAuraModule.SprintResetMode
import meow.ancient.module.modules.movement.InventoryMoveModule
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket

/** A sprint stop must cross a movement boundary before a nonsprinting attack. */
class SprintResetProcessor {
    private var owner: LocalPlayer? = null
    private var holdUntil = -1
    private var restoreSprint = false
    private var resumeNextUpdate = false
    private var mode = SprintResetMode.OFF

    fun isResettingSprint(): Boolean = owner === mc.player
        && (resumeNextUpdate || holdUntil >= 0 && (owner?.tickCount ?: Int.MAX_VALUE) <= holdUntil)

    fun reset() {
        resumeSprint()
        owner = null
        holdUntil = -1
        resumeNextUpdate = false
        mode = SprintResetMode.OFF
    }

    fun prepareAttack(mode: SprintResetMode, readyNextMovement: Boolean) {
        if (this.mode != mode) reset()
        this.mode = mode
        if (!readyNextMovement || mode == SprintResetMode.OFF) return
        val player = mc.player ?: return
        owner = player
        restoreSprint = restoreSprint || player.isSprinting
        // Hold through the next update: the stop must be visible in the preceding movement packet.
        if (holdUntil < 0) holdUntil = player.tickCount + 2
        player.isSprinting = false
    }

    fun cancelPreparation() {
        if (resumeNextUpdate) return // Complete the accepted attack's sprint restore at the next update.
        holdUntil = -1
        resumeSprint()
    }

    fun onPreMovement() {
        val player = owner ?: return
        if (player !== mc.player || !isResettingSprint()) return
        player.isSprinting = false
        stopServerSprint(player)
    }

    fun preAttack(mode: SprintResetMode): Boolean {
        this.mode = mode
        val player = mc.player ?: return false
        if (mode == SprintResetMode.OFF || player.isFallFlying || player.isSwimming || player.isInLiquid) return true
        if (player.isSprinting || !stopServerSprint(player)) return false
        // A STOP packet alone does not change Grim's sprint state from the previous movement.
        return AttackTiming.sprintReady(player.isSprinting, ServerCritState.serverSprinting,
            ServerCritState.serverMovementSprinting)
    }

    fun postAttack(executed: Boolean) {
        val player = owner ?: return
        if (!executed || player !== mc.player || mode == SprintResetMode.OFF) return
        player.isSprinting = false
        if (mode == SprintResetMode.PACKET) {
            // Resume before the next physics step, not between this step and its movement packet.
            resumeNextUpdate = true
        } else {
            holdUntil = player.tickCount + if (mode == SprintResetMode.LEGIT) 2 else 1
        }
    }

    fun onUpdate() {
        val player = mc.player
        if (owner !== player) {
            owner = null
            holdUntil = -1
            restoreSprint = false
            resumeNextUpdate = false
            return
        }
        if (player != null && (resumeNextUpdate || holdUntil >= 0 && player.tickCount >= holdUntil)) {
            holdUntil = -1
            resumeNextUpdate = false
            resumeSprint()
        }
        if (holdUntil >= 0) player?.isSprinting = false
    }

    private fun stopServerSprint(player: LocalPlayer): Boolean {
        if (ServerCritState.serverSprinting) {
            player.connection.send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING))
            if (ServerCritState.serverSprinting) return false
        }
        player.wasSprinting = false
        return true
    }

    private fun resumeSprint() {
        val player = owner
        if (restoreSprint && player != null && player === mc.player && player.connection.connection.isConnected
            && !player.isDeadOrDying && !InventoryMoveModule.shouldPreventSprint()
            && player.input.hasForwardImpulse() && !player.isUsingItem && !player.isShiftKeyDown) {
            player.isSprinting = true
        }
        restoreSprint = false
    }
}
