package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.module.modules.combat.HitAuraModule.SprintResetMode
import meow.ancient.module.modules.movement.InventoryMoveModule
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket

class SprintResetProcessor {
    private var stoppedSprint = false
    private var restorePacketSprint = false
    private var releaseAfterMovement = false
    private var mode = SprintResetMode.OFF

    fun isResettingSprint(): Boolean = stoppedSprint

    fun reset() {
        stoppedSprint = false
        restorePacketSprint = false
        releaseAfterMovement = false
        mode = SprintResetMode.OFF
    }

    fun prepareAttack(mode: SprintResetMode, readyNextMovement: Boolean) {
        this.mode = mode
        val player = mc.player ?: return
        val canReset = inGame && mode != SprintResetMode.OFF
            && !player.isSwimming && !player.isUnderWater && !player.isInLava && !player.isFallFlying
        if (!canReset || !readyNextMovement) {
            releaseAfterMovement = stoppedSprint
            if (releaseAfterMovement) reset()
            return
        }
        if (!stoppedSprint) {
            restorePacketSprint = mode == SprintResetMode.PACKET && (player.isSprinting || player.wasSprinting)
        }
        stoppedSprint = true
        releaseAfterMovement = false
        if (player.wasSprinting) {
            player.connection.send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING))
            player.wasSprinting = false
        }
        player.isSprinting = false
    }

    fun preAttack(mode: SprintResetMode): Boolean {
        if (!inGame) return false
        if (mode == SprintResetMode.OFF) return true
        this.mode = mode
        val player = mc.player ?: return false
        if (player.isSwimming || player.isUnderWater || player.isInLava || player.isFallFlying) return true
        if (player.isSprinting || player.wasSprinting) {
            player.connection.send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING))
        }
        player.isSprinting = false
        player.wasSprinting = false
        stoppedSprint = true
        releaseAfterMovement = false
        return true
    }

    fun postAttack(executed: Boolean) {
        if (executed) releaseAfterMovement = stoppedSprint
    }

    fun onPreMovement() {
        if (!stoppedSprint) return
        val player = mc.player ?: return
        player.isSprinting = false
        releaseAfterMovement = true
    }

    fun onMovementSent() {
        if (!releaseAfterMovement) return
        val restoreSprint = restorePacketSprint
        val currentMode = mode
        reset()
        if (currentMode == SprintResetMode.LB || !restoreSprint || InventoryMoveModule.shouldPreventSprint()) return
        val player = mc.player ?: return
        if (player.connection.connection.isConnected) player.isSprinting = true
    }
}
