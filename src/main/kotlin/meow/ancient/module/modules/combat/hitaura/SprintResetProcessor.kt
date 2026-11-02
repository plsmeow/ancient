package meow.ancient.module.modules.combat.hitaura

import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.module.modules.combat.HitAuraModule.SprintResetMode
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket

class SprintResetProcessor {
    private var blocked = false
    private var release = false
    private var modeNow = SprintResetMode.OFF

    fun isResettingSprint(): Boolean = blocked

    fun reset() {
        blocked = false
        release = false
        modeNow = SprintResetMode.OFF
    }

    fun prepareAttack(mode: SprintResetMode) {
        if (mode == SprintResetMode.OFF) {
            reset()
            return
        }
        blocked = true
        release = false
        modeNow = mode
    }

    fun onAttack() {
        if (modeNow == SprintResetMode.LEGIT) release = true
    }

    fun onTick() {
        if (!inGame) {
            reset()
            return
        }
        val player = mc.player ?: run {
            reset()
            return
        }
        if (release && canSprint(player)) {
            player.isSprinting = true
            player.connection.send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING))
            reset()
        }
    }

    private fun canSprint(player: net.minecraft.client.player.LocalPlayer): Boolean =
        !player.isInWater && !player.isInLava && !player.isSwimming && !player.onClimbable()
            && !player.abilities.flying && !player.isFallFlying && player.onGround()
}
