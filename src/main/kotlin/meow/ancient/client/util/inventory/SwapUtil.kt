package meow.ancient.client.util.inventory

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.anticheat.AnticheatManager
import meow.ancient.module.modules.movement.InventoryMoveModule
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.player.Input

object SwapUtil {
    fun isSlownessActive(): Boolean =
        AnticheatManager.isSwapBypassActive || SlownessManager.isSlowed()

    fun clickWithBypass(
        closeAfter: Boolean = true,
        durationMs: Long = 75L,
        action: () -> Unit
    ) {
        val player = mc.player ?: return
        val connection = player.connection

        if (!AnticheatManager.isSwapBypassActive) {
            action()
            if (closeAfter) {
                connection.send(ServerboundContainerClosePacket(player.containerMenu.containerId))
            }
            return
        }

        SlownessManager.addTask(durationMs) {
            val wasSprinting = player.isSprinting || mc.options.keySprint.isDown
            if (wasSprinting) {
                mc.options.keySprint.isDown = false
                player.isSprinting = false
                connection.send(
                    ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING)
                )
            }

            connection.send(ServerboundPlayerInputPacket(Input.EMPTY))

            action()

            if (closeAfter) {
                connection.send(ServerboundContainerClosePacket(0))
            }
        }
    }
}
