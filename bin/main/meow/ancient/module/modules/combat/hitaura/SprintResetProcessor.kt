package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.module.modules.combat.HitAuraModule.SprintResetMode
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket

class SprintResetProcessor {
    private var stoppedSprint = false

    fun isResettingSprint(): Boolean = stoppedSprint

    fun reset() {
        stoppedSprint = false
    }

    fun onTick() {}

    fun preAttack(mode: SprintResetMode): Boolean {
        if (!inGame) return false
        if (mode != SprintResetMode.PACKET) return true
        val player = mc.player ?: return false
        if (player.isSwimming || player.isUnderWater || player.isInLava || player.isFallFlying) return true
        if (!player.isSprinting) return true
        stoppedSprint = true
        player.isSprinting = false
        player.connection.send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING))
        return true
    }

    fun postAttack(mode: SprintResetMode) {
        if (mode != SprintResetMode.PACKET || !stoppedSprint) return
        stoppedSprint = false
        val player = mc.player ?: return
        player.isSprinting = true
        player.connection.send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING))
    }

    fun shouldAllowSprintWhenHungry(): Boolean = inGame && AnticheatManager.isPacketSprintReset

    fun shouldPreserveAttackKnockback(): Boolean = inGame && AnticheatManager.isPacketSprintReset
}
